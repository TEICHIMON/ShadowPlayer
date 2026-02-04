package com.example.shadowplayer.ui.player

import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.widget.TextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.shadowplayer.MainActivity
import com.example.shadowplayer.player.LrcParser
import com.example.shadowplayer.player.LrcSentence
import com.example.shadowplayer.player.PlaybackSettings

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentAudioFile by viewModel.currentAudioFile.collectAsState()
    val playlist by viewModel.playlist.collectAsState()
    val currentPlaylistIndex by viewModel.currentPlaylistIndex.collectAsState()

    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }

    val displayPosition = if (isDragging) dragPosition else playerState.currentPosition
    val targetIndex = if (isDragging) {
        if (playerState.sentences.isNotEmpty()) LrcParser.findSentenceIndex(playerState.sentences, dragPosition) else -1
    } else playerState.currentIndex

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? MainActivity
        activity?.onVolumeUp = {
            if (viewModel.isVolumeKeyEnabled()) {
                viewModel.handleVolumeUp()
            }
            viewModel.isVolumeKeyEnabled() // 返回是否拦截音量键
        }
        activity?.onVolumeDown = {
            if (viewModel.isVolumeKeyEnabled()) {
                viewModel.handleVolumeDown()
            }
            viewModel.isVolumeKeyEnabled() // 返回是否拦截音量键
        }
        onDispose {
            activity?.onVolumeUp = null
            activity?.onVolumeDown = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // [修复] 标题添加滚动效果
        Text(
            text = currentAudioFile?.title ?: "未选择音频",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(),
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(8.dp))

        // [问题1修复] 字幕区域使用 weight(1f)，下方控件紧凑排列
        if (settings.showSubtitle && playerState.sentences.isNotEmpty()) {
            SubtitleList(
                sentences = playerState.sentences,
                currentIndex = targetIndex,
                onSentenceClick = { viewModel.seekToSentence(it) },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val currentText = if (targetIndex in playerState.sentences.indices) playerState.sentences[targetIndex].text else null
                if (currentText != null && settings.showSubtitle) {
                    SelectableTextView(
                        text = currentText,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                } else {
                    Text(
                        text = if (currentAudioFile == null) "请从文件库选择音频" else "无字幕",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 跟读间隔倒计时提示
        if (playerState.isInInterval) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = "请跟读 (${playerState.intervalCountdown}秒)",
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // 底部控制区域容器
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp) // 紧凑排列
        ) {
            // 进度条
            ProgressSection(
                currentPosition = displayPosition,
                totalDuration = playerState.totalDuration,
                onSeek = { position -> viewModel.seekTo(position); isDragging = false },
                onPreview = { position -> isDragging = true; dragPosition = position }
            )

            // 主要播放控制 (上一句, 播放/暂停, 下一句)
            PlaybackControls(
                isPlaying = playerState.isPlaying,
                onPlayPause = { viewModel.togglePlayPause() },
                onPrevious = { viewModel.previousSentence() },
                onNext = { viewModel.nextSentence() }
            )

            // [问题1修复] 合并 次要控制 (上一首, 快退, 快进, 下一首)
            SecondaryControls(
                seekInterval = settings.seekInterval,
                onSeekBackward = { viewModel.seekBackward() },
                onSeekForward = { viewModel.seekForward() },
                canPlayPrevious = viewModel.canPlayPrevious(),
                canPlayNext = viewModel.canPlayNext(),
                onPreviousTrack = { viewModel.playPrevious() },
                onNextTrack = { viewModel.playNext() },
                currentIndex = currentPlaylistIndex,
                totalTracks = playlist.size
            )

            // 底部设置栏
            SettingsBar(
                settings = settings,
                currentRepeat = playerState.currentRepeat,
                onSpeedChange = { viewModel.setSpeed(it) },
                onRepeatCountChange = { viewModel.setRepeatCount(it) },
                onIntervalChange = { viewModel.setRepeatInterval(it) },
                onToggleSubtitle = { viewModel.toggleSubtitle() }
            )
        }
    }
}

@Composable
fun SelectableTextView(
    text: String,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val textSize = 20f

    AndroidView(
        factory = { context ->
            TextView(context).apply {
                setTextColor(textColor)
                this.textSize = textSize
                gravity = Gravity.CENTER
                setTextIsSelectable(true)
                maxLines = 10
                ellipsize = TextUtils.TruncateAt.END
            }
        },
        update = { textView ->
            textView.text = text
            textView.setTextColor(textColor)
        },
        modifier = modifier
    )
}

@Composable
fun SubtitleList(
    sentences: List<LrcSentence>,
    currentIndex: Int,
    onSentenceClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < sentences.size) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val itemHeight = with(density) { 56.dp.toPx() }.toInt()
            val centerOffset = (viewportHeight / 2) - (itemHeight / 2)

            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -centerOffset
            )
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val highlightTextColor = MaterialTheme.colorScheme.primary.toArgb()
    val highlightBgColor = MaterialTheme.colorScheme.primaryContainer
    val normalBgColor = MaterialTheme.colorScheme.surface

    LazyColumn(state = listState, modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(sentences) { index, sentence ->
            val isCurrentSentence = index == currentIndex

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCurrentSentence) highlightBgColor else normalBgColor)
                    .clickable { onSentenceClick(index) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(sentence.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(50.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                AndroidView(
                    factory = { context ->
                        TextView(context).apply {
                            setTextIsSelectable(true)
                            textSize = 14f
                            // [问题1修复] 移除了 maxLines 和 ellipsize 限制，确保长字幕能完整显示
                        }
                    },
                    update = { textView ->
                        textView.text = sentence.text
                        textView.setTextColor(if (isCurrentSentence) highlightTextColor else textColor)
                        textView.setTypeface(null, if (isCurrentSentence) Typeface.BOLD else Typeface.NORMAL)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ProgressSection(
    currentPosition: Long,
    totalDuration: Long,
    onSeek: (Long) -> Unit,
    onPreview: (Long) -> Unit
) {
    val progress = if (totalDuration > 0) {
        currentPosition.toFloat() / totalDuration
    } else {
        0f
    }

    Column {
        Slider(
            value = progress,
            onValueChange = { value ->
                val previewTime = (value * totalDuration).toLong()
                onPreview(previewTime)
            },
            onValueChangeFinished = {
                onSeek(currentPosition)
            },
            modifier = Modifier.fillMaxWidth().height(20.dp) // 减小 Slider 占用高度
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(currentPosition), style = MaterialTheme.typography.bodySmall)
            Text(text = formatTime(totalDuration), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.SkipPrevious, "上一句", Modifier.size(32.dp))
        }

        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(56.dp) // 稍微调小一点
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (isPlaying) "暂停" else "播放",
                Modifier.size(32.dp)
            )
        }

        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, "下一句", Modifier.size(32.dp))
        }
    }
}

/**
 * [问题1修复] 合并后的次要控制栏：包括 曲目切换 和 快进快退
 */
@Composable
fun SecondaryControls(
    seekInterval: Long,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    canPlayPrevious: Boolean,
    canPlayNext: Boolean,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    currentIndex: Int,
    totalTracks: Int
) {
    val intervalSeconds = (seekInterval / 1000).toInt()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween, // 分散对齐
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上一首
        IconButton(onClick = onPreviousTrack, enabled = canPlayPrevious) {
            Icon(Icons.Default.FastRewind, "上一首", Modifier.size(24.dp))
        }

        // 快退
        TextButton(onClick = onSeekBackward) {
            Icon(Icons.Default.Replay10, null, Modifier.size(20.dp))
            Text(" -${intervalSeconds}s", style = MaterialTheme.typography.labelMedium)
        }

        // 进度/曲目信息
        if (totalTracks > 0) {
            Text(
                text = "${currentIndex + 1}/$totalTracks",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 快进
        TextButton(onClick = onSeekForward) {
            Text("+${intervalSeconds}s ", style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Default.Forward10, null, Modifier.size(20.dp))
        }

        // 下一首
        IconButton(onClick = onNextTrack, enabled = canPlayNext) {
            Icon(Icons.Default.FastForward, "下一首", Modifier.size(24.dp))
        }
    }
}

@Composable
fun SettingsBar(
    settings: PlaybackSettings,
    currentRepeat: Int,
    onSpeedChange: (Float) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
    onIntervalChange: (Long) -> Unit,
    onToggleSubtitle: () -> Unit
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showRepeatMenu by remember { mutableStateOf(false) }
    var showIntervalMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 速度
        Box {
            TextButton(onClick = { showSpeedMenu = true }) {
                Text("${settings.speed}x", style = MaterialTheme.typography.labelLarge)
            }
            DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                PlaybackSettings.SPEED_OPTIONS.forEach { speed ->
                    DropdownMenuItem(
                        text = { Text("${speed}x") },
                        onClick = { onSpeedChange(speed); showSpeedMenu = false }
                    )
                }
            }
        }

        // 重复
        Box {
            TextButton(onClick = { showRepeatMenu = true }) {
                Icon(Icons.Default.Repeat, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${currentRepeat}/${settings.repeatCount}", style = MaterialTheme.typography.labelLarge)
            }
            DropdownMenu(expanded = showRepeatMenu, onDismissRequest = { showRepeatMenu = false }) {
                PlaybackSettings.REPEAT_OPTIONS.forEach { count ->
                    DropdownMenuItem(
                        text = { Text("重复 $count 次") },
                        onClick = { onRepeatCountChange(count); showRepeatMenu = false }
                    )
                }
            }
        }

        // 间隔
        Box {
            TextButton(onClick = { showIntervalMenu = true }) {
                Icon(Icons.Default.Timer, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${settings.repeatInterval / 1000}s", style = MaterialTheme.typography.labelLarge)
            }
            DropdownMenu(expanded = showIntervalMenu, onDismissRequest = { showIntervalMenu = false }) {
                PlaybackSettings.INTERVAL_OPTIONS.forEach { interval ->
                    DropdownMenuItem(
                        text = { Text("${interval / 1000}秒") },
                        onClick = { onIntervalChange(interval); showIntervalMenu = false }
                    )
                }
            }
        }

        // 字幕
        IconButton(onClick = onToggleSubtitle) {
            Icon(
                if (settings.showSubtitle) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                "字幕",
                tint = if(settings.showSubtitle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatTime(msInput: Long): String {
    // [问题2修复] 增加对负数时间的保护，防止显示 12:-55 这种错误格式
    val ms = if (msInput < 0) 0L else msInput
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}