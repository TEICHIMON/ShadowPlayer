package com.example.shadowplayer.ui.player

import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.widget.TextView
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.shadowplayer.MainActivity
import com.example.shadowplayer.player.LrcSentence
import com.example.shadowplayer.player.PlaybackSettings
import com.example.shadowplayer.player.LrcParser

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
        activity?.onVolumeUp = { viewModel.previousSentence() }
        activity?.onVolumeDown = { viewModel.nextSentence() }
        onDispose {
            activity?.onVolumeUp = null
            activity?.onVolumeDown = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. 顶部留白或标题栏由 Activity/Scaffold 负责，这里直接开始内容
        // 删除了原有的 Text(currentAudioFile?.title)，节省空间

        // 2. 中间字幕区域 (权重最大，占据所有剩余空间)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (settings.showSubtitle && playerState.sentences.isNotEmpty()) {
                SubtitleList(
                    sentences = playerState.sentences,
                    currentIndex = targetIndex,
                    onSentenceClick = { viewModel.seekToSentence(it) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val currentText = if (targetIndex in playerState.sentences.indices) playerState.sentences[targetIndex].text else null
                    if (currentText != null && settings.showSubtitle) {
                        SelectableTextView(
                            text = currentText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
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

            // 跟读倒计时浮层
            if (playerState.isInInterval) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "跟读: ${playerState.intervalCountdown}s",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // 3. 底部控制区域 (背景色稍微区分，视觉上更稳重)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 进度条
            ProgressSection(
                currentPosition = displayPosition,
                totalDuration = playerState.totalDuration,
                onSeek = { position -> viewModel.seekTo(position); isDragging = false },
                onPreview = { position -> isDragging = true; dragPosition = position }
            )

            // 统一控制栏：[上一首] [上一句] [播放] [下一句] [下一首]
            UnifiedPlaybackControls(
                isPlaying = playerState.isPlaying,
                canPlayPreviousTrack = viewModel.canPlayPrevious(),
                canPlayNextTrack = viewModel.canPlayNext(),
                onPlayPause = { viewModel.togglePlayPause() },
                onPrevSentence = { viewModel.previousSentence() },
                onNextSentence = { viewModel.nextSentence() },
                onPrevTrack = { viewModel.playPrevious() },
                onNextTrack = { viewModel.playNext() }
            )

            // 紧凑设置栏：一行文字按钮
            CompactSettingsBar(
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

/**
 * 原生 TextView 封装，已移除 maxLines 限制
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
                // 移除 maxLines，允许显示全部内容
                ellipsize = null
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

    // 自动滚动逻辑
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < sentences.size) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val itemHeight = with(density) { 60.dp.toPx() }.toInt() // 估算值
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

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp), // 增加一点垂直内边距
        verticalArrangement = Arrangement.spacedBy(2.dp) // 减小间距，更紧凑
    ) {
        itemsIndexed(sentences) { index, sentence ->
            val isCurrentSentence = index == currentIndex

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp) // 列表项两侧留白
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
                    modifier = Modifier.width(45.dp) // 稍微调窄一点时间宽度
                )
                Spacer(modifier = Modifier.width(8.dp))

                AndroidView(
                    factory = { context ->
                        TextView(context).apply {
                            setTextIsSelectable(true)
                            textSize = 15f // 稍微调大字体
                            // 【关键修改】移除行数限制
                            // maxLines = 5  <-- 删掉
                            // ellipsize = TextUtils.TruncateAt.END <-- 删掉
                        }
                    },
                    update = { textView ->
                        textView.text = sentence.text
                        textView.setTextColor(if (isCurrentSentence) highlightTextColor else textColor)
                        textView.setTypeface(null, if (isCurrentSentence) Typeface.BOLD else Typeface.NORMAL)

                        // 【点击修复】手动转发点击事件
                        textView.setOnClickListener {
                            onSentenceClick(index)
                        }
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

    Column(modifier = Modifier.fillMaxWidth()) {
        // 时间显示放在滑块上方，更紧凑
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(currentPosition), style = MaterialTheme.typography.labelSmall)
            Text(text = formatTime(totalDuration), style = MaterialTheme.typography.labelSmall)
        }

        Slider(
            value = progress,
            onValueChange = { value ->
                val previewTime = (value * totalDuration).toLong()
                onPreview(previewTime)
            },
            onValueChangeFinished = {
                onSeek(currentPosition)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp) // 强制压缩 Slider 高度占用
        )
    }
}

/**
 * 五键合一：上一首 | 上一句 | 播放 | 下一句 | 下一首
 */
@Composable
fun UnifiedPlaybackControls(
    isPlaying: Boolean,
    canPlayPreviousTrack: Boolean,
    canPlayNextTrack: Boolean,
    onPlayPause: () -> Unit,
    onPrevSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onPrevTrack: () -> Unit,
    onNextTrack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 上一首 (小图标)
        IconButton(onClick = onPrevTrack, enabled = canPlayPreviousTrack) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(24.dp))
        }

        // 2. 上一句 (中图标) - 使用 ArrowBack 区分于 Track
        IconButton(onClick = onPrevSentence) {
            Icon(Icons.Default.ArrowBack, contentDescription = "上一句", modifier = Modifier.size(32.dp))
        }

        // 3. 播放/暂停 (大图标)
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(32.dp)
            )
        }

        // 4. 下一句 (中图标)
        IconButton(onClick = onNextSentence) {
            Icon(Icons.Default.ArrowForward, contentDescription = "下一句", modifier = Modifier.size(32.dp))
        }

        // 5. 下一首 (小图标)
        IconButton(onClick = onNextTrack, enabled = canPlayNextTrack) {
            Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * 紧凑型设置栏：只使用文字按钮，节省空间
 */
@Composable
fun CompactSettingsBar(
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
        horizontalArrangement = Arrangement.SpaceBetween, // 分散对齐
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 速度
        Box {
            TextButton(onClick = { showSpeedMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(text = "${settings.speed}x", style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                PlaybackSettings.SPEED_OPTIONS.forEach { speed ->
                    DropdownMenuItem(text = { Text("${speed}x") }, onClick = { onSpeedChange(speed); showSpeedMenu = false })
                }
            }
        }

        // 重复
        Box {
            TextButton(onClick = { showRepeatMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(text = "复读:${currentRepeat}/${settings.repeatCount}", style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(expanded = showRepeatMenu, onDismissRequest = { showRepeatMenu = false }) {
                PlaybackSettings.REPEAT_OPTIONS.forEach { count ->
                    DropdownMenuItem(text = { Text("重复 $count 次") }, onClick = { onRepeatCountChange(count); showRepeatMenu = false })
                }
            }
        }

        // 间隔
        Box {
            TextButton(onClick = { showIntervalMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(text = "间隔:${settings.repeatInterval / 1000}s", style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(expanded = showIntervalMenu, onDismissRequest = { showIntervalMenu = false }) {
                PlaybackSettings.INTERVAL_OPTIONS.forEach { interval ->
                    DropdownMenuItem(text = { Text("${interval / 1000}秒") }, onClick = { onIntervalChange(interval); showIntervalMenu = false })
                }
            }
        }

        // 字幕开关
        TextButton(onClick = onToggleSubtitle, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(
                text = if (settings.showSubtitle) "字幕:开" else "字幕:关",
                style = MaterialTheme.typography.labelMedium,
                color = if (settings.showSubtitle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}