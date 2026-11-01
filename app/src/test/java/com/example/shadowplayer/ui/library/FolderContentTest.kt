package com.example.shadowplayer.ui.library

import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.entity.ScanFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLEncoder

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
        val items = buildFolderContent(BOOK_ID, listOf(root), files, FolderSortType.NAME_ASC, FileSortType.NAME_ASC)

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
            BOOK_ID, listOf(root), files.filter(filters::matchesAudio), FolderSortType.NAME_ASC, FileSortType.NAME_ASC
        )

        assertEquals(listOf(FileSystemItem.Folder("Unit 1", "$BOOK_ID%2F${encode("Unit 1")}", 1)), items)
    }

    @Test
    fun rootListingUsesScanFolderNames() {
        val items = buildFolderContent(null, listOf(root), files, FolderSortType.NAME_ASC, FileSortType.NAME_ASC)

        assertEquals(listOf(FileSystemItem.Folder("我的音频", ROOT_ID, 4)), items)
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

    private fun audio(id: Long, relativePath: String, subtitle: Boolean = false, playCount: Int = 0): AudioFile {
        val docId = "primary:Music/ShadowTest/$relativePath.mp3".split("/").joinToString("%2F") { encode(it) }
        return AudioFile(
            id = id,
            path = "$TREE_URI/document/$docId",
            title = relativePath.substringAfterLast("/"),
            lrcPath = if (subtitle) "$TREE_URI/document/${docId.removeSuffix(".mp3")}.lrc" else null,
            playCount = playCount
        )
    }

    private fun encode(segment: String) = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private companion object {
        const val TREE_URI = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FShadowTest"
        const val ROOT_ID = "primary%3AMusic%2FShadowTest"
        const val BOOK_ID = "$ROOT_ID%2FBook"
    }
}
