package com.example.shadowplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCommandDispatcherTest {

    @Test
    fun routesEveryMediaCommandToSentencePlayback() {
        val received = mutableListOf<String>()
        val dispatcher = MediaCommandDispatcher(
            playAction = { received += "play" },
            pauseAction = { received += "pause" },
            seekAction = { received += "seek:$it" },
            previousAction = { received += "previous" },
            nextAction = { received += "next" },
            seekBackwardAction = { received += "backward" },
            seekForwardAction = { received += "forward" }
        )

        dispatcher.play()
        dispatcher.pause()
        dispatcher.setPlayWhenReady(true)
        dispatcher.setPlayWhenReady(false)
        dispatcher.seekTo(12_345L)
        dispatcher.previous()
        dispatcher.next()
        dispatcher.seekBackward()
        dispatcher.seekForward()
        dispatcher.stop()

        assertEquals(
            listOf(
                "play",
                "pause",
                "play",
                "pause",
                "seek:12345",
                "previous",
                "next",
                "backward",
                "forward",
                "pause"
            ),
            received
        )
    }
}
