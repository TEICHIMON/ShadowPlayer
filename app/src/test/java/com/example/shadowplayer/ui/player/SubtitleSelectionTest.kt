package com.example.shadowplayer.ui.player

import com.example.shadowplayer.player.LrcSentence
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleSelectionTest {

    private val sentences = listOf(
        sentence(index = 0, text = "第一句"),
        sentence(index = 1, text = "重复字幕"),
        sentence(index = 2, text = "第三句"),
        sentence(index = 3, text = "重复字幕")
    )

    @Test
    fun selectedSubtitlesAreCopiedAsPlainLines() {
        val copiedText = buildSubtitleCopyText(sentences, setOf(0, 1))

        assertEquals("第一句\n重复字幕", copiedText)
    }

    @Test
    fun nonContiguousSelectionUsesOriginalSubtitleOrder() {
        val copiedText = buildSubtitleCopyText(sentences, linkedSetOf(3, 0, 2))

        assertEquals("第一句\n第三句\n重复字幕", copiedText)
    }

    @Test
    fun duplicateSubtitleTextIsPreserved() {
        val copiedText = buildSubtitleCopyText(sentences, setOf(1, 3))

        assertEquals("重复字幕\n重复字幕", copiedText)
    }

    @Test
    fun emptySelectionProducesEmptyText() {
        assertEquals("", buildSubtitleCopyText(sentences, emptySet()))
    }

    private fun sentence(index: Int, text: String) = LrcSentence(
        index = index,
        startTime = index * 1_000L,
        endTime = (index + 1) * 1_000L,
        text = text
    )
}
