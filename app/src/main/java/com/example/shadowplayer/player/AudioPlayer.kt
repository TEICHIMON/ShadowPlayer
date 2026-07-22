package com.example.shadowplayer.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AudioPlayerSnapshot(
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val mediaItemCount: Int = 0
) {
    val hasMedia: Boolean
        get() = mediaItemCount > 0

    val isPlaybackRequested: Boolean
        get() = isPlaying || (
            playWhenReady &&
                playbackState != Player.STATE_ENDED &&
                playbackState != Player.STATE_IDLE
            )
}

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var exoPlayer: ExoPlayer? = null
    private var volume: Float = 1.0f
    private var currentTitle: String = ""
    private var currentNotificationSubtitle: String? = null

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
    var onExternalPause: (() -> Unit)? = null

    /**
     * PlaybackService 和页面播放逻辑必须共享同一个播放器实例。
     */
    val player: ExoPlayer
        get() = getOrCreatePlayer()

    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { player ->
                exoPlayer = player
                player.volume = volume
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            // 修复 00:00 问题：准备好后立即更新时长
                            _duration.value = resolvedDuration(player)
                        } else if (playbackState == Player.STATE_ENDED) {
                            _currentPosition.value = resolvedDuration(player)
                            onPlaybackEnded?.invoke()
                        }
                        _isPlaying.value = player.isPlaying
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (!playWhenReady && (
                                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                                )
                        ) {
                            onExternalPause?.invoke()
                        }
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
                            reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                        ) {
                            _currentPosition.value = newPosition.positionMs
                            onSeekCompleted?.invoke()
                        }
                    }
                })
            }
    }

    fun loadAudio(path: String, mediaId: Long, title: String) {
        try {
            val player = getOrCreatePlayer()
            player.playWhenReady = false
            currentTitle = title
            currentNotificationSubtitle = null

            // [修复] 加载新音频时重置 duration，避免显示上一首的时长
            _duration.value = 0L
            _currentPosition.value = 0L

            val mediaItem = MediaItem.Builder()
                .setMediaId(mediaId.takeIf { it > 0 }?.toString() ?: path)
                .setUri(Uri.parse(path))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(currentNotificationSubtitle)
                        .setSubtitle(currentNotificationSubtitle)
                        .setDescription(currentNotificationSubtitle)
                        .build()
                )
                .build()
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

    fun setVolume(volume: Float) {
        val boundedVolume = volume.coerceIn(0f, 1f)
        this.volume = boundedVolume
        exoPlayer?.volume = boundedVolume
    }

    fun updateNotificationSubtitle(subtitle: String?) {
        val player = exoPlayer ?: return
        val currentItem = player.currentMediaItem ?: return
        if (currentNotificationSubtitle == subtitle) return
        currentNotificationSubtitle = subtitle

        val mediaMetadata = currentItem.mediaMetadata
            .buildUpon()
            .setTitle(currentTitle.ifBlank { mediaMetadataTitle(currentItem) })
            .setArtist(subtitle)
            .setSubtitle(subtitle)
            .setDescription(subtitle)
            .build()
        val updatedItem = currentItem.buildUpon()
            .setMediaMetadata(mediaMetadata)
            .build()
        player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
    }

    fun getDuration(): Long {
        return exoPlayer?.let(::resolvedDuration) ?: 0L
    }

    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
    }

    fun hasMedia(): Boolean {
        return getSnapshot().hasMedia
    }

    fun getSnapshot(): AudioPlayerSnapshot {
        val player = exoPlayer ?: return AudioPlayerSnapshot()
        return AudioPlayerSnapshot(
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            playbackState = player.playbackState,
            currentPosition = player.currentPosition.coerceAtLeast(0L),
            duration = resolvedDuration(player),
            mediaItemCount = player.mediaItemCount
        )
    }

    fun updatePosition() {
        exoPlayer?.let { player ->
            val current = player.currentPosition.coerceAtLeast(0L)
            _currentPosition.value = current
            onPositionChanged?.invoke(current)
        }
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    private fun resolvedDuration(player: Player): Long {
        val duration = player.duration
        return if (duration > 0 && duration != C.TIME_UNSET) duration else 0L
    }

    private fun mediaMetadataTitle(mediaItem: MediaItem): String {
        return mediaItem.mediaMetadata.title?.toString().orEmpty()
    }
}
