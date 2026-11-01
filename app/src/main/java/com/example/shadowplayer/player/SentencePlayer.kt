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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
        private const val KEY_SLEEP_TIMER_MINUTES = "sleep_timer_minutes"
        private const val POSITION_SAVE_INTERVAL_MS = 10000L
        private const val SLEEP_TIMER_MINUTE_MS = 60_000L
        private const val SEEK_TOLERANCE_MS = 150L
        // [问题4修复] 增加跳转前导时间，提供呼吸感
        private const val SEEK_PRE_ROLL_MS = 200L
        // [新增] 用户主动 seek 后的意图保护窗口，防止轮询把 index 回拉到上一句
        private const val USER_INTENT_PROTECTION_MS = 500L
        // 同步状态时，位置略超过句尾仍算当前句：句末检测是 50ms 轮询，倍速或卡顿会让暂停位置越过句尾
        private const val SYNC_END_TOLERANCE_MS = 500L
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
    private var sleepTimerJob: Job? = null

    // 暂存 LRC 内容
    private var pendingLrcContent: String? = null
    private var pendingSubtitleType: String? = null

    private val _currentAudioId = MutableStateFlow(-1L)
    val currentAudioId: StateFlow<Long> = _currentAudioId.asStateFlow()

    // [新增] 用户意图保护：记录最近一次用户主动 seek 的目标 index 和时间戳
    private var userIntendedIndex: Int = -1
    private var userIntendTimestamp: Long = 0L

    init {
        loadSettings()

        audioPlayer.onPositionChanged = { position ->
            checkSentenceEnd(position)
        }

        audioPlayer.onExternalPause = {
            handleExternalPause()
        }

        audioPlayer.onPlaybackEnded = {
            handlePlaybackEnded()
        }

        scope.launch {
            audioPlayer.isPlaying.collectLatest { isAudioPlaying ->
                val current = _state.value
                _state.value = current.copy(
                    isAudioPlaying = isAudioPlaying,
                    isPlaying = current.isPlaying || isAudioPlaying
                )
                if (isAudioPlaying) {
                    startPositionUpdate()
                    startPeriodicPositionSave()
                }
            }
        }

        scope.launch {
            audioPlayer.duration.collectLatest { duration ->
                if (duration > 0) {
                    _state.value = _state.value.copy(totalDuration = duration)
                    updateSentencesWithDuration(duration)
                    val audioId = _currentAudioId.value
                    if (audioId > 0) {
                        launch(Dispatchers.IO) {
                            repository.updateDuration(audioId, duration)
                        }
                    }
                }
            }
        }

        scope.launch {
            _state
                .map { it.currentSentence?.text.orEmpty() }
                .distinctUntilChanged()
                .collectLatest { subtitle ->
                    audioPlayer.updateNotificationSubtitle(subtitle.ifBlank { null })
                }
        }
    }

    fun getLastPlayedAudioId(): Long {
        return prefs.getLong(KEY_LAST_PLAYED_AUDIO_ID, -1L)
    }

    fun getCurrentAudioId(): Long = _currentAudioId.value

    fun isPlayingAudio(audioId: Long): Boolean {
        return _currentAudioId.value == audioId && _currentAudioId.value > 0
    }

    fun isCurrentlyPlaying(): Boolean {
        return _state.value.isSessionActive
    }

    fun isPlaybackActive(): Boolean {
        val snapshot = audioPlayer.getSnapshot()
        return _state.value.isSessionActive || snapshot.isPlaybackRequested
    }

    fun hasLoadedMedia(): Boolean = audioPlayer.hasMedia()

    fun syncFromPlayer() {
        val snapshot = audioPlayer.getSnapshot()
        if (!snapshot.hasMedia) {
            stopPositionUpdate()
            stopPeriodicPositionSave()
            _state.value = _state.value.copy(
                isPlaying = false,
                isAudioPlaying = false,
                isInInterval = false,
                intervalCountdown = 0
            )
            return
        }

        val state = _state.value
        val position = snapshot.currentPosition
        val duration = if (snapshot.duration > 0) snapshot.duration else state.totalDuration
        // 位置仍在当前句范围内（含跳句前导和句尾余量）就保留当前句，
        // 否则跳句后切出去再回来，前导位置会被误判成上一句
        val newIndex = if (state.sentences.isNotEmpty()) {
            LrcParser.resolveSentenceIndex(
                sentences = state.sentences,
                position = position,
                currentIndex = state.currentIndex,
                preRollMs = SEEK_PRE_ROLL_MS,
                endToleranceMs = SYNC_END_TOLERANCE_MS
            ).takeIf { it >= 0 && it < state.sentences.size }
                ?: state.currentIndex
        } else {
            state.currentIndex
        }
        val sessionActive = snapshot.isPlaybackRequested || state.isInInterval

        _state.value = state.copy(
            currentPosition = position,
            totalDuration = duration,
            currentIndex = newIndex,
            isPlaying = sessionActive,
            isAudioPlaying = snapshot.isPlaying
        )

        when {
            snapshot.isPlaybackRequested -> {
                startPositionUpdate()
                startPeriodicPositionSave()
            }
            !state.isInInterval -> {
                stopPositionUpdate()
                stopPeriodicPositionSave()
            }
        }
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
        title: String,
        lrcContent: String?,
        subtitlePath: String? = null,
        audioId: Long = -1L,
        initialPosition: Long = 0L
    ) {
        saveCurrentPosition()
        // 间隔倒计时中换音频，倒计时结束后会去播放新音频
        cancelInterval()

        _state.value = SentencePlayerState()
        pendingLrcContent = lrcContent
        pendingSubtitleType = subtitlePath?.substringAfterLast('.', "")?.lowercase()

        _currentAudioId.value = audioId
        if (audioId > 0) {
            prefs.edit { putLong(KEY_LAST_PLAYED_AUDIO_ID, audioId) }
            updatePlaylistIndex(audioId)
        }

        audioPlayer.loadAudio(audioPath, audioId, title)

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
            // 保存的进度可能是跳句时的前导位置，应恢复到后一句
            LrcParser.findSentenceIndexWithPreRoll(sentences, initialPosition, SEEK_PRE_ROLL_MS)
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
            audioPlayer.seekTo(initialPosition)
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
        if (!audioPlayer.hasMedia()) return
        cancelInterval()
        // 播放器停在结尾（STATE_ENDED）时直接 play() 不会出声，先回到开头
        if (audioPlayer.isEnded()) {
            _state.value = _state.value.copy(currentIndex = 0, currentRepeat = 1, currentPosition = 0L)
            audioPlayer.seekTo(0L)
        }
        audioPlayer.setSpeed(_settings.value.speed)
        audioPlayer.play()
        _state.value = _state.value.copy(
            isPlaying = true,
            isAudioPlaying = audioPlayer.getSnapshot().isPlaying,
            isInInterval = false
        )
        startPositionUpdate()
        startPeriodicPositionSave()
    }

    fun pause() {
        settlePausedState()
        audioPlayer.pause()
    }

    /**
     * Called when ExoPlayer pauses because audio focus was lost or a headset was disconnected.
     * This updates logical sentence playback without issuing another player command.
     */
    fun handleExternalPause() {
        settlePausedState()
    }

    /**
     * 播放器播到了音频结尾（STATE_ENDED）。最后一句的句尾就是音频结尾，
     * 50ms 一次的句末检测来不及看到 position >= endTime 就停了，
     * 所以这里补做一次句末处理，否则最后一句的重复和跟读间隔会被跳过。
     */
    private fun handlePlaybackEnded() {
        val state = _state.value
        // 轮询偶尔会比 STATE_ENDED 早几毫秒看到句尾，已经进入跟读间隔；
        // 播放器缓冲已播完，暂停后仍会报告结尾。这时不能当成外部暂停，否则会取消刚开始的间隔
        if (state.isInInterval) return
        if (!state.isPlaying || state.currentSentence == null || isContinuousPlaybackMode()) {
            handleExternalPause()
            return
        }
        _state.value = state.copy(currentPosition = audioPlayer.getCurrentPosition())
        handleSentenceEnd()
    }

    private fun settlePausedState() {
        val currentPosition = audioPlayer.getCurrentPosition()
        stopPositionUpdate()
        stopPeriodicPositionSave()
        intervalJob?.cancel()
        intervalJob = null
        _state.value = _state.value.copy(
            isPlaying = false,
            isAudioPlaying = false,
            isInInterval = false,
            intervalCountdown = 0,
            currentPosition = currentPosition
        )
        saveCurrentPosition()
    }

    fun seekToSentence(index: Int) {
        val sentences = _state.value.sentences
        if (index < 0 || index >= sentences.size) return

        cancelInterval()
        val sentence = sentences[index]

        // [新增] 记录用户意图
        userIntendedIndex = index
        userIntendTimestamp = System.currentTimeMillis()

        _state.value = _state.value.copy(
            currentIndex = index,
            currentRepeat = 1,
            currentPosition = sentence.startTime,
            isInInterval = false
        )

        val seekTarget = maxOf(0L, sentence.startTime - SEEK_PRE_ROLL_MS)
        audioPlayer.seekTo(seekTarget)

        saveCurrentPosition()
        if (_state.value.isPlaying) play()
    }

    fun previousSentence() {
        seekToSentence(maxOf(0, _state.value.currentIndex - 1))
    }

    fun nextSentence() {
        seekToSentence(minOf(_state.value.sentences.size - 1, _state.value.currentIndex + 1))
    }

    fun previousSentenceOrSeek() {
        if (_state.value.hasSentences) previousSentence() else seekBackward()
    }

    fun nextSentenceOrSeek() {
        if (_state.value.hasSentences) nextSentence() else seekForward()
    }

    fun seekTo(position: Long) {
        val newIndex = LrcParser.findSentenceIndex(_state.value.sentences, position)

        // [新增] 记录用户意图
        if (newIndex >= 0) {
            userIntendedIndex = newIndex
            userIntendTimestamp = System.currentTimeMillis()
        }

        _state.value = _state.value.copy(
            currentPosition = position,
            currentIndex = newIndex,
            currentRepeat = 1
        )
        audioPlayer.seekTo(position)
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
        val audioId = _currentAudioId.value
        if (audioId > 0) {
            val position = _state.value.currentPosition
            scope.launch(Dispatchers.IO) {
                repository.updateLastPosition(audioId, position)
            }
        }
    }

    private fun startPeriodicPositionSave() {
        positionSaveJob?.cancel()
        positionSaveJob = scope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                val audioId = _currentAudioId.value
                if (_state.value.isPlaying && audioId > 0) {
                    val position = _state.value.currentPosition
                    withContext(Dispatchers.IO) {
                        repository.updateLastPosition(audioId, position)
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
        val volume = PlaybackSettings.normalizeVolume(prefs.getFloat("volume", 1.0f))
        val repeatCount = prefs.getInt("repeat_count", 1)
        val repeatInterval = prefs.getLong("repeat_interval", 2000L)
        val autoNext = prefs.getBoolean("auto_next", true)
        val showSubtitle = prefs.getBoolean("show_subtitle", true)
        val seekInterval = prefs.getLong("seek_interval", 10000L)
        val volumeKeyEnabled = prefs.getBoolean("volume_key_enabled", false)
        val savedSleepTimerMinutes = prefs.getInt(KEY_SLEEP_TIMER_MINUTES, 0)
        if (savedSleepTimerMinutes != 0) {
            prefs.edit { putInt(KEY_SLEEP_TIMER_MINUTES, 0) }
        }

        val savedSettings = PlaybackSettings(
            speed = speed,
            volume = volume,
            repeatCount = repeatCount,
            repeatInterval = repeatInterval,
            autoNext = autoNext,
            showSubtitle = showSubtitle,
            sleepTimerMinutes = 0,
            seekInterval = seekInterval,
            volumeKeyEnabled = volumeKeyEnabled
        )
        _settings.value = savedSettings
        audioPlayer.setSpeed(speed)
        audioPlayer.setVolume(volume)
    }

    private fun saveSettings(settings: PlaybackSettings) {
        prefs.edit {
            putFloat("speed", settings.speed)
            putFloat("volume", settings.volume)
            putInt("repeat_count", settings.repeatCount)
            putLong("repeat_interval", settings.repeatInterval)
            putBoolean("auto_next", settings.autoNext)
            putBoolean("show_subtitle", settings.showSubtitle)
            putInt(KEY_SLEEP_TIMER_MINUTES, settings.sleepTimerMinutes)
            putLong("seek_interval", settings.seekInterval)
            putBoolean("volume_key_enabled", settings.volumeKeyEnabled)
        }
    }

    fun updateSettings(settings: PlaybackSettings) {
        val boundedSettings = settings.copy(
            volume = PlaybackSettings.normalizeVolume(settings.volume),
            sleepTimerMinutes = PlaybackSettings.normalizeSleepTimerMinutes(settings.sleepTimerMinutes)
        )
        _settings.value = boundedSettings
        audioPlayer.setSpeed(boundedSettings.speed)
        audioPlayer.setVolume(boundedSettings.volume)
        saveSettings(boundedSettings)
    }

    fun setSpeed(speed: Float) {
        val newSettings = _settings.value.copy(speed = speed)
        updateSettings(newSettings)
    }

    fun setVolume(volume: Float) {
        val newSettings = _settings.value.copy(volume = volume)
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

    fun setSleepTimerMinutes(minutes: Int) {
        val normalizedMinutes = PlaybackSettings.normalizeSleepTimerMinutes(minutes)
        updateSettings(_settings.value.copy(sleepTimerMinutes = normalizedMinutes))
        startSleepTimer(normalizedMinutes)
    }

    fun setVolumeKeyEnabled(enabled: Boolean) {
        val newSettings = _settings.value.copy(volumeKeyEnabled = enabled)
        updateSettings(newSettings)
    }

    private fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes <= 0) return

        sleepTimerJob = scope.launch {
            delay(minutes * SLEEP_TIMER_MINUTE_MS)
            if (!isActive) return@launch
            pause()
            updateSettings(_settings.value.copy(sleepTimerMinutes = 0))
            sleepTimerJob = null
        }
    }

    private fun checkSentenceEnd(position: Long) {
        val state = _state.value
        if (state.isInInterval) return

        _state.value = state.copy(currentPosition = position)

        // seekTo 会立即回调位置（播放器在 seekTo 内部同步回调），暂停时拖进度条或恢复进度也会走到这里。
        // 这不是"播完了这一句"：只在播放中处理句末，否则会进入跟读间隔，倒计时结束后自动播放。
        if (!state.isPlaying) return
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
            // [新增] 和 updateCurrentIndexByPosition 同样的意图保护
            val now = System.currentTimeMillis()
            val inProtectionWindow = userIntendedIndex >= 0 &&
                    now - userIntendTimestamp < USER_INTENT_PROTECTION_MS
            if (inProtectionWindow && newIndex != userIntendedIndex) {
                return  // 用户刚表达过意图,忽略这次回弹
            }

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
            isPlaying = true,
            isAudioPlaying = false,
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
            isPlaying = true,
            isAudioPlaying = false,
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

        // [新增] 重复播放也是明确意图，保护当前 index 不被回拉
        userIntendedIndex = state.currentIndex
        userIntendTimestamp = System.currentTimeMillis()

        _state.value = state.copy(currentRepeat = state.currentRepeat + 1, isInInterval = false)

        val seekTarget = maxOf(0L, sentence.startTime - SEEK_PRE_ROLL_MS)
        audioPlayer.seekTo(seekTarget)

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
        // 跳句前导期间位置还在上一句范围内，保持当前句
        val newIndex = LrcParser.resolveSentenceIndex(
            sentences = state.sentences,
            position = currentPosition,
            currentIndex = state.currentIndex,
            preRollMs = SEEK_PRE_ROLL_MS
        )

        if (newIndex < 0) return
        if (newIndex == state.currentIndex) return

        // 用户意图保护窗口：不允许把 index 回拉到用户意图之前
        val now = System.currentTimeMillis()
        val inProtectionWindow = userIntendedIndex >= 0 &&
                now - userIntendTimestamp < USER_INTENT_PROTECTION_MS
        if (inProtectionWindow && newIndex < userIntendedIndex) {
            return  // 保持用户选中的句子，不回退
        }

        // 意图达成（位置已推进到目标或之后）或窗口已过期，清除意图
        if (userIntendedIndex >= 0 &&
            (newIndex >= userIntendedIndex || !inProtectionWindow)) {
            userIntendedIndex = -1
        }

        _state.value = state.copy(currentIndex = newIndex)
    }

    private fun stopPositionUpdate() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    fun release() {
        saveCurrentPosition()
        scope.cancel()
        sleepTimerJob = null
        audioPlayer.release()
    }
}
