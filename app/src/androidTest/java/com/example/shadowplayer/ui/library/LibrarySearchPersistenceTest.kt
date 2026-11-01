package com.example.shadowplayer.ui.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.shadowplayer.ui.library.LibraryTestFixture.Companion.ALICE_ID
import com.example.shadowplayer.ui.library.LibraryTestFixture.Companion.ROOT_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 用真实的 ViewModel 和 SharedPreferences 验证搜索会一直保留：
 * 新建一个 ViewModel（相当于退出后重新打开 App）时，搜索词、过滤条件和排序都还在。
 */
@RunWith(AndroidJUnit4::class)
class LibrarySearchPersistenceTest {

    private lateinit var fixture: LibraryTestFixture

    @Before
    fun setUp() {
        fixture = LibraryTestFixture("library_search_persistence_test")
    }

    @After
    fun tearDown() {
        fixture.release()
    }

    @Test
    fun queryAndFiltersComeBackInANewViewModel() {
        val filters = LibrarySearchFilters(
            resultType = SearchResultType.AUDIO,
            hasSubtitle = true,
            scopeDocId = ALICE_ID
        )
        val first = fixture.newViewModel()
        fixture.onMain {
            first.search("rabbit")
            first.updateSearchFilters(filters)
        }
        fixture.closeViewModels()

        val second = fixture.newViewModel()

        assertEquals("rabbit", fixture.onMain { second.searchQuery })
        assertEquals(filters, second.searchFilters.value)
        assertEquals(listOf(1L, 3L), awaitResult(second).audioFiles.map { it.audioFile.id })
    }

    @Test
    fun restoredSearchWaitsForTheLibraryInsteadOfShowingNoResults() {
        val first = fixture.newViewModel()
        fixture.onMain { first.search("alice") }
        fixture.closeViewModels()

        val result = awaitResult(fixture.newViewModel())

        assertEquals(listOf("Alice"), result.folders.map { it.name })
    }

    @Test
    fun openingAFolderFromResultsAndGoingBackKeepsTheSearch() {
        val viewModel = fixture.newViewModel()
        fixture.onMain { viewModel.search("alice") }
        assertEquals(listOf("Alice"), awaitResult(viewModel).folders.map { it.name })

        fixture.onMain { viewModel.openSearchFolder(ALICE_ID) }
        assertEquals(
            listOf("Extras", "01 Down the Rabbit-Hole", "02 The Pool of Tears"),
            awaitFolderContent(viewModel)
        )

        fixture.onMain {
            viewModel.openSearchSubfolder("$ALICE_ID%2FExtras")
            viewModel.searchFolderUp()
        }
        assertEquals(SearchFolderLocation(ALICE_ID, ALICE_ID), viewModel.searchFolder.value)

        fixture.onMain { viewModel.searchFolderUp() }
        assertNull(viewModel.searchFolder.value)
        assertEquals("alice", fixture.onMain { viewModel.searchQuery })
    }

    @Test
    fun audioFiltersApplyInsideTheOpenedFolder() {
        val viewModel = fixture.newViewModel()
        fixture.onMain {
            viewModel.updateSearchFilters(LibrarySearchFilters(hasSubtitle = true))
            viewModel.search("alice")
            viewModel.openSearchFolder(ALICE_ID)
        }

        // 02 没有字幕；Extras 里的 03 有字幕，所以 Extras 还在
        assertEquals(listOf("Extras", "01 Down the Rabbit-Hole"), awaitFolderContent(viewModel))
    }

    @Test
    fun typingANewQueryClosesTheOpenedFolder() {
        val viewModel = fixture.newViewModel()
        fixture.onMain {
            viewModel.search("alice")
            viewModel.openSearchFolder(ALICE_ID)
            viewModel.search("alice oz")
        }

        assertNull(viewModel.searchFolder.value)
    }

    @Test
    fun folderSortAndSearchSortComeBackInANewViewModel() {
        val first = fixture.newViewModel()
        fixture.onMain {
            first.setFolderSort(ROOT_ID, LibrarySortType.NAME_DESC)
            first.updateSearchSort(LibrarySortType.ADDED_DESC)
        }
        fixture.closeViewModels()

        val second = fixture.newViewModel()
        fixture.onMain { second.navigateToFolder(ROOT_ID) }

        assertEquals(LibrarySortType.ADDED_DESC, second.searchSort.value)
        assertEquals(LibrarySortType.NAME_ASC, second.folderSorts.value.sortFor(ALICE_ID))
        // Books 下面只有子文件夹，选的排序也排子文件夹
        assertEquals(listOf("Oz", "Alice"), awaitNames(second.folderContent))
    }

    private fun awaitResult(viewModel: LibraryViewModel): LibrarySearchResult = runBlocking {
        withTimeout(10_000) { viewModel.searchResult.filterNotNull().first() }
    }

    private fun awaitFolderContent(viewModel: LibraryViewModel): List<String> = awaitNames(viewModel.searchFolderContent)

    private fun awaitNames(content: Flow<List<FileSystemItem>>): List<String> = runBlocking {
        withTimeout(10_000) {
            content.first { it.isNotEmpty() }.map { item ->
                when (item) {
                    is FileSystemItem.Folder -> item.name
                    is FileSystemItem.File -> item.audioFile.title
                }
            }
        }
    }
}
