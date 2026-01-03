package com.example.shadowplayer.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分句播放器 - 控制分句重复、跟读间隔等核心逻辑
 */
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

    init {
        // 监听播放位置变化
        audioPlayer.onPositionChanged = { position ->
            checkSentenceEnd(position)
        }
    }

    /**
     * 加载音频和字幕
     */
    fun load(audioPath: String, lrcContent: String?, lrcOffset: Long = 0) {
        audioPlayer.loadAudio(audioPath)

        scope.launch {
            delay(500) // 等待 ExoPlayer 准备
            val duration = audioPlayer.getDuration()

            val sentences = if (lrcContent != null) {
                LrcParser.parse(lrcContent, duration, lrcOffset)
            } else {
                emptyList()
            }

            _state.value = _state.value.copy(
                sentences = sentences,
                currentIndex = 0,
                currentRepeat = 1,
                totalDuration = duration,
                isPlaying = false,
                isInInterval = false
            )
        }
    }

    /**
     * 播放/暂停切换
     */
    fun togglePlayPause() {
        if (_state.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    /**
     * 播放
     */
    fun play() {
        cancelInterval()
        audioPlayer.setSpeed(_settings.value.speed)
        audioPlayer.play()
        _state.value = _state.value.copy(isPlaying = true, isInInterval = false)
        startPositionUpdate()
    }

    /**
     * 暂停
     */
    fun pause() {
        audioPlayer.pause()
        _state.value = _state.value.copy(isPlaying = false)
        stopPositionUpdate()
        cancelInterval()
    }

    /**
     * 跳转到指定句子
     */
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

    /**
     * 上一句
     */
    fun previousSentence() {
        val currentIndex = _state.value.currentIndex
        seekToSentence(maxOf(0, currentIndex - 1))
    }

    /**
     * 下一句
     */
    fun nextSentence() {
        val currentIndex = _state.value.currentIndex
        val maxIndex = _state.value.sentences.size - 1
        seekToSentence(minOf(maxIndex, currentIndex + 1))
    }

    /**
     * 跳转到指定位置
     */
    fun seekTo(position: Long) {
        audioPlayer.seekTo(position)
        val newIndex = LrcParser.findSentenceIndex(_state.value.sentences, position)
        _state.value = _state.value.copy(
            currentPosition = position,
            currentIndex = newIndex,
            currentRepeat = 1
        )
    }

    /**
     * 更新播放设置
     */
    fun updateSettings(settings: PlaybackSettings) {
        _settings.value = settings
        audioPlayer.setSpeed(settings.speed)
    }

    /**
     * 更新播放速度
     */
    fun setSpeed(speed: Float) {
        _settings.value = _settings.value.copy(speed = speed)
        audioPlayer.setSpeed(speed)
    }

    /**
     * 更新重复次数
     */
    fun setRepeatCount(count: Int) {
        _settings.value = _settings.value.copy(repeatCount = count)
    }

    /**
     * 更新跟读间隔
     */
    fun setRepeatInterval(interval: Long) {
        _settings.value = _settings.value.copy(repeatInterval = interval)
    }

    /**
     * 切换字幕显示
     */
    fun toggleSubtitle() {
        _settings.value = _settings.value.copy(showSubtitle = !_settings.value.showSubtitle)
    }

    /**
     * 检查当前句子是否结束
     */
    private fun checkSentenceEnd(position: Long) {
        val state = _state.value
        val settings = _settings.value
        val currentSentence = state.currentSentence ?: return

        _state.value = state.copy(currentPosition = position)

        // 检查是否到达当前句子结束时间
        if (position >= currentSentence.endTime) {
            handleSentenceEnd()
        }
    }

    /**
     * 处理句子结束
     */
    private fun handleSentenceEnd() {
        val state = _state.value
        val settings = _settings.value

        if (state.currentRepeat < settings.repeatCount) {
            // 需要重复，进入跟读间隔
            startInterval()
        } else {
            // 重复完成，播放下一句
            if (settings.autoNext && state.currentIndex < state.sentences.size - 1) {
                // 如果有跟读间隔，先进入间隔
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

    /**
     * 开始跟读间隔（重复当前句）
     */
    private fun startInterval() {
        val settings = _settings.value
        if (settings.repeatInterval <= 0) {
            // 无间隔，直接重复
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
                _state.value = _state.value.copy(intervalCountdown = i)
                delay(1000)
            }
            _state.value = _state.value.copy(isInInterval = false, intervalCountdown = 0)
            repeatCurrentSentence()
        }
    }

    /**
     * 开始跟读间隔（进入下一句前）
     */
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
                _state.value = _state.value.copy(intervalCountdown = i)
                delay(1000)
            }
            _state.value = _state.value.copy(isInInterval = false, intervalCountdown = 0)
            // 重置重复计数，进入下一句
            _state.value = _state.value.copy(currentRepeat = 1)
            nextSentence()
            play()
        }
    }

    /**
     * 重复播放当前句
     */
    private fun repeatCurrentSentence() {
        val state = _state.value
        val sentence = state.currentSentence ?: return

        _state.value = state.copy(currentRepeat = state.currentRepeat + 1)
        audioPlayer.seekTo(sentence.startTime)
        audioPlayer.play()
    }

    /**
     * 取消间隔
     */
    private fun cancelInterval() {
        intervalJob?.cancel()
        intervalJob = null
        _state.value = _state.value.copy(isInInterval = false, intervalCountdown = 0)
    }

    /**
     * 开始位置更新
     */
    private fun startPositionUpdate() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                audioPlayer.updatePosition()
                delay(100)
            }
        }
    }

    /**
     * 停止位置更新
     */
    private fun stopPositionUpdate() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    /**
     * 释放资源
     */
    fun release() {
        scope.cancel()
        audioPlayer.release()
    }
}
