package com.memora.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaCategoryResult(val count: Int, val bytes: Long)

data class StorageScanResult(
    val totalBytes: Long,
    val freeBytes: Long,
    val images: MediaCategoryResult,
    val videos: MediaCategoryResult,
    val audio: MediaCategoryResult,
    val scannedAt: Long = System.currentTimeMillis()
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val mediaCount: Int get() = images.count + videos.count + audio.count
}

object StorageScanner {
    suspend fun scan(context: Context): StorageScanResult = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong

        StorageScanResult(
            totalBytes = total,
            freeBytes = free,
            images = queryCategory(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            videos = queryCategory(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
            audio = queryCategory(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        )
    }

    private fun queryCategory(context: Context, uri: Uri): MediaCategoryResult {
        var count = 0
        var bytes = 0L
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext()) {
                    count++
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        bytes += cursor.getLong(sizeIndex).coerceAtLeast(0L)
                    }
                }
            }
        } catch (_: SecurityException) {
            // A category without permission is reported as empty; the UI explains granted access.
        }
        return MediaCategoryResult(count, bytes)
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
