package com.example.shadowplayer.ui.library

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import com.example.shadowplayer.ui.theme.ShadowPlayerTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 文件库搜索界面：过滤条件、从结果打开文件夹再返回、只搜「文件夹」页当前打开的文件夹。
 * 数据见 [LibraryTestFixture]。在真机上运行时会占用屏幕几十秒。
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

        composeRule.onNodeWithText("未播放").performClick()

        waitUntilGone("03 Rabbit interview")
        composeRule.onNodeWithText("01 Down the Rabbit-Hole").assertIsDisplayed()
    }

    @Test
    fun folderCountShowsMatchingAudioWhenAudioConditionsAreOn() {
        searchFor("alice")
        waitForText("3 首音频 · Books")

        composeRule.onNodeWithText("有字幕").performClick()

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

        composeRule.onNodeWithText("仅「Alice」").performClick()

        waitUntilGone("01 The Cyclone")
        composeRule.onNodeWithText("01 Down the Rabbit-Hole").assertIsDisplayed()
    }

    private fun searchFor(query: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(query)
    }

    // 搜索在后台线程计算，Room 也在自己的线程里查询，Compose 测试不会自动等它们
    private fun waitForText(text: String) {
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitUntilGone(text: String) {
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }
}
