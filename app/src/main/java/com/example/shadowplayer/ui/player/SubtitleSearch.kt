package com.example.shadowplayer.ui.player

import com.example.shadowplayer.player.LrcSentence

internal data class SubtitleListItem(
    val originalIndex: Int,
    val sentence: LrcSentence
)

internal fun filterSubtitleItems(
    sentences: List<LrcSentence>,
    query: String
): List<SubtitleListItem> {
    val normalizedQuery = query.trim()

    return sentences.mapIndexedNotNull { index, sentence ->
        if (normalizedQuery.isEmpty() || sentence.text.contains(normalizedQuery, ignoreCase = true)) {
            SubtitleListItem(originalIndex = index, sentence = sentence)
        } else {
            null
        }
    }
}

internal fun findSubtitleMatchRanges(text: String, query: String): List<IntRange> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return emptyList()

    val matches = mutableListOf<IntRange>()
    var searchFrom = 0

    while (searchFrom <= text.length - normalizedQuery.length) {
        val matchStart = text.indexOf(
            string = normalizedQuery,
            startIndex = searchFrom,
            ignoreCase = true
        )
        if (matchStart < 0) break

        matches += matchStart until (matchStart + normalizedQuery.length)
        searchFrom = matchStart + normalizedQuery.length
    }

    return matches
}
