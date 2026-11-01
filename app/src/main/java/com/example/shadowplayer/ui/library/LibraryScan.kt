package com.example.shadowplayer.ui.library

/**
 * 扫描以后要删掉的记录：数据库里有、这次没有找到的文件。
 *
 * 列出来是空的文件夹（[emptyFolderDocIds]），里面的记录一条都不删。系统的存储服务出错时，
 * 会把文件夹当成「隐藏」返回 0 个文件，也不报错；2026-11-01 一次启动扫描因此删掉了全部播放记录。
 * 文件夹真的被清空时，记录会留在库里，但不会丢掉播放记录。
 */
internal fun pathsToDelete(
    existingPaths: Set<String>,
    foundPaths: Set<String>,
    emptyFolderDocIds: Set<String>
): Set<String> =
    (existingPaths - foundPaths).filterTo(HashSet()) { path ->
        val docId = extractDocumentId(path)
        emptyFolderDocIds.none { folderDocId -> isInsideFolder(docId, folderDocId) }
    }
