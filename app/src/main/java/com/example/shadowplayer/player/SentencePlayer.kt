package com.example.shadowplayer.player

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.shadowplayer.data.repository.AudioRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SentencePlayer @Inject constructor(
    private val audioPlayer: AudioPlayer,
    private val prefs: SharedPreferences,
    private val repository: AudioRepository
) {
    companion object {
        private const val KEY_LAST_PLAYED_AUDIO_ID = "last_played_audio_id"
        private const val POSITION_SAVE_INTERVAL_MS = 10000L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(SentencePlayerState())
    val state: StateFlow<SentencePlayerState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(PlaybackSettings())
    val settings: StateFlow<PlaybackSettings> = _settings.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var intervalJob: Job? = null
    private var positionSaveJob: Job? = null

    // 暂存 LRC 内容，以便时长准备好后重新解析
    private var pendingLrcContent: String? = null
    private var pendingSubtitleType: String? = null

    private var currentAudioId: Long = -1L

    init {
        loadSettings()

        audioPlayer.onPositionChanged = { position ->
            checkSentenceEnd(position)
        }

        // 监听时长变化，修复 00:00 问题
        scope.launch {
            audioPlayer.duration.collectLatest { duration ->
                if (duration > 0) {
                    // 更新 UI 总时长
                    _state.value = _state.value.copy(totalDuration = duration)

                    // 重新处理字幕（特别是最后一句的结束时间）
                    updateSentencesWithDuration(duration)

                    // 更新数据库中的时长
                    if (currentAudioId > 0) {
                        launch(Dispatchers.IO) {
                            repository.updateDuration(currentAudioId, duration)
                        }
                    }
                }
            }
        }
    }

    fun getLastPlayedAudioId(): Long {
        return prefs.getLong(KEY_LAST_PLAYED_AUDIO_ID, -1L)
    }

    fun load(
        audioPath: String,
        lrcContent: String?,
        subtitlePath: String? = null,
        audioId: Long = -1L,
        initialPosition: Long = 0L
    ) {
        saveCurrentPosition()

        // 重置状态
        _state.value = SentencePlayerState()
        pendingLrcContent = lrcContent
        pendingSubtitleType = subtitlePath?.substringAfterLast('.', "")?.lowercase()

        currentAudioId = audioId
        if (audioId > 0) {
            prefs.edit { putLong(KEY_LAST_PLAYED_AUDIO_ID, audioId) }
        }

        audioPlayer.loadAudio(audioPath)

        // 此时 duration 可能还是 0，先用 0 解析，等 duration 回调后再修正
        val initialDuration = audioPlayer.getDuration()
        val sentences = if (lrcContent != null) {
            parseSubtitle(lrcContent, pendingSubtitleType ?: "lrc", initialDuration)
        } else {
            emptyList()
        }

        // 计算初始索引
        val startIndex = if (sentences.isNotEmpty() && initialPosition > 0) {
            LrcParser.findSentenceIndex(sentences, initialPosition)
        } else {
            0
        }

        _state.value = _state.value.copy(
            sentences = sentences,
            currentIndex = startIndex,
            currentPosition = initialPosition,
            currentRepeat = 1,
            totalDuration = initialDuration,
            isPlaying = false,
            isInInterval = false
        )

        // 恢复播放位置
        if (initialPosition > 0) {
            audioPlayer.seekTo(initialPosition)
        }
    }

    private fun parseSubtitle(content: String, type: String, duration: Long): List<LrcSentence> {
        return when (type) {
            "srt" -> SrtParser.parse(content)
            else -> LrcParser.parse(content, duration)
        }
    }

    // 当获取到真实时长后，修正字幕
    private fun updateSentencesWithDuration(duration: Long) {
        // 如果之前有暂存的字幕内容，重新解析一次是最准确的
        val content = pendingLrcContent
        if (content != null) {
            val type = pendingSubtitleType ?: "lrc"
            // 只有当是 LRC 格式（依赖总时长计算最后一句）才需要重新解析，或者列表为空时
            val currentSentences = _state.value.sentences
            if (currentSentences.isEmpty() || type != "srt") {
                val newSentences = parseSubtitle(content, type, duration)
                // 保持当前的 index 不变
                val currentIndex = _state.value.currentIndex
                _state.value = _state.value.copy(sentences = newSentences, currentIndex = currentIndex)
            }
        }
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun play() {
        cancelInterval()
        audioPlayer.setSpeed(_settings.value.speed)
        audioPlayer.play()
        _state.value = _state.value.copy(isPlaying = true, isInInterval = false)
        startPositionUpdate()
        startPeriodicPositionSave()
    }

    fun pause() {
        audioPlayer.pause()
        _state.value = _state.value.copy(isPlaying = false)
        stopPositionUpdate()
        stopPeriodicPositionSave()
        cancelInterval()
        saveCurrentPosition()
    }

    fun seekToSentence(index: Int) {
        val sentences = _state.value.sentences
        if (index < 0 || index >= sentences.size) return

        cancelInterval()
        val sentence = sentences[index]
        audioPlayer.seekTo(sentence.startTime)
        _state.value = _state.value.copy(
            currentIndex = index,
            currentRepeat = 1,
            currentPosition = sentence.startTime,
            isInInterval = false
        )
        saveCurrentPosition()
        if (_state.value.isPlaying) play()
    }

    fun previousSentence() {
        seekToSentence(maxOf(0, _state.value.currentIndex - 1))
    }

    fun nextSentence() {
        seekToSentence(minOf(_state.value.sentences.size - 1, _state.value.currentIndex + 1))
    }

    fun seekTo(position: Long) {
        audioPlayer.seekTo(position)
        val newIndex = LrcParser.findSentenceIndex(_state.value.sentences, position)
        _state.value = _state.value.copy(
            currentPosition = position,
            currentIndex = newIndex,
            currentRepeat = 1
        )
    }

    private fun saveCurrentPosition() {
        if (currentAudioId > 0) {
            val position = _state.value.currentPosition
            scope.launch(Dispatchers.IO) {
                repository.updateLastPosition(currentAudioId, position)
            }
        }
    }

    private fun startPeriodicPositionSave() {
        positionSaveJob?.cancel()
        positionSaveJob = scope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (_state.value.isPlaying && currentAudioId > 0) {
                    val position = _state.value.currentPosition
                    withContext(Dispatchers.IO) {
                        repository.updateLastPosition(currentAudioId, position)
                    }
                }
            }
        }
    }

    private fun stopPeriodicPositionSave() {
        positionSaveJob?.cancel()
        positionSaveJob = null
    }

    private fun loadSettings() {
        val speed = prefs.getFloat("speed", 1.0f)
        val repeatCount = prefs.getInt("repeat_count", 1)
        val repeatInterval = prefs.getLong("repeat_interval", 2000L)
        val autoNext = prefs.getBoolean("auto_next", true)
        val showSubtitle = prefs.getBoolean("show_subtitle", true)

        val savedSettings = PlaybackSettings(
            speed = speed,
            repeatCount = repeatCount,
            repeatInterval = repeatInterval,
            autoNext = autoNext,
            showSubtitle = showSubtitle
        )
        _settings.value = savedSettings
        audioPlayer.setSpeed(speed)
    }

    private fun saveSettings(settings: PlaybackSettings) {
        prefs.edit {
            putFloat("speed", settings.speed)
            putInt("repeat_count", settings.repeatCount)
            putLong("repeat_interval", settings.repeatInterval)
            putBoolean("auto_next", settings.autoNext)
            putBoolean("show_subtitle", settings.showSubtitle)
        }
    }

    fun updateSettings(settings: PlaybackSettings) {
        _settings.value = settings
        audioPlayer.setSpeed(settings.speed)
        saveSettings(settings)
    }

    fun setSpeed(speed: Float) {
        val newSettings = _settings.value.copy(speed = speed)
        updateSettings(newSettings)
    }

    fun setRepeatCount(count: Int) {
        val newSettings = _settings.value.copy(repeatCount = count)
        updateSettings(newSettings)
    }

    fun setRepeatInterval(interval: Long) {
        val newSettings = _settings.value.copy(repeatInterval = interval)
        updateSettings(newSettings)
    }

    fun toggleSubtitle() {
        val newSettings = _settings.value.copy(showSubtitle = !_settings.value.showSubtitle)
        updateSettings(newSettings)
    }

    private fun checkSentenceEnd(position: Long) {
        val state = _state.value
        if (state.isInInterval) return

        val currentSentence = state.currentSentence ?: return
        _state.value = state.copy(currentPosition = position)

        if (position >= currentSentence.endTime) {
            handleSentenceEnd()
        }
    }

    private fun handleSentenceEnd() {
        val state = _state.value
        val settings = _settings.value

        if (state.currentRepeat < settings.repeatCount) {
            startInterval()
        } else {
            if (settings.autoNext && state.currentIndex < state.sentences.size - 1) {
                if (settings.repeatInterval > 0) {
                    startIntervalForNext()
                } else {
                    nextSentence()
                }
            } else {
                pause()
            }
        }
    }

    private fun startInterval() {
        val settings = _settings.value
        if (settings.repeatInterval <= 0) {
            repeatCurrentSentence()
            return
        }

        audioPlayer.pause()
        _state.value = _state.value.copy(
            isInInterval = true,
            intervalCountdown = (settings.repeatInterval / 1000).toInt()
        )

        intervalJob = scope.launch {
            val totalSeconds = (settings.repeatInterval / 1000).toInt()
            for (i in totalSeconds downTo 1) {
                if (!isActive) break
                _state.value = _state.value.copy(intervalCountdown = i)
                delay(1000)
            }
            if (isActive) {
                _state.value = _state.value.copy(isInInterval = false, intervalCountdown = 0)
                repeatCurrentSentence()
            }
        }
    }

    private fun startIntervalForNext() {
        val settings = _settings.value
        audioPlayer.pause()
        _state.value = _state.value.copy(
            isInInterval = true,
            intervalCountdown = (settings.repeatInterval / 1000).toInt()
        )

        intervalJob = scope.launch {
            val totalSeconds = (settings.repeatInterval / 1000).toInt()
            for (i in totalSeconds downTo 1) {
                if (!isActive) break
                _state.value = _state.value.copy(intervalCountdown = i)
                delay(1000)
            }
            if (isActive) {
                _state.value = _state.value.copy(isInInterval = false, intervalCountdown = 0, currentRepeat = 1)
                nextSentence()
                play()
            }
        }
    }

    private fun repeatCurrentSentence() {
        val state = _state.value
        val sentence = state.currentSentence ?: return
        _state.value = state.copy(currentRepeat = state.currentRepeat + 1, isInInterval = false)
        audioPlayer.seekTo(sentence.startTime)
        audioPlayer.play()
        startPositionUpdate()
    }

    private fun cancelInterval() {
        intervalJob?.cancel()
        intervalJob = null
        _state.value = _state.value.copy(isInInterval = false, intervalCountdown = 0)
    }

    private fun startPositionUpdate() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                audioPlayer.updatePosition()
                delay(100)
            }
        }
    }

    private fun stopPositionUpdate() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    fun release() {
        saveCurrentPosition()
        scope.cancel()
        audioPlayer.release()
    }
}