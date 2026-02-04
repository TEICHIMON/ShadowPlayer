package com.example.shadowplayer.player

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.shadowplayer.data.entity.AudioFile
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
        private const val SEEK_TOLERANCE_MS = 150L
        // [问题4修复] 增加跳转前导时间，提供呼吸感
        private const val SEEK_PRE_ROLL_MS = 200L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(SentencePlayerState())
    val state: StateFlow<SentencePlayerState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(PlaybackSettings())
    val settings: StateFlow<PlaybackSettings> = _settings.asStateFlow()

    // 播放列表状态
    private val _playlist = MutableStateFlow<List<AudioFile>>(emptyList())
    val playlist: StateFlow<List<AudioFile>> = _playlist.asStateFlow()

    private val _currentPlaylistIndex = MutableStateFlow(-1)
    val currentPlaylistIndex: StateFlow<Int> = _currentPlaylistIndex.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var intervalJob: Job? = null
    private var positionSaveJob: Job? = null

    // 暂存 LRC 内容
    private var pendingLrcContent: String? = null
    private var pendingSubtitleType: String? = null

    private var currentAudioId: Long = -1L

    // [关键修复] 增加 seeking 标志位，防止 seekTo 触发的回调导致逻辑重入或死循环
    private var isSeeking = false

    init {
        loadSettings()

        audioPlayer.onPositionChanged = { position ->
            checkSentenceEnd(position)
        }

        scope.launch {
            audioPlayer.duration.collectLatest { duration ->
                if (duration > 0) {
                    _state.value = _state.value.copy(totalDuration = duration)
                    updateSentencesWithDuration(duration)
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

    fun getCurrentAudioId(): Long = currentAudioId

    fun isPlayingAudio(audioId: Long): Boolean {
        return currentAudioId == audioId && currentAudioId > 0
    }

    fun isCurrentlyPlaying(): Boolean {
        return _state.value.isPlaying
    }

    private fun isContinuousPlaybackMode(): Boolean {
        val settings = _settings.value
        return settings.repeatCount == 1 && settings.repeatInterval == 0L && settings.autoNext
    }

    fun setPlaylist(audioFiles: List<AudioFile>, currentIndex: Int) {
        _playlist.value = audioFiles
        _currentPlaylistIndex.value = currentIndex
    }

    private fun updatePlaylistIndex(audioId: Long) {
        val index = _playlist.value.indexOfFirst { it.id == audioId }
        if (index != -1) {
            _currentPlaylistIndex.value = index
        }
    }

    fun canPlayPrevious(): Boolean = _currentPlaylistIndex.value > 0

    fun canPlayNext(): Boolean {
        val playlist = _playlist.value
        val currentIndex = _currentPlaylistIndex.value
        return currentIndex >= 0 && currentIndex < playlist.size - 1
    }

    fun getPreviousAudio(): AudioFile? {
        if (!canPlayPrevious()) return null
        return _playlist.value.getOrNull(_currentPlaylistIndex.value - 1)
    }

    fun getNextAudio(): AudioFile? {
        if (!canPlayNext()) return null
        return _playlist.value.getOrNull(_currentPlaylistIndex.value + 1)
    }

    fun load(
        audioPath: String,
        lrcContent: String?,
        subtitlePath: String? = null,
        audioId: Long = -1L,
        initialPosition: Long = 0L
    ) {
        saveCurrentPosition()

        _state.value = SentencePlayerState()
        pendingLrcContent = lrcContent
        pendingSubtitleType = subtitlePath?.substringAfterLast('.', "")?.lowercase()

        currentAudioId = audioId
        if (audioId > 0) {
            prefs.edit { putLong(KEY_LAST_PLAYED_AUDIO_ID, audioId) }
            updatePlaylistIndex(audioId)
        }

        audioPlayer.loadAudio(audioPath)

        // [修复] 不再调用 getDuration()，使用 0 作为初始值
        // 正确的 duration 会通过 audioPlayer.duration Flow 异步更新
        // updateSentencesWithDuration() 会在收到正确值后重新解析字幕
        val initialDuration = 0L

        val sentences = if (lrcContent != null) {
            parseSubtitle(lrcContent, pendingSubtitleType ?: "lrc", initialDuration)
        } else {
            emptyList()
        }

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

        if (initialPosition > 0) {
            safeSeekTo(initialPosition)
        }
    }

    private fun parseSubtitle(content: String, type: String, duration: Long): List<LrcSentence> {
        return when (type) {
            "srt" -> SrtParser.parse(content)
            else -> LrcParser.parse(content, duration)
        }
    }

    private fun updateSentencesWithDuration(duration: Long) {
        val content = pendingLrcContent
        if (content != null) {
            val type = pendingSubtitleType ?: "lrc"
            val currentSentences = _state.value.sentences
            if (currentSentences.isEmpty() || type != "srt") {
                val newSentences = parseSubtitle(content, type, duration)
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

        // [重要修复] 设置 isSeeking = true，阻止回调逻辑运行
        isSeeking = true
        _state.value = _state.value.copy(
            currentIndex = index,
            currentRepeat = 1,
            currentPosition = sentence.startTime,
            isInInterval = false
        )

        // [问题4修复] 跳转时向前偏移一点点，并处理 0 的边界
        val seekTarget = maxOf(0L, sentence.startTime - SEEK_PRE_ROLL_MS)
        audioPlayer.seekTo(seekTarget)

        // [重要修复] seek 完成后恢复标志位
        isSeeking = false

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
        val newIndex = LrcParser.findSentenceIndex(_state.value.sentences, position)

        // [重要修复] 使用 isSeeking 保护
        isSeeking = true
        _state.value = _state.value.copy(
            currentPosition = position,
            currentIndex = newIndex,
            currentRepeat = 1
        )
        audioPlayer.seekTo(position)
        isSeeking = false
    }

    // 内部使用的安全 seek 方法
    private fun safeSeekTo(position: Long) {
        isSeeking = true
        audioPlayer.seekTo(position)
        isSeeking = false
    }

    fun seekForward() {
        val interval = _settings.value.seekInterval
        val currentPos = audioPlayer.getCurrentPosition()
        val duration = _state.value.totalDuration
        val newPos = minOf(currentPos + interval, duration)
        seekTo(newPos)
    }

    fun seekBackward() {
        val interval = _settings.value.seekInterval
        val currentPos = audioPlayer.getCurrentPosition()
        val newPos = maxOf(currentPos - interval, 0L)
        seekTo(newPos)
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
        val seekInterval = prefs.getLong("seek_interval", 10000L)
        val volumeKeyEnabled = prefs.getBoolean("volume_key_enabled", true)

        val savedSettings = PlaybackSettings(
            speed = speed,
            repeatCount = repeatCount,
            repeatInterval = repeatInterval,
            autoNext = autoNext,
            showSubtitle = showSubtitle,
            seekInterval = seekInterval,
            volumeKeyEnabled = volumeKeyEnabled
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
            putLong("seek_interval", settings.seekInterval)
            putBoolean("volume_key_enabled", settings.volumeKeyEnabled)
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

    fun setSeekInterval(interval: Long) {
        val newSettings = _settings.value.copy(seekInterval = interval)
        updateSettings(newSettings)
    }

    fun setVolumeKeyEnabled(enabled: Boolean) {
        val newSettings = _settings.value.copy(volumeKeyEnabled = enabled)
        updateSettings(newSettings)
    }

    private fun checkSentenceEnd(position: Long) {
        val state = _state.value
        // [重要修复] 如果正在 seek 或处于间隔中，直接忽略回调，防止逻辑冲突
        if (state.isInInterval || isSeeking) return

        _state.value = state.copy(currentPosition = position)

        val currentSentence = state.currentSentence ?: return

        if (position >= currentSentence.endTime) {
            handleSentenceEnd()
        }
    }

    private fun handleSentenceEnd() {
        val state = _state.value
        val settings = _settings.value

        // 连续播放模式：不重复、无间隔、自动下一句
        if (isContinuousPlaybackMode()) {
            handleContinuousPlayback()
            return
        }

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

    private fun handleContinuousPlayback() {
        val state = _state.value
        val currentPosition = audioPlayer.getCurrentPosition()

        val newIndex = LrcParser.findSentenceIndex(state.sentences, currentPosition)

        if (newIndex != state.currentIndex && newIndex >= 0 && newIndex < state.sentences.size) {
            _state.value = state.copy(
                currentIndex = newIndex,
                currentRepeat = 1
            )
        }

        if (state.currentIndex >= state.sentences.size - 1) {
            val lastSentence = state.sentences.lastOrNull()
            if (lastSentence != null && currentPosition >= lastSentence.endTime) {
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
                _state.value = _state.value.copy(intervalCountdown = 0)
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
                _state.value = _state.value.copy(intervalCountdown = 0, currentRepeat = 1)
                moveToNextSentenceSmooth()
            }
        }
    }

    private fun moveToNextSentenceSmooth() {
        val state = _state.value
        val nextIndex = state.currentIndex + 1
        if (nextIndex >= state.sentences.size) {
            pause()
            return
        }

        val nextSentence = state.sentences[nextIndex]
        val currentPosition = audioPlayer.getCurrentPosition()

        val distanceToNextStart = currentPosition - nextSentence.startTime
        if (distanceToNextStart >= 0 && distanceToNextStart < SEEK_TOLERANCE_MS) {
            _state.value = state.copy(
                currentIndex = nextIndex,
                currentRepeat = 1,
                currentPosition = currentPosition,
                isInInterval = false
            )
            audioPlayer.play()
            startPositionUpdate()
        } else {
            nextSentence()
            play()
        }
    }

    private fun repeatCurrentSentence() {
        val state = _state.value
        val sentence = state.currentSentence ?: return

        // [重要修复] 使用 isSeeking 保护
        isSeeking = true
        _state.value = state.copy(currentRepeat = state.currentRepeat + 1, isInInterval = false)

        // [问题4修复] 跳转时向前偏移一点点，并处理 0 的边界
        val seekTarget = maxOf(0L, sentence.startTime - SEEK_PRE_ROLL_MS)
        audioPlayer.seekTo(seekTarget)

        isSeeking = false

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
                if (isContinuousPlaybackMode()) {
                    updateCurrentIndexByPosition()
                }
                delay(50)
            }
        }
    }

    private fun updateCurrentIndexByPosition() {
        val state = _state.value
        if (state.sentences.isEmpty()) return

        val currentPosition = audioPlayer.getCurrentPosition()
        val newIndex = LrcParser.findSentenceIndex(state.sentences, currentPosition)

        if (newIndex != state.currentIndex && newIndex >= 0) {
            _state.value = state.copy(currentIndex = newIndex)
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