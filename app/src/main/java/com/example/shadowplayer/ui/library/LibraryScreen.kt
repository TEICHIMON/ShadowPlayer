package com.example.shadowplayer.ui.library

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.entity.ScanFolder
import com.example.shadowplayer.data.entity.Tag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onFileSelected: (AudioFile) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val recordGroups by viewModel.recordGroups.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()
    val rootTags by viewModel.rootTags.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val selectedTagId by viewModel.selectedTagId.collectAsState()
    val audioFilesByTag by viewModel.audioFilesByTag.collectAsState()

    val scanFolders by viewModel.scanFolders.collectAsState()
    val folderContent by viewModel.folderContent.collectAsState()
    val currentFolderPath by viewModel.currentFolderPath.collectAsState()
    val expandedFolders by viewModel.expandedFolders.collectAsState()

    // [问题2修复] 订阅折叠状态
    val expandedGroupPaths by viewModel.expandedGroupPaths.collectAsState()

    val isScanning by viewModel.isScanning.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds by viewModel.selectedAudioIds.collectAsState()

    val audioDetails by viewModel.audioDetailsState.collectAsState()

    val currentPlayingAudioId by viewModel.currentPlayingAudioId.collectAsState()

    var selectedTab by remember { mutableStateOf(LibraryTab.RECORDS) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showAddToTagDialog by remember { mutableStateOf<AudioFile?>(null) }
    var showBatchAddTagDialog by remember { mutableStateOf(false) }
    var showTagManager by remember { mutableStateOf(false) }

    val folderSortType by viewModel.folderSortType.collectAsState()
    val fileSortType = viewModel.getFileSortType(currentFolderPath)

    val context = LocalContext.current
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            viewModel.addScanFolder(it)
        }
    }

    BackHandler(enabled = isSelectionMode || showTagManager || (selectedTab == LibraryTab.FOLDERS && currentFolderPath != null)) {
        when {
            showTagManager -> showTagManager = false
            isSelectionMode -> viewModel.exitSelectionMode()
            selectedTab == LibraryTab.FOLDERS && currentFolderPath != null -> viewModel.navigateUp()
        }
    }

    val currentViewList = when (selectedTab) {
        LibraryTab.RECORDS -> recordGroups.flatMap { it.audioFiles }
        LibraryTab.FAVORITES -> favorites
        LibraryTab.HISTORY -> history
        LibraryTab.TAGS -> audioFilesByTag
        LibraryTab.FOLDERS -> folderContent.mapNotNull { (it as? FileSystemItem.File)?.audioFile }
    }

    val handleFileSelected: (AudioFile) -> Unit = { audioFile ->
        viewModel.setPlaylistForAudio(audioFile)
        onFileSelected(audioFile)
    }

    if (showTagManager) {
        TagManagementScreen(
            allTags = allTags,
            onBack = { showTagManager = false },
            onDeleteTag = { viewModel.deleteTag(it) },
            onUpdateTag = { viewModel.updateTag(it) },
            onCreateTag = { name, parentId -> viewModel.createTag(name, parentId) }
        )
    } else {
        Scaffold(
            topBar = {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text("已选择 ${selectedIds.size} 项") },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.exitSelectionMode() }) {
                                Icon(Icons.Default.Close, contentDescription = "取消")
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.selectAll(currentViewList) }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "全选")
                            }
                            IconButton(onClick = { showBatchAddTagDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "添加标签")
                            }
                            IconButton(onClick = { viewModel.deleteSelected() }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (!isSelectionMode) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.search(it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("搜索音频...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.search("") }) { Icon(Icons.Default.Close, null) } },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    ScrollableTabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 16.dp
                    ) {
                        Tab(selected = selectedTab == LibraryTab.RECORDS, onClick = { selectedTab = LibraryTab.RECORDS }, text = { Text("记录") })
                        Tab(selected = selectedTab == LibraryTab.FAVORITES, onClick = { selectedTab = LibraryTab.FAVORITES }, text = { Text("收藏") })
                        Tab(selected = selectedTab == LibraryTab.HISTORY, onClick = { selectedTab = LibraryTab.HISTORY }, text = { Text("历史") })
                        Tab(selected = selectedTab == LibraryTab.TAGS, onClick = { selectedTab = LibraryTab.TAGS }, text = { Text("标签") })
                        Tab(selected = selectedTab == LibraryTab.FOLDERS, onClick = { selectedTab = LibraryTab.FOLDERS }, text = { Text("文件夹") })
                    }
                }

                if (isScanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                when (selectedTab) {
                    LibraryTab.RECORDS -> {
                        RecordsGroupedSection(
                            groups = recordGroups,
                            currentPlayingAudioId = currentPlayingAudioId,
                            expandedGroupPaths = expandedGroupPaths, // 传入状态
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onToggleGroup = { viewModel.toggleGroupExpansion(it) }, // 传入回调
                            onFileClick = { if (isSelectionMode) viewModel.toggleSelection(it.id) else handleFileSelected(it) },
                            onFileLongClick = { viewModel.showAudioDetails(it) },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                            onAddTagClick = { showAddToTagDialog = it },
                            onDeleteGroup = { folderPath -> viewModel.clearFolderPlayHistory(folderPath) }
                        )
                    }
                    LibraryTab.FAVORITES -> {
                        AudioFileList(
                            audioFiles = favorites,
                            currentPlayingAudioId = currentPlayingAudioId,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onFileClick = { if (isSelectionMode) viewModel.toggleSelection(it.id) else handleFileSelected(it) },
                            onFileLongClick = { viewModel.showAudioDetails(it) },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                            onDeleteClick = { viewModel.deleteAudio(it) },
                            onAddTagClick = { showAddToTagDialog = it }
                        )
                    }
                    LibraryTab.HISTORY -> {
                        AudioFileList(
                            audioFiles = history,
                            currentPlayingAudioId = currentPlayingAudioId,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onFileClick = { if (isSelectionMode) viewModel.toggleSelection(it.id) else handleFileSelected(it) },
                            onFileLongClick = { viewModel.showAudioDetails(it) },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                            onDeleteClick = { viewModel.deleteAudio(it) },
                            onAddTagClick = { showAddToTagDialog = it }
                        )
                    }
                    LibraryTab.TAGS -> {
                        TagsSection(
                            allTags = allTags,
                            selectedTagId = selectedTagId,
                            audioFiles = audioFilesByTag,
                            currentPlayingAudioId = currentPlayingAudioId,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onTagClick = { viewModel.selectTag(it) },
                            onManageTags = { showTagManager = true },
                            onFileClick = { if (isSelectionMode) viewModel.toggleSelection(it.id) else handleFileSelected(it) },
                            onFileLongClick = { viewModel.showAudioDetails(it) },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                            onAddTagClick = { showAddToTagDialog = it }
                        )
                    }
                    LibraryTab.FOLDERS -> {
                        FoldersExplorerSection(
                            viewModel = viewModel, // [问题3修复] 传入 ViewModel 方便存取状态
                            currentPath = currentFolderPath,
                            items = folderContent,
                            currentPlayingAudioId = currentPlayingAudioId,
                            expandedFolders = expandedFolders,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            isRefreshing = isRefreshing,
                            folderSortType = folderSortType,
                            fileSortType = fileSortType,
                            onRefresh = { viewModel.refreshFolders() },
                            onNavigate = { viewModel.navigateToFolder(it) },
                            onNavigateUp = { viewModel.navigateUp() },
                            onToggleExpand = { viewModel.toggleFolderExpanded(it) },
                            onFileClick = { if (isSelectionMode) viewModel.toggleSelection(it.id) else handleFileSelected(it) },
                            onFileLongClick = { viewModel.showAudioDetails(it) },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                            onAddTagClick = { showAddToTagDialog = it },
                            onAddScanFolder = { folderPickerLauncher.launch(null) },
                            // [问题3修复] 使用 removeScanFolderByItem
                            onRemoveScanFolder = { folder -> viewModel.removeScanFolderByItem(folder) },
                            onScanAll = { viewModel.scanAllFolders() },
                            onFolderSortChange = { viewModel.setFolderSortType(it) },
                            onFileSortChange = { sortType ->
                                currentFolderPath?.let { viewModel.setFileSortType(it, sortType) }
                            }
                        )
                    }
                }
            }
        }
    }
    // ... 对话框代码保持不变 ...
    if (showAddTagDialog) {
        AddTagDialog(
            availableParents = allTags,
            onDismiss = { showAddTagDialog = false },
            onConfirm = { name, parentId ->
                viewModel.createTag(name, parentId)
                showAddTagDialog = false
            }
        )
    }
    showAddToTagDialog?.let { audioFile ->
        AddToTagDialog(
            audioFile = audioFile,
            tags = allTags,
            onDismiss = { showAddToTagDialog = null },
            onTagSelected = { tag ->
                viewModel.addTagToAudio(audioFile.id, tag.id)
                showAddToTagDialog = null
            }
        )
    }
    if (showBatchAddTagDialog) {
        AddToTagDialog(
            audioFile = AudioFile(0, "", "", 0),
            tags = allTags,
            onDismiss = { showBatchAddTagDialog = false },
            onTagSelected = { tag ->
                viewModel.addTagsToSelected(tag.id)
                showBatchAddTagDialog = false
            }
        )
    }
    audioDetails?.let { details ->
        AudioDetailsDialog(
            details = details,
            onDismiss = { viewModel.dismissAudioDetails() }
        )
    }
}

// ... AudioDetailsDialog 等辅助组件保持不变 ...
@Composable
fun AudioDetailsDialog(
    details: AudioFileDetails,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AudioFile, contentDescription = null) },
        title = {
            Text(
                text = details.audioFile.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("文件夹", details.parentFolder)
                DetailRow("时长", details.formattedDuration)
                DetailRow("大小", details.formattedSize)
                DetailRow("字幕", if (details.hasSubtitle) "有" else "无")
                DetailRow("播放次数", "${details.audioFile.playCount} 次")
                if (details.audioFile.lastPlayedAt != null) {
                    DetailRow("最近播放", formatTimestamp(details.audioFile.lastPlayedAt))
                }

                Text(
                    text = "文件路径",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = details.audioFile.path,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

@Composable
fun RecordsGroupedSection(
    groups: List<FolderGroup>,
    currentPlayingAudioId: Long,
    expandedGroupPaths: Set<String>, // 新增参数
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggleGroup: (String) -> Unit, // 新增回调
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    onFavoriteClick: (AudioFile) -> Unit,
    onAddTagClick: (AudioFile) -> Unit,
    onDeleteGroup: (String) -> Unit
) {
    if (groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "暂无播放记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(groups, key = { it.folderPath }) { group ->
                FolderGroupCard(
                    group = group,
                    isExpanded = expandedGroupPaths.contains(group.folderPath), // 传递状态
                    onToggleExpand = { onToggleGroup(group.folderPath) }, // 传递回调
                    currentPlayingAudioId = currentPlayingAudioId,
                    isSelectionMode = isSelectionMode,
                    selectedIds = selectedIds,
                    onFileClick = onFileClick,
                    onFileLongClick = onFileLongClick,
                    onFavoriteClick = onFavoriteClick,
                    onAddTagClick = onAddTagClick,
                    onDeleteGroup = { onDeleteGroup(group.folderPath) }
                )
            }
        }
    }
}

@Composable
fun FolderGroupCard(
    group: FolderGroup,
    isExpanded: Boolean, // 修改
    onToggleExpand: () -> Unit, // 修改
    currentPlayingAudioId: Long,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    onFavoriteClick: (AudioFile) -> Unit,
    onAddTagClick: (AudioFile) -> Unit,
    onDeleteGroup: () -> Unit
) {
    // 移除本地状态 var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val containsPlaying = group.audioFiles.any { it.id == currentPlayingAudioId }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Surface(
                onClick = onToggleExpand, // 使用外部回调
                color = if (containsPlaying)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.folderName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${group.audioFiles.size} 首音频",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (containsPlaying) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "正在播放",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("清除播放记录") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    }

                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "收起" else "展开"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded, // 使用外部状态
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    group.audioFiles.forEach { audioFile ->
                        CompactAudioFileItem(
                            audioFile = audioFile,
                            isCurrentlyPlaying = audioFile.id == currentPlayingAudioId,
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedIds.contains(audioFile.id),
                            onClick = { onFileClick(audioFile) },
                            onLongClick = { onFileLongClick(audioFile) },
                            onFavoriteClick = { onFavoriteClick(audioFile) },
                            onAddTagClick = { onAddTagClick(audioFile) }
                        )
                        if (audioFile != group.audioFiles.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteSweep, null) },
            title = { Text("清除播放记录") },
            text = { Text("确定要清除「${group.folderName}」文件夹的播放记录吗？\n音频文件不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteGroup()
                }) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactAudioFileItem(
    audioFile: AudioFile,
    isCurrentlyPlaying: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onAddTagClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val backgroundColor = when {
        isCurrentlyPlaying -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            if (isCurrentlyPlaying) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = "正在播放",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            // 标题完整换行显示，便于一眼找到目标音频
            Text(
                text = audioFile.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Row {
                if (audioFile.lrcPath != null) {
                    Text(
                        "字幕",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (audioFile.playCount > 0) {
                    Text(
                        "${audioFile.playCount}次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!isSelectionMode) {
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (audioFile.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (audioFile.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ... FoldersExplorerSection, FolderListItem 等其他组件保持不变 (除了上面的 onRemoveScanFolder 调用点已修改) ...

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersExplorerSection(
    viewModel: LibraryViewModel, // [问题3修复] 传入 ViewModel
    currentPath: String?,
    items: List<FileSystemItem>,
    currentPlayingAudioId: Long,
    expandedFolders: Set<String>,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    isRefreshing: Boolean,
    folderSortType: FolderSortType,
    fileSortType: FileSortType,
    onRefresh: () -> Unit,
    onNavigate: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onToggleExpand: (String) -> Unit,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    onFavoriteClick: (AudioFile) -> Unit,
    onAddTagClick: (AudioFile) -> Unit,
    onAddScanFolder: () -> Unit,
    onRemoveScanFolder: (FileSystemItem.Folder) -> Unit, // 注意这里的类型
    onScanAll: () -> Unit,
    onFolderSortChange: (FolderSortType) -> Unit,
    onFileSortChange: (FileSortType) -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    var showFolderSortMenu by remember { mutableStateOf(false) }
    var showFileSortMenu by remember { mutableStateOf(false) }

    // [问题3修复] 列表状态记忆
    val listState = rememberLazyListState()

    // 当路径变更时，恢复已保存的滚动位置
    LaunchedEffect(currentPath) {
        val (index, offset) = viewModel.getScrollPosition(currentPath)
        listState.scrollToItem(index, offset)
    }

    // 在退出组件时保存当前位置（例如切换 Tab）
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveScrollPosition(
                currentPath,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    // 封装导航函数，在跳转前保存当前位置
    val handleNavigate: (String) -> Unit = { path ->
        viewModel.saveScrollPosition(
            currentPath,
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset
        )
        onNavigate(path)
    }

    val handleNavigateUp: () -> Unit = {
        viewModel.saveScrollPosition(
            currentPath,
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset
        )
        onNavigateUp()
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (currentPath == null) {
                // 根目录工具栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onAddScanFolder, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CreateNewFolder, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加文件夹", maxLines = 1)
                    }

                    // 文件夹排序按钮
                    Box {
                        OutlinedButton(onClick = { showFolderSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("排序", maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = showFolderSortMenu,
                            onDismissRequest = { showFolderSortMenu = false }
                        ) {
                            FolderSortType.entries.forEach { sortType ->
                                DropdownMenuItem(
                                    text = { Text(sortType.displayName) },
                                    onClick = {
                                        onFolderSortChange(sortType)
                                        showFolderSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortType == folderSortType) {
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = onScanAll) {
                        Icon(Icons.Default.Refresh, "扫描全部")
                    }
                }
            } else {
                // 子文件夹工具栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = handleNavigateUp, // [修复] 使用带保存逻辑的导航
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "返回上一级",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showFolderSortMenu = true }) {
                            Icon(Icons.Default.Folder, null)
                        }
                        DropdownMenu(
                            expanded = showFolderSortMenu,
                            onDismissRequest = { showFolderSortMenu = false }
                        ) {
                            Text(
                                "文件夹排序",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                            FolderSortType.entries.forEach { sortType ->
                                DropdownMenuItem(
                                    text = { Text(sortType.displayName) },
                                    onClick = {
                                        onFolderSortChange(sortType)
                                        showFolderSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortType == folderSortType) {
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        IconButton(onClick = { showFileSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, "排序")
                        }
                        DropdownMenu(
                            expanded = showFileSortMenu,
                            onDismissRequest = { showFileSortMenu = false }
                        ) {
                            Text(
                                "文件排序",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                            FileSortType.entries.forEach { sortType ->
                                DropdownMenuItem(
                                    text = { Text(sortType.displayName) },
                                    onClick = {
                                        onFileSortChange(sortType)
                                        showFileSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortType == fileSortType) {
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }


            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (currentPath == null) "点击上方按钮添加音频文件夹" else "此文件夹为空",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (currentPath == null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "下拉刷新可扫描新增文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState, // [修复] 绑定状态
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(items) { item ->
                        when (item) {
                            is FileSystemItem.Folder -> {
                                FolderListItem(
                                    folder = item,
                                    isRoot = currentPath == null,
                                    onNavigate = handleNavigate, // [修复] 使用带保存逻辑的导航
                                    onRemove = if (currentPath == null) {
                                        { onRemoveScanFolder(item) } // [关键修复] 调用 ViewModel 新增的方法
                                    } else null
                                )
                            }
                            is FileSystemItem.File -> {
                                CompactAudioFileItem(
                                    audioFile = item.audioFile,
                                    isCurrentlyPlaying = item.audioFile.id == currentPlayingAudioId,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedIds.contains(item.audioFile.id),
                                    onClick = { onFileClick(item.audioFile) },
                                    onLongClick = { onFileLongClick(item.audioFile) },
                                    onFavoriteClick = { onFavoriteClick(item.audioFile) },
                                    onAddTagClick = { onAddTagClick(item.audioFile) }
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}
// ... 剩余部分如 FolderListItem, TagManagementScreen 等保持不变 ...
@Composable
fun FolderListItem(
    folder: FileSystemItem.Folder,
    isRoot: Boolean,
    onNavigate: (String) -> Unit,
    onRemove: (() -> Unit)?
) {
    ListItem(
        headlineContent = {
            Text(
                folder.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                "${folder.audioCount} 首音频",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
        },
        trailingContent = {
            Row {
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "进入",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = Modifier.clickable { onNavigate(folder.path) }
    )
}

// ... TagManagementScreen, buildTagTree, AudioFileList, AudioFileItem, TagsSection, AddTagDialog ... 保持不变
// 由于篇幅限制且这些部分无需修改，此处省略，请在实际文件中保留原有的这些函数
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagementScreen(
    allTags: List<Tag>,
    onBack: () -> Unit,
    onDeleteTag: (Tag) -> Unit,
    onUpdateTag: (Tag) -> Unit,
    onCreateTag: (String, Long?) -> Unit
) {
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }
    var tagToEdit by remember { mutableStateOf<Tag?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val displayList = remember(allTags) { buildTagTree(allTags) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("标签管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建标签")
            }
        }
    ) { padding ->
        if (allTags.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无标签", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(displayList) { (tag, depth) ->
                    ListItem(
                        modifier = Modifier.padding(start = (depth * 24).dp),
                        headlineContent = { Text(tag.name) },
                        leadingContent = { Icon(if (depth == 0) Icons.AutoMirrored.Filled.Label else Icons.Default.SubdirectoryArrowRight, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { tagToEdit = tag }) { Icon(Icons.Default.Edit, "重命名") }
                                IconButton(onClick = { tagToDelete = tag }) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }

    tagToDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text("删除标签") },
            text = { Text("确定要删除标签 \"${tag.name}\" 吗？\n所有子标签也会被级联删除。") },
            confirmButton = { TextButton(onClick = { onDeleteTag(tag); tagToDelete = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { tagToDelete = null }) { Text("取消") } }
        )
    }

    tagToEdit?.let { tag ->
        EditTagDialog(initialName = tag.name, onDismiss = { tagToEdit = null }, onConfirm = { newName -> onUpdateTag(tag.copy(name = newName)); tagToEdit = null })
    }

    if (showCreateDialog) {
        AddTagDialog(availableParents = allTags, onDismiss = { showCreateDialog = false }, onConfirm = { name, parentId -> onCreateTag(name, parentId); showCreateDialog = false })
    }
}

fun buildTagTree(tags: List<Tag>): List<Pair<Tag, Int>> {
    val result = mutableListOf<Pair<Tag, Int>>()
    val grouped = tags.groupBy { it.parentId }
    fun recurse(parentId: Long?, depth: Int) {
        grouped[parentId]?.sortedBy { it.order }?.forEach { tag ->
            result.add(tag to depth)
            recurse(tag.id, depth + 1)
        }
    }
    recurse(null, 0)
    return result
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioFileList(
    audioFiles: List<AudioFile>,
    currentPlayingAudioId: Long,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    onFavoriteClick: (AudioFile) -> Unit,
    onDeleteClick: ((AudioFile) -> Unit)?,
    onAddTagClick: ((AudioFile) -> Unit)?
) {
    if (audioFiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "未找到音频文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(audioFiles, key = { it.id }) { audioFile ->
                AudioFileItem(
                    audioFile = audioFile,
                    isCurrentlyPlaying = audioFile.id == currentPlayingAudioId,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedIds.contains(audioFile.id),
                    onClick = { onFileClick(audioFile) },
                    onLongClick = { onFileLongClick(audioFile) },
                    onFavoriteClick = { onFavoriteClick(audioFile) },
                    onDeleteClick = if (!isSelectionMode) onDeleteClick else null,
                    onAddTagClick = if (!isSelectionMode) onAddTagClick else null
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioFileItem(
    audioFile: AudioFile,
    isCurrentlyPlaying: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDeleteClick: ((AudioFile) -> Unit)?,
    onAddTagClick: ((AudioFile) -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val backgroundColor = when {
        isCurrentlyPlaying -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            }
        ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            } else {
                if (isCurrentlyPlaying) {
                    Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Default.AudioFile, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audioFile.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    if (audioFile.lrcPath != null) { Text("有字幕", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)) }
                    if (audioFile.playCount > 0) Text("播放${audioFile.playCount}次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!isSelectionMode) {
                IconButton(onClick = onFavoriteClick) { Icon(if (audioFile.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (audioFile.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        onAddTagClick?.let { DropdownMenuItem(text = { Text("添加到标签") }, onClick = { showMenu = false; it(audioFile) }) }
                        onDeleteClick?.let { DropdownMenuItem(text = { Text("删除记录") }, onClick = { showMenu = false; it(audioFile) }) }
                    }
                }
            }
        }
    }
}

@Composable
fun TagsSection(
    allTags: List<Tag>,
    selectedTagId: Long?,
    audioFiles: List<AudioFile>,
    currentPlayingAudioId: Long,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onTagClick: (Long?) -> Unit,
    onManageTags: () -> Unit,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    onFavoriteClick: (AudioFile) -> Unit,
    onAddTagClick: (AudioFile) -> Unit
) {
    var tagMenuExpanded by remember { mutableStateOf(false) }
    val tagTree = remember(allTags) { buildTagTree(allTags) }
    val tagsById = remember(allTags) { allTags.associateBy { it.id } }
    val selectedTagLabel = remember(selectedTagId, tagsById) {
        selectedTagId?.let { id ->
            tagsById[id]?.let { buildTagPathLabel(it, tagsById) }
        } ?: "全部标签"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { tagMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            selectedTagLabel,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = tagMenuExpanded,
                        onDismissRequest = { tagMenuExpanded = false },
                        modifier = Modifier.widthIn(min = 260.dp).heightIn(max = 420.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部标签") },
                            onClick = {
                                onTagClick(null)
                                tagMenuExpanded = false
                            },
                            leadingIcon = {
                                if (selectedTagId == null) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                        tagTree.forEach { (tag, depth) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = tag.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = (depth * 18).dp)
                                    )
                                },
                                onClick = {
                                    onTagClick(tag.id)
                                    tagMenuExpanded = false
                                },
                                leadingIcon = {
                                    if (selectedTagId == tag.id) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Icon(
                                            if (depth == 0) Icons.AutoMirrored.Filled.Label else Icons.Default.SubdirectoryArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onManageTags) { Icon(Icons.Default.Settings, "管理标签", tint = MaterialTheme.colorScheme.primary) }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }

        AudioFileList(
            audioFiles = audioFiles,
            currentPlayingAudioId = currentPlayingAudioId,
            isSelectionMode = isSelectionMode,
            selectedIds = selectedIds,
            onFileClick = onFileClick,
            onFileLongClick = onFileLongClick,
            onFavoriteClick = onFavoriteClick,
            onDeleteClick = null,
            onAddTagClick = if(!isSelectionMode) onAddTagClick else null
        )
    }
}

private fun buildTagPathLabel(tag: Tag, tagsById: Map<Long, Tag>): String {
    val names = mutableListOf<String>()
    var current: Tag? = tag
    repeat(16) {
        val node = current ?: return@repeat
        names += node.name
        current = node.parentId?.let(tagsById::get)
    }
    return names.asReversed().joinToString(" / ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTagDialog(availableParents: List<Tag>, onDismiss: () -> Unit, onConfirm: (String, Long?) -> Unit) {
    var tagName by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedParent by remember { mutableStateOf<Tag?>(null) }
    val parentOptions = remember(availableParents) { availableParents.filter { it.parentId == null } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建新标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = tagName, onValueChange = { tagName = it }, label = { Text("标签名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(readOnly = true, value = selectedParent?.name ?: "无 (作为根标签)", onValueChange = { }, label = { Text("父标签") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(), modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("无 (作为根标签)") }, onClick = { selectedParent = null; expanded = false })
                        parentOptions.forEach { parent -> DropdownMenuItem(text = { Text(parent.name) }, onClick = { selectedParent = parent; expanded = false }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (tagName.isNotBlank()) onConfirm(tagName, selectedParent?.id) }, enabled = tagName.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun EditTagDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var tagName by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("重命名标签") },
        text = { OutlinedTextField(value = tagName, onValueChange = { tagName = it }, label = { Text("标签名称") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (tagName.isNotBlank()) onConfirm(tagName) }, enabled = tagName.isNotBlank() && tagName != initialName) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun AddToTagDialog(audioFile: AudioFile, tags: List<Tag>, onDismiss: () -> Unit, onTagSelected: (Tag) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("添加到标签") },
        text = {
            if (tags.isEmpty()) Box(modifier = Modifier.padding(16.dp)) { Text("暂无可用标签") } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    val tree = buildTagTree(tags)
                    items(tree) { (tag, depth) ->
                        ListItem(modifier = Modifier.clickable { onTagSelected(tag) }.padding(start = (depth * 24).dp), headlineContent = { Text(tag.name) }, leadingContent = { Icon(
                            Icons.AutoMirrored.Filled.Label, null) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
