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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import javax.inject.Inject

enum class LibraryTab {
    RECORDS, FAVORITES, HISTORY, TAGS, FOLDERS
}

sealed class FileSystemItem {
    data class Folder(val name: String, val path: String, val audioCount: Int = 0) : FileSystemItem()
    data class File(val audioFile: AudioFile) : FileSystemItem()
}

data class FolderGroup(
    val folderName: String,
    val folderPath: String,
    val audioFiles: List<AudioFile>
)

data class AudioFileDetails(
    val audioFile: AudioFile,
    val fileSizeBytes: Long,
    val formattedSize: String,
    val formattedDuration: String,
    val hasSubtitle: Boolean,
    val parentFolder: String
)
enum class FolderSortType(val displayName: String) {
    NAME_ASC("名称 A→Z"),
    NAME_DESC("名称 Z→A"),
    AUDIO_COUNT_DESC("文件数量 多→少"),
    AUDIO_COUNT_ASC("文件数量 少→多")
}

enum class FileSortType(val displayName: String) {
    NAME_ASC("名称 A→Z"),
    NAME_DESC("名称 Z→A"),
    PLAY_COUNT_DESC("播放次数 多→少"),
    PLAY_COUNT_ASC("播放次数 少→多"),
    RECENT_PLAY("最近播放")
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: AudioRepository,
    private val sentencePlayer: SentencePlayer,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    private val _folderSortType = MutableStateFlow(FolderSortType.NAME_ASC)
    val folderSortType: StateFlow<FolderSortType> = _folderSortType.asStateFlow()

    private val _fileSortByFolder = MutableStateFlow<Map<String, FileSortType>>(emptyMap())
    val fileSortByFolder: StateFlow<Map<String, FileSortType>> = _fileSortByFolder.asStateFlow()

    // [问题3修复] 用于保存文件夹滚动位置的 Map (path -> index to offset)
    private val _folderScrollPositions = mutableMapOf<String?, Pair<Int, Int>>()
    private val scanMutex = Mutex()

    fun saveScrollPosition(path: String?, index: Int, offset: Int) {
        _folderScrollPositions[path] = index to offset
    }

    fun getScrollPosition(path: String?): Pair<Int, Int> {
        return _folderScrollPositions[path] ?: (0 to 0)
    }

    fun setFolderSortType(sortType: FolderSortType) {
        _folderSortType.value = sortType
    }

    fun setFileSortType(folderPath: String, sortType: FileSortType) {
        _fileSortByFolder.value = _fileSortByFolder.value + (folderPath to sortType)
    }

    fun getFileSortType(folderPath: String?): FileSortType {
        return folderPath?.let { _fileSortByFolder.value[it] } ?: FileSortType.NAME_ASC
    }

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedAudioIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedAudioIds: StateFlow<Set<Long>> = _selectedAudioIds.asStateFlow()

    private val _audioDetailsState = MutableStateFlow<AudioFileDetails?>(null)
    val audioDetailsState: StateFlow<AudioFileDetails?> = _audioDetailsState.asStateFlow()

    val currentPlayingAudioId: StateFlow<Long> = sentencePlayer.currentAudioId
        .stateIn(viewModelScope, SharingStarted.Lazily, -1L)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // [问题2修复] 记录Tab折叠状态管理：ViewModel 持有状态，避免重组丢失
    private val _expandedGroupPaths = MutableStateFlow<Set<String>>(emptySet())
    val expandedGroupPaths: StateFlow<Set<String>> = _expandedGroupPaths.asStateFlow()

    fun toggleGroupExpansion(path: String) {
        val current = _expandedGroupPaths.value
        if (current.contains(path)) {
            _expandedGroupPaths.value = current - path
        } else {
            _expandedGroupPaths.value = current + path
        }
    }

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

    private val allAudioFiles: StateFlow<List<AudioFile>> = repository.getAllAudioFiles()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recordGroups: StateFlow<List<FolderGroup>> = combine(
        repository.getHistory(),
        allAudioFiles,
        _searchQuery
    ) { historyFiles, allFiles, query ->
        val foldersWithHistory = historyFiles.map { audioFile ->
            getParentFolderPath(audioFile.path)
        }.toSet()

        val groups = foldersWithHistory.mapNotNull { folderPath ->
            val folderAudioFiles = allFiles.filter { audioFile ->
                getParentFolderPath(audioFile.path) == folderPath
            }

            if (folderAudioFiles.isEmpty()) return@mapNotNull null

            val filteredFiles = if (query.isBlank()) {
                folderAudioFiles
            } else {
                folderAudioFiles.filter {it.title.lowercase().contains(query.lowercase()) }
            }

            if (filteredFiles.isEmpty()) return@mapNotNull null

            val sortedFiles = filteredFiles.sortedBy { it.title.lowercase() }

            FolderGroup(
                folderName = getParentFolderName(folderPath),
                folderPath = folderPath,
                audioFiles = sortedFiles
            )
        }

        groups.sortedByDescending { group ->
            group.audioFiles
                .filter { it.lastPlayedAt != null }
                .maxOfOrNull { it.lastPlayedAt ?: 0L } ?: 0L
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favorites: StateFlow<List<AudioFile>> = combine(
        repository.getFavorites(),
        _searchQuery
    ) { files, query ->
        if (query.isBlank()) files else files.filter { it.title.lowercase().contains(query.lowercase()) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val history: StateFlow<List<AudioFile>> = combine(
        repository.getHistory(),
        _searchQuery
    ) { files, query ->
        if (query.isBlank()) files else files.filter { it.title.lowercase().contains(query.lowercase()) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val scanFolders: StateFlow<List<ScanFolder>> = repository.getAllScanFolders()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentFolderDocId = MutableStateFlow<String?>(null)
    val currentFolderPath: StateFlow<String?> = _currentFolderDocId.asStateFlow()

    private val _expandedFolders = MutableStateFlow<Set<String>>(emptySet())
    val expandedFolders: StateFlow<Set<String>> = _expandedFolders.asStateFlow()

    val folderContent: StateFlow<List<FileSystemItem>> = combine(
        _currentFolderDocId,
        scanFolders,
        allAudioFiles,
        _searchQuery,
        _folderSortType,
        _fileSortByFolder
    ) { values ->
        val currentDocId = values[0] as String?
        val roots = values[1] as List<ScanFolder>
        val allFiles = values[2] as List<AudioFile>
        val query = values[3] as String
        val folderSort = values[4] as FolderSortType
        val fileSortMap = values[5] as Map<String, FileSortType>

        val fileSort = currentDocId?.let { fileSortMap[it] } ?: FileSortType.NAME_ASC

        if (currentDocId == null) {
            val folders = roots.map { folder ->
                val folderDocId = extractDocumentId(folder.path)
                val filesInFolder = allFiles.filter { file ->
                    val fileDocId = extractDocumentId(file.path)
                    fileDocId == folderDocId ||
                            fileDocId.startsWith("$folderDocId%2F") ||
                            fileDocId.startsWith("$folderDocId/")
                }
                val filteredCount = if (query.isBlank()) {
                    filesInFolder.size
                } else {
                    filesInFolder.count { it.title.lowercase().contains(query.lowercase()) }
                }
                FileSystemItem.Folder(folder.name, folderDocId, filteredCount)
            }.let { folders ->
                if (query.isBlank()) folders else folders.filter { it.audioCount > 0 }
            }

            when (folderSort) {
                FolderSortType.NAME_ASC -> folders.sortedBy { it.name.lowercase() }
                FolderSortType.NAME_DESC -> folders.sortedByDescending { it.name.lowercase() }
                FolderSortType.AUDIO_COUNT_DESC -> folders.sortedByDescending { it.audioCount }
                FolderSortType.AUDIO_COUNT_ASC -> folders.sortedBy { it.audioCount }
            }
        } else {
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
                            if (query.isBlank() || file.title.lowercase().contains(query.lowercase())) {
                                items.add(FileSystemItem.File(file))
                            }
                        }
                    }
                }
            }

            processedSubFolders.forEach { subFolderDocId ->
                val filesInSubFolder = subFolderFiles[subFolderDocId] ?: emptyList()
                val matchingCount = if (query.isBlank()) {
                    filesInSubFolder.size
                } else {
                    filesInSubFolder.count { it.title.lowercase().contains(query.lowercase()) }
                }

                if (query.isBlank() || matchingCount > 0) {
                    val subFolderName = subFolderDocId.substringAfterLast("%2F").substringAfterLast("/")
                    val displayName = try {
                        URLDecoder.decode(subFolderName, "UTF-8")
                    } catch (e: Exception) {
                        subFolderName
                    }
                    items.add(FileSystemItem.Folder(displayName, subFolderDocId, matchingCount))
                }
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

            sortedFolders + sortedFiles
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
        if (query.isBlank()) files else files.filter { it.title.lowercase().contains(query.lowercase()) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            scanAllFoldersIncrementalInternal()
        }
    }

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

    fun dismissAudioDetails() { _audioDetailsState.value = null }

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
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    fun search(query: String) { _searchQuery.value = query }

    fun navigateToFolder(docId: String) { _currentFolderDocId.value = docId }

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
        _expandedFolders.value = if (current.contains(path)) current - path else current + path
    }

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

    fun selectAll(files: List<AudioFile>) { _selectedAudioIds.value = files.map { it.id }.toSet() }

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

    fun addScanFolder(uri: Uri) {
        viewModelScope.launch {
            val docFile = DocumentFile.fromTreeUri(context, uri)
            val name = docFile?.name ?: "Unknown"
            val path = uri.toString()
            val folder = ScanFolder(path = path, name = name)
            repository.insertScanFolder(folder)
            scanFolderIncremental(folder)
        }
    }

    fun removeScanFolder(folder: ScanFolder) {
        viewModelScope.launch {
            repository.deleteScanFolder(folder)
            val folderDocId = extractDocumentId(folder.path)
            repository.deleteAudioFilesByPathPrefix(folderDocId)
        }
    }

    // [问题3修复] 通过 UI 层的 FileSystemItem.Folder (只包含 docId) 删除 ScanFolder
    fun removeScanFolderByItem(folderItem: FileSystemItem.Folder) {
        viewModelScope.launch {
            val scanFolder = scanFolders.value.find {
                extractDocumentId(it.path) == folderItem.path
            }
            if (scanFolder != null) {
                removeScanFolder(scanFolder)
            }
        }
    }

    fun refreshFolders() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            try {
                scanAllFoldersIncrementalInternal()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun scanFolderIncremental(folder: ScanFolder) {
        viewModelScope.launch {
            scanMutex.withLock {
                _isScanning.value = true
                try {
                    scanFolderIncrementalInternal(folder)
                } finally {
                    _isScanning.value = false
                }
            }
        }
    }

    fun scanAllFoldersIncremental() {
        viewModelScope.launch {
            scanAllFoldersIncrementalInternal()
        }
    }

    fun scanAllFolders() { scanAllFoldersIncremental() }

    private suspend fun scanAllFoldersIncrementalInternal() {
        scanMutex.withLock {
            _isScanning.value = true
            try {
                val folders = repository.getAllScanFolders().first()
                folders.forEach { scanFolderIncrementalInternal(it) }
            } finally {
                _isScanning.value = false
            }
        }
    }

    private suspend fun scanFolderIncrementalInternal(folder: ScanFolder) = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(folder.path)
            val docFile = DocumentFile.fromTreeUri(context, uri)
            val folderDocId = extractDocumentId(folder.path)
            val existingPaths = repository.getAllPaths().filter { path ->
                val fileDocId = extractDocumentId(path)
                fileDocId.startsWith("$folderDocId%2F") ||
                        fileDocId.startsWith("$folderDocId/") ||
                        fileDocId == folderDocId
            }.toHashSet()
            val foundPaths = mutableSetOf<String>()
            val newAudioFiles = mutableListOf<AudioFile>()
            docFile?.let { scanDirectoryIncremental(it, newAudioFiles, existingPaths, foundPaths) }
            if (newAudioFiles.isNotEmpty()) repository.insertAllIgnore(newAudioFiles)
            val deletedPaths = existingPaths - foundPaths
            deletedPaths.forEach { repository.deleteAudioByPath(it) }
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "Incremental scan failed", e)
        }
    }

    private fun scanDirectoryIncremental(
        directory: DocumentFile,
        newFiles: MutableList<AudioFile>,
        existingPaths: HashSet<String>,
        foundPaths: MutableSet<String>
    ) {
        directory.listFiles().forEach { file ->
            if (file.isDirectory) {
                scanDirectoryIncremental(file, newFiles, existingPaths, foundPaths)
            } else {
                val fileName = file.name ?: ""
                val filePath = file.uri.toString()
                if (isAudioFile(fileName)) {
                    foundPaths.add(filePath)
                    if (!existingPaths.contains(filePath)) {
                        createAudioFile(file, filePath)?.let { newFiles.add(it) }
                    }
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

    fun selectTag(tagId: Long?) { _selectedTagId.value = tagId }
    fun createTag(name: String, parentId: Long?) { viewModelScope.launch { repository.insertTag(Tag(name = name, parentId = parentId)) } }
    fun updateTag(tag: Tag) { viewModelScope.launch { repository.updateTag(tag) } }
    fun deleteTag(tag: Tag) { viewModelScope.launch { repository.deleteTag(tag) } }
    fun addTagToAudio(audioId: Long, tagId: Long) { viewModelScope.launch { repository.addTagToAudio(audioId, tagId) } }
    fun toggleFavorite(audioFile: AudioFile) { viewModelScope.launch { repository.updateFavorite(audioFile.id, !audioFile.isFavorite) } }
    fun deleteAudio(audioFile: AudioFile) { viewModelScope.launch { repository.deleteAudio(audioFile) } }

    fun getPlaylistForAudio(audioFile: AudioFile): Pair<List<AudioFile>, Int> {
        val parentPath = getParentFolderPath(audioFile.path)
        val allFiles = allAudioFiles.value
        val playlist = allFiles
            .filter { getParentFolderPath(it.path) == parentPath }
            .sortedBy { it.title.lowercase() }
        val currentIndex = playlist.indexOfFirst { it.id == audioFile.id }
        return Pair(playlist, currentIndex)
    }

    fun setPlaylistForAudio(audioFile: AudioFile) {
        val (playlist, index) = getPlaylistForAudio(audioFile)
        sentencePlayer.setPlaylist(playlist, index)
    }

    fun clearFolderPlayHistory(folderPath: String) {
        viewModelScope.launch {
            val allFiles = allAudioFiles.value
            val idsToUpdate = allFiles
                .filter { getParentFolderPath(it.path) == folderPath }
                .map { it.id }
            if (idsToUpdate.isNotEmpty()) {
                repository.clearPlayHistory(idsToUpdate)
            }
        }
    }
}
