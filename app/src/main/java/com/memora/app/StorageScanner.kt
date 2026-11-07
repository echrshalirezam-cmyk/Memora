package com.memora.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaCategoryResult(val count: Int, val bytes: Long)
data class StorageFileItem(
    val id: Long,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val dateModified: Long,
    val uri: Uri
)

data class StorageScanResult(
    val totalBytes: Long,
    val freeBytes: Long,
    val images: MediaCategoryResult,
    val videos: MediaCategoryResult,
    val audio: MediaCategoryResult,
    val documents: MediaCategoryResult,
    val archives: MediaCategoryResult,
    val apks: MediaCategoryResult,
    val other: MediaCategoryResult,
    val largestFiles: List<StorageFileItem>,
    val indexedFileCount: Int,
    val scannedAt: Long = System.currentTimeMillis()
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val mediaCount: Int get() = indexedFileCount
}

object StorageScanner {
    suspend fun scan(context: Context): StorageScanResult = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong

        var imageCount = 0; var imageBytes = 0L
        var videoCount = 0; var videoBytes = 0L
        var audioCount = 0; var audioBytes = 0L
        var docCount = 0; var docBytes = 0L
        var archiveCount = 0; var archiveBytes = 0L
        var apkCount = 0; var apkBytes = 0L
        var otherCount = 0; var otherBytes = 0L
        var totalCount = 0
        val largest = ArrayList<StorageFileItem>()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        context.contentResolver.query(collection, projection, null, null, "${MediaStore.Files.FileColumns.SIZE} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "بدون نام"
                val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                val mime = if (cursor.isNull(mimeCol)) null else cursor.getString(mimeCol)
                val modified = if (cursor.isNull(dateCol)) 0L else cursor.getLong(dateCol)
                totalCount++
                when {
                    mime?.startsWith("image/") == true -> { imageCount++; imageBytes += size }
                    mime?.startsWith("video/") == true -> { videoCount++; videoBytes += size }
                    mime?.startsWith("audio/") == true -> { audioCount++; audioBytes += size }
                    mime == "application/vnd.android.package-archive" || name.endsWith(".apk", true) -> { apkCount++; apkBytes += size }
                    mime == "application/zip" || mime == "application/x-rar-compressed" ||
                        mime == "application/x-7z-compressed" || name.endsWith(".zip", true) ||
                        name.endsWith(".rar", true) || name.endsWith(".7z", true) -> { archiveCount++; archiveBytes += size }
                    mime?.startsWith("text/") == true || mime?.contains("pdf") == true ||
                        mime?.contains("document") == true || mime?.contains("spreadsheet") == true ||
                        mime?.contains("presentation") == true || name.endsWith(".doc", true) ||
                        name.endsWith(".docx", true) || name.endsWith(".xls", true) ||
                        name.endsWith(".xlsx", true) || name.endsWith(".pdf", true) ||
                        name.endsWith(".txt", true) -> { docCount++; docBytes += size }
                    else -> { otherCount++; otherBytes += size }
                }
                if (size > 0L && largest.size < 100) {
                    largest.add(StorageFileItem(id, name, size, mime, modified, Uri.withAppendedPath(collection, id.toString())))
                }
            }
        }

        StorageScanResult(
            totalBytes = total, freeBytes = free,
            images = MediaCategoryResult(imageCount, imageBytes),
            videos = MediaCategoryResult(videoCount, videoBytes),
            audio = MediaCategoryResult(audioCount, audioBytes),
            documents = MediaCategoryResult(docCount, docBytes),
            archives = MediaCategoryResult(archiveCount, archiveBytes),
            apks = MediaCategoryResult(apkCount, apkBytes),
            other = MediaCategoryResult(otherCount, otherBytes),
            largestFiles = largest.sortedByDescending { it.size }.take(20),
            indexedFileCount = totalCount
        )
    }

    fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_AUDIO
        )
        Build.VERSION.SDK_INT >= 23 -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> emptyArray()
    }

    fun hasRequiredPermissions(context: Context): Boolean =
        requiredPermissions().all {
            Build.VERSION.SDK_INT < 23 ||
                context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes بایت"
        val units = arrayOf("کیلوبایت", "مگابایت", "گیگابایت", "ترابایت")
        var value = bytes.toDouble()
        var index = -1
        do {
            value /= 1024.0
            index++
        } while (value >= 1024.0 && index < units.lastIndex)
        return String.format(java.util.Locale("fa"), "%.1f %s", value, units[index])
    }
}
