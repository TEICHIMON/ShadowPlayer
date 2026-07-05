package com.example.shadowplayer.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentencePlayerStateTest {

    @Test
    fun intervalKeepsSessionActiveWithoutAudioOutput() {
        val state = SentencePlayerState(
            isPlaying = true,
            isAudioPlaying = false,
            isInInterval = true
        )

        assertTrue(state.isSessionActive)
        assertFalse(state.isAudioPlaying)
    }

    @Test
    fun stoppedStateIsNotSessionActive() {
        val state = SentencePlayerState(
            isPlaying = false,
            isAudioPlaying = false,
            isInInterval = false
        )

        assertFalse(state.isSessionActive)
    }
}
