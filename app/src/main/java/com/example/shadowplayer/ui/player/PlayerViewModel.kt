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

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val _currentAudioFile = MutableStateFlow<AudioFile?>(null)
    val currentAudioFile: StateFlow<AudioFile?> = _currentAudioFile.asStateFlow()

    // 直接暴露 SentencePlayer 的状态供 UI 使用
    val playerState: StateFlow<SentencePlayerState> = sentencePlayer.state
    val settings: StateFlow<PlaybackSettings> = sentencePlayer.settings

    init {
        Log.d(TAG, "PlayerViewModel init")

        val audioId = savedStateHandle.get<Long>("audioId") ?: -1L
        Log.d(TAG, "Received audioId: $audioId")

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
        Log.d(TAG, "Restoring last played audio, id: $lastAudioId")

        if (lastAudioId > 0) {
            val currentId = _currentAudioFile.value?.id
            if (currentId != lastAudioId) {
                loadAudioById(lastAudioId)
            }
        } else {
            Log.d(TAG, "No last played audio to restore")
        }
    }

    private fun loadAudioById(audioId: Long) {
        viewModelScope.launch {
            try {
                val audioFile = repository.getAudioById(audioId)

                if (audioFile != null) {
                    loadAudio(audioFile)
                } else {
                    Log.e(TAG, "AudioFile not found for id: $audioId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading audio by id", e)
            }
        }
    }

    private fun loadAudio(audioFile: AudioFile) {
        _currentAudioFile.value = audioFile

        viewModelScope.launch {
            // 读取字幕内容
            val subtitlePath = audioFile.lrcPath
            val lrcContent = if (!subtitlePath.isNullOrEmpty()) {
                readLrcContent(subtitlePath)
            } else {
                null
            }

            // [修改] 调用 load 时传入 audioId 和 lastPosition
            // 恢复播放位置的逻辑现已委托给 SentencePlayer.load 内部处理
            sentencePlayer.load(
                audioPath = audioFile.path,
                lrcContent = lrcContent,
                subtitlePath = subtitlePath,
                audioId = audioFile.id,
                initialPosition = audioFile.lastPosition // 传入上次保存的位置
            )

            // [修改] 已移除单独的 seekTo 调用，防止状态竞争
            // if (audioFile.lastPosition > 0) { sentencePlayer.seekTo(...) } -> 已删除

            // 更新播放次数
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
            Log.e(TAG, "Error reading LRC file", e)
            null
        }
    }

    fun togglePlayPause() {
        sentencePlayer.togglePlayPause()
    }

    fun nextSentence() {
        sentencePlayer.nextSentence()
    }

    fun previousSentence() {
        sentencePlayer.previousSentence()
    }

    fun seekToSentence(index: Int) {
        sentencePlayer.seekToSentence(index)
    }

    fun seekTo(position: Long) {
        sentencePlayer.seekTo(position)
    }

    fun setSpeed(speed: Float) {
        sentencePlayer.setSpeed(speed)
    }

    fun setRepeatCount(count: Int) {
        sentencePlayer.setRepeatCount(count)
    }

    fun setRepeatInterval(interval: Long) {
        sentencePlayer.setRepeatInterval(interval)
    }

    fun toggleSubtitle() {
        sentencePlayer.toggleSubtitle()
    }

    fun setAutoNext(enabled: Boolean) {
        val current = settings.value
        sentencePlayer.updateSettings(current.copy(autoNext = enabled))
    }

    override fun onCleared() {
        super.onCleared()
    }
}