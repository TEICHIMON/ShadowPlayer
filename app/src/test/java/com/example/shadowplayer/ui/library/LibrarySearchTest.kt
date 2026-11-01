package com.example.shadowplayer.ui.library

import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.entity.ScanFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class LibrarySearchTest {

    private val root = ScanFolder(id = 1, path = TREE_URI, name = "ShadowTest")

    private val files = listOf(
        audio(1, "Alice/01 Chapter I. Down the Rabbit-Hole"),
        audio(2, "Alice/02 Chapter II. The Pool of Tears"),
        audio(3, "新概念英语/第一册/Lesson 01 Excuse me"),
        audio(4, "新概念英语/第一册/Lesson 02 Is this your"),
        audio(5, "新概念英语/第二册/Lesson 01 A private conversation"),
        // macOS 拷来的文件名常是分解形式：か + 結合用濁点
        audio(6, "NHK/ニュース がっこう"),
        audio(7, "NHK/第１課 あいさつ")
    )

    private val index = buildLibrarySearchIndex(files, listOf(root))

    @Test
    fun tokensDropKeyboardSpacesAndFoldCaseAndWidth() {
        assertEquals(listOf("rabbit"), searchTokens("Ｒａｂｂｉｔ "))
        assertEquals(listOf("lesson", "01"), searchTokens("　lesson　０１ "))
        assertTrue(searchTokens("   ").isEmpty())
    }

    @Test
    fun trailingSpaceFromKeyboardSuggestionStillMatches() {
        val result = search("rabbit ")

        assertEquals(listOf(1L), result.audioFiles.map { it.audioFile.id })
    }

    @Test
    fun decomposedKanaInFileNameMatchesTypedText() {
        val result = search("がっこう")

        assertEquals(listOf(6L), result.audioFiles.map { it.audioFile.id })
    }

    @Test
    fun halfWidthDigitsMatchFullWidthFileNames() {
        val result = search("第1課")

        assertEquals(listOf(7L), result.audioFiles.map { it.audioFile.id })
    }

    @Test
    fun folderNameQueryListsTheFolderInsteadOfEveryFileInside() {
        val result = search("alice")

        assertEquals(listOf("Alice"), result.folders.map { it.name })
        assertTrue(result.audioFiles.isEmpty())
    }

    @Test
    fun tokensCanCombineFolderPathAndTitle() {
        val result = search("新概念 lesson 01")

        assertEquals(listOf(3L, 5L), result.audioFiles.map { it.audioFile.id })
        assertEquals(
            listOf("ShadowTest/新概念英语/第一册", "ShadowTest/新概念英语/第二册"),
            result.audioFiles.map { it.folderLabel }
        )
    }

    @Test
    fun multiLevelFolderQueryMatchesOnlyTheDeepestNamedFolder() {
        val result = search("新概念 第一册")

        assertEquals(listOf("第一册"), result.folders.map { it.name })
    }

    @Test
    fun indexUsesFolderBrowserIdsAndRecursiveCounts() {
        val rootId = "primary%3AMusic%2FShadowTest"
        val foldersById = index.folders.associateBy { it.docId }

        assertEquals(7, foldersById.getValue(rootId).audioCount)
        val textbook = foldersById.getValue("$rootId%2F${encode("新概念英语")}")
        assertEquals(3, textbook.audioCount)
        assertEquals("ShadowTest", textbook.parentLabel)
        val book1 = foldersById.getValue("$rootId%2F${encode("新概念英语")}%2F${encode("第一册")}")
        assertEquals(2, book1.audioCount)
        assertEquals("ShadowTest/新概念英语", book1.parentLabel)
    }

    @Test
    fun filesOutsideScanFoldersAreStillSearchable() {
        val orphan = AudioFile(
            id = 9,
            path = "file:///data/user/0/com.example.shadowplayer/files/test.wav",
            title = "测试跟读音频"
        )
        val result = searchLibrary(buildLibrarySearchIndex(files + orphan, listOf(root)), searchTokens("跟读"))

        assertEquals(listOf(9L), result.audioFiles.map { it.audioFile.id })
        assertEquals("files", result.audioFiles.single().folderLabel)
    }

    @Test
    fun noMatchGivesAnEmptyResult() {
        assertTrue(search("不存在的书").isEmpty)
    }

    @Test
    fun matchRangesIgnoreCaseAndMergeOverlaps() {
        val ranges = findSearchMatchRanges("Lesson 01 lesson", listOf("lesson", "son 0"))

        assertEquals(listOf(0..7, 10..15), ranges)
    }

    private fun search(query: String) = searchLibrary(index, searchTokens(query))

    private fun audio(id: Long, relativePath: String): AudioFile {
        val docId = "primary:Music/ShadowTest/$relativePath.mp3".split("/").joinToString("%2F") { encode(it) }
        return AudioFile(
            id = id,
            path = "$TREE_URI/document/$docId",
            title = relativePath.substringAfterLast("/")
        )
    }

    private fun encode(segment: String) = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private companion object {
        const val TREE_URI = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FShadowTest"
    }
}
