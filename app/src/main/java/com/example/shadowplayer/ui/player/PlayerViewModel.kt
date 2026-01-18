package com.example.shadowplayer.ui.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.repository.AudioRepository
import com.example.shadowplayer.player.PlaybackSettings
import com.example.shadowplayer.player.SentencePlayer
import com.example.shadowplayer.player.SentencePlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val sentencePlayer: SentencePlayer,
    private val repository: AudioRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _currentAudioFile = MutableStateFlow<AudioFile?>(null)
    val currentAudioFile: StateFlow<AudioFile?> = _currentAudioFile.asStateFlow()

    val playerState: StateFlow<SentencePlayerState> = sentencePlayer.state
    val settings: StateFlow<PlaybackSettings> = sentencePlayer.settings

    // 播放列表状态
    val playlist: StateFlow<List<AudioFile>> = sentencePlayer.playlist
    val currentPlaylistIndex: StateFlow<Int> = sentencePlayer.currentPlaylistIndex

    init {
        val audioId = savedStateHandle.get<Long>("audioId") ?: -1L
        if (audioId > 0) {
            // 从列表点击进来，带有 audioId
            if (sentencePlayer.isPlayingAudio(audioId)) {
                // 已经在播放这首音频，只需同步 UI 状态
                viewModelScope.launch {
                    val audioFile = repository.getAudioById(audioId)
                    _currentAudioFile.value = audioFile
                    // 确保播放列表已设置
                    if (audioFile != null && sentencePlayer.playlist.value.isEmpty()) {
                        setupPlaylistForAudio(audioFile)
                    }
                }
            } else {
                // 不是同一首，正常加载
                loadAudioById(audioId)
            }
        } else {
            // 直接点击Tab进入播放页面
            restoreLastPlayedAudio()
        }
    }

    private fun restoreLastPlayedAudio() {
        // 检查 SentencePlayer 是否已有正在播放的音频
        val currentPlayingId = sentencePlayer.getCurrentAudioId()
        if (currentPlayingId > 0) {
            // 已有正在播放的音频，同步 UI 状态并确保播放列表存在
            viewModelScope.launch {
                val audioFile = repository.getAudioById(currentPlayingId)
                _currentAudioFile.value = audioFile
                // 如果播放列表为空，重新构建
                if (audioFile != null && sentencePlayer.playlist.value.isEmpty()) {
                    setupPlaylistForAudio(audioFile)
                }
            }
            return
        }

        // 没有正在播放的，尝试恢复上次播放的音频
        val lastAudioId = sentencePlayer.getLastPlayedAudioId()
        if (lastAudioId > 0) {
            loadAudioById(lastAudioId)
        }
    }

    private fun loadAudioById(audioId: Long) {
        viewModelScope.launch {
            val audioFile = repository.getAudioById(audioId)
            if (audioFile != null) {
                // 先设置播放列表，再加载音频
                setupPlaylistForAudio(audioFile)
                loadAudio(audioFile)
                // 更新最近播放时间
                repository.updateLastPlayedAt(audioId, System.currentTimeMillis())
            }
        }
    }

    /**
     * 根据当前音频设置播放列表（同文件夹下的所有音频）
     */
    private suspend fun setupPlaylistForAudio(audioFile: AudioFile) {
        val parentPath = getParentFolderPath(audioFile.path)

        // 获取所有音频文件
        val allFiles = repository.getAllAudioFiles().first()

        // 过滤同一父文件夹下的音频并排序
        val playlist = allFiles
            .filter { getParentFolderPath(it.path) == parentPath }
            .sortedBy { it.title.lowercase() }

        val currentIndex = playlist.indexOfFirst { it.id == audioFile.id }

        if (playlist.isNotEmpty() && currentIndex >= 0) {
            sentencePlayer.setPlaylist(playlist, currentIndex)
        }
    }

    /**
     * 从URI路径中提取Document ID
     */
    private fun extractDocumentId(uri: String): String {
        val docMarker = "/document/"
        val docIndex = uri.indexOf(docMarker)
        if (docIndex != -1) {
            return uri.substring(docIndex + docMarker.length)
        }
        val treeMarker = "/tree/"
        val treeIndex = uri.indexOf(treeMarker)
        if (treeIndex != -1) {
            return uri.substring(treeIndex + treeMarker.length)
        }
        return uri
    }

    /**
     * 获取文件的父文件夹路径
     */
    private fun getParentFolderPath(filePath: String): String {
        val docId = extractDocumentId(filePath)
        val decoded = try {
            URLDecoder.decode(docId, "UTF-8")
        } catch (e: Exception) {
            docId
        }
        return decoded.substringBeforeLast("/")
    }

    private fun loadAudio(audioFile: AudioFile) {
        _currentAudioFile.value = audioFile
        viewModelScope.launch {
            val lrcContent = if (!audioFile.lrcPath.isNullOrEmpty()) {
                readLrcContent(audioFile.lrcPath)
            } else null

            sentencePlayer.load(
                audioPath = audioFile.path,
                lrcContent = lrcContent,
                subtitlePath = audioFile.lrcPath,
                audioId = audioFile.id,
                initialPosition = audioFile.lastPosition
            )
            repository.incrementPlayCount(audioFile.id)
        }
    }

    private suspend fun readLrcContent(lrcPath: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(lrcPath)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            }
        } catch (e: Exception) {
            null
        }
    }

    // 句子控制
    fun togglePlayPause() = sentencePlayer.togglePlayPause()
    fun nextSentence() = sentencePlayer.nextSentence()
    fun previousSentence() = sentencePlayer.previousSentence()
    fun seekToSentence(index: Int) = sentencePlayer.seekToSentence(index)
    fun seekTo(position: Long) = sentencePlayer.seekTo(position)

    // 设置
    fun setSpeed(speed: Float) = sentencePlayer.setSpeed(speed)
    fun setRepeatCount(count: Int) = sentencePlayer.setRepeatCount(count)
    fun setRepeatInterval(interval: Long) = sentencePlayer.setRepeatInterval(interval)
    fun toggleSubtitle() = sentencePlayer.toggleSubtitle()
    fun setAutoNext(enabled: Boolean) {
        val current = settings.value
        sentencePlayer.updateSettings(current.copy(autoNext = enabled))
    }

    // ===== 上一首/下一首功能 =====

    /**
     * 是否可以播放上一首
     */
    fun canPlayPrevious(): Boolean = sentencePlayer.canPlayPrevious()

    /**
     * 是否可以播放下一首
     */
    fun canPlayNext(): Boolean = sentencePlayer.canPlayNext()

    /**
     * 播放上一首
     */
    fun playPrevious() {
        val previousAudio = sentencePlayer.getPreviousAudio() ?: return
        loadAudioById(previousAudio.id)
    }

    /**
     * 播放下一首
     */
    fun playNext() {
        val nextAudio = sentencePlayer.getNextAudio() ?: return
        loadAudioById(nextAudio.id)
    }

    /**
     * 设置播放列表（从 LibraryScreen 调用）
     */
    fun setPlaylist(audioFiles: List<AudioFile>, currentIndex: Int) {
        sentencePlayer.setPlaylist(audioFiles, currentIndex)
    }
}