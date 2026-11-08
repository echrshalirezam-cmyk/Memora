package com.memora.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest

data class ManagedEntry(
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val uri: Uri,
    val mimeType: String?,
    val modified: Long,
    val relativePath: String
)

data class ManagedTree(
    val rootName: String,
    val entries: List<ManagedEntry>,
    val emptyFolders: List<ManagedEntry>,
    val totalBytes: Long
) {
    val files: List<ManagedEntry> get() = entries.filter { !it.isDirectory }
    val folders: List<ManagedEntry> get() = entries.filter { it.isDirectory }
    val largestFiles: List<ManagedEntry> get() = files.sortedByDescending { it.size }.take(30)
    val largestFolders: List<ManagedEntry> get() = folders.filter { it.size > 0L }.sortedByDescending { it.size }.take(20)
}

object TreeStorageManager {
    private fun uniqueName(parent: DocumentFile, requested: String): String =
        StorageAlgorithms.uniqueName(requested) { candidate -> parent.findFile(candidate) != null }
    suspend fun scan(
        context: Context,
        treeUri: Uri,
        pauseCheck: suspend () -> Unit = {},
        onProgress: suspend (Int, Int) -> Unit = { _, _ -> }
    ): ManagedTree = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("پوشه انتخاب‌شده در دسترس نیست")
        if (!root.canRead()) throw SecurityException("مجوز خواندن پوشه وجود ندارد")
        val all = mutableListOf<ManagedEntry>()
        val empty = mutableListOf<ManagedEntry>()
        var visited = 0
        var bytes = 0L

        suspend fun walk(dir: DocumentFile, path: String) {
            pauseCheck()
            currentCoroutineContext().ensureActive()
            // Publish activity before querying SAF; some SD-card providers take time here.
            onProgress(visited, -1)
            val children = dir.listFiles()
            if (children.isEmpty() && dir.uri != root.uri) {
                empty.add(ManagedEntry(dir.name ?: "بدون نام", 0L, true, dir.uri, null, dir.lastModified(), path))
            }
            for (child in children) {
                pauseCheck()
                currentCoroutineContext().ensureActive()
                val name = child.name ?: "بدون نام"
                val childPath = if (path.isEmpty()) name else "$path/$name"
                val folder = child.isDirectory
                val size = if (folder) 0L else child.length().coerceAtLeast(0L)
                if (!folder) bytes += size
                all.add(ManagedEntry(name, size, folder, child.uri, child.type, child.lastModified(), childPath))
                visited++
                // Update for every entry so the UI never appears frozen at zero.
                onProgress(visited, -1)
                if (folder) walk(child, childPath)
            }
        }

        onProgress(0, -1)
        walk(root, "")
        onProgress(visited, -1)
        val folderSizes = StorageAlgorithms.aggregateFolderSizes(
            all.filter { !it.isDirectory }.map { it.relativePath to it.size }
        )
        val sizedEntries = all.map { entry ->
            if (entry.isDirectory) entry.copy(size = folderSizes[entry.relativePath] ?: 0L) else entry
        }
        val sizedEmpty = empty.map { folder ->
            folder.copy(size = folderSizes[folder.relativePath] ?: 0L)
        }
        ManagedTree(root.name ?: "حافظه انتخاب‌شده", sizedEntries, sizedEmpty, bytes)
    }

    suspend fun deleteSelection(context: Context, tree: ManagedTree, uris: List<Uri>): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val selected = tree.entries.filter { it.uri in uris }
        val expanded = selected.toMutableList()
        selected.filter { it.isDirectory }.forEach { folder ->
            expanded.addAll(tree.entries.filter { it.relativePath.startsWith(folder.relativePath + "/") })
        }
        val ordered = expanded.distinctBy { it.uri }.sortedByDescending { it.relativePath.split("/").size }
        var success = 0
        var failed = 0
        ordered.forEach { entry ->
            if (DocumentFile.fromSingleUri(context, entry.uri)?.delete() == true) success++ else failed++
        }
        success to failed
    }

    suspend fun createFolderInPath(context: Context, treeUri: Uri, relativePath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
        var parent = root
        if (relativePath.isNotBlank()) {
            for (part in relativePath.split("/").filter { it.isNotBlank() }) {
                parent = parent.findFile(part) ?: return@withContext false
            }
        }
        if (!parent.isDirectory || !parent.canWrite() || name.isBlank() || name.contains("/") || parent.findFile(name) != null) return@withContext false
        parent.createDirectory(name) != null
    }

    suspend fun rename(context: Context, uri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        if (newName.isBlank() || newName.contains('/')) return@withContext false
        DocumentFile.fromSingleUri(context, uri)?.renameTo(newName) ?: false
    }

    suspend fun delete(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        DocumentFile.fromSingleUri(context, uri)?.delete() ?: false
    }

    suspend fun deleteMany(context: Context, uris: List<Uri>): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var success = 0
        var failed = 0
        uris.forEach { if (DocumentFile.fromSingleUri(context, it)?.delete() == true) success++ else failed++ }
        success to failed
    }

    suspend fun copyOrMoveEntry(
        context: Context,
        sourceTreeUri: Uri,
        entry: ManagedEntry,
        destinationTreeUri: Uri,
        move: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val sourceRoot = DocumentFile.fromTreeUri(context, sourceTreeUri) ?: return@withContext false
        val destination = DocumentFile.fromTreeUri(context, destinationTreeUri) ?: return@withContext false
        var source = sourceRoot
        for (part in entry.relativePath.split("/").filter { it.isNotBlank() }) {
            source = source.findFile(part) ?: return@withContext false
        }
        if (!source.canRead() || !destination.canWrite()) return@withContext false
        val sourceDocumentId = DocumentsContract.getDocumentId(source.uri)
        val destinationDocumentId = DocumentsContract.getTreeDocumentId(destinationTreeUri)
        if (source.isDirectory && (destinationDocumentId == sourceDocumentId || destinationDocumentId.startsWith("$sourceDocumentId/"))) return@withContext false
        if (!source.isDirectory && sourceDocumentId.substringBeforeLast("/", "") == destinationDocumentId) return@withContext false

        suspend fun copyNode(node: DocumentFile, parent: DocumentFile): DocumentFile {
            if (node.isDirectory) {
                val newDir = parent.createDirectory(uniqueName(parent, node.name ?: "پوشه"))
                    ?: throw IllegalStateException("ساخت پوشه مقصد ناموفق بود")
                node.listFiles().forEach { child -> copyNode(child, newDir) }
                return newDir
            }
            val target = parent.createFile(node.type ?: "application/octet-stream", uniqueName(parent, node.name ?: "file"))
                ?: throw IllegalStateException("ساخت فایل مقصد ناموفق بود")
            try {
                context.contentResolver.openInputStream(node.uri).use { input ->
                    context.contentResolver.openOutputStream(target.uri, "w").use { output ->
                        if (input == null || output == null) throw IllegalStateException("فایل باز نشد")
                        input.copyTo(output)
                        output.flush()
                    }
                }
            } catch (e: Exception) {
                target.delete()
                throw e
            }
            return target
        }

        suspend fun deleteNode(node: DocumentFile): Boolean {
            if (node.isDirectory) {
                var allDeleted = true
                node.listFiles().forEach { child -> if (!deleteNode(child)) allDeleted = false }
                if (!allDeleted) return false
            }
            return node.delete()
        }

        try {
            copyNode(source, destination)
            if (move) deleteNode(source) else true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun findSameNames(entries: List<ManagedEntry>): List<List<ManagedEntry>> = withContext(Dispatchers.Default) {
        StorageAlgorithms.groupExactNames(
            entries.filter { !it.isDirectory },
            name = { it.name },
            size = { it.size }
        )
    }

    suspend fun findSimilarNames(entries: List<ManagedEntry>): List<List<ManagedEntry>> = withContext(Dispatchers.Default) {
        StorageAlgorithms.groupSimilarNames(
            entries.filter { !it.isDirectory },
            name = { it.name },
            size = { it.size }
        )
    }

    suspend fun findDuplicates(context: Context, entries: List<ManagedEntry>): List<List<ManagedEntry>> = withContext(Dispatchers.IO) {
        val bySize = entries.filter { !it.isDirectory && it.size > 0L }.groupBy { it.size }.values.filter { it.size > 1 }
        val groups = mutableListOf<List<ManagedEntry>>()
        for (sameSize in bySize) {
            val hashed = sameSize.mapNotNull { entry ->
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val hash = context.contentResolver.openInputStream(entry.uri).use { input ->
                        if (input == null) null else {
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                digest.update(buffer, 0, read)
                            }
                            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                        }
                    }
                    if (hash == null) null else hash to entry
                } catch (_: Exception) {
                    null
                }
            }
            val byHash = hashed.groupBy({ it.first }, { it.second })
            byHash.values.filter { it.size > 1 }.forEach { groups.add(it) }
        }
        groups
    }
}
