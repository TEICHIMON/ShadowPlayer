package com.example.shadowplayer.ui.library

import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.entity.ScanFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset

class LibrarySearchTest {

    private val root = ScanFolder(id = 1, path = TREE_URI, name = "ShadowTest")

    private val files = listOf(
        audio(1, "Alice/01 Chapter I. Down the Rabbit-Hole", subtitle = true, playCount = 3),
        audio(2, "Alice/02 Chapter II. The Pool of Tears"),
        audio(3, "新概念英语/第一册/Lesson 01 Excuse me", subtitle = true),
        audio(4, "新概念英语/第一册/Lesson 02 Is this your", playCount = 2),
        audio(5, "新概念英语/第二册/Lesson 01 A private conversation", subtitle = true, playCount = 1, favorite = true),
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

    @Test
    fun audioConditionsKeepFoldersThatContainMatchingAudioAndCountThem() {
        val result = search("新概念", LibrarySearchFilters(hasSubtitle = true))

        assertEquals(listOf("新概念英语"), result.folders.map { it.name })
        assertEquals(2, result.matchedAudioCounts?.get(result.folders.single().docId))
    }

    @Test
    fun unplayedHidesFoldersWhereEveryAudioWasPlayed() {
        assertEquals(listOf("第一册", "第二册"), search("第").folders.map { it.name })

        val result = search("第", LibrarySearchFilters(unplayed = true))

        assertEquals(listOf("第一册"), result.folders.map { it.name })
        assertEquals(1, result.matchedAudioCounts?.get(result.folders.single().docId))
        assertEquals(listOf(7L), result.audioFiles.map { it.audioFile.id })
    }

    @Test
    fun audioConditionsCombineWithAnd() {
        val result = search("lesson", LibrarySearchFilters(hasSubtitle = true, favorite = true))

        assertEquals(listOf(5L), result.audioFiles.map { it.audioFile.id })
    }

    @Test
    fun resultTypeShowsOnlyOneSection() {
        val folders = search("第", LibrarySearchFilters(resultType = SearchResultType.FOLDERS))
        assertEquals(listOf("第一册", "第二册"), folders.folders.map { it.name })
        assertTrue(folders.audioFiles.isEmpty())

        val audio = search("第", LibrarySearchFilters(resultType = SearchResultType.AUDIO))
        assertTrue(audio.folders.isEmpty())
        assertEquals(listOf(7L), audio.audioFiles.map { it.audioFile.id })
    }

    @Test
    fun scopeLimitsResultsToTheInsideOfTheFolder() {
        val textbookId = "$ROOT_ID%2F${encode("新概念英语")}"
        val book1Id = "$textbookId%2F${encode("第一册")}"

        assertEquals(listOf(3L, 4L), search("lesson", LibrarySearchFilters(scopeDocId = book1Id)).audioFiles.map { it.audioFile.id })

        val inTextbook = search("第", LibrarySearchFilters(scopeDocId = textbookId))
        assertEquals(listOf("第一册", "第二册"), inTextbook.folders.map { it.name })
        // 「第１課」在 NHK 下面
        assertTrue(inTextbook.audioFiles.isEmpty())
        // 范围文件夹本身不算结果
        assertTrue(search("新概念", LibrarySearchFilters(scopeDocId = textbookId)).isEmpty)
    }

    @Test
    fun sortAppliesToFolderAndAudioResults() {
        val library = listOf(
            audio(1, "Old book/01 Intro", createdAt = DAY_1),
            audio(2, "New book/01 Intro", createdAt = DAY_3),
            audio(3, "Old book/02 Intro again", createdAt = DAY_2)
        )
        val sortIndex = buildLibrarySearchIndex(library, listOf(root))
        fun searchSorted(query: String, sort: LibrarySortType) =
            searchLibrary(sortIndex, searchTokens(query), sort = sort, zone = ZoneOffset.UTC)

        // 默认按路径：同一个文件夹的音频排在一起
        assertEquals(listOf(2L, 1L, 3L), searchSorted("intro", LibrarySortType.PATH).audioFiles.map { it.audioFile.id })
        val newestFirst = searchSorted("intro", LibrarySortType.ADDED_DESC)
        assertEquals(listOf(2L, 3L, 1L), newestFirst.audioFiles.map { it.audioFile.id })
        assertEquals(LibrarySortType.ADDED_DESC, newestFirst.sort)
        // 文件夹的添加时间是里面最新加入的音频：Old book 是第 2 天，New book 是第 3 天
        assertEquals(listOf("New book", "Old book"), searchSorted("book", LibrarySortType.PATH).folders.map { it.name })
        assertEquals(listOf("Old book", "New book"), searchSorted("book", LibrarySortType.ADDED_ASC).folders.map { it.name })
    }

    @Test
    fun noFiltersMeansNoMatchedCounts() {
        val result = search("新概念")

        assertEquals(null, result.matchedAudioCounts)
        assertTrue(!result.filters.isActive)
    }

    private fun search(query: String, filters: LibrarySearchFilters = LibrarySearchFilters()) =
        searchLibrary(index, searchTokens(query), filters)

    private fun audio(
        id: Long,
        relativePath: String,
        subtitle: Boolean = false,
        playCount: Int = 0,
        favorite: Boolean = false,
        createdAt: Long = DAY_1
    ): AudioFile {
        val docId = "primary:Music/ShadowTest/$relativePath.mp3".split("/").joinToString("%2F") { encode(it) }
        return AudioFile(
            id = id,
            path = "$TREE_URI/document/$docId",
            title = relativePath.substringAfterLast("/"),
            lrcPath = if (subtitle) "$TREE_URI/document/${docId.removeSuffix(".mp3")}.lrc" else null,
            playCount = playCount,
            isFavorite = favorite,
            createdAt = createdAt
        )
    }

    private fun encode(segment: String) = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private companion object {
        const val TREE_URI = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FShadowTest"
        const val ROOT_ID = "primary%3AMusic%2FShadowTest"
        val DAY_1 = Instant.parse("2026-10-01T09:00:00Z").toEpochMilli()
        val DAY_2 = Instant.parse("2026-10-02T09:00:00Z").toEpochMilli()
        val DAY_3 = Instant.parse("2026-10-03T09:00:00Z").toEpochMilli()
    }
}
