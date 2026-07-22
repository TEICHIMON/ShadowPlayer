package com.example.shadowplayer.ui.player

import com.example.shadowplayer.player.LrcSentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSearchTest {

    private val sentences = listOf(
        sentence(index = 0, text = "Hello world"),
        sentence(index = 1, text = "今天学习字幕搜索"),
        sentence(index = 2, text = "HELLO again"),
        sentence(index = 3, text = "没有命中的内容")
    )

    @Test
    fun emptyQueryReturnsEverySentenceWithOriginalIndices() {
        val results = filterSubtitleItems(sentences, "   ")

        assertEquals(listOf(0, 1, 2, 3), results.map { it.originalIndex })
        assertEquals(sentences, results.map { it.sentence })
    }

    @Test
    fun chineseQueryUsesSubstringMatching() {
        val results = filterSubtitleItems(sentences, "字幕")

        assertEquals(listOf(1), results.map { it.originalIndex })
    }

    @Test
    fun englishQueryIgnoresCase() {
        val results = filterSubtitleItems(sentences, "hello")

        assertEquals(listOf(0, 2), results.map { it.originalIndex })
    }

    @Test
    fun queryTrimsLeadingAndTrailingWhitespace() {
        val results = filterSubtitleItems(sentences, "  学习  ")

        assertEquals(listOf(1), results.map { it.originalIndex })
    }

    @Test
    fun unmatchedQueryReturnsEmptyList() {
        val results = filterSubtitleItems(sentences, "不存在")

        assertTrue(results.isEmpty())
    }

    @Test
    fun duplicateTextKeepsSourceOrderAndOriginalIndices() {
        val duplicateSentences = listOf(
            sentence(index = 0, text = "repeat"),
            sentence(index = 1, text = "skip"),
            sentence(index = 2, text = "repeat")
        )

        val results = filterSubtitleItems(duplicateSentences, "repeat")

        assertEquals(listOf(0, 2), results.map { it.originalIndex })
        assertEquals(listOf("repeat", "repeat"), results.map { it.sentence.text })
    }

    @Test
    fun matchRangesFindAllNonOverlappingOccurrencesIgnoringCase() {
        val ranges = findSubtitleMatchRanges("Hello hello", "HELLO")

        assertEquals(listOf(0..4, 6..10), ranges)
    }

    private fun sentence(index: Int, text: String) = LrcSentence(
        index = index,
        startTime = index * 1_000L,
        endTime = (index + 1) * 1_000L,
        text = text
    )
}
