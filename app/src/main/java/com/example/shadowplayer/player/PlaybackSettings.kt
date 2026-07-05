package com.example.shadowplayer.player

/**
 * 播放设置
 */
data class PlaybackSettings(
    val speed: Float = 1.0f,           // 播放速度: 0.5 ~ 2.0
    val volume: Float = 1.0f,          // 应用内音量: 0.0 ~ 1.0
    val repeatCount: Int = 1,          // 每句重复次数: 1 ~ 10
    val repeatInterval: Long = 2000,   // 重复间隔(毫秒): 0 ~ 10000, 用于跟读
    val autoNext: Boolean = true,      // 自动播放下一句
    val showSubtitle: Boolean = true,  // 显示字幕
    val sleepTimerMinutes: Int = 0,    // 睡眠定时(分钟): 0表示关闭
    val seekInterval: Long = 10000,    // 快进快退间隔(毫秒): 5000, 10000, 15000, 30000
    val volumeKeyEnabled: Boolean = false  // 音量键控制开关
) {
    companion object {
        val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val REPEAT_OPTIONS = (1..10).toList()
        val INTERVAL_OPTIONS = listOf(0L, 1000L, 2000L, 3000L, 5000L, 8000L, 10000L)
        val SLEEP_TIMER_OPTIONS = listOf(0, 5, 10, 15, 30, 45, 60)
        val SEEK_INTERVAL_OPTIONS = listOf(5000L, 10000L, 15000L, 30000L)

        fun normalizeSleepTimerMinutes(minutes: Int): Int =
            if (minutes in SLEEP_TIMER_OPTIONS) minutes else 0

        fun sleepTimerLabel(minutes: Int): String {
            val normalizedMinutes = normalizeSleepTimerMinutes(minutes)
            return if (normalizedMinutes == 0) "关闭" else "${normalizedMinutes}分钟"
        }
    }
}

/**
 * 分句播放状态
 */
data class SentencePlayerState(
    val sentences: List<LrcSentence> = emptyList(),
    val currentIndex: Int = 0,
    val currentRepeat: Int = 1,      // 当前是第几遍
    val isPlaying: Boolean = false,
    val isAudioPlaying: Boolean = false,
    val isInInterval: Boolean = false,  // 是否在跟读间隔中
    val intervalCountdown: Int = 0,     // 倒计时秒数
    val currentPosition: Long = 0,      // 当前播放位置
    val totalDuration: Long = 0         // 总时长
) {
    val currentSentence: LrcSentence?
        get() = sentences.getOrNull(currentIndex)

    val progress: Float
        get() = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f

    val hasSentences: Boolean
        get() = sentences.isNotEmpty()

    val isSessionActive: Boolean
        get() = isPlaying || isInInterval
}
