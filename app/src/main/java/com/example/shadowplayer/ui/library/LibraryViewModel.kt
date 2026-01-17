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
import java.net.URLDecoder
import javax.inject.Inject

// 新增 Tab：历史记录
enum class LibraryTab {
    ALL, FAVORITES, HISTORY, TAGS, FOLDERS
}

// 文件夹显示项模型
sealed class FileSystemItem {
    data class Folder(val name: String, val path: String) : FileSystemItem()
    data class File(val audioFile: AudioFile) : FileSystemItem()
}

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

    // 全部文件
    val audioFiles: StateFlow<List<AudioFile>> = combine(
        repository.getAllAudioFiles(),
        _searchQuery
    ) { files, query ->
        if (query.isBlank()) files else files.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 收藏
    val favorites: StateFlow<List<AudioFile>> = combine(
        repository.getFavorites(),
        _searchQuery
    ) { files, query ->
        if (query.isBlank()) files else files.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // [新增] 历史记录
    val history: StateFlow<List<AudioFile>> = combine(
        repository.getHistory(),
        _searchQuery
    ) { files, query ->
        if (query.isBlank()) files else files.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val scanFolders: StateFlow<List<ScanFolder>> = repository.getAllScanFolders()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- 文件夹浏览器状态 ---
    // 当前浏览的路径，null 表示在根目录（显示 ScanFolders 列表）
    private val _currentFolderPath = MutableStateFlow<String?>(null)
    val currentFolderPath: StateFlow<String?> = _currentFolderPath.asStateFlow()

    // 文件夹内容：结合了 scanFolders 和 audioFiles 计算得出
    val folderContent: StateFlow<List<FileSystemItem>> = combine(
        _currentFolderPath,
        scanFolders,
        audioFiles // 使用全部文件来计算层级
    ) { currentPath, roots, allFiles ->
        if (currentPath == null) {
            // 根目录：显示所有已添加的扫描文件夹
            roots.map { FileSystemItem.Folder(it.name, it.path) }
        } else {
            // 子目录：筛选当前路径下的文件和子文件夹
            val items = mutableListOf<FileSystemItem>()
            val processedSubFolders = mutableSetOf<String>()

            // 1. 找文件
            // 标准化路径：移除末尾斜杠以匹配
            val normalizedCurrent = currentPath.trimEnd('/')

            allFiles.forEach { file ->
                // 解码路径以正确处理空格和特殊字符
                val decodedPath = try { URLDecoder.decode(file.path, "UTF-8") } catch(e:Exception) { file.path }
                val parentPath = decodedPath.substringBeforeLast("/")

                // 检查文件是否直接在当前文件夹下 (需要处理 URL 编码差异，这里做简化包含判断)
                // 严谨做法是比较 decodedPath 的父路径
                if (parentPath == normalizedCurrent || URLDecoder.decode(file.path, "UTF-8").substringBeforeLast("/") == normalizedCurrent) {
                    items.add(FileSystemItem.File(file))
                }
                // 2. 找子文件夹
                else if (file.path.startsWith(normalizedCurrent)) {
                    // 提取子文件夹名
                    // 例如 current=/storage/emulated/0/Music
                    // file=/storage/emulated/0/Music/Pop/Song.mp3
                    // remainder=/Pop/Song.mp3
                    // subFolder=Pop
                    val remainder = file.path.removePrefix(normalizedCurrent).removePrefix("/")
                    val subFolderName = remainder.substringBefore("/")
                    if (subFolderName != remainder) { // 确保还有下一级
                        if (!processedSubFolders.contains(subFolderName)) {
                            processedSubFolders.add(subFolderName)
                            items.add(FileSystemItem.Folder(subFolderName, "$normalizedCurrent/$subFolderName"))
                        }
                    }
                }
            }
            items.sortedBy {
                when(it) {
                    is FileSystemItem.Folder -> "0${it.name}" // 文件夹排前面
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

    fun search(query: String) { _searchQuery.value = query }

    // --- 文件夹导航 ---
    fun navigateToFolder(path: String) {
        _currentFolderPath.value = path
    }

    fun navigateUp() {
        val current = _currentFolderPath.value
        if (current != null) {
            // 检查当前是否是某个 ScanFolder 的根路径，如果是，则退回到 null (显示根列表)
            val isRoot = scanFolders.value.any { it.path == current }
            if (isRoot) {
                _currentFolderPath.value = null
            } else {
                // 截取上一级路径
                val parent = current.substringBeforeLast("/")
                _currentFolderPath.value = parent
            }
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
            val path = try { URLDecoder.decode(uri.toString(), "UTF-8") } catch(e:Exception) { uri.toString() }

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
                // 将存储的 path (可能是解码后的或原始 URI) 转回 Uri 对象进行扫描
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
                // 存储解码后的路径，方便显示和层级计算，或者保持 URI 格式统一
                val filePath = try { URLDecoder.decode(file.uri.toString(), "UTF-8") } catch(e:Exception) { file.uri.toString() }

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
            if (file.name.equals("$baseName.lrc", ignoreCase = true)) return file.uri.toString()
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