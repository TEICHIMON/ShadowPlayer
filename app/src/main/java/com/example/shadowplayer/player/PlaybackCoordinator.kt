package com.example.shadowplayer.player

import android.content.Context
import android.net.Uri
import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.repository.AudioRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackCoordinator @Inject constructor(
    private val sentencePlayer: SentencePlayer,
    private val repository: AudioRepository,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _currentAudioFile = MutableStateFlow<AudioFile?>(null)
    val currentAudioFile: StateFlow<AudioFile?> = _currentAudioFile.asStateFlow()

    init {
        scope.launch {
            sentencePlayer.currentAudioId.collectLatest { audioId ->
                _currentAudioFile.value = if (audioId > 0) {
                    withContext(Dispatchers.IO) { repository.getAudioById(audioId) }
                } else {
                    null
                }
            }
        }
    }

    suspend fun loadAudioById(
        audioId: Long,
        setupPlaylist: Boolean = true,
        playAfterLoad: Boolean = false
    ): Boolean {
        val audioFile = withContext(Dispatchers.IO) { repository.getAudioById(audioId) }
            ?: return false
        return loadAudio(audioFile, setupPlaylist, playAfterLoad)
    }

    suspend fun loadAudio(
        audioFile: AudioFile,
        setupPlaylist: Boolean = true,
        playAfterLoad: Boolean = false
    ): Boolean {
        if (setupPlaylist) {
            setupPlaylistForAudio(audioFile)
        }

        _currentAudioFile.value = audioFile
        val lrcContent = audioFile.lrcPath?.takeIf { it.isNotBlank() }?.let { readLrcContent(it) }

        sentencePlayer.load(
            audioPath = audioFile.path,
            title = audioFile.title,
            lrcContent = lrcContent,
            subtitlePath = audioFile.lrcPath,
            audioId = audioFile.id,
            initialPosition = audioFile.lastPosition
        )
        withContext(Dispatchers.IO) {
            repository.incrementPlayCount(audioFile.id)
            repository.updateLastPlayedAt(audioFile.id, System.currentTimeMillis())
        }
        if (playAfterLoad) {
            sentencePlayer.play()
        }
        return true
    }

    suspend fun ensurePlaylistForAudio(audioFile: AudioFile) {
        setupPlaylistForAudio(audioFile)
    }

    suspend fun syncCurrentAudioFile(): AudioFile? {
        val audioId = sentencePlayer.getCurrentAudioId()
        val audioFile = if (audioId > 0) {
            withContext(Dispatchers.IO) { repository.getAudioById(audioId) }
        } else {
            null
        }
        _currentAudioFile.value = audioFile
        return audioFile
    }

    fun playPreviousAudio() {
        val previousAudio = sentencePlayer.getPreviousAudio() ?: return
        val shouldResume = sentencePlayer.isPlaybackActive()
        scope.launch {
            loadAudio(previousAudio, setupPlaylist = false, playAfterLoad = shouldResume)
        }
    }

    fun playNextAudio() {
        val nextAudio = sentencePlayer.getNextAudio() ?: return
        val shouldResume = sentencePlayer.isPlaybackActive()
        scope.launch {
            loadAudio(nextAudio, setupPlaylist = false, playAfterLoad = shouldResume)
        }
    }

    private suspend fun setupPlaylistForAudio(audioFile: AudioFile) {
        val parentPath = getParentFolderPath(audioFile.path)
        val allFiles = repository.getAllAudioFiles().first()
        val playlist = allFiles
            .filter { getParentFolderPath(it.path) == parentPath }
            .sortedBy { it.title.lowercase() }
        val currentIndex = playlist.indexOfFirst { it.id == audioFile.id }

        if (playlist.isNotEmpty() && currentIndex >= 0) {
            sentencePlayer.setPlaylist(playlist, currentIndex)
        }
    }

    private suspend fun readLrcContent(lrcPath: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(lrcPath)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            }
        } catch (e: Exception) {
            null
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

    private fun getParentFolderPath(filePath: String): String {
        val docId = extractDocumentId(filePath)
        val decoded = try {
            URLDecoder.decode(docId, "UTF-8")
        } catch (e: Exception) {
            docId
        }
        return decoded.substringBeforeLast("/")
    }
}
