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

// [修改] Tab枚举：把ALL改为RECORDS（记录）
enum class LibraryTab {
    RECORDS, FAVORITES, HISTORY, TAGS, FOLDERS
}

// 文件夹显示项模型
sealed class FileSystemItem {
    data class Folder(val name: String, val path: String, val audioCount: Int = 0) : FileSystemItem()
    data class File(val audioFile: AudioFile) : FileSystemItem()
}

// [新增] 按文件夹分组的记录
data class FolderGroup(
    val folderName: String,
    val folderPath: String,
    val audioFiles: List<AudioFile>
)

// [新增] 音频详情数据类
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

    // [新增] 音频详情对话框状态
    private val _audioDetailsState = MutableStateFlow<AudioFileDetails?>(null)
    val audioDetailsState: StateFlow<AudioFileDetails?> = _audioDetailsState.asStateFlow()

    // [修改] 记录页面：按直接父文件夹分组的历史记录
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

    // [新增] 文件夹展开状态
    private val _expandedFolders = MutableStateFlow<Set<String>>(emptySet())
    val expandedFolders: StateFlow<Set<String>> = _expandedFolders.asStateFlow()

    // [修改] 文件夹内容：显示文件夹和其下的音频
    val folderContent: StateFlow<List<FileSystemItem>> = combine(
        _currentFolderPath,
        scanFolders,
        allAudioFiles
    ) { currentPath, roots, allFiles ->
        if (currentPath == null) {
            // 根目录：显示所有已添加的扫描文件夹，并计算音频数量
            roots.map { folder ->
                val count = allFiles.count { it.path.startsWith(folder.path) }
                FileSystemItem.Folder(folder.name, folder.path, count)
            }
        } else {
            // 子目录：筛选当前路径下的文件和子文件夹
            val items = mutableListOf<FileSystemItem>()
            val processedSubFolders = mutableSetOf<String>()
            val normalizedCurrent = currentPath.trimEnd('/')

            allFiles.forEach { file ->
                val decodedPath = try { URLDecoder.decode(file.path, "UTF-8") } catch(e: Exception) { file.path }
                val parentPath = decodedPath.substringBeforeLast("/")

                // 文件直接在当前文件夹下
                if (parentPath == normalizedCurrent ||
                    try { URLDecoder.decode(file.path, "UTF-8") } catch(e: Exception) { file.path }
                        .substringBeforeLast("/") == normalizedCurrent) {
                    items.add(FileSystemItem.File(file))
                }
                // 找子文件夹
                else if (file.path.startsWith(normalizedCurrent)) {
                    val remainder = file.path.removePrefix(normalizedCurrent).removePrefix("/")
                    val subFolderName = remainder.substringBefore("/")
                    if (subFolderName != remainder && !processedSubFolders.contains(subFolderName)) {
                        processedSubFolders.add(subFolderName)
                        val subFolderPath = "$normalizedCurrent/$subFolderName"
                        val count = allFiles.count { it.path.startsWith(subFolderPath) }
                        items.add(FileSystemItem.Folder(subFolderName, subFolderPath, count))
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
    private fun getParentFolderPath(filePath: String): String {
        val decoded = try { URLDecoder.decode(filePath, "UTF-8") } catch(e: Exception) { filePath }
        return decoded.substringBeforeLast("/")
    }

    private fun getParentFolderName(folderPath: String): String {
        return folderPath.substringAfterLast("/").ifEmpty { folderPath }
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
                    parentFolder = getParentFolderName(getParentFolderPath(audioFile.path))
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
        val current = _currentFolderPath.value
        if (current != null) {
            val isRoot = scanFolders.value.any { it.path == current }
            if (isRoot) {
                _currentFolderPath.value = null
            } else {
                val parent = current.substringBeforeLast("/")
                _currentFolderPath.value = parent
            }
        }
    }

    // [新增] 切换文件夹展开状态
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
            val path = try { URLDecoder.decode(uri.toString(), "UTF-8") } catch(e: Exception) { uri.toString() }

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
                val filePath = try { URLDecoder.decode(file.uri.toString(), "UTF-8") } catch(e: Exception) { file.uri.toString() }

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