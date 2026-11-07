package com.memora.app

object StorageScanner {
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
