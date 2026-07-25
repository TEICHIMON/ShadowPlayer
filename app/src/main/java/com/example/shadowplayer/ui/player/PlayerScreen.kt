package com.example.shadowplayer.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.shadowplayer.MainActivity
import com.example.shadowplayer.player.AudioOutputRoute
import com.example.shadowplayer.player.AudioOutputType
import com.example.shadowplayer.player.LrcParser
import com.example.shadowplayer.player.LrcSentence
import com.example.shadowplayer.player.PlaybackSettings
import com.example.shadowplayer.player.SentencePlayerState
import com.example.shadowplayer.ui.theme.ShadowPlayerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class PlayerScreenUiState(
    val audioId: Long?,
    val title: String,
    val playerState: SentencePlayerState,
    val settings: PlaybackSettings,
    val systemVolume: Float,
    val audioOutputRoute: AudioOutputRoute,
    val currentPlaylistIndex: Int,
    val playlistSize: Int,
    val canPlayPreviousTrack: Boolean,
    val canPlayNextTrack: Boolean
)

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val systemVolume by viewModel.systemVolume.collectAsState()
    val currentAudioFile by viewModel.currentAudioFile.collectAsState()
    val playlist by viewModel.playlist.collectAsState()
    val currentPlaylistIndex by viewModel.currentPlaylistIndex.collectAsState()
    val audioOutputRoute by viewModel.audioOutputRoute.collectAsState()

    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncPlaybackState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        val activity = context as? MainActivity
        activity?.onVolumeUp = {
            if (viewModel.isVolumeKeyEnabled()) viewModel.handleVolumeUp()
            viewModel.isVolumeKeyEnabled()
        }
        activity?.onVolumeDown = {
            if (viewModel.isVolumeKeyEnabled()) viewModel.handleVolumeDown()
            viewModel.isVolumeKeyEnabled()
        }
        onDispose {
            activity?.onVolumeUp = null
            activity?.onVolumeDown = null
        }
    }

    PlayerScreenContent(
        uiState = PlayerScreenUiState(
            audioId = currentAudioFile?.id,
            title = currentAudioFile?.title ?: "未选择音频",
            playerState = playerState,
            settings = settings,
            systemVolume = systemVolume.percent,
            audioOutputRoute = audioOutputRoute,
            currentPlaylistIndex = currentPlaylistIndex,
            playlistSize = playlist.size,
            canPlayPreviousTrack = viewModel.canPlayPrevious(),
            canPlayNextTrack = viewModel.canPlayNext()
        ),
        onShowOutputSwitcher = { viewModel.showOutputSwitcher(context) },
        onSentenceClick = viewModel::seekToSentence,
        onCopySubtitleText = { text ->
            clipboardManager.setPrimaryClip(ClipData.newPlainText("字幕", text))
        },
        onSeek = viewModel::seekTo,
        onPlayPause = viewModel::togglePlayPause,
        onPreviousSentence = viewModel::previousSentence,
        onNextSentence = viewModel::nextSentence,
        onSpeedChange = viewModel::setSpeed,
        onRepeatCountChange = viewModel::setRepeatCount,
        onIntervalChange = viewModel::setRepeatInterval,
        onSystemVolumeChange = viewModel::setSystemVolume,
        onPlayerVolumeChange = viewModel::setPlayerVolume,
        onPreviousTrack = viewModel::playPrevious,
        onNextTrack = viewModel::playNext,
        onSeekBackward = viewModel::seekBackward,
        onSeekForward = viewModel::seekForward,
        onSleepTimerChange = viewModel::setSleepTimerMinutes,
        onToggleSubtitle = viewModel::toggleSubtitle
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerScreenContent(
    uiState: PlayerScreenUiState,
    onShowOutputSwitcher: () -> Unit,
    onSentenceClick: (Int) -> Unit,
    onCopySubtitleText: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
    onIntervalChange: (Long) -> Unit,
    onSystemVolumeChange: (Float) -> Unit,
    onPlayerVolumeChange: (Float) -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSleepTimerChange: (Int) -> Unit,
    onToggleSubtitle: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }
    var showMoreControls by rememberSaveable { mutableStateOf(false) }
    var isSubtitleSearchOpen by rememberSaveable { mutableStateOf(false) }
    var subtitleSearchQuery by rememberSaveable { mutableStateOf("") }
    var subtitleSearchAudioId by rememberSaveable { mutableStateOf<Long?>(null) }
    var shouldFocusSubtitleSearch by remember { mutableStateOf(false) }
    var isSubtitleSelectionMode by remember { mutableStateOf(false) }
    var selectedSubtitleIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.audioId) {
        if (subtitleSearchAudioId != uiState.audioId) {
            isSubtitleSearchOpen = false
            subtitleSearchQuery = ""
            shouldFocusSubtitleSearch = false
            isSubtitleSelectionMode = false
            selectedSubtitleIndices = emptySet()
            subtitleSearchAudioId = uiState.audioId
        }
    }

    LaunchedEffect(uiState.settings.showSubtitle, uiState.playerState.sentences.size) {
        if (!uiState.settings.showSubtitle || uiState.playerState.sentences.isEmpty()) {
            isSubtitleSelectionMode = false
            selectedSubtitleIndices = emptySet()
            isSubtitleSearchOpen = false
            subtitleSearchQuery = ""
            shouldFocusSubtitleSearch = false
        } else {
            selectedSubtitleIndices = selectedSubtitleIndices
                .filterTo(linkedSetOf()) { it in uiState.playerState.sentences.indices }
        }
    }

    val displayPosition = if (isDragging) dragPosition else uiState.playerState.currentPosition
    val targetIndex = if (isDragging && uiState.playerState.sentences.isNotEmpty()) {
        LrcParser.findSentenceIndex(uiState.playerState.sentences, dragPosition)
    } else {
        uiState.playerState.currentIndex
    }
    val subtitleItems = remember(uiState.playerState.sentences, subtitleSearchQuery) {
        filterSubtitleItems(uiState.playerState.sentences, subtitleSearchQuery)
    }
    val canSearch = uiState.audioId != null &&
        uiState.settings.showSubtitle &&
        uiState.playerState.sentences.isNotEmpty()

    fun exitSubtitleSelection() {
        isSubtitleSelectionMode = false
        selectedSubtitleIndices = emptySet()
    }

    fun enterSubtitleSelection(index: Int) {
        shouldFocusSubtitleSearch = false
        keyboardController?.hide()
        focusManager.clearFocus()
        isSubtitleSelectionMode = true
        selectedSubtitleIndices = selectedSubtitleIndices + index
    }

    fun handleSubtitleClick(index: Int) {
        if (!isSubtitleSelectionMode) {
            onSentenceClick(index)
            return
        }

        selectedSubtitleIndices = if (index in selectedSubtitleIndices) {
            selectedSubtitleIndices - index
        } else {
            selectedSubtitleIndices + index
        }
    }

    BackHandler(enabled = isSubtitleSelectionMode) {
        exitSubtitleSelection()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PlayerTopBar(
                title = uiState.title,
                route = uiState.audioOutputRoute,
                canSearch = canSearch,
                isSelectionMode = isSubtitleSelectionMode,
                selectedCount = selectedSubtitleIndices.size,
                canSelectAll = subtitleItems.any { it.originalIndex !in selectedSubtitleIndices },
                canCopySelection = selectedSubtitleIndices.isNotEmpty(),
                isSearchOpen = isSubtitleSearchOpen,
                searchQuery = subtitleSearchQuery,
                searchResultCount = subtitleItems.size,
                requestSearchFocus = shouldFocusSubtitleSearch,
                onOutputClick = onShowOutputSwitcher,
                onOpenSearch = {
                    shouldFocusSubtitleSearch = true
                    isSubtitleSearchOpen = true
                },
                onSearchQueryChange = { subtitleSearchQuery = it },
                onClearSearch = { subtitleSearchQuery = "" },
                onSearchFocusHandled = { shouldFocusSubtitleSearch = false },
                onCloseSearch = {
                    isSubtitleSearchOpen = false
                    subtitleSearchQuery = ""
                    shouldFocusSubtitleSearch = false
                },
                onExitSelection = ::exitSubtitleSelection,
                onSelectAll = {
                    selectedSubtitleIndices = subtitleItems
                        .mapTo(linkedSetOf()) { it.originalIndex }
                },
                onCopySelection = {
                    val selectedCount = selectedSubtitleIndices.size
                    val copiedText = buildSubtitleCopyText(
                        sentences = uiState.playerState.sentences,
                        selectedIndices = selectedSubtitleIndices
                    )
                    if (copiedText.isNotEmpty()) {
                        onCopySubtitleText(copiedText)
                        exitSubtitleSelection()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("已复制 $selectedCount 条字幕")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubtitleArea(
                audioId = uiState.audioId,
                showSubtitle = uiState.settings.showSubtitle,
                subtitleItems = subtitleItems,
                currentIndex = targetIndex,
                searchQuery = subtitleSearchQuery,
                isSearching = isSubtitleSearchOpen,
                isSelectionMode = isSubtitleSelectionMode,
                selectedIndices = selectedSubtitleIndices,
                isInInterval = uiState.playerState.isInInterval,
                intervalCountdown = uiState.playerState.intervalCountdown,
                onSentenceClick = ::handleSubtitleClick,
                onSentenceLongClick = ::enterSubtitleSelection,
                onShowSubtitle = onToggleSubtitle,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            PlayerControlDeck(
                currentPosition = displayPosition,
                totalDuration = uiState.playerState.totalDuration,
                isPlaying = uiState.playerState.isPlaying,
                settings = uiState.settings,
                currentRepeat = uiState.playerState.currentRepeat,
                onSeekPreview = { position ->
                    isDragging = true
                    dragPosition = position
                },
                onSeek = { position ->
                    onSeek(position)
                    isDragging = false
                },
                onPlayPause = onPlayPause,
                onPreviousSentence = onPreviousSentence,
                onNextSentence = onNextSentence,
                onSpeedChange = onSpeedChange,
                onRepeatCountChange = onRepeatCountChange,
                onIntervalChange = onIntervalChange,
                onMoreControls = { showMoreControls = true }
            )
        }
    }

    if (showMoreControls) {
        MoreControlsSheet(
            settings = uiState.settings,
            systemVolume = uiState.systemVolume,
            currentPlaylistIndex = uiState.currentPlaylistIndex,
            playlistSize = uiState.playlistSize,
            canPlayPreviousTrack = uiState.canPlayPreviousTrack,
            canPlayNextTrack = uiState.canPlayNextTrack,
            onDismiss = { showMoreControls = false },
            onSystemVolumeChange = onSystemVolumeChange,
            onPlayerVolumeChange = onPlayerVolumeChange,
            onPreviousTrack = onPreviousTrack,
            onNextTrack = onNextTrack,
            onSeekBackward = onSeekBackward,
            onSeekForward = onSeekForward,
            onSleepTimerChange = onSleepTimerChange,
            onToggleSubtitle = onToggleSubtitle
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTopBar(
    title: String,
    route: AudioOutputRoute,
    canSearch: Boolean,
    isSelectionMode: Boolean,
    selectedCount: Int,
    canSelectAll: Boolean,
    canCopySelection: Boolean,
    isSearchOpen: Boolean,
    searchQuery: String,
    searchResultCount: Int,
    requestSearchFocus: Boolean,
    onOutputClick: () -> Unit,
    onOpenSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearchFocusHandled: () -> Unit,
    onCloseSearch: () -> Unit,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onCopySelection: () -> Unit
) {
    if (isSelectionMode) {
        TopAppBar(
            expandedHeight = 48.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.testTag("subtitle_selection_top_bar"),
            navigationIcon = {
                IconButton(onClick = onExitSelection) {
                    Icon(Icons.Default.Close, contentDescription = "退出多选")
                }
            },
            title = {
                Text(
                    text = "已选择 $selectedCount 条",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            actions = {
                IconButton(onClick = onSelectAll, enabled = canSelectAll) {
                    Icon(Icons.Default.SelectAll, contentDescription = "全选字幕")
                }
                IconButton(onClick = onCopySelection, enabled = canCopySelection) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制字幕")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
    } else if (isSearchOpen) {
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        LaunchedEffect(requestSearchFocus) {
            if (requestSearchFocus) {
                focusRequester.requestFocus()
                keyboardController?.show()
                onSearchFocusHandled()
            }
        }

        TopAppBar(
            expandedHeight = 48.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onCloseSearch()
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                }
            },
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .focusRequester(focusRequester)
                        .testTag("subtitle_search_field"),
                    placeholder = { Text("搜索字幕") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = onClearSearch) {
                                Icon(Icons.Default.Clear, contentDescription = "清空搜索")
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            },
            actions = {
                if (searchQuery.trim().isNotEmpty()) {
                    Text(
                        text = "$searchResultCount 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        )
    } else {
        CenterAlignedTopAppBar(
            expandedHeight = 48.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = {
                Text(
                    text = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                IconButton(onClick = onOutputClick) {
                    Icon(
                        imageVector = audioOutputIcon(route.type),
                        contentDescription = "音频输出：${route.name}"
                    )
                }
            },
            actions = {
                IconButton(onClick = onOpenSearch, enabled = canSearch) {
                    Icon(Icons.Default.Search, contentDescription = "搜索字幕")
                }
            }
        )
    }
}

private fun audioOutputIcon(type: AudioOutputType) = when (type) {
    AudioOutputType.BLUETOOTH -> Icons.Default.BluetoothAudio
    AudioOutputType.WIRED -> Icons.Default.Headphones
    AudioOutputType.SPEAKER -> Icons.Default.PhoneAndroid
    AudioOutputType.OTHER -> Icons.AutoMirrored.Filled.VolumeUp
}

@Composable
private fun SubtitleArea(
    audioId: Long?,
    showSubtitle: Boolean,
    subtitleItems: List<SubtitleListItem>,
    currentIndex: Int,
    searchQuery: String,
    isSearching: Boolean,
    isSelectionMode: Boolean,
    selectedIndices: Set<Int>,
    isInInterval: Boolean,
    intervalCountdown: Int,
    onSentenceClick: (Int) -> Unit,
    onSentenceLongClick: (Int) -> Unit,
    onShowSubtitle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.testTag("subtitle_area")) {
        when {
            audioId == null -> SubtitleEmptyState(
                icon = Icons.Default.Subtitles,
                message = "请从文件库选择音频"
            )

            !showSubtitle -> SubtitleEmptyState(
                icon = Icons.Default.SubtitlesOff,
                message = "字幕已关闭",
                actionLabel = "显示字幕",
                onAction = onShowSubtitle
            )

            isSearching && subtitleItems.isEmpty() -> SubtitleEmptyState(
                icon = Icons.Default.Search,
                message = "未找到匹配字幕"
            )

            subtitleItems.isEmpty() -> SubtitleEmptyState(
                icon = Icons.Default.SubtitlesOff,
                message = "未找到字幕"
            )

            else -> SubtitleList(
                subtitleItems = subtitleItems,
                currentIndex = currentIndex,
                searchQuery = searchQuery,
                isSelectionMode = isSelectionMode,
                selectedIndices = selectedIndices,
                onSentenceClick = onSentenceClick,
                onSentenceLongClick = onSentenceLongClick,
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = isInInterval,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp,
                modifier = Modifier.testTag("interval_overlay")
            ) {
                Text(
                    text = "请跟读 · ${intervalCountdown.coerceAtLeast(0)} 秒",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun SubtitleEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SubtitleList(
    subtitleItems: List<SubtitleListItem>,
    currentIndex: Int,
    searchQuery: String,
    isSelectionMode: Boolean,
    selectedIndices: Set<Int>,
    onSentenceClick: (Int) -> Unit,
    onSentenceLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val visibleCurrentIndex = subtitleItems.indexOfFirst { it.originalIndex == currentIndex }

    LaunchedEffect(visibleCurrentIndex, subtitleItems, isSelectionMode) {
        if (isSelectionMode || visibleCurrentIndex < 0) return@LaunchedEffect

        val viewportHeight = snapshotFlow {
            listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        }.first { it > 0 }

        listState.animateScrollToItem(
            index = visibleCurrentIndex,
            scrollOffset = -(viewportHeight / 2)
        )
        withFrameNanos { }

        val layoutInfo = listState.layoutInfo
        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == visibleCurrentIndex }
        if (itemInfo != null) {
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val itemCenter = itemInfo.offset + itemInfo.size / 2
            listState.animateScrollBy((itemCenter - viewportCenter).toFloat())
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.testTag("subtitle_list"),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items = subtitleItems, key = { it.originalIndex }) { item ->
            SubtitleRow(
                item = item,
                isCurrentSentence = item.originalIndex == currentIndex,
                isSelectionMode = isSelectionMode,
                isSelected = item.originalIndex in selectedIndices,
                searchQuery = searchQuery,
                onClick = { onSentenceClick(item.originalIndex) },
                onLongClick = { onSentenceLongClick(item.originalIndex) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubtitleRow(
    item: SubtitleListItem,
    isCurrentSentence: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    searchQuery: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isCurrentSentence -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        isCurrentSentence -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val indicatorColor = if (isCurrentSentence) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag("subtitle_row_${item.originalIndex}")
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(vertical = if (isCurrentSentence) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("subtitle_checkbox_${item.originalIndex}")
            )
            Spacer(Modifier.width(4.dp))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(indicatorColor)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = formatTime(item.sentence.startTime),
            modifier = Modifier.width(42.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrentSentence) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = highlightedSubtitleText(
                text = item.sentence.text,
                query = searchQuery,
                highlightColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            color = textColor,
            fontSize = if (isCurrentSentence) 18.sp else 16.sp,
            lineHeight = if (isCurrentSentence) 26.sp else 23.sp,
            fontWeight = if (isCurrentSentence) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        )
    }
}

private fun highlightedSubtitleText(
    text: String,
    query: String,
    highlightColor: Color
): AnnotatedString = buildAnnotatedString {
    append(text)
    findSubtitleMatchRanges(text, query).forEach { range ->
        addStyle(
            style = SpanStyle(background = highlightColor),
            start = range.first,
            end = range.last + 1
        )
    }
}

@Composable
private fun PlayerControlDeck(
    currentPosition: Long,
    totalDuration: Long,
    isPlaying: Boolean,
    settings: PlaybackSettings,
    currentRepeat: Int,
    onSeekPreview: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
    onIntervalChange: (Long) -> Unit,
    onMoreControls: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("player_control_deck"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            ProgressSection(
                currentPosition = currentPosition,
                totalDuration = totalDuration,
                onSeek = onSeek,
                onPreview = onSeekPreview
            )
            PlaybackControls(
                isPlaying = isPlaying,
                onPlayPause = onPlayPause,
                onPrevious = onPreviousSentence,
                onNext = onNextSentence
            )
            LearningControlsBar(
                settings = settings,
                currentRepeat = currentRepeat,
                onSpeedChange = onSpeedChange,
                onRepeatCountChange = onRepeatCountChange,
                onIntervalChange = onIntervalChange,
                onMoreControls = onMoreControls
            )
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
    var pendingSeekPosition by remember { mutableLongStateOf(currentPosition) }
    LaunchedEffect(currentPosition, totalDuration) {
        pendingSeekPosition = currentPosition.coerceIn(0L, totalDuration.coerceAtLeast(0L))
    }

    val progress = if (totalDuration > 0) {
        (pendingSeekPosition.toFloat() / totalDuration).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column {
        Slider(
            value = progress,
            onValueChange = { value ->
                val previewTime = (value * totalDuration).toLong()
                    .coerceIn(0L, totalDuration.coerceAtLeast(0L))
                pendingSeekPosition = previewTime
                onPreview(previewTime)
            },
            onValueChangeFinished = { onSeek(pendingSeekPosition) },
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(currentPosition), style = MaterialTheme.typography.labelSmall)
            Text(formatTime(totalDuration), style = MaterialTheme.typography.labelSmall)
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
            Icon(Icons.Default.SkipPrevious, "上一句", Modifier.size(30.dp))
        }
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
        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, "下一句", Modifier.size(30.dp))
        }
    }
}

@Composable
private fun LearningControlsBar(
    settings: PlaybackSettings,
    currentRepeat: Int,
    onSpeedChange: (Float) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
    onIntervalChange: (Long) -> Unit,
    onMoreControls: () -> Unit
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showRepeatMenu by remember { mutableStateOf(false) }
    var showIntervalMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            TextButton(onClick = { showSpeedMenu = true }) {
                Text("${settings.speed}x", style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.ExpandMore, contentDescription = null, Modifier.size(16.dp))
            }
            DropdownMenu(showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
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

        Box {
            TextButton(onClick = { showRepeatMenu = true }) {
                Icon(Icons.Default.Repeat, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("$currentRepeat/${settings.repeatCount}")
            }
            DropdownMenu(showRepeatMenu, onDismissRequest = { showRepeatMenu = false }) {
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

        Box {
            TextButton(onClick = { showIntervalMenu = true }) {
                Icon(Icons.Default.Timer, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${settings.repeatInterval / 1000}s")
            }
            DropdownMenu(showIntervalMenu, onDismissRequest = { showIntervalMenu = false }) {
                PlaybackSettings.INTERVAL_OPTIONS.forEach { interval ->
                    DropdownMenuItem(
                        text = { Text("${interval / 1000} 秒") },
                        onClick = {
                            onIntervalChange(interval)
                            showIntervalMenu = false
                        }
                    )
                }
            }
        }

        IconButton(onClick = onMoreControls) {
            Icon(Icons.Default.Tune, contentDescription = "更多控制")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreControlsSheet(
    settings: PlaybackSettings,
    systemVolume: Float,
    currentPlaylistIndex: Int,
    playlistSize: Int,
    canPlayPreviousTrack: Boolean,
    canPlayNextTrack: Boolean,
    onDismiss: () -> Unit,
    onSystemVolumeChange: (Float) -> Unit,
    onPlayerVolumeChange: (Float) -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSleepTimerChange: (Int) -> Unit,
    onToggleSubtitle: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSleepTimerMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("more_controls_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text("更多控制", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            VolumeSlider(
                label = "系统音量",
                volume = systemVolume,
                onVolumeChange = onSystemVolumeChange
            )
            Spacer(Modifier.height(8.dp))
            VolumeSlider(
                label = "播放器音量",
                volume = settings.volume,
                onVolumeChange = onPlayerVolumeChange
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                text = "曲目与跳转",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            TrackControls(
                currentIndex = currentPlaylistIndex,
                totalTracks = playlistSize,
                canPlayPrevious = canPlayPreviousTrack,
                canPlayNext = canPlayNextTrack,
                onPreviousTrack = onPreviousTrack,
                onNextTrack = onNextTrack
            )
            SeekControls(
                seekInterval = settings.seekInterval,
                onSeekBackward = onSeekBackward,
                onSeekForward = onSeekForward
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            ListItem(
                headlineContent = { Text("睡眠定时") },
                supportingContent = { Text("到时自动暂停播放") },
                leadingContent = { Icon(Icons.Default.Timer, contentDescription = null) },
                trailingContent = {
                    Box {
                        TextButton(onClick = { showSleepTimerMenu = true }) {
                            Text(PlaybackSettings.sleepTimerLabel(settings.sleepTimerMinutes))
                            Icon(Icons.Default.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showSleepTimerMenu,
                            onDismissRequest = { showSleepTimerMenu = false }
                        ) {
                            PlaybackSettings.SLEEP_TIMER_OPTIONS.forEach { minutes ->
                                DropdownMenuItem(
                                    text = { Text(PlaybackSettings.sleepTimerLabel(minutes)) },
                                    onClick = {
                                        onSleepTimerChange(minutes)
                                        showSleepTimerMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
            ListItem(
                headlineContent = { Text("显示字幕") },
                supportingContent = { Text("显示完整字幕列表") },
                leadingContent = {
                    Icon(
                        if (settings.showSubtitle) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                        contentDescription = null
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settings.showSubtitle,
                        onCheckedChange = { onToggleSubtitle() }
                    )
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TrackControls(
    currentIndex: Int,
    totalTracks: Int,
    canPlayPrevious: Boolean,
    canPlayNext: Boolean,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousTrack, enabled = canPlayPrevious) {
            Icon(Icons.Default.FastRewind, contentDescription = "上一首")
        }
        Text(
            text = if (totalTracks > 0) "${currentIndex + 1}/$totalTracks" else "暂无播放列表",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onNextTrack, enabled = canPlayNext) {
            Icon(Icons.Default.FastForward, contentDescription = "下一首")
        }
    }
}

@Composable
private fun SeekControls(
    seekInterval: Long,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit
) {
    val intervalSeconds = (seekInterval / 1000).toInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onSeekBackward, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Replay10, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("-$intervalSeconds 秒")
        }
        OutlinedButton(onClick = onSeekForward, modifier = Modifier.weight(1f)) {
            Text("+$intervalSeconds 秒")
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.Forward10, contentDescription = null)
        }
    }
}

@Composable
fun VolumeSlider(
    label: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    contentDescription: String = label
) {
    val currentVolume = volume.coerceIn(0f, 1f)
    val volumePercent = (currentVolume * 100).roundToInt()
    val icon = when {
        currentVolume <= 0f -> Icons.AutoMirrored.Filled.VolumeOff
        currentVolume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Slider(
                value = currentVolume,
                onValueChange = { onVolumeChange(it.coerceIn(0f, 1f)) },
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$volumePercent%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(42.dp)
            )
        }
    }
}

fun formatTime(msInput: Long): String {
    val ms = msInput.coerceAtLeast(0L)
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

private val previewSentences = listOf(
    LrcSentence(0, 0L, 4_000L, "Shadowing means listening and speaking at almost the same time."),
    LrcSentence(1, 4_000L, 9_000L, "先听清楚句子的节奏，再自然地跟读。"),
    LrcSentence(2, 9_000L, 15_000L, "This longer subtitle demonstrates how a multi-line sentence stays readable and centered."),
    LrcSentence(3, 15_000L, 20_000L, "保持放松，注意连读和重音。")
)

@Preview(name = "小屏", widthDp = 360, heightDp = 640, showBackground = true)
@Preview(
    name = "深色大字体",
    widthDp = 411,
    heightDp = 891,
    fontScale = 1.3f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
private fun PlayerScreenPreview() {
    ShadowPlayerTheme(dynamicColor = false) {
        PlayerScreenContent(
            uiState = PlayerScreenUiState(
                audioId = 1L,
                title = "Shadowing Practice · Lesson 01",
                playerState = SentencePlayerState(
                    sentences = previewSentences,
                    currentIndex = 1,
                    currentRepeat = 2,
                    currentPosition = 6_500L,
                    totalDuration = 120_000L,
                    isPlaying = true
                ),
                settings = PlaybackSettings(repeatCount = 3, repeatInterval = 2_000L),
                systemVolume = 0.65f,
                audioOutputRoute = AudioOutputRoute("蓝牙耳机", AudioOutputType.BLUETOOTH),
                currentPlaylistIndex = 0,
                playlistSize = 6,
                canPlayPreviousTrack = false,
                canPlayNextTrack = true
            ),
            onShowOutputSwitcher = {},
            onSentenceClick = {},
            onCopySubtitleText = {},
            onSeek = {},
            onPlayPause = {},
            onPreviousSentence = {},
            onNextSentence = {},
            onSpeedChange = {},
            onRepeatCountChange = {},
            onIntervalChange = {},
            onSystemVolumeChange = {},
            onPlayerVolumeChange = {},
            onPreviousTrack = {},
            onNextTrack = {},
            onSeekBackward = {},
            onSeekForward = {},
            onSleepTimerChange = {},
            onToggleSubtitle = {}
        )
    }
}
