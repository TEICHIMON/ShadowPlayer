package com.example.shadowplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackSettingsTest {

    @Test
    fun defaultsKeepSystemVolumeKeysAndSleepTimerOff() {
        val settings = PlaybackSettings()

        assertEquals(1.0f, settings.volume, 0.0001f)
        assertFalse(settings.volumeKeyEnabled)
        assertEquals(0, settings.sleepTimerMinutes)
    }

    @Test
    fun normalizesPlayerVolumeToUnitRange() {
        assertEquals(0f, PlaybackSettings.normalizeVolume(-0.5f), 0.0001f)
        assertEquals(0.5f, PlaybackSettings.normalizeVolume(0.5f), 0.0001f)
        assertEquals(1f, PlaybackSettings.normalizeVolume(1.5f), 0.0001f)
        assertEquals(1f, PlaybackSettings.normalizeVolume(Float.NaN), 0.0001f)
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
