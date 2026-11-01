package com.example.shadowplayer.ui.library

import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.entity.ScanFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset

class FolderContentTest {

    private val root = ScanFolder(id = 1, path = TREE_URI, name = "我的音频")

    private val files = listOf(
        audio(1, "Book/Unit 1/01 Hello", subtitle = true),
        audio(2, "Book/Unit 1/02 Goodbye"),
        audio(3, "Book/Unit 2/Part A/01 Numbers", playCount = 1),
        audio(4, "Book/Intro")
    )

    @Test
    fun listsSubfoldersWithRecursiveCountsBeforeFiles() {
        val items = buildFolderContent(BOOK_ID, listOf(root), files, LibrarySortType.NAME_ASC)

        assertEquals(
            listOf(
                FileSystemItem.Folder("Unit 1", "$BOOK_ID%2F${encode("Unit 1")}", 2),
                FileSystemItem.Folder("Unit 2", "$BOOK_ID%2F${encode("Unit 2")}", 1),
                FileSystemItem.File(files[3])
            ),
            items
        )
    }

    @Test
    fun filteredFilesHideSubfoldersWithoutMatchingAudio() {
        val filters = LibrarySearchFilters(hasSubtitle = true)

        val items = buildFolderContent(
            BOOK_ID, listOf(root), files.filter(filters::matchesAudio), LibrarySortType.NAME_ASC
        )

        assertEquals(listOf(FileSystemItem.Folder("Unit 1", "$BOOK_ID%2F${encode("Unit 1")}", 1)), items)
    }

    @Test
    fun rootListingUsesScanFolderNames() {
        val items = buildFolderContent(null, listOf(root), files, LibrarySortType.NAME_ASC)

        assertEquals(listOf(FileSystemItem.Folder("我的音频", ROOT_ID, 4)), items)
    }

    @Test
    fun oneSortOrdersSubfoldersAndFiles() {
        // 以前子文件夹和音频各有一个菜单，只有子文件夹的目录里选「文件排序」没有反应
        val items = buildFolderContent(BOOK_ID, listOf(root), files, LibrarySortType.NAME_DESC)

        assertEquals(listOf("Unit 2", "Unit 1", "Intro"), names(items))
    }

    @Test
    fun fileNamesCompareNumbersByValue() {
        val tracks = listOf(audio(1, "Book/10"), audio(2, "Book/2"), audio(3, "Book/1"))

        assertEquals(listOf("1", "2", "10"), names(buildFolderContent(BOOK_ID, listOf(root), tracks, LibrarySortType.NAME_ASC)))
    }

    @Test
    fun folderAddedTimeIsItsNewestAudioAndTheSameDaySortsByName() {
        val library = listOf(
            audio(1, "Book/Unit 1/01 Hello", createdAt = DAY_1),
            audio(2, "Book/Unit 2/01 Numbers", createdAt = DAY_1 + HOUR),
            audio(3, "Book/Unit 3/01 Colors", createdAt = DAY_1),
            audio(4, "Book/Unit 3/02 Shapes", createdAt = DAY_3)
        )

        val newestFirst = buildFolderContent(BOOK_ID, listOf(root), library, LibrarySortType.ADDED_DESC, ZoneOffset.UTC)
        assertEquals(listOf("Unit 3", "Unit 1", "Unit 2"), names(newestFirst))
        val oldestFirst = buildFolderContent(BOOK_ID, listOf(root), library, LibrarySortType.ADDED_ASC, ZoneOffset.UTC)
        assertEquals(listOf("Unit 1", "Unit 2", "Unit 3"), names(oldestFirst))
    }

    @Test
    fun recentPlayPutsWhatWasNeverPlayedLast() {
        val library = listOf(
            audio(1, "Book/Unit 1/01 Hello", lastPlayedAt = DAY_1),
            audio(2, "Book/Unit 2/01 Numbers", lastPlayedAt = DAY_3),
            audio(3, "Book/Unit 3/01 Colors"),
            audio(4, "Book/b side"),
            audio(5, "Book/a side", lastPlayedAt = DAY_1),
            audio(6, "Book/c side")
        )

        assertEquals(
            listOf("Unit 2", "Unit 1", "Unit 3", "a side", "b side", "c side"),
            names(buildFolderContent(BOOK_ID, listOf(root), library, LibrarySortType.RECENT_PLAY))
        )
    }

    @Test
    fun parentDocumentIdRemovesTheLastSegment() {
        assertEquals("a%2Fb", parentDocumentId("a%2Fb%2Fc"))
        assertEquals("a%2Fb", parentDocumentId("a%2Fb/c"))
        assertEquals("a/b", parentDocumentId("a/b%2Fc"))
        assertNull(parentDocumentId("primary%3AMusic"))
    }

    @Test
    fun folderDisplayNameUsesScanFolderNameOrTheDecodedLastSegment() {
        assertEquals("我的音频", folderDisplayName(ROOT_ID, listOf(root)))
        assertEquals("Unit 1", folderDisplayName("$BOOK_ID%2F${encode("Unit 1")}", listOf(root)))
    }

    private fun audio(
        id: Long,
        relativePath: String,
        subtitle: Boolean = false,
        playCount: Int = 0,
        createdAt: Long = DAY_1,
        lastPlayedAt: Long? = null
    ): AudioFile {
        val docId = "primary:Music/ShadowTest/$relativePath.mp3".split("/").joinToString("%2F") { encode(it) }
        return AudioFile(
            id = id,
            path = "$TREE_URI/document/$docId",
            title = relativePath.substringAfterLast("/"),
            lrcPath = if (subtitle) "$TREE_URI/document/${docId.removeSuffix(".mp3")}.lrc" else null,
            playCount = playCount,
            createdAt = createdAt,
            lastPlayedAt = lastPlayedAt
        )
    }

    private fun names(items: List<FileSystemItem>) = items.map { item ->
        when (item) {
            is FileSystemItem.Folder -> item.name
            is FileSystemItem.File -> item.audioFile.title
        }
    }

    private fun encode(segment: String) = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private companion object {
        const val TREE_URI = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FShadowTest"
        const val ROOT_ID = "primary%3AMusic%2FShadowTest"
        const val BOOK_ID = "$ROOT_ID%2FBook"
        const val HOUR = 3_600_000L
        val DAY_1 = Instant.parse("2026-10-01T09:00:00Z").toEpochMilli()
        val DAY_3 = Instant.parse("2026-10-03T09:00:00Z").toEpochMilli()
    }
}
