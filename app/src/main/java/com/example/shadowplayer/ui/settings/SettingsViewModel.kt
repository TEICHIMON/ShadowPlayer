package com.example.shadowplayer.ui.settings

import androidx.lifecycle.ViewModel
import com.example.shadowplayer.player.PlaybackSettings
import com.example.shadowplayer.player.SentencePlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sentencePlayer: SentencePlayer
) : ViewModel() {

    // 暴露当前播放设置
    val settings: StateFlow<PlaybackSettings> = sentencePlayer.settings

    /**
     * 设置播放速度
     */
    fun setSpeed(speed: Float) {
        sentencePlayer.setSpeed(speed)
    }

    /**
     * 设置重复次数
     */
    fun setRepeatCount(count: Int) {
        sentencePlayer.setRepeatCount(count)
    }

    /**
     * 设置跟读间隔
     */
    fun setRepeatInterval(interval: Long) {
        sentencePlayer.setRepeatInterval(interval)
    }

    /**
     * 切换字幕显示
     */
    fun toggleSubtitle() {
        sentencePlayer.toggleSubtitle()
    }

    /**
     * 设置自动播放下一句
     */
    fun setAutoNext(enabled: Boolean) {
        val current = settings.value
        sentencePlayer.updateSettings(current.copy(autoNext = enabled))
    }

    /**
     * 设置快进快退间隔
     */
    fun setSeekInterval(interval: Long) {
        sentencePlayer.setSeekInterval(interval)
    }

    /**
     * 设置睡眠定时
     */
    fun setSleepTimerMinutes(minutes: Int) {
        sentencePlayer.setSleepTimerMinutes(minutes)
    }

    /**
     * 设置音量键控制开关
     */
    fun setVolumeKeyEnabled(enabled: Boolean) {
        sentencePlayer.setVolumeKeyEnabled(enabled)
    }
}
