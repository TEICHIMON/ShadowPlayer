package com.example.shadowplayer.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class SystemVolumeState(
    val currentVolume: Int = 0,
    val maxVolume: Int = 1
) {
    val percent: Float
        get() = SystemVolumeController.percentOf(currentVolume, maxVolume)
}

@Singleton
class SystemVolumeController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val _volume = MutableStateFlow(readVolumeState())
    val volume: StateFlow<SystemVolumeState> = _volume.asStateFlow()

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_VOLUME_CHANGED) return
            val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_MUSIC)
            if (streamType == AudioManager.STREAM_MUSIC) {
                refresh()
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_VOLUME_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(volumeReceiver, filter)
        }
        refresh()
    }

    fun refresh() {
        _volume.value = readVolumeState()
    }

    fun setVolumePercent(percent: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val targetVolume = volumeFromPercent(percent, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        refresh()
    }

    private fun readVolumeState(): SystemVolumeState {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val currentVolume = audioManager
            .getStreamVolume(AudioManager.STREAM_MUSIC)
            .coerceIn(0, maxVolume)
        return SystemVolumeState(currentVolume, maxVolume)
    }

    companion object {
        private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

        fun percentOf(currentVolume: Int, maxVolume: Int): Float {
            val boundedMax = maxVolume.coerceAtLeast(1)
            return (currentVolume.toFloat() / boundedMax).coerceIn(0f, 1f)
        }

        fun volumeFromPercent(percent: Float, maxVolume: Int): Int {
            val boundedMax = maxVolume.coerceAtLeast(1)
            return (percent.coerceIn(0f, 1f) * boundedMax).roundToInt().coerceIn(0, boundedMax)
        }
    }
}
