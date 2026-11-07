package com.memora.app

/**
 * Pure algorithms used by storage analysis. Kept independent from Android APIs
 * so their behavior can be verified with local JVM unit tests.
 */
object StorageAlgorithms {
    fun uniqueName(requested: String, exists: (String) -> Boolean): String {
        if (!exists(requested)) return requested
        val dot = requested.lastIndexOf('.')
        val base = if (dot > 0) requested.substring(0, dot) else requested
        val extension = if (dot > 0) requested.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = "$base ($index)$extension"
            if (!exists(candidate)) return candidate
            index++
        }
    }

    fun normalizeSimilarName(name: String): String {
        var stem = name.substringBeforeLast(".", name).lowercase().trim()
        stem = stem.replace(Regex("""\s*\(\d+\)$"""), "")
        stem = stem.replace(Regex("""\s*(copy|نسخه)\s*\d*$"""), "")
        return stem.trim()
    }

    fun <T> groupExactNames(
        items: List<T>,
        name: (T) -> String,
        size: (T) -> Long
    ): List<List<T>> = items
        .map { name(it).trim().lowercase() to it }
        .filter { it.first.isNotBlank() }
        .groupBy({ it.first }, { it.second })
        .values
        .filter { it.size > 1 }
        .sortedByDescending { group -> group.sumOf(size) }

    fun <T> groupSimilarNames(
        items: List<T>,
        name: (T) -> String,
        size: (T) -> Long
    ): List<List<T>> = items
        .map { normalizeSimilarName(name(it)) to it }
        .filter { it.first.length >= 3 }
        .groupBy({ it.first }, { it.second })
        .values
        .filter { it.size > 1 }
        .sortedByDescending { group -> group.sumOf(size) }

    /** Returns the total bytes of each non-root ancestor directory. */
    fun aggregateFolderSizes(files: List<Pair<String, Long>>): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        files.filter { it.second > 0L }.forEach { (path, bytes) ->
            var parent = path.substringBeforeLast("/", "")
            while (parent.isNotBlank()) {
                totals[parent] = (totals[parent] ?: 0L) + bytes
                parent = parent.substringBeforeLast("/", "")
            }
        }
        return totals
    }
}
