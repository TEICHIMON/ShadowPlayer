package com.example.shadowplayer.ui.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.entity.ScanFolder
import com.example.shadowplayer.data.entity.Tag
import com.example.shadowplayer.data.repository.AudioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: AudioRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val audioFiles: StateFlow<List<AudioFile>> = repository.getAllAudioFiles()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favorites: StateFlow<List<AudioFile>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val scanFolders: StateFlow<List<ScanFolder>> = repository.getAllScanFolders()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val rootTags: StateFlow<List<Tag>> = repository.getRootTags()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedTagId = MutableStateFlow<Long?>(null)
    val selectedTagId: StateFlow<Long?> = _selectedTagId.asStateFlow()

    val audioFilesByTag: StateFlow<List<AudioFile>> = _selectedTagId
        .flatMapLatest { tagId ->
            if (tagId != null) {
                repository.getAudioFilesByTag(tagId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /**
     * 添加扫描文件夹 (SAF Uri)
     */
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
     * 删除扫描文件夹
     */
    fun removeScanFolder(folder: ScanFolder) {
        viewModelScope.launch {
            repository.deleteScanFolder(folder)
        }
    }

    /**
     * 扫描指定文件夹 (修复版)
     */
    fun scanFolder(folder: ScanFolder) {
        // 修改点1：使用 IO Dispatcher 避免主线程卡死
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val uri = Uri.parse(folder.path)
                val docFile = DocumentFile.fromTreeUri(context, uri)

                // 修改点2：先获取数据库中已有的所有文件路径
                // (请确保在 Repository/DAO 中实现了 getAllPaths)
                val existingPaths = repository.getAllPaths().toHashSet()

                val newAudioFiles = mutableListOf<AudioFile>()

                docFile?.let {
                    // 传入 existingPaths 进行过滤
                    scanDirectory(it, newAudioFiles, existingPaths)
                }

                if (newAudioFiles.isNotEmpty()) {
                    // 修改点3：使用 IGNORE 策略插入，避免覆盖旧数据
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

    /**
     * 扫描所有文件夹
     */
    fun scanAllFolders() {
        viewModelScope.launch {
            scanFolders.value.forEach { folder ->
                scanFolder(folder)
            }
        }
    }

    // 递归扫描逻辑优化
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

                // 修改点4：核心优化！如果路径已存在，直接跳过，
                // 不再进行耗时的 createAudioFile (MediaMetadataRetriever)
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

        Log.d("LibraryViewModel", "Creating audio file: $name, URI: $uri")

        // 获取时长
        val duration = try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0
        } catch (e: Exception) {
            0L
        }

        // 查找对应的 LRC 文件
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

        // 查找同名的 .lrc 文件
        parent.listFiles().forEach { file ->
            val fileName = file.name ?: return@forEach
            if (fileName.equals("$baseName.lrc", ignoreCase = true)) {
                return file.uri.toString()
            }
        }
        return null
    }

    /**
     * 选择标签筛选
     */
    fun selectTag(tagId: Long?) {
        _selectedTagId.value = tagId
    }

    /**
     * 创建标签
     */
    fun createTag(name: String, parentId: Long? = null, color: String = "#1976D2") {
        viewModelScope.launch {
            val tag = Tag(name = name, parentId = parentId, color = color)
            repository.insertTag(tag)
        }
    }

    /**
     * 删除标签
     */
    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            repository.deleteTag(tag)
        }
    }

    /**
     * 给音频添加标签
     */
    fun addTagToAudio(audioId: Long, tagId: Long) {
        viewModelScope.launch {
            repository.addTagToAudio(audioId, tagId)
        }
    }

    /**
     * 移除音频的标签
     */
    fun removeTagFromAudio(audioId: Long, tagId: Long) {
        viewModelScope.launch {
            repository.removeTagFromAudio(audioId, tagId)
        }
    }

    /**
     * 切换收藏
     */
    fun toggleFavorite(audioFile: AudioFile) {
        viewModelScope.launch {
            repository.updateFavorite(audioFile.id, !audioFile.isFavorite)
        }
    }

    /**
     * 删除音频 (从数据库移除，不删除文件)
     */
    fun deleteAudio(audioFile: AudioFile) {
        viewModelScope.launch {
            repository.deleteAudio(audioFile)
        }
    }
}