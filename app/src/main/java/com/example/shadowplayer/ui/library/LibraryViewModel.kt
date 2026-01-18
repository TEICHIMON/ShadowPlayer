package com.example.shadowplayer.ui.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.entity.ScanFolder
import com.example.shadowplayer.data.entity.Tag
import com.example.shadowplayer.data.repository.AudioRepository
import com.example.shadowplayer.player.SentencePlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder
import javax.inject.Inject

// Tab枚举
enum class LibraryTab {
    RECORDS, FAVORITES, HISTORY, TAGS, FOLDERS
}

// 文件夹显示项模型
sealed class FileSystemItem {
    data class Folder(val name: String, val path: String, val audioCount: Int = 0) : FileSystemItem()
    data class File(val audioFile: AudioFile) : FileSystemItem()
}

// 按文件夹分组的记录
data class FolderGroup(
    val folderName: String,
    val folderPath: String,
    val audioFiles: List<AudioFile>
)

// 音频详情数据类
data class AudioFileDetails(
    val audioFile: AudioFile,
    val fileSizeBytes: Long,
    val formattedSize: String,
    val formattedDuration: String,
    val hasSubtitle: Boolean,
    val parentFolder: String
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: AudioRepository,
    private val sentencePlayer: SentencePlayer,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedAudioIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedAudioIds: StateFlow<Set<Long>> = _selectedAudioIds.asStateFlow()

    // 音频详情对话框状态
    private val _audioDetailsState = MutableStateFlow<AudioFileDetails?>(null)
    val audioDetailsState: StateFlow<AudioFileDetails?> = _audioDetailsState.asStateFlow()

    // 当前正在播放的音频ID（从SentencePlayer获取）
    val currentPlayingAudioId: StateFlow<Long> = sentencePlayer.state
        .map { sentencePlayer.getCurrentAudioId() }
        .stateIn(viewModelScope, SharingStarted.Lazily, -1L)

    // ===== 核心修复：提取 Document ID =====
    private fun extractDocumentId(uri: String): String {
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

    // 全部音频（用于文件夹内容计算）
    private val allAudioFiles: StateFlow<List<AudioFile>> = repository.getAllAudioFiles()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // [问题4修改] 记录页面：按直接父文件夹分组，显示该文件夹下的所有音频
    val recordGroups: StateFlow<List<FolderGroup>> = combine(
        repository.getHistory(),
        allAudioFiles,
        _searchQuery
    ) { historyFiles, allFiles, query ->
        // 1. 找出所有有播放记录的文件的父文件夹
        val foldersWithHistory = historyFiles.map { audioFile ->
            getParentFolderPath(audioFile.path)
        }.toSet()

        // 2. 对于每个这样的文件夹，获取该文件夹下的所有音频（不仅仅是播放过的）
        val groups = foldersWithHistory.mapNotNull { folderPath ->
            // 获取该文件夹下的所有音频
            val folderAudioFiles = allFiles.filter { audioFile ->
                getParentFolderPath(audioFile.path) == folderPath
            }

            if (folderAudioFiles.isEmpty()) return@mapNotNull null

            // 应用搜索过滤
            val filteredFiles = if (query.isBlank()) {
                folderAudioFiles
            } else {
                folderAudioFiles.filter { it.title.contains(query, ignoreCase = true) }
            }

            if (filteredFiles.isEmpty()) return@mapNotNull null

            // 按文件名排序
            val sortedFiles = filteredFiles.sortedBy { it.title.lowercase() }

            FolderGroup(
                folderName = getParentFolderName(folderPath),
                folderPath = folderPath,
                audioFiles = sortedFiles
            )
        }

        // 3. 按最近播放时间排序文件夹（基于该文件夹中有播放记录的文件）
        groups.sortedByDescending { group ->
            group.audioFiles
                .filter { it.lastPlayedAt != null }
                .maxOfOrNull { it.lastPlayedAt ?: 0L } ?: 0L
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 收藏
    val favorites: StateFlow<List<AudioFile>> = combine(
        repository.getFavorites(),
        _searchQuery
    ) { files, query ->
        if (query.isBlank()) files else files.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 历史记录（扁平列表，用于HISTORY tab）
    val history: StateFlow<List<AudioFile>> = combine(
        repository.getHistory(),
        _searchQuery
    ) { files, query ->
        if (query.isBlank()) files else files.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val scanFolders: StateFlow<List<ScanFolder>> = repository.getAllScanFolders()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- 文件夹浏览器状态 ---
    private val _currentFolderDocId = MutableStateFlow<String?>(null)
    val currentFolderPath: StateFlow<String?> = _currentFolderDocId.asStateFlow()

    private val _expandedFolders = MutableStateFlow<Set<String>>(emptySet())
    val expandedFolders: StateFlow<Set<String>> = _expandedFolders.asStateFlow()

    // 文件夹内容
    val folderContent: StateFlow<List<FileSystemItem>> = combine(
        _currentFolderDocId,
        scanFolders,
        allAudioFiles
    ) { currentDocId, roots, allFiles ->
        if (currentDocId == null) {
            roots.map { folder ->
                val folderDocId = extractDocumentId(folder.path)
                val count = allFiles.count { file ->
                    val fileDocId = extractDocumentId(file.path)
                    fileDocId == folderDocId ||
                            fileDocId.startsWith("$folderDocId%2F") ||
                            fileDocId.startsWith("$folderDocId/")
                }
                FileSystemItem.Folder(folder.name, folderDocId, count)
            }
        } else {
            val items = mutableListOf<FileSystemItem>()
            val processedSubFolders = mutableSetOf<String>()

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
                    if (remainder.isEmpty()) return@forEach

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

                        if (!processedSubFolders.contains(subFolderDocId)) {
                            processedSubFolders.add(subFolderDocId)

                            val displayName = try {
                                URLDecoder.decode(subFolderName, "UTF-8")
                            } catch (e: Exception) {
                                subFolderName
                            }

                            val subCount = allFiles.count { f ->
                                val fDocId = extractDocumentId(f.path)
                                fDocId.startsWith("$subFolderDocId%2F") ||
                                        fDocId.startsWith("$subFolderDocId/")
                            }

                            items.add(FileSystemItem.Folder(displayName, subFolderDocId, subCount))
                        }
                    } else {
                        items.add(FileSystemItem.File(file))
                    }
                }
            }

            items.sortedBy {
                when (it) {
                    is FileSystemItem.Folder -> "0${it.name.lowercase()}"
                    is FileSystemItem.File -> "1${it.audioFile.title.lowercase()}"
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val rootTags: StateFlow<List<Tag>> = repository.getRootTags()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allTags: StateFlow<List<Tag>> = repository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedTagId = MutableStateFlow<Long?>(null)
    val selectedTagId: StateFlow<Long?> = _selectedTagId.asStateFlow()

    val audioFilesByTag: StateFlow<List<AudioFile>> = combine(
        _selectedTagId.flatMapLatest { tagId ->
            if (tagId != null) repository.getAudioFilesByTag(tagId) else repository.getAudioFilesWithAnyTag()
        },
        _searchQuery
    ) { files, query ->
        if (query.isBlank()) files else files.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // --- 辅助函数 ---
    private fun getParentFolderPath(filePath: String): String {
        val docId = extractDocumentId(filePath)
        val decoded = try { URLDecoder.decode(docId, "UTF-8") } catch (e: Exception) { docId }
        return decoded.substringBeforeLast("/")
    }

    private fun getParentFolderName(folderPath: String): String {
        val decoded = try { URLDecoder.decode(folderPath, "UTF-8") } catch (e: Exception) { folderPath }
        return decoded.substringAfterLast("/").ifEmpty { decoded.substringAfterLast(":") }
    }

    fun showAudioDetails(audioFile: AudioFile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(audioFile.path)
                var fileSize = 0L

                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        fileSize = pfd.statSize
                    }
                } catch (e: Exception) {
                    Log.e("LibraryViewModel", "Failed to get file size", e)
                }

                val details = AudioFileDetails(
                    audioFile = audioFile,
                    fileSizeBytes = fileSize,
                    formattedSize = formatFileSize(fileSize),
                    formattedDuration = formatDuration(audioFile.duration),
                    hasSubtitle = !audioFile.lrcPath.isNullOrEmpty(),
                    parentFolder = getParentFolderName(audioFile.path)
                )
                _audioDetailsState.value = details
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Failed to get audio details", e)
            }
        }
    }

    fun dismissAudioDetails() {
        _audioDetailsState.value = null
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    fun search(query: String) { _searchQuery.value = query }

    // --- 文件夹导航 ---
    fun navigateToFolder(docId: String) {
        _currentFolderDocId.value = docId
    }

    fun navigateUp() {
        val currentDocId = _currentFolderDocId.value ?: return

        val isRoot = scanFolders.value.any { extractDocumentId(it.path) == currentDocId }

        if (isRoot) {
            _currentFolderDocId.value = null
        } else {
            val lastEncodedSep = currentDocId.lastIndexOf("%2F")
            val lastNormalSep = currentDocId.lastIndexOf("/")

            val parentDocId = when {
                lastEncodedSep > lastNormalSep -> currentDocId.substring(0, lastEncodedSep)
                lastNormalSep != -1 -> currentDocId.substring(0, lastNormalSep)
                else -> null
            }
            _currentFolderDocId.value = parentDocId
        }
    }

    fun toggleFolderExpanded(path: String) {
        val current = _expandedFolders.value
        _expandedFolders.value = if (current.contains(path)) {
            current - path
        } else {
            current + path
        }
    }

    // --- 批量操作 ---
    fun enterSelectionMode(initialId: Long) {
        _isSelectionMode.value = true
        _selectedAudioIds.value = setOf(initialId)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedAudioIds.value = emptySet()
    }

    fun toggleSelection(id: Long) {
        val current = _selectedAudioIds.value
        if (current.contains(id)) {
            _selectedAudioIds.value = current - id
            if (_selectedAudioIds.value.isEmpty()) exitSelectionMode()
        } else {
            _selectedAudioIds.value = current + id
        }
    }

    fun selectAll(files: List<AudioFile>) {
        _selectedAudioIds.value = files.map { it.id }.toSet()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val ids = _selectedAudioIds.value.toList()
            if (ids.isNotEmpty()) {
                repository.deleteAudios(ids)
                exitSelectionMode()
            }
        }
    }

    fun addTagsToSelected(tagId: Long) {
        viewModelScope.launch {
            val ids = _selectedAudioIds.value.toList()
            if (ids.isNotEmpty()) {
                repository.addTagToAudios(ids, tagId)
                exitSelectionMode()
            }
        }
    }

    // --- 扫描逻辑 ---
    fun addScanFolder(uri: Uri) {
        viewModelScope.launch {
            val docFile = DocumentFile.fromTreeUri(context, uri)
            val name = docFile?.name ?: "Unknown"
            val path = uri.toString()

            val folder = ScanFolder(path = path, name = name)
            repository.insertScanFolder(folder)
            scanFolder(folder)
        }
    }

    fun removeScanFolder(folder: ScanFolder) {
        viewModelScope.launch {
            repository.deleteScanFolder(folder)
            val folderDocId = extractDocumentId(folder.path)
            repository.deleteAudioFilesByPathPrefix(folderDocId)
        }
    }

    fun scanFolder(folder: ScanFolder) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val uri = Uri.parse(folder.path)
                val docFile = DocumentFile.fromTreeUri(context, uri)
                val existingPaths = repository.getAllPaths().toHashSet()
                val newAudioFiles = mutableListOf<AudioFile>()

                docFile?.let { scanDirectory(it, newAudioFiles, existingPaths) }

                if (newAudioFiles.isNotEmpty()) {
                    repository.insertAllIgnore(newAudioFiles)
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Scan failed", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanAllFolders() {
        viewModelScope.launch {
            scanFolders.value.forEach { scanFolder(it) }
        }
    }

    private fun scanDirectory(directory: DocumentFile, results: MutableList<AudioFile>, existingPaths: HashSet<String>) {
        directory.listFiles().forEach { file ->
            if (file.isDirectory) {
                scanDirectory(file, results, existingPaths)
            } else {
                val fileName = file.name ?: ""
                val filePath = file.uri.toString()

                if (existingPaths.contains(filePath)) return@forEach

                if (isAudioFile(fileName)) {
                    createAudioFile(file, filePath)?.let { results.add(it) }
                }
            }
        }
    }

    private fun isAudioFile(name: String): Boolean {
        val extensions = listOf(".mp3", ".m4a", ".wav", ".flac", ".ogg", ".aac")
        return extensions.any { name.lowercase().endsWith(it) }
    }

    private fun createAudioFile(file: DocumentFile, filePath: String): AudioFile? {
        val name = file.name ?: return null
        val duration = try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, file.uri)
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            d?.toLongOrNull() ?: 0
        } catch (e: Exception) { 0L }

        val lrcPath = findLrcFile(file)
        return AudioFile(path = filePath, title = name.substringBeforeLast("."), duration = duration, lrcPath = lrcPath)
    }

    private fun findLrcFile(audioFile: DocumentFile): String? {
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.name?.substringBeforeLast(".") ?: return null
        parent.listFiles().forEach { file ->
            val fileName = file.name ?: ""
            if (fileName.equals("$baseName.lrc", ignoreCase = true) ||
                fileName.equals("$baseName.srt", ignoreCase = true)) {
                return file.uri.toString()
            }
        }
        return null
    }

    // --- 标签与单项操作 ---
    fun selectTag(tagId: Long?) { _selectedTagId.value = tagId }
    fun createTag(name: String, parentId: Long?) { viewModelScope.launch { repository.insertTag(Tag(name = name, parentId = parentId)) } }
    fun updateTag(tag: Tag) { viewModelScope.launch { repository.updateTag(tag) } }
    fun deleteTag(tag: Tag) { viewModelScope.launch { repository.deleteTag(tag) } }
    fun addTagToAudio(audioId: Long, tagId: Long) { viewModelScope.launch { repository.addTagToAudio(audioId, tagId) } }
    fun toggleFavorite(audioFile: AudioFile) { viewModelScope.launch { repository.updateFavorite(audioFile.id, !audioFile.isFavorite) } }
    fun deleteAudio(audioFile: AudioFile) { viewModelScope.launch { repository.deleteAudio(audioFile) } }

    // ===== [问题3] 获取父文件夹下的所有音频用于播放列表 =====
    /**
     * 获取指定音频文件所在父文件夹下的所有音频（按文件名排序）
     * @return Pair<播放列表, 当前音频在列表中的索引>
     */
    fun getPlaylistForAudio(audioFile: AudioFile): Pair<List<AudioFile>, Int> {
        val parentPath = getParentFolderPath(audioFile.path)
        val allFiles = allAudioFiles.value

        // 获取同一父文件夹下的所有音频
        val playlist = allFiles
            .filter { getParentFolderPath(it.path) == parentPath }
            .sortedBy { it.title.lowercase() }

        val currentIndex = playlist.indexOfFirst { it.id == audioFile.id }
        return Pair(playlist, currentIndex)
    }

    /**
     * 设置播放列表到 SentencePlayer
     */
    fun setPlaylistForAudio(audioFile: AudioFile) {
        val (playlist, index) = getPlaylistForAudio(audioFile)
        sentencePlayer.setPlaylist(playlist, index)
    }
}
