package com.example.shadowplayer.ui.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.player.AudioOutputRoute
import com.example.shadowplayer.player.AudioRouteManager
import com.example.shadowplayer.player.PlaybackCoordinator
import com.example.shadowplayer.player.PlaybackSettings
import com.example.shadowplayer.player.SentencePlayer
import com.example.shadowplayer.player.SentencePlayerState
import com.example.shadowplayer.player.SystemVolumeController
import com.example.shadowplayer.player.SystemVolumeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val sentencePlayer: SentencePlayer,
    private val playbackCoordinator: PlaybackCoordinator,
    private val audioRouteManager: AudioRouteManager,
    private val systemVolumeController: SystemVolumeController,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val currentAudioFile: StateFlow<AudioFile?> = playbackCoordinator.currentAudioFile

    val playerState: StateFlow<SentencePlayerState> = sentencePlayer.state
    val settings: StateFlow<PlaybackSettings> = sentencePlayer.settings
    val systemVolume: StateFlow<SystemVolumeState> = systemVolumeController.volume

    // 播放列表状态
    val playlist: StateFlow<List<AudioFile>> = sentencePlayer.playlist
    val currentPlaylistIndex: StateFlow<Int> = sentencePlayer.currentPlaylistIndex
    val audioOutputRoute: StateFlow<AudioOutputRoute> = audioRouteManager.currentRoute

    init {
        syncPlaybackState()
        val audioId = savedStateHandle.get<Long>("audioId") ?: -1L
        if (audioId > 0) {
            // 从列表点击进来，带有 audioId
            if (sentencePlayer.isPlayingAudio(audioId)) {
                // 已经在播放这首音频，只需同步 UI 状态
                viewModelScope.launch {
                    val audioFile = playbackCoordinator.syncCurrentAudioFile()
                    sentencePlayer.syncFromPlayer()
                    // 确保播放列表已设置
                    if (audioFile != null && sentencePlayer.playlist.value.isEmpty()) {
                        playbackCoordinator.ensurePlaylistForAudio(audioFile)
                    }
                }
            } else {
                // 不是同一首，正常加载
                loadAudioById(audioId)
            }
        } else {
            // 直接点击Tab进入播放页面
            restoreLastPlayedAudio()
        }
    }

    private fun restoreLastPlayedAudio() {
        // 检查 SentencePlayer 是否已有正在播放的音频
        val currentPlayingId = sentencePlayer.getCurrentAudioId()
        if (currentPlayingId > 0) {
            // 已有正在播放的音频，同步 UI 状态并确保播放列表存在
            viewModelScope.launch {
                val audioFile = playbackCoordinator.syncCurrentAudioFile()
                sentencePlayer.syncFromPlayer()
                // 如果播放列表为空，重新构建
                if (audioFile != null && sentencePlayer.playlist.value.isEmpty()) {
                    playbackCoordinator.ensurePlaylistForAudio(audioFile)
                }
            }
            return
        }

        // 没有正在播放的，尝试恢复上次播放的音频
        val lastAudioId = sentencePlayer.getLastPlayedAudioId()
        if (lastAudioId > 0) {
            loadAudioById(lastAudioId)
        }
    }

    private fun loadAudioById(audioId: Long) {
        viewModelScope.launch {
            playbackCoordinator.loadAudioById(audioId)
        }
    }

    // 句子控制
    fun togglePlayPause() = sentencePlayer.togglePlayPause()
    fun nextSentence() = sentencePlayer.nextSentence()
    fun previousSentence() = sentencePlayer.previousSentence()
    fun seekToSentence(index: Int) = sentencePlayer.seekToSentence(index)
    fun seekTo(position: Long) = sentencePlayer.seekTo(position)

    // 快进快退
    fun seekForward() = sentencePlayer.seekForward()
    fun seekBackward() = sentencePlayer.seekBackward()

    // 设置
    fun setSpeed(speed: Float) = sentencePlayer.setSpeed(speed)
    fun setSystemVolume(percent: Float) = systemVolumeController.setVolumePercent(percent)
    fun setPlayerVolume(volume: Float) = sentencePlayer.setVolume(volume)
    fun setRepeatCount(count: Int) = sentencePlayer.setRepeatCount(count)
    fun setRepeatInterval(interval: Long) = sentencePlayer.setRepeatInterval(interval)
    fun setSleepTimerMinutes(minutes: Int) = sentencePlayer.setSleepTimerMinutes(minutes)
    fun toggleSubtitle() = sentencePlayer.toggleSubtitle()
    fun setAutoNext(enabled: Boolean) {
        val current = settings.value
        sentencePlayer.updateSettings(current.copy(autoNext = enabled))
    }

    // ===== 上一首/下一首功能 =====

    /**
     * 是否可以播放上一首
     */
    fun canPlayPrevious(): Boolean = sentencePlayer.canPlayPrevious()

    /**
     * 是否可以播放下一首
     */
    fun canPlayNext(): Boolean = sentencePlayer.canPlayNext()

    /**
     * 播放上一首
     */
    fun playPrevious() {
        playbackCoordinator.playPreviousAudio()
    }

    /**
     * 播放下一首
     */
    fun playNext() {
        playbackCoordinator.playNextAudio()
    }

    /**
     * 设置播放列表（从 LibraryScreen 调用）
     */
    fun setPlaylist(audioFiles: List<AudioFile>, currentIndex: Int) {
        sentencePlayer.setPlaylist(audioFiles, currentIndex)
    }

    // ===== 音量键控制逻辑 =====

    /**
     * 处理音量键上（根据当前状态决定行为）
     */
    fun handleVolumeUp() {
        val state = playerState.value
        val showSubtitle = settings.value.showSubtitle
        // 有字幕且显示字幕：上一句；否则：快退
        if (state.hasSentences && showSubtitle) {
            previousSentence()
        } else {
            seekBackward()
        }
    }

    /**
     * 处理音量键下（根据当前状态决定行为）
     */
    fun handleVolumeDown() {
        val state = playerState.value
        val showSubtitle = settings.value.showSubtitle
        // 有字幕且显示字幕：下一句；否则：快进
        if (state.hasSentences && showSubtitle) {
            nextSentence()
        } else {
            seekForward()
        }
    }

    /**
     * 音量键控制是否启用
     */
    fun isVolumeKeyEnabled(): Boolean = settings.value.volumeKeyEnabled

    fun syncPlaybackState() {
        sentencePlayer.syncFromPlayer()
        systemVolumeController.refresh()
        audioRouteManager.refreshRoute()
    }

    fun showOutputSwitcher(context: Context) = audioRouteManager.showOutputSwitcher(context)
}
