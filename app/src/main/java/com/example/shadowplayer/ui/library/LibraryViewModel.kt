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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder
import javax.inject.Inject

// Tab枚举：把ALL改为RECORDS（记录）
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

    // 记录页面：按直接父文件夹分组的历史记录
    val recordGroups: StateFlow<List<FolderGroup>> = combine(
        repository.getHistory(),
        _searchQuery
    ) { files, query ->
        val filtered = if (query.isBlank()) files else files.filter { it.title.contains(query, ignoreCase = true) }

        // 按直接父文件夹分组
        filtered.groupBy { audioFile ->
            getParentFolderPath(audioFile.path)
        }.map { (folderPath, audioFiles) ->
            FolderGroup(
                folderName = getParentFolderName(folderPath),
                folderPath = folderPath,
                audioFiles = audioFiles
            )
        }.sortedByDescending { group ->
            // 按最近播放时间排序
            group.audioFiles.maxOfOrNull { it.lastPlayedAt ?: 0L } ?: 0L
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

    // 全部音频（用于文件夹内容计算）
    private val allAudioFiles: StateFlow<List<AudioFile>> = repository.getAllAudioFiles()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- 文件夹浏览器状态 ---
    private val _currentFolderPath = MutableStateFlow<String?>(null)
    val currentFolderPath: StateFlow<String?> = _currentFolderPath.asStateFlow()

    // 文件夹展开状态
    private val _expandedFolders = MutableStateFlow<Set<String>>(emptySet())
    val expandedFolders: StateFlow<Set<String>> = _expandedFolders.asStateFlow()

    // [重写] 文件夹内容：完全基于 Raw URI 进行路径匹配，解决层级展开问题
    val folderContent: StateFlow<List<FileSystemItem>> = combine(
        _currentFolderPath,
        scanFolders,
        allAudioFiles
    ) { currentPath, roots, allFiles ->
        if (currentPath == null) {
            // 根目录：显示所有已添加的扫描文件夹
            roots.map { folder ->
                // folder.path 是 Raw URI (e.g. ...%3AMusic)
                // file.path 也是 Raw URI，直接前缀匹配即可
                // 注意：为了精确匹配，我们加上 %2F 确保匹配到目录边界，或者完全相等
                val prefix = folder.path
                val count = allFiles.count {
                    it.path == prefix || it.path.startsWith("$prefix%2F") || it.path.startsWith("$prefix/")
                }
                FileSystemItem.Folder(folder.name, folder.path, count)
            }
        } else {
            // 子目录
            // currentPath 是 Raw URI (e.g. ...%3AMusic%2FSub)
            val items = mutableListOf<FileSystemItem>()
            val processedSubFolders = mutableSetOf<String>()

            // SAF 路径通常使用 %2F 作为分隔符，但也可能使用 / (取决于具体 URI 实现)
            // 为了稳健性，我们在 currentPath 后加上分隔符来做前缀匹配
            // 如果 raw path 本身不以 %2F 结尾，我们假设它是目录
            val targetPrefixEncoded = "$currentPath%2F"
            // 有些情况下（如 file scheme），可能是 /
            val targetPrefixNormal = "$currentPath/"

            allFiles.forEach { file ->
                val rawPath = file.path

                // 1. 检查是否在当前目录下
                // 必须以当前路径开头
                if (rawPath.startsWith(targetPrefixEncoded) || rawPath.startsWith(targetPrefixNormal)) {

                    val prefixUsed = if (rawPath.startsWith(targetPrefixEncoded)) targetPrefixEncoded else targetPrefixNormal

                    // 获取相对路径部分
                    val remainder = rawPath.removePrefix(prefixUsed)

                    if (remainder.isEmpty()) return@forEach // 异常情况

                    // 检查剩余部分是否包含分隔符 (%2F 或 /)
                    // 如果包含，说明是子文件夹；如果不包含，说明是直接文件
                    val nextSeparatorIndexEncoded = remainder.indexOf("%2F")
                    val nextSeparatorIndexNormal = remainder.indexOf("/")

                    val isSubFolderEncoded = nextSeparatorIndexEncoded != -1
                    val isSubFolderNormal = nextSeparatorIndexNormal != -1

                    if (isSubFolderEncoded || isSubFolderNormal) {
                        // 是子文件夹
                        val subFolderNameRaw = when {
                            isSubFolderEncoded && isSubFolderNormal -> remainder.substring(0, minOf(nextSeparatorIndexEncoded, nextSeparatorIndexNormal))
                            isSubFolderEncoded -> remainder.substring(0, nextSeparatorIndexEncoded)
                            else -> remainder.substring(0, nextSeparatorIndexNormal)
                        }

                        // [关键] 构造子文件夹的 Raw Path，用于后续导航
                        // 必须保持原始编码，这样点击进入后 currentPath 依然是 Raw 的
                        val subFolderPathRaw = prefixUsed + subFolderNameRaw

                        if (!processedSubFolders.contains(subFolderPathRaw)) {
                            processedSubFolders.add(subFolderPathRaw)

                            // 显示名称需要解码 (e.g. Music%201 -> Music 1)
                            val displayName = try {
                                URLDecoder.decode(subFolderNameRaw, "UTF-8")
                            } catch (e: Exception) {
                                subFolderNameRaw
                            }

                            // 计算子目录下的文件数 (递归)
                            val subCount = allFiles.count {
                                it.path.startsWith("$subFolderPathRaw%2F") || it.path.startsWith("$subFolderPathRaw/")
                            }

                            items.add(FileSystemItem.Folder(displayName, subFolderPathRaw, subCount))
                        }
                    } else {
                        // 是当前目录下的文件
                        items.add(FileSystemItem.File(file))
                    }
                }
            }
            items.sortedBy {
                when(it) {
                    is FileSystemItem.Folder -> "0${it.name}"
                    is FileSystemItem.File -> "1${it.audioFile.title}"
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

    // [修改] 仅用于显示或历史记录分组，返回解码后的父路径
    private fun getParentFolderPath(filePath: String): String {
        val decoded = try { URLDecoder.decode(filePath, "UTF-8") } catch(e: Exception) { filePath }
        return decoded.substringBeforeLast("/")
    }

    private fun getParentFolderName(folderPath: String): String {
        // 先解码再截取，确保显示正常
        val decoded = try { URLDecoder.decode(folderPath, "UTF-8") } catch(e: Exception) { folderPath }
        return decoded.substringAfterLast("/").ifEmpty { decoded }
    }

    // [新增] 获取音频详情
    fun showAudioDetails(audioFile: AudioFile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(audioFile.path)
                var fileSize = 0L

                // 尝试获取文件大小
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
                    parentFolder = getParentFolderName(audioFile.path) // 使用 decode 后的名称
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
    fun navigateToFolder(path: String) {
        _currentFolderPath.value = path
    }

    fun navigateUp() {
        val current = _currentFolderPath.value ?: return

        // 1. 检查是否已经是根目录 (Raw Path 比较)
        val isRoot = scanFolders.value.any { it.path == current }

        if (isRoot) {
            _currentFolderPath.value = null
        } else {
            // 2. 回到上一级 (移除最后一个 %2F 或 / 及其之后的内容)
            // 优先处理 %2F (SAF 常见)
            val lastEncodedSeparator = current.lastIndexOf("%2F")
            val lastNormalSeparator = current.lastIndexOf("/")

            if (lastEncodedSeparator != -1 && lastEncodedSeparator > lastNormalSeparator) {
                _currentFolderPath.value = current.substring(0, lastEncodedSeparator)
            } else if (lastNormalSeparator != -1) {
                _currentFolderPath.value = current.substring(0, lastNormalSeparator)
            } else {
                // 找不到分隔符，异常情况，回根目录
                _currentFolderPath.value = null
            }
        }
    }

    // 切换文件夹展开状态
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

            // [修复] 保持 Raw URI，不要解码
            val path = uri.toString()

            val folder = ScanFolder(path = path, name = name)
            repository.insertScanFolder(folder)
            scanFolder(folder)
        }
    }

    fun removeScanFolder(folder: ScanFolder) {
        viewModelScope.launch {
            repository.deleteScanFolder(folder)
            repository.deleteAudioFilesByPathPrefix(folder.path)
        }
    }

    fun scanFolder(folder: ScanFolder) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                // folder.path 是 Raw URI，直接 parse
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

                // [修复] 使用 Raw URI
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
}