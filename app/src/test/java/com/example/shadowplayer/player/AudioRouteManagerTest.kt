package com.example.shadowplayer.player

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRouteManagerTest {

    @Test
    fun mapsBluetoothDeviceTypes() {
        assertEquals(
            AudioOutputType.BLUETOOTH,
            audioOutputTypeForDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        )
        assertEquals(
            AudioOutputType.BLUETOOTH,
            audioOutputTypeForDeviceType(AudioDeviceInfo.TYPE_BLE_HEADSET)
        )
    }

    @Test
    fun mapsWiredDeviceTypes() {
        assertEquals(
            AudioOutputType.WIRED,
            audioOutputTypeForDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        )
        assertEquals(
            AudioOutputType.WIRED,
            audioOutputTypeForDeviceType(AudioDeviceInfo.TYPE_USB_HEADSET)
        )
    }

    @Test
    fun mapsSpeakerAndUnknownDeviceTypes() {
        assertEquals(
            AudioOutputType.SPEAKER,
            audioOutputTypeForDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        )
        assertEquals(AudioOutputType.OTHER, audioOutputTypeForDeviceType(-1))
    }
}
