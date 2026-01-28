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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.shadowplayer.MainActivity
import com.example.shadowplayer.player.LrcSentence
import com.example.shadowplayer.player.PlaybackSettings
import com.example.shadowplayer.player.LrcParser

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
                    // [问题1修复] 使用原生 TextView 支持欧路词典等第三方应用
                    SelectableTextView(
                        text = currentText,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                } else {
                    Text(text = if (currentAudioFile == null) "请从文件库选择音频" else "无字幕", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (playerState.isInInterval) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(text = "请跟读 (${playerState.intervalCountdown}秒)", modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            }
        }

        ProgressSection(
            currentPosition = displayPosition,
            totalDuration = playerState.totalDuration,
            onSeek = { position -> viewModel.seekTo(position); isDragging = false },
            onPreview = { position -> isDragging = true; dragPosition = position }
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 句子控制
        PlaybackControls(
            isPlaying = playerState.isPlaying,
            onPlayPause = { viewModel.togglePlayPause() },
            onPrevious = { viewModel.previousSentence() },
            onNext = { viewModel.nextSentence() }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 快进快退控制
        SeekControls(
            seekInterval = settings.seekInterval,
            onSeekBackward = { viewModel.seekBackward() },
            onSeekForward = { viewModel.seekForward() }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 曲目控制（上一首/下一首）
        TrackControls(
            canPlayPrevious = viewModel.canPlayPrevious(),
            canPlayNext = viewModel.canPlayNext(),
            onPreviousTrack = { viewModel.playPrevious() },
            onNextTrack = { viewModel.playNext() },
            currentIndex = currentPlaylistIndex,
            totalTracks = playlist.size
        )

        Spacer(modifier = Modifier.height(8.dp))

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

/**
 * [问题1修复] 可选中的原生 TextView，支持系统级 ACTION_PROCESS_TEXT
 * 这样欧路词典等应用可以出现在选中菜单中
 */
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

    // [修复问题2] 让当前字幕居中显示
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < sentences.size) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

            // 估算每个项的高度（大约56dp）
            val itemHeight = with(density) { 56.dp.toPx() }.toInt()
            // 计算让当前项居中需要的偏移量
            val centerOffset = (viewportHeight / 2) - (itemHeight / 2)

            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -centerOffset
            )
        }
    }

    // 获取颜色供 AndroidView 使用
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val highlightTextColor = MaterialTheme.colorScheme.primary.toArgb()
    val timeColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
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

                // [问题1修复] 使用原生 TextView 替代 Compose Text，支持欧路词典
                AndroidView(
                    factory = { context ->
                        TextView(context).apply {
                            setTextIsSelectable(true)
                            textSize = 14f
                            maxLines = 5
                            ellipsize = TextUtils.TruncateAt.END
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
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = formatTime(totalDuration),
                style = MaterialTheme.typography.bodySmall
            )
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
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "上一句",
                modifier = Modifier.size(36.dp)
            )
        }

        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(36.dp)
            )
        }

        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "下一句",
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/**
 * 快进快退控制
 */
@Composable
fun SeekControls(
    seekInterval: Long,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit
) {
    val intervalSeconds = (seekInterval / 1000).toInt()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onSeekBackward,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Replay10,
                contentDescription = "快退",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("-${intervalSeconds}秒")
        }

        TextButton(
            onClick = onSeekForward,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text("+${intervalSeconds}秒")
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Forward10,
                contentDescription = "快进",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 曲目控制 - 上一首/下一首
 */
@Composable
fun TrackControls(
    canPlayPrevious: Boolean,
    canPlayNext: Boolean,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    currentIndex: Int,
    totalTracks: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上一首按钮
        TextButton(
            onClick = onPreviousTrack,
            enabled = canPlayPrevious
        ) {
            Icon(
                imageVector = Icons.Default.FastRewind,
                contentDescription = "上一首",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("上一首")
        }

        // 显示当前位置信息
        if (totalTracks > 0) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "${currentIndex + 1} / $totalTracks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        // 下一首按钮
        TextButton(
            onClick = onNextTrack,
            enabled = canPlayNext
        ) {
            Text("下一首")
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = "下一首",
                modifier = Modifier.size(20.dp)
            )
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
                Text("${settings.speed}x")
            }
            DropdownMenu(
                expanded = showSpeedMenu,
                onDismissRequest = { showSpeedMenu = false }
            ) {
                PlaybackSettings.SPEED_OPTIONS.forEach { speed ->
                    DropdownMenuItem(
                        text = { Text("${speed}x") },
                        onClick = {
                            onSpeedChange(speed)
                            showSpeedMenu = false
                        }
                    )
                }
            }
        }

        // 重复次数
        Box {
            TextButton(onClick = { showRepeatMenu = true }) {
                Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("${currentRepeat}/${settings.repeatCount}")
            }
            DropdownMenu(
                expanded = showRepeatMenu,
                onDismissRequest = { showRepeatMenu = false }
            ) {
                PlaybackSettings.REPEAT_OPTIONS.forEach { count ->
                    DropdownMenuItem(
                        text = { Text("重复 $count 次") },
                        onClick = {
                            onRepeatCountChange(count)
                            showRepeatMenu = false
                        }
                    )
                }
            }
        }

        // 跟读间隔
        Box {
            TextButton(onClick = { showIntervalMenu = true }) {
                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("${settings.repeatInterval / 1000}秒")
            }
            DropdownMenu(
                expanded = showIntervalMenu,
                onDismissRequest = { showIntervalMenu = false }
            ) {
                PlaybackSettings.INTERVAL_OPTIONS.forEach { interval ->
                    DropdownMenuItem(
                        text = { Text("${interval / 1000}秒") },
                        onClick = {
                            onIntervalChange(interval)
                            showIntervalMenu = false
                        }
                    )
                }
            }
        }

        // 字幕开关
        IconButton(onClick = onToggleSubtitle) {
            Icon(
                imageVector = if (settings.showSubtitle) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                contentDescription = "字幕"
            )
        }
    }
}

// 优化后的时间格式化，支持小时显示
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}