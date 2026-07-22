package com.example.shadowplayer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.shadowplayer.player.AudioOutputRoute
import com.example.shadowplayer.player.AudioOutputType
import com.example.shadowplayer.player.LrcSentence
import com.example.shadowplayer.player.PlaybackSettings
import com.example.shadowplayer.player.SentencePlayerState
import com.example.shadowplayer.ui.theme.ShadowPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultLayoutKeepsSecondaryControlsOutOfTheMainPage() {
        setPlayerContent(playingState())

        composeRule.onNodeWithTag("subtitle_list").assertIsDisplayed()
        composeRule.onNodeWithTag("player_control_deck").assertIsDisplayed()
        composeRule.onNodeWithText("系统音量").assertDoesNotExist()
        composeRule.onNodeWithText("播放器音量").assertDoesNotExist()
        composeRule.onNodeWithTag("subtitle_search_field").assertDoesNotExist()
    }

    @Test
    fun compactPhoneKeepsAtLeast220DpForSubtitles() {
        var density = 1f
        composeRule.setContent {
            density = LocalDensity.current.density
            ShadowPlayerTheme(dynamicColor = false) {
                Box(Modifier.size(width = 360.dp, height = 560.dp)) {
                    TestPlayerContent(playingState())
                }
            }
        }

        val subtitleHeightDp = subtitleAreaHeight() / density
        assertTrue("subtitle height was $subtitleHeightDp dp", subtitleHeightDp >= 220f)
    }

    @Test
    fun searchFiltersClearsClosesAndKeepsOriginalSentenceIndex() {
        var clickedIndex = -1
        setPlayerContent(playingState(), onSentenceClick = { clickedIndex = it })

        composeRule.onNodeWithContentDescription("搜索字幕").performClick()
        composeRule.onNodeWithTag("subtitle_search_field").performTextInput("目标")

        composeRule.onNodeWithText("1 条").assertExists()
        composeRule.onNodeWithText("目标字幕").assertIsDisplayed()
        composeRule.onNodeWithText("普通字幕").assertDoesNotExist()
        composeRule.onNodeWithTag("subtitle_row_1").performClick()
        composeRule.runOnIdle { assertEquals(1, clickedIndex) }

        composeRule.onNodeWithContentDescription("清空搜索").performClick()
        composeRule.onNodeWithText("普通字幕").assertExists()
        composeRule.onNodeWithContentDescription("关闭搜索").performClick()
        composeRule.onNodeWithTag("subtitle_search_field").assertDoesNotExist()
    }

    @Test
    fun unmatchedSearchShowsAnExplicitEmptyState() {
        setPlayerContent(playingState())

        composeRule.onNodeWithContentDescription("搜索字幕").performClick()
        composeRule.onNodeWithTag("subtitle_search_field").performTextInput("不存在")

        composeRule.onNodeWithText("0 条").assertExists()
        composeRule.onNodeWithText("未找到匹配字幕").assertIsDisplayed()
    }

    @Test
    fun moreControlsUseAnOverlayWithoutResizingSubtitleArea() {
        setPlayerContent(playingState())
        val heightBefore = subtitleAreaHeight()

        composeRule.onNodeWithContentDescription("更多控制").performClick()
        composeRule.onNodeWithTag("more_controls_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("系统音量").assertIsDisplayed()
        composeRule.onNodeWithText("播放器音量").assertIsDisplayed()

        assertEquals(heightBefore, subtitleAreaHeight(), 0.5f)
    }

    @Test
    fun intervalPromptDoesNotResizeSubtitleArea() {
        val state = mutableStateOf(playingState())
        composeRule.setContent {
            ShadowPlayerTheme(dynamicColor = false) {
                TestPlayerContent(state.value)
            }
        }
        val heightBefore = subtitleAreaHeight()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                playerState = state.value.playerState.copy(
                    isInInterval = true,
                    intervalCountdown = 3
                )
            )
        }

        composeRule.onNodeWithText("请跟读 · 3 秒").assertIsDisplayed()
        assertEquals(heightBefore, subtitleAreaHeight(), 0.5f)
    }

    @Test
    fun emptyStatesDistinguishAudioSubtitleAndVisibility() {
        val state = mutableStateOf(
            playingState().copy(audioId = null, title = "未选择音频")
        )
        composeRule.setContent {
            ShadowPlayerTheme(dynamicColor = false) {
                TestPlayerContent(state.value)
            }
        }
        composeRule.onNodeWithText("请从文件库选择音频").assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = playingState().copy(
                playerState = SentencePlayerState(),
                settings = PlaybackSettings(showSubtitle = true)
            )
        }
        composeRule.onNodeWithText("未找到字幕").assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = playingState().copy(settings = PlaybackSettings(showSubtitle = false))
        }
        composeRule.onNodeWithText("字幕已关闭").assertIsDisplayed()
        composeRule.onNodeWithText("显示字幕").assertIsDisplayed()
    }

    private fun subtitleAreaHeight(): Float = composeRule
        .onNodeWithTag("subtitle_area", useUnmergedTree = true)
        .fetchSemanticsNode()
        .boundsInRoot
        .height

    private fun setPlayerContent(
        uiState: PlayerScreenUiState,
        onSentenceClick: (Int) -> Unit = {}
    ) {
        composeRule.setContent {
            ShadowPlayerTheme(dynamicColor = false) {
                TestPlayerContent(uiState, onSentenceClick)
            }
        }
    }

    @Composable
    private fun TestPlayerContent(
        uiState: PlayerScreenUiState,
        onSentenceClick: (Int) -> Unit = {}
    ) {
        PlayerScreenContent(
            uiState = uiState,
            onShowOutputSwitcher = {},
            onSentenceClick = onSentenceClick,
            onSeek = {},
            onPlayPause = {},
            onPreviousSentence = {},
            onNextSentence = {},
            onSpeedChange = {},
            onRepeatCountChange = {},
            onIntervalChange = {},
            onSystemVolumeChange = {},
            onPlayerVolumeChange = {},
            onPreviousTrack = {},
            onNextTrack = {},
            onSeekBackward = {},
            onSeekForward = {},
            onSleepTimerChange = {},
            onToggleSubtitle = {}
        )
    }

    private fun playingState() = PlayerScreenUiState(
        audioId = 7L,
        title = "跟读练习",
        playerState = SentencePlayerState(
            sentences = listOf(
                LrcSentence(0, 0L, 2_000L, "普通字幕"),
                LrcSentence(1, 2_000L, 5_000L, "目标字幕"),
                LrcSentence(
                    2,
                    5_000L,
                    10_000L,
                    "这是一条会自动换行的较长字幕，用于验证当前句能够按照真实高度居中"
                )
            ),
            currentIndex = 1,
            currentRepeat = 1,
            isPlaying = true,
            currentPosition = 3_000L,
            totalDuration = 10_000L
        ),
        settings = PlaybackSettings(repeatCount = 2, repeatInterval = 2_000L),
        systemVolume = 0.5f,
        audioOutputRoute = AudioOutputRoute("蓝牙耳机", AudioOutputType.BLUETOOTH),
        currentPlaylistIndex = 0,
        playlistSize = 3,
        canPlayPreviousTrack = false,
        canPlayNextTrack = true
    )
}
