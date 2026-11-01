package com.example.shadowplayer.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class LibrarySortTest {

    private val natural = Comparator(::compareNatural)

    @Test
    fun numbersInNamesCompareByValue() {
        val names = listOf("track 10", "track 2", "track 01", "track 1", "第10课", "第2课")

        assertEquals(listOf("track 1", "track 01", "track 2", "track 10", "第2课", "第10课"), names.sortedWith(natural))
    }

    @Test
    fun zeroPaddedNamesKeepTheirOrder() {
        val names = listOf("010.10 track 10", "002.02 track 2", "001.01 track 1")

        assertEquals(listOf("001.01 track 1", "002.02 track 2", "010.10 track 10"), names.sortedWith(natural))
    }

    @Test
    fun fullWidthDigitsCompareByValueAfterNormalizing() {
        assertTrue(compareNatural(normalizeSearchText("第１０課"), normalizeSearchText("第2課")) > 0)
    }

    @Test
    fun addedTimeComparesDaysAndSortsTheSameDayByName() {
        val keys = listOf(
            key("b", addedAt = DAY_1 + 5 * HOUR),
            key("a", addedAt = DAY_1 + 6 * HOUR),
            key("c", addedAt = DAY_2)
        )

        assertEquals(listOf("c", "a", "b"), keys.sortedForLibrary(LibrarySortType.ADDED_DESC, ZoneOffset.UTC) { it }.names())
        assertEquals(listOf("a", "b", "c"), keys.sortedForLibrary(LibrarySortType.ADDED_ASC, ZoneOffset.UTC) { it }.names())
    }

    @Test
    fun dayBoundaryFollowsTheLocalTimeZone() {
        // UTC 10 月 1 日 14:00 和 16:00 是同一天；在东京分别是 1 日 23:00 和 2 日 01:00
        val keys = listOf(
            key("a", addedAt = Instant.parse("2026-10-01T14:00:00Z").toEpochMilli()),
            key("b", addedAt = Instant.parse("2026-10-01T16:00:00Z").toEpochMilli())
        )

        assertEquals(listOf("a", "b"), keys.sortedForLibrary(LibrarySortType.ADDED_DESC, ZoneOffset.UTC) { it }.names())
        assertEquals(listOf("b", "a"), keys.sortedForLibrary(LibrarySortType.ADDED_DESC, ZoneId.of("Asia/Tokyo")) { it }.names())
    }

    @Test
    fun recentPlayPutsNeverPlayedLastAndTiesSortByName() {
        val keys = listOf(key("c"), key("b", lastPlayedAt = DAY_1), key("a"), key("d", lastPlayedAt = DAY_2, playCount = 3))

        assertEquals(listOf("d", "b", "a", "c"), keys.sortedForLibrary(LibrarySortType.RECENT_PLAY) { it }.names())
        assertEquals(listOf("d", "a", "b", "c"), keys.sortedForLibrary(LibrarySortType.PLAY_COUNT_DESC) { it }.names())
    }

    @Test
    fun foldersWithoutAChoiceUseNameAndTheRootUsesAnEmptyKey() {
        val sorts = mapOf("" to LibrarySortType.ADDED_DESC, "books%2FAlice" to LibrarySortType.NAME_DESC)

        assertEquals(LibrarySortType.ADDED_DESC, sorts.sortFor(null))
        assertEquals(LibrarySortType.NAME_DESC, sorts.sortFor("books%2FAlice"))
        assertEquals(LibrarySortType.NAME_ASC, sorts.sortFor("books%2FOz"))
    }

    private fun key(name: String, addedAt: Long = DAY_1, lastPlayedAt: Long = 0L, playCount: Int = 0) =
        LibrarySortKey(name, addedAt, lastPlayedAt, playCount, audioCount = 1)

    private fun List<LibrarySortKey>.names() = map { it.name }

    private companion object {
        const val HOUR = 3_600_000L
        val DAY_1 = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
        val DAY_2 = Instant.parse("2026-10-02T00:00:00Z").toEpochMilli()
    }
}
