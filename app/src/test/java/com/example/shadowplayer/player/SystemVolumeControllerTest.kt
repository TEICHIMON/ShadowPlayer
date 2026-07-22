package com.example.shadowplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemVolumeControllerTest {

    @Test
    fun percentOfBoundsCurrentVolumeToMediaVolumeRange() {
        assertEquals(0f, SystemVolumeController.percentOf(-1, 10), 0.0001f)
        assertEquals(0.5f, SystemVolumeController.percentOf(5, 10), 0.0001f)
        assertEquals(1f, SystemVolumeController.percentOf(15, 10), 0.0001f)
        assertEquals(1f, SystemVolumeController.percentOf(1, 0), 0.0001f)
    }

    @Test
    fun volumeFromPercentRoundsAndBoundsToStreamRange() {
        assertEquals(0, SystemVolumeController.volumeFromPercent(-0.5f, 15))
        assertEquals(8, SystemVolumeController.volumeFromPercent(0.5f, 15))
        assertEquals(15, SystemVolumeController.volumeFromPercent(1.5f, 15))
        assertEquals(1, SystemVolumeController.volumeFromPercent(1f, 0))
    }
}
