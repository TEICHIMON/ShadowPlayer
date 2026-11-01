package com.example.shadowplayer.ui.library

import com.example.shadowplayer.data.entity.AudioFile
import java.time.Instant
import java.time.ZoneId

/**
 * 文件夹浏览和搜索结果共用的排序。文件夹和音频按同一个规则排，文件夹仍然排在音频前面。
 * 规则比不出先后时按名称排；名称里的数字按数值比较，"2" 排在 "10" 前面。
 */
enum class LibrarySortType(val displayName: String) {
    PATH("路径 A→Z"),
    NAME_ASC("名称 A→Z"),
    NAME_DESC("名称 Z→A"),
    ADDED_DESC("添加时间 新→旧"),
    ADDED_ASC("添加时间 旧→新"),
    RECENT_PLAY("最近播放"),
    PLAY_COUNT_DESC("播放次数 多→少"),
    PLAY_COUNT_ASC("播放次数 少→多"),
    AUDIO_COUNT_DESC("文件数量 多→少"),
    AUDIO_COUNT_ASC("文件数量 少→多")
}

/** 同一个文件夹里的项目路径都一样，不需要「路径」。 */
internal val FOLDER_SORT_OPTIONS: List<LibrarySortType> = LibrarySortType.entries - LibrarySortType.PATH

/** 文件夹里只有音频时，「文件数量」排不出先后，选了看起来没有反应。 */
internal val AUDIO_ONLY_SORT_OPTIONS: List<LibrarySortType> =
    FOLDER_SORT_OPTIONS - LibrarySortType.AUDIO_COUNT_DESC - LibrarySortType.AUDIO_COUNT_ASC

/** 搜索结果默认按路径，同一个文件夹的结果排在一起。音频没有「文件数量」，搜索里不提供。 */
internal val SEARCH_SORT_OPTIONS: List<LibrarySortType> =
    LibrarySortType.entries - LibrarySortType.AUDIO_COUNT_DESC - LibrarySortType.AUDIO_COUNT_ASC

/** 没选过排序的文件夹按名称排。[folderDocId] 为空表示扫描目录列表。 */
internal fun Map<String, LibrarySortType>.sortFor(folderDocId: String?): LibrarySortType =
    this[folderDocId.orEmpty()] ?: LibrarySortType.NAME_ASC

/**
 * 排序用的值。[name] 和 [path] 要先经过 [normalizeSearchText]。
 * [addedAt] 是第一次扫描到的时间，[lastPlayedAt] 为 0 表示没有播放过。
 */
internal data class LibrarySortKey(
    val name: String,
    val addedAt: Long,
    val lastPlayedAt: Long,
    val playCount: Int,
    val audioCount: Int,
    val path: String = ""
)

internal fun AudioFile.sortKey(name: String = normalizeSearchText(title), path: String = "") =
    LibrarySortKey(name, createdAt, lastPlayedAt ?: 0L, playCount, audioCount = 1, path = path)

/** 文件夹里（含子文件夹）全部音频的统计：添加时间取最新加入的，最近播放取最近的，播放次数取总和。 */
internal class FolderSortStats {
    var audioCount = 0
        private set
    var latestAddedAt = 0L
        private set
    var latestPlayedAt = 0L
        private set
    var playCount = 0
        private set

    fun add(audioFile: AudioFile) {
        audioCount++
        latestAddedAt = maxOf(latestAddedAt, audioFile.createdAt)
        latestPlayedAt = maxOf(latestPlayedAt, audioFile.lastPlayedAt ?: 0L)
        playCount += audioFile.playCount
    }

    fun sortKey(name: String, path: String = "") =
        LibrarySortKey(name, latestAddedAt, latestPlayedAt, playCount, audioCount, path)
}

/** 名称也相同时保持原来的顺序。 */
internal fun <T> List<T>.sortedForLibrary(
    sort: LibrarySortType,
    zone: ZoneId = ZoneId.systemDefault(),
    sortKey: (T) -> LibrarySortKey
): List<T> {
    if (size < 2) return this
    return map { SortEntry(it, sortKey(it), zone) }
        .sortedWith(entryComparator(sort))
        .map { it.item }
}

/**
 * 比较两个已经规范化的名字。连续的数字按数值比较，其他字符按字符编码比较。
 * 数值相同时（"01" 和 "1"）位数少的排前面。
 */
internal fun compareNatural(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        if (a[i].isPlainDigit() && b[j].isPlainDigit()) {
            val startA = i
            val startB = j
            while (i < a.length && a[i].isPlainDigit()) i++
            while (j < b.length && b[j].isPlainDigit()) j++
            val result = compareNumbers(a, startA, i, b, startB, j)
            if (result != 0) return result
        } else {
            if (a[i] != b[j]) return a[i].compareTo(b[j])
            i++
            j++
        }
    }
    return (a.length - i).compareTo(b.length - j)
}

/**
 * 「添加时间」只比到日期：第一次扫描时几千个文件的时间只差几毫秒，
 * 按毫秒排就是扫描的顺序，看起来是乱的。同一天加入的按名称排。
 */
private class SortEntry<T>(val item: T, val key: LibrarySortKey, zone: ZoneId) {
    val addedDay: Long = Instant.ofEpochMilli(key.addedAt).atZone(zone).toLocalDate().toEpochDay()
}

private fun entryComparator(sort: LibrarySortType): Comparator<SortEntry<*>> {
    val byName = Comparator<SortEntry<*>> { a, b -> compareNatural(a.key.name, b.key.name) }
    val byPath = Comparator<SortEntry<*>> { a, b -> compareNatural(a.key.path, b.key.path) }
    return when (sort) {
        LibrarySortType.PATH -> byPath.then(byName)
        LibrarySortType.NAME_ASC -> byName
        LibrarySortType.NAME_DESC -> byName.reversed()
        LibrarySortType.ADDED_DESC -> compareByDescending<SortEntry<*>> { it.addedDay }.then(byName)
        LibrarySortType.ADDED_ASC -> compareBy<SortEntry<*>> { it.addedDay }.then(byName)
        LibrarySortType.RECENT_PLAY -> compareByDescending<SortEntry<*>> { it.key.lastPlayedAt }.then(byName)
        LibrarySortType.PLAY_COUNT_DESC -> compareByDescending<SortEntry<*>> { it.key.playCount }.then(byName)
        LibrarySortType.PLAY_COUNT_ASC -> compareBy<SortEntry<*>> { it.key.playCount }.then(byName)
        LibrarySortType.AUDIO_COUNT_DESC -> compareByDescending<SortEntry<*>> { it.key.audioCount }.then(byName)
        LibrarySortType.AUDIO_COUNT_ASC -> compareBy<SortEntry<*>> { it.key.audioCount }.then(byName)
    }
}

private fun Char.isPlainDigit() = this in '0'..'9'

/** 去掉开头的 0 以后，位数多的数大；位数相同时逐位比较。 */
private fun compareNumbers(a: String, startA: Int, endA: Int, b: String, startB: Int, endB: Int): Int {
    var valueA = startA
    while (valueA < endA - 1 && a[valueA] == '0') valueA++
    var valueB = startB
    while (valueB < endB - 1 && b[valueB] == '0') valueB++
    val digitsA = endA - valueA
    val digitsB = endB - valueB
    if (digitsA != digitsB) return digitsA.compareTo(digitsB)
    for (k in 0 until digitsA) {
        if (a[valueA + k] != b[valueB + k]) return a[valueA + k].compareTo(b[valueB + k])
    }
    return (endA - startA).compareTo(endB - startB)
}
