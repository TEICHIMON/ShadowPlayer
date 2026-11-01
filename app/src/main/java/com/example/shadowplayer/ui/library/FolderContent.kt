package com.example.shadowplayer.ui.library

import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.entity.ScanFolder
import java.net.URLDecoder
import java.time.ZoneId

/** 从搜索结果打开的文件夹。[rootDocId] 是在结果里点开的那一层，[docId] 是现在显示的那一层。 */
internal data class SearchFolderLocation(val rootDocId: String, val docId: String) {
    val isAtRoot: Boolean get() = docId == rootDocId
}

/**
 * 列出 [currentDocId] 下一层的文件夹和音频；[currentDocId] 为空时列出扫描目录。
 * 文件夹排在音频前面，两者按同一个 [sort] 排。
 */
internal fun buildFolderContent(
    currentDocId: String?,
    roots: List<ScanFolder>,
    allFiles: List<AudioFile>,
    sort: LibrarySortType,
    zone: ZoneId = ZoneId.systemDefault()
): List<FileSystemItem> {
    if (currentDocId == null) {
        val folders = roots.map { folder ->
            val folderDocId = extractDocumentId(folder.path)
            val stats = FolderSortStats()
            allFiles.forEach { file ->
                val fileDocId = extractDocumentId(file.path)
                if (fileDocId == folderDocId ||
                    fileDocId.startsWith("$folderDocId%2F") ||
                    fileDocId.startsWith("$folderDocId/")
                ) {
                    stats.add(file)
                }
            }
            FileSystemItem.Folder(folder.name, folderDocId, stats.audioCount) to stats
        }
        return folders.sortedFolders(sort, zone)
    }

    val files = mutableListOf<AudioFile>()
    val subFolderStats = LinkedHashMap<String, FolderSortStats>()

    val prefixWithEncodedSep = "$currentDocId%2F"
    val prefixWithNormalSep = "$currentDocId/"

    allFiles.forEach { file ->
        val fileDocId = extractDocumentId(file.path)
        val matchedPrefix = when {
            fileDocId.startsWith(prefixWithEncodedSep) -> prefixWithEncodedSep
            fileDocId.startsWith(prefixWithNormalSep) -> prefixWithNormalSep
            else -> null
        }

        if (matchedPrefix != null) {
            val remainder = fileDocId.removePrefix(matchedPrefix)
            if (remainder.isNotEmpty()) {
                val nextEncodedSep = remainder.indexOf("%2F")
                val nextNormalSep = remainder.indexOf("/")

                if (nextEncodedSep != -1 || nextNormalSep != -1) {
                    val separatorIndex = when {
                        nextEncodedSep != -1 && nextNormalSep != -1 -> minOf(nextEncodedSep, nextNormalSep)
                        nextEncodedSep != -1 -> nextEncodedSep
                        else -> nextNormalSep
                    }
                    val subFolderName = remainder.substring(0, separatorIndex)
                    val subFolderDocId = matchedPrefix + subFolderName
                    subFolderStats.getOrPut(subFolderDocId) { FolderSortStats() }.add(file)
                } else {
                    files.add(file)
                }
            }
        }
    }

    val folders = subFolderStats.map { (subFolderDocId, stats) ->
        val subFolderName = subFolderDocId.substringAfterLast("%2F").substringAfterLast("/")
        val displayName = try {
            URLDecoder.decode(subFolderName, "UTF-8")
        } catch (e: Exception) {
            subFolderName
        }
        FileSystemItem.Folder(displayName, subFolderDocId, stats.audioCount) to stats
    }

    return folders.sortedFolders(sort, zone) +
        files.sortedForLibrary(sort, zone) { it.sortKey() }.map { FileSystemItem.File(it) }
}

private fun List<Pair<FileSystemItem.Folder, FolderSortStats>>.sortedFolders(
    sort: LibrarySortType,
    zone: ZoneId
): List<FileSystemItem> =
    sortedForLibrary(sort, zone) { (folder, stats) -> stats.sortKey(normalizeSearchText(folder.name)) }
        .map { it.first }

/** 去掉最后一级，得到上一级文件夹的 ID；没有分隔符时返回 null。 */
internal fun parentDocumentId(docId: String): String? {
    val lastEncodedSep = docId.lastIndexOf("%2F")
    val lastNormalSep = docId.lastIndexOf("/")
    return when {
        lastEncodedSep > lastNormalSep -> docId.substring(0, lastEncodedSep)
        lastNormalSep != -1 -> docId.substring(0, lastNormalSep)
        else -> null
    }
}

/** 扫描目录用添加时保存的名字，其他文件夹用 ID 的最后一级。 */
internal fun folderDisplayName(docId: String, roots: List<ScanFolder>): String {
    roots.firstOrNull { extractDocumentId(it.path) == docId }?.let { return it.name }
    val lastSegment = docId.substringAfterLast("%2F").substringAfterLast("/")
    return try {
        URLDecoder.decode(lastSegment, "UTF-8")
    } catch (e: Exception) {
        lastSegment
    }
}
