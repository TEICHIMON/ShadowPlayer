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

    var onPositionChanged: ((Long) -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null

    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(context).build().also { player ->
            exoPlayer = player
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d(TAG, "onIsPlayingChanged: $isPlaying")
                    _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.d(TAG, "onPlaybackStateChanged: $playbackState")
                    if (playbackState == Player.STATE_ENDED) {
                        onPlaybackEnded?.invoke()
                    }
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = player.duration
                        Log.d(TAG, "Duration: ${player.duration}")
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player error: ${error.message}", error)
                    Log.e(TAG, "Error code: ${error.errorCode}")
                }
            })
        }
    }

    fun loadAudio(path: String) {
        Log.d(TAG, "Loading audio: $path")
        try {
            val player = getOrCreatePlayer()
            val uri = Uri.parse(path)
            Log.d(TAG, "Parsed URI: $uri")

            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            Log.d(TAG, "Audio prepared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading audio", e)
        }
    }

    fun play() {
        Log.d(TAG, "Play called")
        exoPlayer?.play()
    }

    fun pause() {
        Log.d(TAG, "Pause called")
        exoPlayer?.pause()
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _currentPosition.value = position
    }

    fun setSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0
    }

    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0
    }

    fun updatePosition() {
        val position = getCurrentPosition()
        _currentPosition.value = position
        onPositionChanged?.invoke(position)
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}