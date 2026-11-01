package com.example.shadowplayer.ui.library

import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.entity.ScanFolder
import java.net.URLDecoder
import java.text.Normalizer
import java.time.ZoneId

/**
 * 扫描目录下的一个文件夹。[docId] 与文件夹浏览使用的 ID 相同，可以直接用于跳转。
 * 时间和播放次数是排序用的，含义见 [FolderSortStats]。
 */
internal data class SearchableFolder(
    val docId: String,
    val name: String,
    val parentLabel: String,
    val audioCount: Int,
    val nameKey: String,
    val pathKey: String,
    val latestAddedAt: Long = 0L,
    val latestPlayedAt: Long = 0L,
    val playCount: Int = 0
) {
    val sortKey: LibrarySortKey
        get() = LibrarySortKey(nameKey, latestAddedAt, latestPlayedAt, playCount, audioCount, path = pathKey)
}

/**
 * [folderLabel] 是可读的文件夹路径，例如 "ShadowTest/Alice"。
 * [folderDocIds] 是从扫描目录到所在文件夹的每一级文件夹 ID；不在扫描目录下的音频为空。
 */
internal data class SearchableAudio(
    val audioFile: AudioFile,
    val folderLabel: String,
    val folderKey: String,
    val titleKey: String,
    val pathKey: String,
    val folderDocIds: List<String> = emptyList()
) {
    /** 按路径排时先比所在文件夹，再比名字。 */
    val sortKey: LibrarySortKey get() = audioFile.sortKey(titleKey, path = folderKey)
}

internal data class LibrarySearchIndex(
    val folders: List<SearchableFolder>,
    val audioFiles: List<SearchableAudio>
)

internal enum class SearchResultType { ALL, FOLDERS, AUDIO }

/**
 * 搜索的过滤条件。字幕、未播放、收藏只看音频本身；
 * 文件夹里（含子文件夹）至少有一个符合条件的音频，这个文件夹才会出现在结果里。
 * [scopeDocId] 不为空时，只搜这个文件夹里面的内容。
 */
internal data class LibrarySearchFilters(
    val resultType: SearchResultType = SearchResultType.ALL,
    val hasSubtitle: Boolean = false,
    val unplayed: Boolean = false,
    val favorite: Boolean = false,
    val scopeDocId: String? = null
) {
    val hasAudioConditions: Boolean get() = hasSubtitle || unplayed || favorite
    val isActive: Boolean get() = resultType != SearchResultType.ALL || hasAudioConditions || scopeDocId != null

    /** 「未播放」看播放次数：清除播放记录只清空最近播放时间，播放次数还在。 */
    fun matchesAudio(audioFile: AudioFile): Boolean =
        (!hasSubtitle || !audioFile.lrcPath.isNullOrBlank()) &&
            (!unplayed || audioFile.playCount == 0) &&
            (!favorite || audioFile.isFavorite)
}

/** [matchedAudioCounts]：有音频条件时，每个文件夹里符合条件的音频数；没有音频条件时为 null。 */
internal data class LibrarySearchResult(
    val tokens: List<String>,
    val folders: List<SearchableFolder>,
    val audioFiles: List<SearchableAudio>,
    val filters: LibrarySearchFilters = LibrarySearchFilters(),
    val matchedAudioCounts: Map<String, Int>? = null,
    val sort: LibrarySortType = LibrarySortType.PATH
) {
    val isEmpty: Boolean get() = folders.isEmpty() && audioFiles.isEmpty()
}

private val WHITESPACE = Regex("\\s+")

/** NFKC 统一全角/半角和分解形式的假名（例如 "か" + "゙"），再统一大小写。 */
internal fun normalizeSearchText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()

/** 手机键盘选联想词后常会自动补空格，所以按空白拆词并丢掉空词。 */
internal fun searchTokens(query: String): List<String> =
    normalizeSearchText(query).split(WHITESPACE).filter { it.isNotEmpty() }

internal fun extractDocumentId(uri: String): String {
    val docMarker = "/document/"
    val docIndex = uri.indexOf(docMarker)
    if (docIndex != -1) {
        return uri.substring(docIndex + docMarker.length)
    }
    val treeMarker = "/tree/"
    val treeIndex = uri.indexOf(treeMarker)
    if (treeIndex != -1) {
        return uri.substring(treeIndex + treeMarker.length)
    }
    return uri
}

internal fun buildLibrarySearchIndex(
    audioFiles: List<AudioFile>,
    scanFolders: List<ScanFolder>
): LibrarySearchIndex {
    // 扫描目录互相嵌套时，取最深的那个
    val roots = scanFolders
        .map { it to extractDocumentId(it.path) }
        .sortedByDescending { (_, rootId) -> rootId.length }
    val folders = HashMap<String, FolderBuilder>()
    val decodedNames = HashMap<String, String>()

    val searchableAudio = audioFiles.map { audioFile ->
        val docId = extractDocumentId(audioFile.path)
        val root = roots.firstOrNull { (_, rootId) -> separatorLength(docId, rootId.length) > 0 && docId.startsWith(rootId) }
        val titleKey = normalizeSearchText(audioFile.title)

        if (root == null) {
            // 不在任何扫描目录下（例如扫描目录已被移除），只显示上一级文件夹名
            val label = parentFolderName(docId)
            val folderKey = normalizeSearchText(label)
            return@map SearchableAudio(audioFile, label, folderKey, titleKey, "$folderKey/$titleKey")
        }

        val (scanFolder, rootId) = root
        var folder = folders.getOrPut(rootId) { FolderBuilder(rootId, scanFolder.name, parentLabel = "") }
        folder.stats.add(audioFile)
        val folderDocIds = mutableListOf(rootId)
        var separatorIndex = rootId.length
        while (true) {
            val segmentStart = separatorIndex + separatorLength(docId, separatorIndex)
            val nextSeparator = nextSeparatorIndex(docId, segmentStart)
            if (nextSeparator < 0) break
            val folderDocId = docId.substring(0, nextSeparator)
            val parent = folder
            folder = folders.getOrPut(folderDocId) {
                val encodedName = docId.substring(segmentStart, nextSeparator)
                val name = decodedNames.getOrPut(encodedName) { decodeDocumentId(encodedName) }
                FolderBuilder(folderDocId, name, parentLabel = parent.label)
            }
            folder.stats.add(audioFile)
            folderDocIds += folder.docId
            separatorIndex = nextSeparator
        }
        SearchableAudio(audioFile, folder.label, folder.pathKey, titleKey, "${folder.pathKey}/$titleKey", folderDocIds)
    }

    return LibrarySearchIndex(
        folders = folders.values.map { it.build() }.sortedBy { it.pathKey },
        audioFiles = searchableAudio.sortedWith(compareBy({ it.folderKey }, { it.titleKey }))
    )
}

/**
 * 每个关键词都要出现在路径里，而且至少一个出现在自身名字里。
 * 这样输入书名时只列出这个文件夹，不会把里面几百个音频全部列出来。
 * 文件夹和音频两部分各自按 [sort] 排。
 */
internal fun searchLibrary(
    index: LibrarySearchIndex,
    tokens: List<String>,
    filters: LibrarySearchFilters = LibrarySearchFilters(),
    sort: LibrarySortType = LibrarySortType.PATH,
    zone: ZoneId = ZoneId.systemDefault()
): LibrarySearchResult {
    fun matches(nameKey: String, pathKey: String) =
        tokens.all { pathKey.contains(it) } && tokens.any { nameKey.contains(it) }

    val scope = filters.scopeDocId
    val matchedAudioCounts = if (filters.hasAudioConditions) {
        HashMap<String, Int>().also { counts ->
            index.audioFiles.forEach { audio ->
                if (filters.matchesAudio(audio.audioFile)) {
                    audio.folderDocIds.forEach { counts[it] = (counts[it] ?: 0) + 1 }
                }
            }
        }
    } else {
        null
    }

    val folders = if (filters.resultType == SearchResultType.AUDIO) emptyList() else {
        index.folders.filter { folder ->
            (scope == null || isInsideFolder(folder.docId, scope)) &&
                (matchedAudioCounts == null || (matchedAudioCounts[folder.docId] ?: 0) > 0) &&
                matches(folder.nameKey, folder.pathKey)
        }
    }
    val audioFiles = if (filters.resultType == SearchResultType.FOLDERS) emptyList() else {
        index.audioFiles.filter { audio ->
            (scope == null || scope in audio.folderDocIds) &&
                filters.matchesAudio(audio.audioFile) &&
                matches(audio.titleKey, audio.pathKey)
        }
    }
    return LibrarySearchResult(
        tokens = tokens,
        folders = folders.sortedForLibrary(sort, zone) { it.sortKey },
        audioFiles = audioFiles.sortedForLibrary(sort, zone) { it.sortKey },
        filters = filters,
        matchedAudioCounts = matchedAudioCounts,
        sort = sort
    )
}

/** [docId] 在 [folderDocId] 里面（不含它本身）。 */
internal fun isInsideFolder(docId: String, folderDocId: String): Boolean =
    docId.length > folderDocId.length &&
        docId.startsWith(folderDocId) &&
        separatorLength(docId, folderDocId.length) > 0

/** 关键词在原文中的位置（忽略大小写），重叠的区间会合并，用于高亮。 */
internal fun findSearchMatchRanges(text: String, tokens: List<String>): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    tokens.filter { it.isNotEmpty() }.forEach { token ->
        var matchStart = text.indexOf(token, ignoreCase = true)
        while (matchStart >= 0) {
            ranges += matchStart until (matchStart + token.length)
            matchStart = text.indexOf(token, matchStart + token.length, ignoreCase = true)
        }
    }

    val merged = mutableListOf<IntRange>()
    ranges.sortedBy { it.first }.forEach { range ->
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
    }
    return merged
}

private class FolderBuilder(val docId: String, val name: String, val parentLabel: String) {
    val label = if (parentLabel.isEmpty()) name else "$parentLabel/$name"
    val pathKey = normalizeSearchText(label)
    val stats = FolderSortStats()

    fun build() = SearchableFolder(
        docId = docId,
        name = name,
        parentLabel = parentLabel,
        audioCount = stats.audioCount,
        nameKey = normalizeSearchText(name),
        pathKey = pathKey,
        latestAddedAt = stats.latestAddedAt,
        latestPlayedAt = stats.latestPlayedAt,
        playCount = stats.playCount
    )
}

/** Document ID 中的路径分隔符可能是编码后的 "%2F"，也可能是 "/"。 */
private fun separatorLength(docId: String, index: Int): Int = when {
    docId.startsWith("%2F", index) -> 3
    docId.startsWith("/", index) -> 1
    else -> 0
}

private fun nextSeparatorIndex(docId: String, from: Int): Int {
    val encoded = docId.indexOf("%2F", from)
    val plain = docId.indexOf('/', from)
    return when {
        encoded < 0 -> plain
        plain < 0 -> encoded
        else -> minOf(encoded, plain)
    }
}

private fun decodeDocumentId(encoded: String): String =
    try {
        URLDecoder.decode(encoded, "UTF-8")
    } catch (e: Exception) {
        encoded
    }

private fun parentFolderName(docId: String): String {
    val parentPath = decodeDocumentId(docId).substringBeforeLast("/", missingDelimiterValue = "")
    return parentPath.substringAfterLast("/").ifEmpty { parentPath.substringAfterLast(":") }
}
