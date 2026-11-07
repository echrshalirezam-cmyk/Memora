package com.memora.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

data class TreeFileItem(val name: String, val size: Long, val uri: Uri, val mimeType: String?)
data class TreeScanResult(
    val rootName: String,
    val fileCount: Int,
    val folderCount: Int,
    val totalBytes: Long,
    val largestFiles: List<TreeFileItem>
)

object TreeStorageScanner {
    suspend fun scan(context: Context, treeUri: Uri): TreeScanResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("پوشه انتخاب‌شده قابل دسترسی نیست")
        if (!root.canRead()) throw SecurityException("مجوز خواندن این پوشه داده نشده است")

        val queue = ArrayDeque<DocumentFile>()
        queue.add(root)
        var files = 0
        var folders = 0
        var bytes = 0L
        val largest = ArrayList<TreeFileItem>()

        while (queue.isNotEmpty()) {
            val directory = queue.removeFirst()
            val children = directory.listFiles()
            for (child in children) {
                if (child.isDirectory) {
                    folders++
                    queue.add(child)
                } else if (child.isFile) {
                    files++
                    val size = child.length().coerceAtLeast(0L)
                    bytes += size
                    if (size > 0L) {
                        largest.add(TreeFileItem(child.name ?: "بدون نام", size, child.uri, child.type))
                        largest.sortByDescending { it.size }
                        if (largest.size > 20) largest.removeAt(largest.lastIndex)
                    }
                }
            }
        }
        TreeScanResult(root.name ?: "حافظه انتخاب‌شده", files, folders, bytes, largest)
    }
}
