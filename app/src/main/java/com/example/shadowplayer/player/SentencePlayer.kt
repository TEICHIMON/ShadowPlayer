package com.example.shadowplayer.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SentencePlayer @Inject constructor(
    private val audioPlayer: AudioPlayer
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(SentencePlayerState())
    val state: StateFlow<SentencePlayerState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(PlaybackSettings())
    val settings: StateFlow<PlaybackSettings> = _settings.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var intervalJob: Job? = null

    // 暂存字幕内容和类型，用于在获取到时长后重新处理(LRC)
    private var pendingLrcContent: String? = null
    private var pendingSubtitleType: String? = null

    init {
        // 监听播放位置变化
        audioPlayer.onPositionChanged = { position ->
            checkSentenceEnd(position)
        }

        // --- 修复点1：监听 Duration 变化，解决时长显示为0的问题 ---
        scope.launch {
            audioPlayer.duration.collectLatest { duration ->
                if (duration > 0) {
                    _state.value = _state.value.copy(totalDuration = duration)
                    // 时长更新后，修正最后一句字幕的结束时间
                    updateLastSentenceDuration(duration)
                }
            }
        }
    }

    /**
     * 加载音频和字幕
     * @param subtitlePath 用于判断是 lrc 还是 srt
     */
    fun load(audioPath: String, lrcContent: String?, subtitlePath: String? = null) {
        // 重置状态
        _state.value = SentencePlayerState()
        pendingLrcContent = lrcContent
        pendingSubtitleType = subtitlePath?.substringAfterLast('.', "")?.lowercase()

        audioPlayer.loadAudio(audioPath)

        // --- 修复点：移除 delay(500)，直接解析 ---
        scope.launch {
            val initialDuration = audioPlayer.getDuration()
            val finalDuration = if (initialDuration > 0) initialDuration else 0L

            val sentences = if (lrcContent != null) {
                parseSubtitle(lrcContent, pendingSubtitleType ?: "lrc", finalDuration)
            } else {
                emptyList()
            }

            _state.value = _state.value.copy(
                sentences = sentences,
                currentIndex = 0,
                currentRepeat = 1,
                totalDuration = finalDuration,
                isPlaying = false,
                isInInterval = false
            )
        }
    }

    // 根据类型选择解析器
    private fun parseSubtitle(content: String, type: String, duration: Long): List<LrcSentence> {
        return when (type) {
            "srt" -> SrtParser.parse(content) // 调用新的 SRT 解析器
            else -> LrcParser.parse(content, duration) // 默认 LRC
        }
    }

    // 修正最后一句的时间（针对LRC依赖总时长的情况）
    private fun updateLastSentenceDuration(duration: Long) {
        val currentSentences = _state.value.sentences
        // SRT 自带结束时间，通常不需要用总时长修正，除非你需要强制对齐
        if (currentSentences.isNotEmpty() && pendingSubtitleType != "srt") {
            val last = currentSentences.last()
            if (last.endTime <= last.startTime || last.endTime == 0L) {
                val newLast = last.copy(endTime = duration)
                val newSentences = currentSentences.toMutableList().apply {
                    set(lastIndex, newLast)
                }
                _state.value = _state.value.copy(sentences = newSentences)
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
    }

    fun pause() {
        audioPlayer.pause()
        _state.value = _state.value.copy(isPlaying = false)
        stopPositionUpdate()
        cancelInterval()
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

        if (_state.value.isPlaying) {
            play()
        }
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

    fun updateSettings(settings: PlaybackSettings) {
        _settings.value = settings
        audioPlayer.setSpeed(settings.speed)
    }

    fun setSpeed(speed: Float) {
        _settings.value = _settings.value.copy(speed = speed)
        audioPlayer.setSpeed(speed)
    }

    fun setRepeatCount(count: Int) {
        _settings.value = _settings.value.copy(repeatCount = count)
    }

    fun setRepeatInterval(interval: Long) {
        _settings.value = _settings.value.copy(repeatInterval = interval)
    }

    fun toggleSubtitle() {
        _settings.value = _settings.value.copy(showSubtitle = !_settings.value.showSubtitle)
    }

    /**
     * 检查当前句子是否结束
     */
    private fun checkSentenceEnd(position: Long) {
        val state = _state.value

        // --- 修复点2：防抖动逻辑 ---
        // 如果正在间隔倒计时中，直接返回，不再检查结束时间，防止多次触发 handleSentenceEnd 导致逻辑混乱
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
        scope.cancel()
        audioPlayer.release()
    }
}