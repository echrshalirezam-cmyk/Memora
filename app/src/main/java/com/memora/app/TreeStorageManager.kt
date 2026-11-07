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
}

object TreeStorageManager {
    private fun uniqueName(parent: DocumentFile, requested: String): String {
        if (parent.findFile(requested) == null) return requested
        val dot = requested.lastIndexOf('.')
        val base = if (dot > 0) requested.substring(0, dot) else requested
        val extension = if (dot > 0) requested.substring(dot) else ""
        var index = 1
        var candidate: String
        do {
            candidate = "$base ($index)$extension"
            index++
        } while (parent.findFile(candidate) != null)
        return candidate
    }
    suspend fun scan(
        context: Context,
        treeUri: Uri,
        pauseCheck: suspend () -> Unit = {},
        onProgress: suspend (Int, Int) -> Unit = { _, _ -> }
    ): ManagedTree = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("پوشه انتخاب‌شده در دسترس نیست")
        if (!root.canRead()) throw SecurityException("مجوز خواندن پوشه وجود ندارد")
        val pending = mutableListOf<Pair<DocumentFile, String>>()
        val empty = mutableListOf<ManagedEntry>()

        suspend fun discover(dir: DocumentFile, path: String) {
            pauseCheck()
            currentCoroutineContext().ensureActive()
            val children = dir.listFiles()
            if (children.isEmpty() && dir.uri != root.uri) {
                empty.add(ManagedEntry(dir.name ?: "بدون نام", 0L, true, dir.uri, null, dir.lastModified(), path))
            }
            for (child in children) {
                pauseCheck()
                currentCoroutineContext().ensureActive()
                val name = child.name ?: "بدون نام"
                val childPath = if (path.isEmpty()) name else "$path/$name"
                pending.add(child to childPath)
                if (child.isDirectory) discover(child, childPath)
                if (pending.size % 25 == 0) onProgress(pending.size, -1)
            }
        }
        discover(root, "")
        val total = pending.size
        onProgress(0, total)
        val all = mutableListOf<ManagedEntry>()
        var bytes = 0L
        pending.forEachIndexed { index, pair ->
            pauseCheck()
            currentCoroutineContext().ensureActive()
            val child = pair.first
            val path = pair.second
            val folder = child.isDirectory
            val size = if (folder) 0L else child.length().coerceAtLeast(0L)
            if (!folder) bytes += size
            all.add(ManagedEntry(child.name ?: "بدون نام", size, folder, child.uri, child.type, child.lastModified(), path))
            if ((index + 1) % 10 == 0 || index == total - 1) onProgress(index + 1, total)
        }
        ManagedTree(root.name ?: "حافظه انتخاب‌شده", all, empty, bytes)
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

    suspend fun createFolder(context: Context, parentUri: Uri, name: String): Boolean = withContext(Dispatchers.IO) {
        val parent = DocumentFile.fromTreeUri(context, parentUri)
            ?: DocumentFile.fromSingleUri(context, parentUri)
            ?: return@withContext false
        if (!parent.canWrite() || name.isBlank() || name.contains('/')) return@withContext false
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

    suspend fun copyOrMove(
        context: Context,
        sourceUri: Uri,
        destinationTreeUri: Uri,
        move: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val source = DocumentFile.fromSingleUri(context, sourceUri) ?: return@withContext false
        val destination = DocumentFile.fromTreeUri(context, destinationTreeUri) ?: return@withContext false
        if (!source.isFile || !source.canRead() || !destination.canWrite()) return@withContext false
        val target = destination.createFile(source.type ?: "application/octet-stream", source.name ?: "file")
            ?: return@withContext false
        try {
            context.contentResolver.openInputStream(source.uri).use { input ->
                context.contentResolver.openOutputStream(target.uri, "w").use { output ->
                    if (input == null || output == null) throw IllegalStateException("جریان فایل باز نشد")
                    input.copyTo(output)
                    output.flush()
                }
            }
            if (move && !source.delete()) {
                target.delete()
                false
            } else true
        } catch (e: Exception) {
            target.delete()
            throw e
        }
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
