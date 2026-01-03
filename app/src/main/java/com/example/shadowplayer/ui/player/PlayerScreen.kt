package com.example.shadowplayer.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.shadowplayer.player.LrcSentence
import com.example.shadowplayer.player.PlaybackSettings
import androidx.compose.ui.platform.LocalContext // 确保有这个 import
import com.example.shadowplayer.MainActivity // 确保引入 MainActivity

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentAudioFile by viewModel.currentAudioFile.collectAsState()
// --- [新增代码 START] ---
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? MainActivity
        // 绑定音量键事件：音量上 -> 上一句，音量下 -> 下一句
        activity?.onVolumeUp = { viewModel.previousSentence() }
        activity?.onVolumeDown = { viewModel.nextSentence() }

        onDispose {
            // 界面退出时清理引用，防止内存泄漏
            activity?.onVolumeUp = null
            activity?.onVolumeDown = null
        }
    }
    // --- [新增代码 END] ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = currentAudioFile?.title ?: "未选择音频",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 字幕列表
        if (settings.showSubtitle && playerState.sentences.isNotEmpty()) {
            SubtitleList(
                sentences = playerState.sentences,
                currentIndex = playerState.currentIndex,
                onSentenceClick = { index ->
                    viewModel.seekToSentence(index)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            // 无字幕或隐藏字幕时显示当前句
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (playerState.currentSentence != null && settings.showSubtitle) {
                    Text(
                        text = playerState.currentSentence!!.text,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
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

        // 跟读倒计时
        if (playerState.isInInterval) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "请跟读 (${playerState.intervalCountdown}秒)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // 进度条
        ProgressSection(
            currentPosition = playerState.currentPosition,
            totalDuration = playerState.totalDuration,
            onSeek = { viewModel.seekTo(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 播放控制
        PlaybackControls(
            isPlaying = playerState.isPlaying,
            onPlayPause = { viewModel.togglePlayPause() },
            onPrevious = { viewModel.previousSentence() },
            onNext = { viewModel.nextSentence() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 设置栏
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

@Composable
fun SubtitleList(
    sentences: List<LrcSentence>,
    currentIndex: Int,
    onSentenceClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 自动滚动到当前句
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < sentences.size) {
            listState.animateScrollToItem(
                index = maxOf(0, currentIndex - 2)
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(sentences) { index, sentence ->
            val isCurrentSentence = index == currentIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isCurrentSentence) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
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
                Text(
                    text = sentence.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrentSentence) FontWeight.Bold else FontWeight.Normal,
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
    onSeek: (Long) -> Unit
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val progress = if (totalDuration > 0 && !isDragging) {
        currentPosition.toFloat() / totalDuration
    } else {
        sliderPosition
    }

    Column {
        Slider(
            value = progress,
            onValueChange = { value ->
                isDragging = true
                sliderPosition = value
            },
            onValueChangeFinished = {
                onSeek((sliderPosition * totalDuration).toLong())
                isDragging = false
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

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
