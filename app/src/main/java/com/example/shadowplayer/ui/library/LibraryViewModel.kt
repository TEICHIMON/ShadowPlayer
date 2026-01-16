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
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: AudioRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // 1. 搜索关键词状态
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // --- 批量操作状态 [新增] ---
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedAudioIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedAudioIds: StateFlow<Set<Long>> = _selectedAudioIds.asStateFlow()

    // 2. '全部'列表
    val audioFiles: StateFlow<List<AudioFile>> = combine(
        repository.getAllAudioFiles(),
        _searchQuery
    ) { files, query ->
        if (query.isBlank()) files else files.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 3. '收藏'列表
    val favorites: StateFlow<List<AudioFile>> = combine(
        repository.getFavorites(),
        _searchQuery
    ) { files, query ->
        if (query.isBlank()) files else files.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val scanFolders: StateFlow<List<ScanFolder>> = repository.getAllScanFolders()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val rootTags: StateFlow<List<Tag>> = repository.getRootTags()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allTags: StateFlow<List<Tag>> = repository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedTagId = MutableStateFlow<Long?>(null)
    val selectedTagId: StateFlow<Long?> = _selectedTagId.asStateFlow()

    // 4. '标签'列表
    val audioFilesByTag: StateFlow<List<AudioFile>> = combine(
        _selectedTagId.flatMapLatest { tagId ->
            if (tagId != null) {
                repository.getAudioFilesByTag(tagId)
            } else {
                repository.getAudioFilesWithAnyTag()
            }
        },
        _searchQuery
    ) { files, query ->
        if (query.isBlank()) files else files.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // 更新搜索词
    fun search(query: String) {
        _searchQuery.value = query
    }

    // --- 批量操作逻辑 [新增] ---

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
            if (_selectedAudioIds.value.isEmpty()) {
                exitSelectionMode()
            }
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

    // --- 文件夹操作 ---

    fun addScanFolder(uri: Uri) {
        viewModelScope.launch {
            val docFile = DocumentFile.fromTreeUri(context, uri)
            val name = docFile?.name ?: "Unknown"
            val folder = ScanFolder(
                path = uri.toString(),
                name = name
            )
            repository.insertScanFolder(folder)
            scanFolder(folder)
        }
    }

    /**
     * 删除扫描文件夹 (修复：同时删除该文件夹下的音频记录)
     */
    fun removeScanFolder(folder: ScanFolder) {
        viewModelScope.launch {
            // 1. 删除文件夹配置
            repository.deleteScanFolder(folder)
            // 2. [新增] 级联删除该文件夹路径下的所有音频
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

                docFile?.let {
                    scanDirectory(it, newAudioFiles, existingPaths)
                }

                if (newAudioFiles.isNotEmpty()) {
                    repository.insertAllIgnore(newAudioFiles)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("LibraryViewModel", "Scan failed", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanAllFolders() {
        viewModelScope.launch {
            scanFolders.value.forEach { folder ->
                scanFolder(folder)
            }
        }
    }

    private fun scanDirectory(
        directory: DocumentFile,
        results: MutableList<AudioFile>,
        existingPaths: HashSet<String>
    ) {
        directory.listFiles().forEach { file ->
            if (file.isDirectory) {
                scanDirectory(file, results, existingPaths)
            } else {
                val fileName = file.name ?: ""
                val filePath = file.uri.toString()

                if (existingPaths.contains(filePath)) {
                    return@forEach
                }

                if (isAudioFile(fileName)) {
                    val audioFile = createAudioFile(file)
                    if (audioFile != null) {
                        results.add(audioFile)
                    }
                }
            }
        }
    }

    private fun isAudioFile(name: String): Boolean {
        val extensions = listOf(".mp3", ".m4a", ".wav", ".flac", ".ogg", ".aac")
        return extensions.any { name.lowercase().endsWith(it) }
    }

    private fun createAudioFile(file: DocumentFile): AudioFile? {
        val uri = file.uri
        val name = file.name ?: return null

        // 简单日志，实际发布可去掉
        // Log.d("LibraryViewModel", "Creating audio file: $name")

        val duration = try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0
        } catch (e: Exception) {
            0L
        }

        val lrcPath = findLrcFile(file)

        return AudioFile(
            path = uri.toString(),
            title = name.substringBeforeLast("."),
            duration = duration,
            lrcPath = lrcPath
        )
    }

    private fun findLrcFile(audioFile: DocumentFile): String? {
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.name?.substringBeforeLast(".") ?: return null

        parent.listFiles().forEach { file ->
            val fileName = file.name ?: return@forEach
            if (fileName.equals("$baseName.lrc", ignoreCase = true)) {
                return file.uri.toString()
            }
        }
        return null
    }

    // --- 标签与单项操作 ---

    fun selectTag(tagId: Long?) {
        _selectedTagId.value = tagId
    }

    fun createTag(name: String, parentId: Long? = null, color: String = "#1976D2") {
        viewModelScope.launch {
            val tag = Tag(name = name, parentId = parentId, color = color)
            repository.insertTag(tag)
        }
    }

    fun updateTag(tag: Tag) {
        viewModelScope.launch {
            repository.updateTag(tag)
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            repository.deleteTag(tag)
        }
    }

    fun addTagToAudio(audioId: Long, tagId: Long) {
        viewModelScope.launch {
            repository.addTagToAudio(audioId, tagId)
        }
    }

    fun removeTagFromAudio(audioId: Long, tagId: Long) {
        viewModelScope.launch {
            repository.removeTagFromAudio(audioId, tagId)
        }
    }

    fun toggleFavorite(audioFile: AudioFile) {
        viewModelScope.launch {
            repository.updateFavorite(audioFile.id, !audioFile.isFavorite)
        }
    }

    fun deleteAudio(audioFile: AudioFile) {
        viewModelScope.launch {
            repository.deleteAudio(audioFile)
        }
    }
}