package com.example.shadowplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {

    private val preRollMs = 200L

    private val sentences = listOf(
        LrcSentence(0, 0L, 2_000L, "first"),
        LrcSentence(1, 2_000L, 5_000L, "second"),
        LrcSentence(2, 5_000L, 9_000L, "third")
    )

    @Test
    fun keepsTargetSentenceWhilePositionIsInSeekPreRoll() {
        // 跳到第 2 句后播放器停在句首前 200ms，这个位置在第 1 句的时间范围内
        assertEquals(0, LrcParser.findSentenceIndex(sentences, 1_800L))
        assertEquals(
            1,
            LrcParser.resolveSentenceIndex(sentences, 1_800L, currentIndex = 1, preRollMs = preRollMs)
        )
    }

    @Test
    fun keepsCurrentSentenceWhenPausedSlightlyPastItsEnd() {
        // 跟读间隔中暂停时，位置会略超过句尾
        assertEquals(
            1,
            LrcParser.resolveSentenceIndex(
                sentences,
                5_100L,
                currentIndex = 1,
                preRollMs = preRollMs,
                endToleranceMs = 500L
            )
        )
    }

    @Test
    fun movesOnOncePositionLeavesEndTolerance() {
        assertEquals(
            2,
            LrcParser.resolveSentenceIndex(
                sentences,
                5_600L,
                currentIndex = 1,
                preRollMs = preRollMs,
                endToleranceMs = 500L
            )
        )
    }

    @Test
    fun advancesExactlyAtSentenceEndWithoutTolerance() {
        // 连续播放时句尾不留余量，到点立刻切到下一句
        assertEquals(
            2,
            LrcParser.resolveSentenceIndex(sentences, 5_000L, currentIndex = 1, preRollMs = preRollMs)
        )
    }

    @Test
    fun followsPositionWhenFarFromCurrentSentence() {
        assertEquals(
            2,
            LrcParser.resolveSentenceIndex(sentences, 6_000L, currentIndex = 0, preRollMs = preRollMs)
        )
        assertEquals(
            0,
            LrcParser.resolveSentenceIndex(sentences, 500L, currentIndex = 2, preRollMs = preRollMs)
        )
    }

    @Test
    fun followsPositionWhenCurrentIndexIsInvalid() {
        assertEquals(
            1,
            LrcParser.resolveSentenceIndex(sentences, 3_000L, currentIndex = -1, preRollMs = preRollMs)
        )
        assertEquals(
            1,
            LrcParser.resolveSentenceIndex(sentences, 3_000L, currentIndex = 10, preRollMs = preRollMs)
        )
    }

    @Test
    fun returnsMinusOneWithoutSentences() {
        assertEquals(
            -1,
            LrcParser.resolveSentenceIndex(emptyList(), 3_000L, currentIndex = 0, preRollMs = preRollMs)
        )
    }

    @Test
    fun restoresSavedPreRollPositionToFollowingSentence() {
        // 跳句后保存的进度是句首前 200ms，恢复时应回到目标句
        assertEquals(2, LrcParser.findSentenceIndexWithPreRoll(sentences, 4_800L, preRollMs))
        assertEquals(1, LrcParser.findSentenceIndexWithPreRoll(sentences, 3_000L, preRollMs))
    }
}
