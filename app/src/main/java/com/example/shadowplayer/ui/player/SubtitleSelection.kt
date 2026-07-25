package com.example.shadowplayer.ui.player

import com.example.shadowplayer.player.LrcSentence

internal fun buildSubtitleCopyText(
    sentences: List<LrcSentence>,
    selectedIndices: Set<Int>
): String = sentences
    .mapIndexedNotNull { index, sentence ->
        sentence.text.takeIf { index in selectedIndices }
    }
    .joinToString(separator = "\n")
