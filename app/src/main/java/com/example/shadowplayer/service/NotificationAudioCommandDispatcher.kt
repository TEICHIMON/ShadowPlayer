package com.example.shadowplayer.service

internal object PlaybackNotificationActions {
    const val ACTION_PREVIOUS_AUDIO = "com.example.shadowplayer.action.PREVIOUS_AUDIO"
    const val ACTION_NEXT_AUDIO = "com.example.shadowplayer.action.NEXT_AUDIO"
}

internal class NotificationAudioCommandDispatcher(
    private val previousAudioAction: () -> Unit,
    private val nextAudioAction: () -> Unit
) {
    fun dispatch(action: String): Boolean {
        return when (action) {
            PlaybackNotificationActions.ACTION_PREVIOUS_AUDIO -> {
                previousAudioAction()
                true
            }
            PlaybackNotificationActions.ACTION_NEXT_AUDIO -> {
                nextAudioAction()
                true
            }
            else -> false
        }
    }
}
