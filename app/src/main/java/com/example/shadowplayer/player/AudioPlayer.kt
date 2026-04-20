package com.example.shadowplayer.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var exoPlayer: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    // 供 SentencePlayer 监听位置变化
    var onPositionChanged: ((Long) -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null
    // [新增] seek 真正完成的回调，用于精准清除 isSeeking 标志
    var onSeekCompleted: (() -> Unit)? = null

    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(context).build().also { player ->
            exoPlayer = player
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        // 修复 00:00 问题：准备好后立即更新时长
                        _duration.value = player.duration
                    } else if (playbackState == Player.STATE_ENDED) {
                        onPlaybackEnded?.invoke()
                    }
                    _isPlaying.value = player.isPlaying
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player error: ${error.message}")
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    // 仅关心 SEEK 相关的不连续事件
                    if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                        reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                        _currentPosition.value = newPosition.positionMs
                        onSeekCompleted?.invoke()
                    }
                }
            })
        }
    }

    fun loadAudio(path: String) {
        try {
            val player = getOrCreatePlayer()
            player.playWhenReady = false

            // [修复] 加载新音频时重置 duration，避免显示上一首的时长
            _duration.value = 0L
            _currentPosition.value = 0L

            val mediaItem = MediaItem.fromUri(Uri.parse(path))
            player.setMediaItem(mediaItem)
            player.prepare()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading audio: ${e.message}")
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _currentPosition.value = position
        onPositionChanged?.invoke(position)
    }

    fun setSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }

    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }

    fun updatePosition() {
        exoPlayer?.let { player ->
            val current = player.currentPosition
            _currentPosition.value = current
            onPositionChanged?.invoke(current)
        }
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        _isPlaying.value = false
    }
}