package com.example.shadowplayer.ui.library

import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.entity.ScanFolder
import java.net.URLDecoder

/** 从搜索结果打开的文件夹。[rootDocId] 是在结果里点开的那一层，[docId] 是现在显示的那一层。 */
internal data class SearchFolderLocation(val rootDocId: String, val docId: String) {
    val isAtRoot: Boolean get() = docId == rootDocId
}

/** 列出 [currentDocId] 下一层的文件夹和音频；[currentDocId] 为空时列出扫描目录。 */
internal fun buildFolderContent(
    currentDocId: String?,
    roots: List<ScanFolder>,
    allFiles: List<AudioFile>,
    folderSort: FolderSortType,
    fileSort: FileSortType
): List<FileSystemItem> {
    if (currentDocId == null) {
        val folders = roots.map { folder ->
            val folderDocId = extractDocumentId(folder.path)
            val audioCount = allFiles.count { file ->
                val fileDocId = extractDocumentId(file.path)
                fileDocId == folderDocId ||
                        fileDocId.startsWith("$folderDocId%2F") ||
                        fileDocId.startsWith("$folderDocId/")
            }
            FileSystemItem.Folder(folder.name, folderDocId, audioCount)
        }

        return when (folderSort) {
            FolderSortType.NAME_ASC -> folders.sortedBy { it.name.lowercase() }
            FolderSortType.NAME_DESC -> folders.sortedByDescending { it.name.lowercase() }
            FolderSortType.AUDIO_COUNT_DESC -> folders.sortedByDescending { it.audioCount }
            FolderSortType.AUDIO_COUNT_ASC -> folders.sortedBy { it.audioCount }
        }
    }

    val items = mutableListOf<FileSystemItem>()
    val processedSubFolders = mutableSetOf<String>()
    val subFolderFiles = mutableMapOf<String, MutableList<AudioFile>>()

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
                    subFolderFiles.getOrPut(subFolderDocId) { mutableListOf() }.add(file)
                    if (!processedSubFolders.contains(subFolderDocId)) {
                        processedSubFolders.add(subFolderDocId)
                    }
                } else {
                    items.add(FileSystemItem.File(file))
                }
            }
        }
    }

    processedSubFolders.forEach { subFolderDocId ->
        val filesInSubFolder = subFolderFiles[subFolderDocId] ?: emptyList()
        val subFolderName = subFolderDocId.substringAfterLast("%2F").substringAfterLast("/")
        val displayName = try {
            URLDecoder.decode(subFolderName, "UTF-8")
        } catch (e: Exception) {
            subFolderName
        }
        items.add(FileSystemItem.Folder(displayName, subFolderDocId, filesInSubFolder.size))
    }

    val folders = items.filterIsInstance<FileSystemItem.Folder>()
    val files = items.filterIsInstance<FileSystemItem.File>()

    val sortedFolders = when (folderSort) {
        FolderSortType.NAME_ASC -> folders.sortedBy { it.name.lowercase() }
        FolderSortType.NAME_DESC -> folders.sortedByDescending { it.name.lowercase() }
        FolderSortType.AUDIO_COUNT_DESC -> folders.sortedByDescending { it.audioCount }
        FolderSortType.AUDIO_COUNT_ASC -> folders.sortedBy { it.audioCount }
    }

    val sortedFiles = when (fileSort) {
        FileSortType.NAME_ASC -> files.sortedBy { it.audioFile.title.lowercase() }
        FileSortType.NAME_DESC -> files.sortedByDescending { it.audioFile.title.lowercase() }
        FileSortType.PLAY_COUNT_DESC -> files.sortedByDescending { it.audioFile.playCount }
        FileSortType.PLAY_COUNT_ASC -> files.sortedBy { it.audioFile.playCount }
        FileSortType.RECENT_PLAY -> files.sortedByDescending { it.audioFile.lastPlayedAt ?: 0L }
    }

    return sortedFolders + sortedFiles
}

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
