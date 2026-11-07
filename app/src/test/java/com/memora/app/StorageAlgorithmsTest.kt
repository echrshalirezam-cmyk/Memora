package com.memora.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAlgorithmsTest {
    @Test fun uniqueNameAddsNumberBeforeExtensionAndNeverOverwrites() {
        val existing = mutableSetOf("movie.mkv", "movie (1).mkv", "movie (2).mkv")
        val result = StorageAlgorithms.uniqueName("movie.mkv") { it in existing }
        assertEquals("movie (3).mkv", result)
        assertTrue(result !in existing)
    }

    @Test fun uniqueNameHandlesNamesWithoutExtension() {
        val result = StorageAlgorithms.uniqueName("Archive") { it == "Archive" || it == "Archive (1)" }
        assertEquals("Archive (2)", result)
    }

    @Test fun similarNamesNormalizeCopyAndNumberSuffixes() {
        assertEquals("the.matrix", StorageAlgorithms.normalizeSimilarName("The.Matrix (2).mkv"))
        assertEquals("sample", StorageAlgorithms.normalizeSimilarName("Sample copy 2.mp4"))
        assertEquals("sample", StorageAlgorithms.normalizeSimilarName("Sample نسخه 3.avi"))
    }

    @Test fun exactNameGroupsMatchFullFilenameAcrossFoldersAndIgnoreCase() {
        data class Item(val name: String, val path: String, val bytes: Long)
        val groups = StorageAlgorithms.groupExactNames(
            listOf(Item("photo.jpg", "A/photo.jpg", 10), Item("PHOTO.JPG", "B/PHOTO.JPG", 20), Item("photo.png", "C/photo.png", 30)),
            name = { it.name }, size = { it.bytes }
        )
        assertEquals(1, groups.size)
        assertEquals(listOf("A/photo.jpg", "B/PHOTO.JPG"), groups.single().map { it.path })
    }

    @Test fun similarNameGroupsExcludeDirectoriesAndSortByTotalSize() {
        data class Item(val name: String, val bytes: Long, val folder: Boolean = false)
        val input = listOf(
            Item("Movie.mkv", 100),
            Item("Movie (1).mkv", 200),
            Item("Other.mp4", 50),
            Item("Other copy.mp4", 60),
            Item("Folder", 999, folder = true),
            Item("ab.mkv", 1)
        )
        val groups = StorageAlgorithms.groupSimilarNames(
            input.filterNot { it.folder },
            name = { it.name },
            size = { it.bytes }
        )
        assertEquals(2, groups.size)
        assertEquals(listOf("Movie.mkv", "Movie (1).mkv"), groups[0].map { it.name })
        assertEquals(listOf("Other.mp4", "Other copy.mp4"), groups[1].map { it.name })
    }

    @Test fun folderSizesIncludeEveryAncestorButNotRoot() {
        val sizes = StorageAlgorithms.aggregateFolderSizes(
            listOf("Movies/Action/a.mkv" to 100L, "Movies/Comedy/b.mkv" to 250L, "readme.txt" to 10L)
        )
        assertEquals(350L, sizes["Movies"])
        assertEquals(100L, sizes["Movies/Action"])
        assertEquals(250L, sizes["Movies/Comedy"])
        assertTrue("" !in sizes)
    }

    @Test fun zeroAndNegativeSizesAreIgnored() {
        assertTrue(StorageAlgorithms.aggregateFolderSizes(listOf("A/empty.bin" to 0L, "A/bad.bin" to -5L)).isEmpty())
    }
}
