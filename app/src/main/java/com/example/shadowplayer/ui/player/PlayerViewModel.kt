package com.example.shadowplayer.ui.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.repository.AudioRepository
import com.example.shadowplayer.player.PlaybackSettings
import com.example.shadowplayer.player.SentencePlayer
import com.example.shadowplayer.player.SentencePlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val sentencePlayer: SentencePlayer,
    private val repository: AudioRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _currentAudioFile = MutableStateFlow<AudioFile?>(null)
    val currentAudioFile: StateFlow<AudioFile?> = _currentAudioFile.asStateFlow()

    val playerState: StateFlow<SentencePlayerState> = sentencePlayer.state
    val settings: StateFlow<PlaybackSettings> = sentencePlayer.settings

    init {
        val audioId = savedStateHandle.get<Long>("audioId") ?: -1L
        if (audioId > 0) {
            val currentId = currentAudioFile.value?.id
            if (currentId != audioId) {
                loadAudioById(audioId)
            }
        } else {
            restoreLastPlayedAudio()
        }
    }

    private fun restoreLastPlayedAudio() {
        val lastAudioId = sentencePlayer.getLastPlayedAudioId()
        if (lastAudioId > 0) {
            val currentId = _currentAudioFile.value?.id
            if (currentId != lastAudioId) {
                loadAudioById(lastAudioId)
            }
        }
    }

    private fun loadAudioById(audioId: Long) {
        viewModelScope.launch {
            val audioFile = repository.getAudioById(audioId)
            if (audioFile != null) {
                loadAudio(audioFile)
                // [新增] 更新最近播放时间
                repository.updateLastPlayedAt(audioId, System.currentTimeMillis())
            }
        }
    }

    private fun loadAudio(audioFile: AudioFile) {
        _currentAudioFile.value = audioFile
        viewModelScope.launch {
            val lrcContent = if (!audioFile.lrcPath.isNullOrEmpty()) {
                readLrcContent(audioFile.lrcPath)
            } else null

            sentencePlayer.load(
                audioPath = audioFile.path,
                lrcContent = lrcContent,
                subtitlePath = audioFile.lrcPath,
                audioId = audioFile.id,
                initialPosition = audioFile.lastPosition
            )
            repository.incrementPlayCount(audioFile.id)
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

    fun togglePlayPause() = sentencePlayer.togglePlayPause()
    fun nextSentence() = sentencePlayer.nextSentence()
    fun previousSentence() = sentencePlayer.previousSentence()
    fun seekToSentence(index: Int) = sentencePlayer.seekToSentence(index)
    fun seekTo(position: Long) = sentencePlayer.seekTo(position)
    fun setSpeed(speed: Float) = sentencePlayer.setSpeed(speed)
    fun setRepeatCount(count: Int) = sentencePlayer.setRepeatCount(count)
    fun setRepeatInterval(interval: Long) = sentencePlayer.setRepeatInterval(interval)
    fun toggleSubtitle() = sentencePlayer.toggleSubtitle()
    fun setAutoNext(enabled: Boolean) {
        val current = settings.value
        sentencePlayer.updateSettings(current.copy(autoNext = enabled))
    }
}