package com.example.shadowplayer.ui.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.repository.AudioRepository
import com.example.shadowplayer.player.AudioPlayer
import com.example.shadowplayer.player.LrcParser
import com.example.shadowplayer.player.PlaybackSettings
import com.example.shadowplayer.player.Sentence
import com.example.shadowplayer.player.SentencePlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val audioPlayer: AudioPlayer,
    private val repository: AudioRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val _currentAudioFile = MutableStateFlow<AudioFile?>(null)
    val currentAudioFile: StateFlow<AudioFile?> = _currentAudioFile.asStateFlow()

    private val _sentences = MutableStateFlow<List<Sentence>>(emptyList())
    val sentences: StateFlow<List<Sentence>> = _sentences.asStateFlow()

    private val _currentSentenceIndex = MutableStateFlow(0)
    val currentSentenceIndex: StateFlow<Int> = _currentSentenceIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSettings = MutableStateFlow(PlaybackSettings())
    val playbackSettings: StateFlow<PlaybackSettings> = _playbackSettings.asStateFlow()

    private val _currentRepeat = MutableStateFlow(0)
    val currentRepeat: StateFlow<Int> = _currentRepeat.asStateFlow()

    private val _isInInterval = MutableStateFlow(false)
    val isInInterval: StateFlow<Boolean> = _isInInterval.asStateFlow()

    private val _intervalCountdown = MutableStateFlow(0)
    val intervalCountdown: StateFlow<Int> = _intervalCountdown.asStateFlow()

    private val _showSubtitle = MutableStateFlow(true)
    val showSubtitle: StateFlow<Boolean> = _showSubtitle.asStateFlow()

    private val sentencePlayer = SentencePlayer(
        audioPlayer = audioPlayer,
        scope = viewModelScope,
        onSentenceChanged = { index ->
            Log.d(TAG, "onSentenceChanged: $index")
            _currentSentenceIndex.value = index
        },
        onRepeatChanged = { repeat ->
            Log.d(TAG, "onRepeatChanged: $repeat")
            _currentRepeat.value = repeat
        },
        onIntervalStart = { countdown ->
            Log.d(TAG, "onIntervalStart: $countdown")
            _isInInterval.value = true
            _intervalCountdown.value = countdown
        },
        onIntervalTick = { remaining ->
            _intervalCountdown.value = remaining
        },
        onIntervalEnd = {
            Log.d(TAG, "onIntervalEnd")
            _isInInterval.value = false
        },
        onPositionChanged = { position ->
            _currentPosition.value = position
        }
    )

    init {
        Log.d(TAG, "PlayerViewModel init")

        // 获取导航传递的 audioId
        val audioId: Long? = savedStateHandle.get<Long>("audioId")
        Log.d(TAG, "Received audioId from navigation: $audioId")

        if (audioId != null && audioId > 0) {
            loadAudioById(audioId)
        } else {
            Log.e(TAG, "Invalid audioId: $audioId")
        }

        // 监听播放状态
        viewModelScope.launch {
            audioPlayer.isPlaying.collect { playing ->
                Log.d(TAG, "isPlaying changed: $playing")
                _isPlaying.value = playing
            }
        }

        viewModelScope.launch {
            audioPlayer.duration.collect { dur ->
                Log.d(TAG, "duration changed: $dur")
                _duration.value = dur
            }
        }
    }

    private fun loadAudioById(audioId: Long) {
        Log.d(TAG, "loadAudioById: $audioId")
        viewModelScope.launch {
            try {
                val audioFile = repository.getAudioFileById(audioId)
                Log.d(TAG, "Repository returned audioFile: $audioFile")

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

    fun loadAudio(audioFile: AudioFile) {
        Log.d(TAG, "=== loadAudio called ===")
        Log.d(TAG, "AudioFile id: ${audioFile.id}")
        Log.d(TAG, "AudioFile path: ${audioFile.path}")
        Log.d(TAG, "AudioFile title: ${audioFile.title}")
        Log.d(TAG, "AudioFile lrcPath: ${audioFile.lrcPath}")

        viewModelScope.launch {
            try {
                _currentAudioFile.value = audioFile

                // 加载音频
                Log.d(TAG, "Calling audioPlayer.loadAudio...")
                audioPlayer.loadAudio(audioFile.path)
                Log.d(TAG, "audioPlayer.loadAudio completed")

                // 加载字幕
                if (!audioFile.lrcPath.isNullOrEmpty()) {
                    Log.d(TAG, "Loading LRC from: ${audioFile.lrcPath}")
                    loadLrc(audioFile.lrcPath!!)
                } else {
                    Log.d(TAG, "No LRC file available")
                    _sentences.value = emptyList()
                }

                // 设置句子到播放器
                sentencePlayer.setSentences(_sentences.value)
                sentencePlayer.setSettings(_playbackSettings.value)

                // 恢复上次播放位置
                if (audioFile.lastPosition > 0) {
                    Log.d(TAG, "Seeking to last position: ${audioFile.lastPosition}")
                    sentencePlayer.seekTo(audioFile.lastPosition)
                }

                // 更新播放次数
                repository.incrementPlayCount(audioFile.id)

                Log.d(TAG, "=== loadAudio completed successfully ===")
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadAudio", e)
            }
        }
    }

    private fun loadLrc(lrcPath: String) {
        Log.d(TAG, "loadLrc: $lrcPath")
        try {
            val uri = Uri.parse(lrcPath)
            Log.d(TAG, "LRC URI: $uri")

            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open LRC inputStream")
                return
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            val content = reader.readText()
            reader.close()

            Log.d(TAG, "LRC content length: ${content.length}")
            Log.d(TAG, "LRC content preview: ${content.take(200)}")

            val parser = LrcParser()
            val parsedSentences = parser.parse(content, _duration.value)
            Log.d(TAG, "Parsed ${parsedSentences.size} sentences")

            _sentences.value = parsedSentences

            if (parsedSentences.isNotEmpty()) {
                Log.d(TAG, "First sentence: ${parsedSentences.first()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading LRC", e)
            _sentences.value = emptyList()
        }
    }

    fun togglePlayPause() {
        Log.d(TAG, "togglePlayPause called, current isPlaying: ${_isPlaying.value}")
        if (_isPlaying.value) {
            sentencePlayer.pause()
        } else {
            sentencePlayer.play()
        }
    }

    fun nextSentence() {
        Log.d(TAG, "nextSentence called")
        sentencePlayer.nextSentence()
    }

    fun previousSentence() {
        Log.d(TAG, "previousSentence called")
        sentencePlayer.previousSentence()
    }

    fun seekToSentence(index: Int) {
        Log.d(TAG, "seekToSentence: $index")
        sentencePlayer.seekToSentence(index)
    }

    fun seekTo(position: Long) {
        Log.d(TAG, "seekTo: $position")
        sentencePlayer.seekTo(position)
    }

    fun setSpeed(speed: Float) {
        Log.d(TAG, "setSpeed: $speed")
        _playbackSettings.value = _playbackSettings.value.copy(speed = speed)
        sentencePlayer.setSettings(_playbackSettings.value)
        audioPlayer.setSpeed(speed)
    }

    fun setRepeatCount(count: Int) {
        Log.d(TAG, "setRepeatCount: $count")
        _playbackSettings.value = _playbackSettings.value.copy(repeatCount = count)
        sentencePlayer.setSettings(_playbackSettings.value)
    }

    fun setInterval(seconds: Int) {
        Log.d(TAG, "setInterval: $seconds")
        _playbackSettings.value = _playbackSettings.value.copy(intervalSeconds = seconds)
        sentencePlayer.setSettings(_playbackSettings.value)
    }

    fun toggleSubtitle() {
        _showSubtitle.value = !_showSubtitle.value
        Log.d(TAG, "toggleSubtitle: ${_showSubtitle.value}")
    }

    fun setAutoNext(enabled: Boolean) {
        Log.d(TAG, "setAutoNext: $enabled")
        _playbackSettings.value = _playbackSettings.value.copy(autoNext = enabled)
        sentencePlayer.setSettings(_playbackSettings.value)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared - saving position")

        // 保存播放位置
        _currentAudioFile.value?.let { audioFile ->
            viewModelScope.launch {
                repository.updateLastPosition(audioFile.id, _currentPosition.value)
            }
        }

        audioPlayer.release()
    }
}