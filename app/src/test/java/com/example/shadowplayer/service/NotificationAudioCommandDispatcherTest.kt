package com.example.shadowplayer.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAudioCommandDispatcherTest {

    @Test
    fun dispatchRoutesNotificationAudioActionsOnly() {
        val received = mutableListOf<String>()
        val dispatcher = NotificationAudioCommandDispatcher(
            previousAudioAction = { received += "previous-audio" },
            nextAudioAction = { received += "next-audio" }
        )

        assertTrue(dispatcher.dispatch(PlaybackNotificationActions.ACTION_PREVIOUS_AUDIO))
        assertTrue(dispatcher.dispatch(PlaybackNotificationActions.ACTION_NEXT_AUDIO))
        assertFalse(dispatcher.dispatch("unknown"))

        assertEquals(listOf("previous-audio", "next-audio"), received)
    }
}
