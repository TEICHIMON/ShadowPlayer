package com.example.shadowplayer.ui.library

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import com.example.shadowplayer.ui.theme.ShadowPlayerTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 文件库界面：搜索的过滤条件和排序、从结果打开文件夹再返回、只搜「文件夹」页当前打开的文件夹、
 * 文件夹的排序。数据见 [LibraryTestFixture]。在真机上运行时会占用屏幕几十秒。
 */
class LibrarySearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var fixture: LibraryTestFixture

    @Before
    fun setUp() {
        fixture = LibraryTestFixture("library_search_screen_test")
        val viewModel = fixture.newViewModel()
        composeRule.setContent {
            ShadowPlayerTheme(dynamicColor = false) {
                LibraryScreen(onFileSelected = {}, viewModel = viewModel)
            }
        }
    }

    @After
    fun tearDown() {
        fixture.release()
    }

    @Test
    fun filterChipsAppearWhileSearchingAndNarrowTheResults() {
        composeRule.onNodeWithText("有字幕").assertDoesNotExist()

        searchFor("rabbit")
        waitForText("03 Rabbit interview")
        composeRule.onNodeWithText("01 Down the Rabbit-Hole").assertIsDisplayed()

        clickChip("未播放")

        waitUntilGone("03 Rabbit interview")
        composeRule.onNodeWithText("01 Down the Rabbit-Hole").assertIsDisplayed()
    }

    @Test
    fun folderCountShowsMatchingAudioWhenAudioConditionsAreOn() {
        searchFor("alice")
        waitForText("3 首音频 · Books")

        clickChip("有字幕")

        waitForText("2 / 3 首音频 · Books")
    }

    @Test
    fun openedFolderGoesBackToTheResultsAndKeepsTheSearch() {
        searchFor("alice")
        waitForText("Alice")
        composeRule.onNodeWithText("Alice").performClick()

        waitForText("返回搜索结果")
        composeRule.onNodeWithText("02 The Pool of Tears").assertIsDisplayed()
        // 打开的文件夹里只有音频条件，没有范围和类型
        composeRule.onNodeWithText("有字幕").assertIsDisplayed()
        composeRule.onNodeWithText("音频").assertDoesNotExist()

        pressBack()

        waitForText("Alice")
        composeRule.onNodeWithText("返回搜索结果").assertDoesNotExist()
        composeRule.onNode(hasSetTextAction()).assert(hasText("alice"))
    }

    @Test
    fun scopeChipLimitsTheSearchToTheFolderOpenInTheFoldersTab() {
        composeRule.onNodeWithText("文件夹").performClick()
        waitForText("Books")
        composeRule.onNodeWithText("Books").performClick()
        waitForText("Alice")
        composeRule.onNodeWithText("Alice").performClick()
        waitForText("02 The Pool of Tears")

        searchFor("01")
        waitForText("01 The Cyclone")

        clickChip("仅「Alice」")

        waitUntilGone("01 The Cyclone")
        composeRule.onNodeWithText("01 Down the Rabbit-Hole").assertIsDisplayed()
    }

    @Test
    fun oneSortMenuReordersAFolderThatOnlyHasSubfolders() {
        composeRule.onNodeWithText("文件夹").performClick()
        waitForText("Books")
        composeRule.onNodeWithText("Books").performClick()
        waitForText("Oz")
        assertTrue(top("Alice") < top("Oz"))

        composeRule.onNodeWithContentDescription("排序").performClick()
        composeRule.onNodeWithText("名称 Z→A").performClick()

        composeRule.waitUntil(10_000) { top("Oz") < top("Alice") }
    }

    @Test
    fun sortChipReordersTheSearchResults() {
        searchFor("01")
        waitForText("01 The Cyclone")
        // 默认按路径：Alice 的结果在 Oz 前面
        assertTrue(top("01 Down the Rabbit-Hole") < top("01 The Cyclone"))

        composeRule.onNodeWithText("路径 A→Z").performClick()
        composeRule.onNodeWithText("名称 Z→A").performClick()

        composeRule.waitUntil(10_000) { top("01 The Cyclone") < top("01 Down the Rabbit-Hole") }
    }

    private fun searchFor(query: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(query)
    }

    // 过滤条件一行可以横向滚动，排序按钮加在最前面以后，后面的按钮可能在屏幕外
    private fun clickChip(label: String) {
        composeRule.onNodeWithText(label).performScrollTo().performClick()
    }

    private fun top(text: String) = composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    // 搜索在后台线程计算，Room 也在自己的线程里查询，Compose 测试不会自动等它们
    private fun waitForText(text: String) {
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitUntilGone(text: String) {
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }
}
