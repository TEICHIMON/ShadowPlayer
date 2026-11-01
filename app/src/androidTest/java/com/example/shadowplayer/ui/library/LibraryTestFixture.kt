package com.example.shadowplayer.ui.library

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.shadowplayer.data.AppDatabase
import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.entity.ScanFolder
import com.example.shadowplayer.data.repository.AudioRepository
import com.example.shadowplayer.player.AudioPlayer
import com.example.shadowplayer.player.SentencePlayer
import kotlinx.coroutines.runBlocking

/**
 * 文件库测试共用的环境：内存数据库、测试用 SharedPreferences、手动创建的 [LibraryViewModel]。
 * 测试数据的路径不是 content:// 地址，ViewModel 启动时的增量扫描会直接失败，不会删掉这些数据。
 *
 * 文件库结构：
 * Books/Alice/01 Down the Rabbit-Hole（有字幕）
 * Books/Alice/02 The Pool of Tears
 * Books/Alice/Extras/03 Rabbit interview（有字幕，播放过）
 * Books/Oz/01 The Cyclone
 */
internal class LibraryTestFixture(prefsName: String) {

    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context: Context = instrumentation.targetContext
    private val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val repository = AudioRepository(
        database.audioFileDao(),
        database.tagDao(),
        database.bookmarkDao(),
        database.scanFolderDao()
    )
    private val prefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var sentencePlayer: SentencePlayer
    private val viewModelStores = mutableListOf<ViewModelStore>()

    init {
        prefs.edit().clear().commit()
        instrumentation.runOnMainSync {
            audioPlayer = AudioPlayer(context)
            sentencePlayer = SentencePlayer(audioPlayer, prefs, repository)
        }
        runBlocking {
            repository.insertScanFolder(ScanFolder(path = ROOT_ID, name = "Books"))
            repository.insertAllAudio(
                listOf(
                    audio(1, "Alice/01 Down the Rabbit-Hole", subtitle = true),
                    audio(2, "Alice/02 The Pool of Tears"),
                    audio(3, "Alice/Extras/03 Rabbit interview", subtitle = true, playCount = 2),
                    audio(4, "Oz/01 The Cyclone")
                )
            )
        }
    }

    fun newViewModel(): LibraryViewModel {
        val store = ViewModelStore().also { viewModelStores += it }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LibraryViewModel(repository, sentencePlayer, context, prefs) as T
        }
        return onMain { ViewModelProvider(store, factory)[LibraryViewModel::class.java] }
    }

    /** 清掉所有 ViewModel，相当于退出 App。 */
    fun closeViewModels() = onMain {
        viewModelStores.forEach { it.clear() }
        viewModelStores.clear()
    }

    fun release() {
        closeViewModels()
        instrumentation.runOnMainSync { sentencePlayer.release() }
        database.close()
        prefs.edit().clear().commit()
    }

    fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun audio(id: Long, relativePath: String, subtitle: Boolean = false, playCount: Int = 0): AudioFile {
        val docId = "$ROOT_ID%2F" + relativePath.split("/").joinToString("%2F") { it.replace(" ", "%20") } + ".mp3"
        return AudioFile(
            id = id,
            path = docId,
            title = relativePath.substringAfterLast("/"),
            lrcPath = if (subtitle) docId.removeSuffix(".mp3") + ".lrc" else null,
            playCount = playCount
        )
    }

    companion object {
        const val ROOT_ID = "books"
        const val ALICE_ID = "$ROOT_ID%2FAlice"
    }
}
