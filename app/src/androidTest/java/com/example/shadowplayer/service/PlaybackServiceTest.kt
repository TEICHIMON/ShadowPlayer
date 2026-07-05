package com.example.shadowplayer.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {

    @Test
    fun controllerConnectsPublishesMetadataAndAdvancesPlayback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val audioFile = createTestWav(context)
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)

        try {
            instrumentation.runOnMainSync {
                assertTrue(controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
                assertTrue(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
                assertTrue(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))

                controller.setMediaItem(
                    MediaItem.Builder()
                        .setMediaId("integration-test")
                        .setUri(Uri.fromFile(audioFile))
                        .setMediaMetadata(
                            MediaMetadata.Builder().setTitle("Bluetooth test audio").build()
                        )
                        .build()
                )
                controller.prepare()
                controller.play()
            }

            waitUntil(10_000) {
                onMain(instrumentation) { controller.isPlaying && controller.currentPosition > 200 }
            }

            instrumentation.runOnMainSync {
                assertEquals("integration-test", controller.currentMediaItem?.mediaId)
                assertEquals("Bluetooth test audio", controller.mediaMetadata.title)
                assertTrue(controller.currentPosition > 200)
                controller.pause()
            }
        } finally {
            instrumentation.runOnMainSync { controller.release() }
            audioFile.delete()
        }
    }

    private fun createTestWav(context: Context): File {
        val sampleRate = 8_000
        val durationSeconds = 2
        val sampleCount = sampleRate * durationSeconds
        val dataSize = sampleCount * 2
        val file = File(context.cacheDir, "media-session-test.wav")
        FileOutputStream(file).use { output ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt(36 + dataSize)
                put("WAVEfmt ".toByteArray())
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(sampleRate)
                putInt(sampleRate * 2)
                putShort(2)
                putShort(16)
                put("data".toByteArray())
                putInt(dataSize)
            }
            output.write(header.array())
            val samples = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            repeat(sampleCount) { index ->
                val sample = (Short.MAX_VALUE * 0.15 *
                    kotlin.math.sin(2.0 * Math.PI * 440.0 * index / sampleRate)).toInt()
                samples.putShort(sample.toShort())
            }
            output.write(samples.array())
        }
        return file
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(50)
        }
        assertTrue("Condition was not met within ${timeoutMs}ms", condition())
    }

    private fun <T> onMain(
        instrumentation: android.app.Instrumentation,
        block: () -> T
    ): T {
        var value: Result<T>? = null
        instrumentation.runOnMainSync { value = runCatching(block) }
        return value!!.getOrThrow()
    }
}
