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

        // 获取参数，如果没有传递则为 -1L (在 AppNavigation 中定义的默认值)
        val audioId = savedStateHandle.get<Long>("audioId") ?: -1L
        Log.d(TAG, "Received audioId: $audioId")

        if (audioId > 0) {
            // 只有当传入的新 ID 与当前正在播放的 ID 不同时才重新加载
            // 避免重复点击导致重置播放进度
            val currentId = currentAudioFile.value?.id
            if (currentId != audioId) {
                loadAudioById(audioId)
            }
        } else {
            // 如果 audioId 为 -1，说明是直接点击 Tab 进来的
            // 此时不需要做任何操作，显示之前的播放状态即可
            Log.d(TAG, "No audioId passed, keeping current state")
        }
    }

    private fun loadAudioById(audioId: Long) {
        viewModelScope.launch {
            try {
                // --- 修复点：使用正确的方法名 getAudioById ---
                val audioFile = repository.getAudioById(audioId)

                if (audioFile != null) {
                    // 此时 audioFile 类型确认为 AudioFile，不再是 Any，消除了类型不匹配错误
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
            // 读取 LRC 内容
            val lrcContent = if (!audioFile.lrcPath.isNullOrEmpty()) {
                readLrcContent(audioFile.lrcPath!!)
            } else {
                null
            }

            // 调用 SentencePlayer 加载音频和字幕
            sentencePlayer.load(audioFile.path, lrcContent)

            // 恢复上次播放位置
            if (audioFile.lastPosition > 0) {
                sentencePlayer.seekTo(audioFile.lastPosition)
            }

            // 更新播放次数
            repository.incrementPlayCount(audioFile.id)
        }
    }

    // 辅助方法：读取 LRC 文件内容
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

    // --- 播放控制 (委托给 SentencePlayer) ---

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

    // --- 设置控制 (委托给 SentencePlayer) ---

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
        // 保存播放位置
        _currentAudioFile.value?.let { audioFile ->
            val position = playerState.value.currentPosition
            viewModelScope.launch {
                repository.updateLastPosition(audioFile.id, position)
            }
        }
        sentencePlayer.release()
    }
}