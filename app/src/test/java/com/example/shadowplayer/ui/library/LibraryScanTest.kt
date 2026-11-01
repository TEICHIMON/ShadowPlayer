package com.example.shadowplayer.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScanTest {

    private val existing = setOf(
        path("audio%2FBook%2F01.mp3"),
        path("audio%2FBook%2F02.mp3"),
        path("audio%2FTalks%2Fa.mp3")
    )

    @Test
    fun filesMissingFromAFolderThatListedNormallyAreDeleted() {
        val found = existing - path("audio%2FBook%2F02.mp3")

        assertEquals(setOf(path("audio%2FBook%2F02.mp3")), pathsToDelete(existing, found, emptyFolderDocIds = emptySet()))
    }

    @Test
    fun anEmptyScanFolderKeepsEveryRecord() {
        // 2026-11-01：存储服务把扫描目录当成隐藏，返回 0 个文件
        assertTrue(pathsToDelete(existing, foundPaths = emptySet(), emptyFolderDocIds = setOf(ROOT)).isEmpty())
    }

    @Test
    fun anEmptySubfolderKeepsItsRecordsWhileOtherMissingFilesAreDeleted() {
        val found = setOf(path("audio%2FBook%2F01.mp3"))

        val deleted = pathsToDelete(existing, found, emptyFolderDocIds = setOf("$ROOT%2FTalks"))

        assertEquals(setOf(path("audio%2FBook%2F02.mp3")), deleted)
    }

    @Test
    fun aFolderWhoseNameOnlyStartsWithTheEmptyFolderNameIsNotProtected() {
        val other = path("audio2%2Fother.mp3")

        assertEquals(setOf(other), pathsToDelete(setOf(other), foundPaths = emptySet(), emptyFolderDocIds = setOf(ROOT)))
    }

    private fun path(relative: String) = "$TREE/document/primary%3AdataFolder%2F$relative"

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AdataFolder%2Faudio"
        const val ROOT = "primary%3AdataFolder%2Faudio"
    }
}
