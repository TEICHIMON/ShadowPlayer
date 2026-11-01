package com.example.shadowplayer.player

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.shadowplayer.data.AppDatabase
import com.example.shadowplayer.data.repository.AudioRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * 用真实的 ExoPlayer 验证音频结尾处的句子逻辑。最后一句的句尾就是音频结尾。
 * 音频是静音 WAV，在真机上跑也不会出声。
 */
@RunWith(AndroidJUnit4::class)
class SentencePlayerEndOfAudioTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var database: AppDatabase
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var sentencePlayer: SentencePlayer
    private lateinit var audioFile: File

    @Before
    fun setUp() {
        audioFile = createSilentWav(context, durationMs = 4_000)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = AudioRepository(
            database.audioFileDao(),
            database.tagDao(),
            database.bookmarkDao(),
            database.scanFolderDao()
        )
        val prefs = context.getSharedPreferences("sentence_player_end_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        instrumentation.runOnMainSync {
            audioPlayer = AudioPlayer(context)
            sentencePlayer = SentencePlayer(audioPlayer, prefs, repository)
        }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { sentencePlayer.release() }
        database.close()
        audioFile.delete()
    }

    @Test
    fun lastSentenceStillRepeatsWhenTheAudioEnds() {
        loadWithSettings(PlaybackSettings(repeatCount = 2, repeatInterval = 1_000L))

        instrumentation.runOnMainSync {
            sentencePlayer.seekToSentence(LAST_SENTENCE)
            sentencePlayer.play()
        }

        waitUntil(10_000) { onMain { sentencePlayer.state.value.currentRepeat == 2 } }
        waitUntil(10_000) { onMain { !sentencePlayer.state.value.isPlaying } }
        val state = onMain { sentencePlayer.state.value }
        assertEquals(LAST_SENTENCE, state.currentIndex)
        assertEquals(2, state.currentRepeat)
    }

    @Test
    fun playAfterTheEndRestartsFromTheBeginning() {
        loadWithSettings(PlaybackSettings(repeatCount = 1, repeatInterval = 0L, autoNext = true))

        instrumentation.runOnMainSync {
            sentencePlayer.seekToSentence(LAST_SENTENCE)
            sentencePlayer.play()
        }
        waitUntil(10_000) { onMain { audioPlayer.isEnded() && !sentencePlayer.state.value.isPlaying } }

        instrumentation.runOnMainSync { sentencePlayer.play() }

        waitUntil(5_000) { onMain { audioPlayer.getCurrentPosition() in 300L..1_500L } }
        val state = onMain { sentencePlayer.state.value }
        assertTrue(state.isPlaying)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun restoringAPausedPositionInTheLastSentenceNeverStartsTheInterval() {
        // 加载时时长还是 0，最后一句的句尾等于句首；旧代码会把恢复进度当成"播完一句"，进入间隔后自动播放
        instrumentation.runOnMainSync {
            sentencePlayer.updateSettings(PlaybackSettings(repeatCount = 2, repeatInterval = 1_000L))
            sentencePlayer.load(
                audioPath = Uri.fromFile(audioFile).toString(),
                title = "end of audio test",
                lrcContent = LRC,
                initialPosition = 3_500L
            )
        }

        Thread.sleep(2_500)
        val state = onMain { sentencePlayer.state.value }
        assertFalse(state.isPlaying)
        assertFalse(state.isInInterval)
        assertFalse(onMain { audioPlayer.getSnapshot().playWhenReady })
    }

    private fun loadWithSettings(settings: PlaybackSettings) {
        instrumentation.runOnMainSync {
            sentencePlayer.updateSettings(settings)
            sentencePlayer.load(
                audioPath = Uri.fromFile(audioFile).toString(),
                title = "end of audio test",
                lrcContent = LRC
            )
        }
        waitUntil(10_000) { onMain { sentencePlayer.state.value.totalDuration > 0 } }
    }

    private fun createSilentWav(context: Context, durationMs: Int): File {
        val sampleRate = 8_000
        val dataSize = sampleRate * durationMs / 1_000 * 2
        val file = File(context.cacheDir, "sentence-end-test.wav")
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
            output.write(ByteArray(dataSize))
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

    private fun <T> onMain(block: () -> T): T {
        var value: Result<T>? = null
        instrumentation.runOnMainSync { value = runCatching(block) }
        return value!!.getOrThrow()
    }

    private companion object {
        const val LAST_SENTENCE = 1
        const val LRC = "[00:00.00]First sentence.\n[00:02.00]Last sentence.\n"
    }
}
