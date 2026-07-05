package com.example.shadowplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackSettingsTest {

    @Test
    fun defaultsKeepSystemVolumeKeysAndSleepTimerOff() {
        val settings = PlaybackSettings()

        assertFalse(settings.volumeKeyEnabled)
        assertEquals(0, settings.sleepTimerMinutes)
    }

    @Test
    fun normalizesSleepTimerOptions() {
        assertEquals(0, PlaybackSettings.normalizeSleepTimerMinutes(0))
        assertEquals(30, PlaybackSettings.normalizeSleepTimerMinutes(30))
        assertEquals(0, PlaybackSettings.normalizeSleepTimerMinutes(-1))
        assertEquals(0, PlaybackSettings.normalizeSleepTimerMinutes(7))
    }

    @Test
    fun formatsSleepTimerLabels() {
        assertEquals("关闭", PlaybackSettings.sleepTimerLabel(0))
        assertEquals("30分钟", PlaybackSettings.sleepTimerLabel(30))
        assertEquals("关闭", PlaybackSettings.sleepTimerLabel(7))
    }
}
