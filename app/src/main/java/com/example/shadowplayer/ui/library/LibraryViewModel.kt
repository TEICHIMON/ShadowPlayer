package com.example.shadowplayer.ui.library

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import javax.inject.Inject
import android.util.Log
import kotlinx.coroutines.Job

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: AudioRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "LibraryViewModel"
        private const val BATCH_SIZE = 20  // 每批插入的文件数量
    }

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

    // 用于取消扫描任务
    private var scanJob: Job? = null

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
     * 取消当前扫描任务
     */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    /**
     * 扫描指定文件夹
     */
    fun scanFolder(folder: ScanFolder) {
        // 取消之前的扫描任务
        scanJob?.cancel()

        scanJob = viewModelScope.launch {
            _isScanning.value = true
            try {
                withContext(Dispatchers.IO) {
                    val uri = Uri.parse(folder.path)
                    val docFile = DocumentFile.fromTreeUri(context, uri)
                    val audioFiles = mutableListOf<AudioFile>()

                    docFile?.let {
                        scanDirectory(it, audioFiles) { batch ->
                            // 分批插入数据库
                            if (batch.isNotEmpty()) {
                                repository.insertAllAudio(batch)
                                Log.d(TAG, "Inserted batch of ${batch.size} files")
                            }
                        }
                    }

                    // 插入剩余的文件
                    if (audioFiles.isNotEmpty()) {
                        repository.insertAllAudio(audioFiles)
                        Log.d(TAG, "Inserted final batch of ${audioFiles.size} files")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning folder", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * 扫描所有文件夹
     */
    fun scanAllFolders() {
        // 取消之前的扫描任务
        scanJob?.cancel()

        scanJob = viewModelScope.launch {
            _isScanning.value = true
            try {
                withContext(Dispatchers.IO) {
                    scanFolders.value.forEach { folder ->
                        if (!isActive) return@withContext  // 检查是否被取消

                        val uri = Uri.parse(folder.path)
                        val docFile = DocumentFile.fromTreeUri(context, uri)
                        val audioFiles = mutableListOf<AudioFile>()

                        docFile?.let {
                            scanDirectory(it, audioFiles) { batch ->
                                if (batch.isNotEmpty()) {
                                    repository.insertAllAudio(batch)
                                    Log.d(TAG, "Inserted batch of ${batch.size} files")
                                }
                            }
                        }

                        if (audioFiles.isNotEmpty()) {
                            repository.insertAllAudio(audioFiles)
                            Log.d(TAG, "Inserted final batch of ${audioFiles.size} files")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning all folders", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * 递归扫描目录，分批插入
     */
    private suspend fun scanDirectory(
        directory: DocumentFile,
        results: MutableList<AudioFile>,
        onBatchReady: suspend (List<AudioFile>) -> Unit
    ) {
        // 检查协程是否被取消
        if (!currentCoroutineContext().isActive) return

        directory.listFiles().forEach { file ->
            // 再次检查是否被取消
            if (!currentCoroutineContext().isActive) return

            if (file.isDirectory) {
                scanDirectory(file, results, onBatchReady)
            } else if (isAudioFile(file.name ?: "")) {
                val audioFile = createAudioFile(file)
                if (audioFile != null) {
                    results.add(audioFile)

                    // 达到批次大小时插入数据库
                    if (results.size >= BATCH_SIZE) {
                        onBatchReady(results.toList())
                        results.clear()
                    }
                }
            }
        }
    }

    private fun isAudioFile(name: String): Boolean {
        val extensions = listOf(".mp3", ".m4a", ".wav", ".flac", ".ogg", ".aac")
        return extensions.any { name.lowercase().endsWith(it) }
    }

    private fun isSubtitleFile(name: String): Boolean {
        val extensions = listOf(".lrc", ".srt")
        return extensions.any { name.lowercase().endsWith(it) }
    }

    /**
     * 创建 AudioFile 对象
     * 注意：不再获取 duration，设为 0，播放时由 ExoPlayer 获取
     */
    private fun createAudioFile(file: DocumentFile): AudioFile? {
        val uri = file.uri
        val name = file.name ?: return null

        Log.d(TAG, "Creating audio file: $name, URI: $uri")

        // 不再使用 MediaMetadataRetriever 获取时长
        // 时长将在播放时由 ExoPlayer 获取并更新

        // 查找对应的字幕文件 (LRC 或 SRT)
        val subtitlePath = findSubtitleFile(file)

        return AudioFile(
            path = uri.toString(),
            title = name.substringBeforeLast("."),
            duration = 0,  // 延迟获取，播放时更新
            lrcPath = subtitlePath
        )
    }

    /**
     * 查找对应的字幕文件（支持 LRC 和 SRT）
     */
    private fun findSubtitleFile(audioFile: DocumentFile): String? {
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.name?.substringBeforeLast(".") ?: return null

        // 优先查找 LRC，其次 SRT
        val subtitleExtensions = listOf(".lrc", ".srt")

        parent.listFiles().forEach { file ->
            val fileName = file.name ?: return@forEach
            for (ext in subtitleExtensions) {
                if (fileName.equals("$baseName$ext", ignoreCase = true)) {
                    return file.uri.toString()
                }
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

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}