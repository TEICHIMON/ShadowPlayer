package com.example.shadowplayer.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
    val audioFiles by viewModel.audioFiles.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState() // [新增]
    val rootTags by viewModel.rootTags.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val selectedTagId by viewModel.selectedTagId.collectAsState()
    val audioFilesByTag by viewModel.audioFilesByTag.collectAsState()

    // 文件夹视图状态
    val scanFolders by viewModel.scanFolders.collectAsState()
    val folderContent by viewModel.folderContent.collectAsState()
    val currentFolderPath by viewModel.currentFolderPath.collectAsState()

    val isScanning by viewModel.isScanning.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds by viewModel.selectedAudioIds.collectAsState()

    var selectedTab by remember { mutableStateOf(LibraryTab.ALL) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showAddToTagDialog by remember { mutableStateOf<AudioFile?>(null) }
    var showBatchAddTagDialog by remember { mutableStateOf(false) }
    var showTagManager by remember { mutableStateOf(false) }

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

    // 处理返回键：文件夹导航 > 标签管理 > 选择模式
    BackHandler(enabled = isSelectionMode || showTagManager || (selectedTab == LibraryTab.FOLDERS && currentFolderPath != null)) {
        when {
            showTagManager -> showTagManager = false
            isSelectionMode -> viewModel.exitSelectionMode()
            selectedTab == LibraryTab.FOLDERS && currentFolderPath != null -> viewModel.navigateUp()
        }
    }

    val currentViewList = when (selectedTab) {
        LibraryTab.ALL -> audioFiles
        LibraryTab.FAVORITES -> favorites
        LibraryTab.HISTORY -> history
        LibraryTab.TAGS -> audioFilesByTag
        LibraryTab.FOLDERS -> folderContent.mapNotNull { (it as? FileSystemItem.File)?.audioFile } // 仅在文件夹模式下提取文件用于全选
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
                                Icon(Icons.Default.Label, contentDescription = "添加标签")
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
                        Tab(selected = selectedTab == LibraryTab.ALL, onClick = { selectedTab = LibraryTab.ALL }, text = { Text("全部") })
                        Tab(selected = selectedTab == LibraryTab.FAVORITES, onClick = { selectedTab = LibraryTab.FAVORITES }, text = { Text("收藏") })
                        Tab(selected = selectedTab == LibraryTab.HISTORY, onClick = { selectedTab = LibraryTab.HISTORY }, text = { Text("历史") }) // [新增]
                        Tab(selected = selectedTab == LibraryTab.TAGS, onClick = { selectedTab = LibraryTab.TAGS }, text = { Text("标签") })
                        Tab(selected = selectedTab == LibraryTab.FOLDERS, onClick = { selectedTab = LibraryTab.FOLDERS }, text = { Text("文件夹") })
                    }
                }

                if (isScanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                when (selectedTab) {
                    LibraryTab.ALL, LibraryTab.FAVORITES, LibraryTab.HISTORY -> {
                        AudioFileList(
                            audioFiles = if (selectedTab == LibraryTab.ALL) audioFiles else if (selectedTab == LibraryTab.FAVORITES) favorites else history,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onFileClick = { if (isSelectionMode) viewModel.toggleSelection(it.id) else onFileSelected(it) },
                            onFileLongClick = { viewModel.enterSelectionMode(it.id) },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                            onDeleteClick = { viewModel.deleteAudio(it) },
                            onAddTagClick = { showAddToTagDialog = it }
                        )
                    }
                    LibraryTab.TAGS -> {
                        TagsSection(
                            allTags = allTags, selectedTagId = selectedTagId, audioFiles = audioFilesByTag,
                            isSelectionMode = isSelectionMode, selectedIds = selectedIds,
                            onTagClick = { viewModel.selectTag(it) }, onManageTags = { showTagManager = true },
                            onFileClick = { if (isSelectionMode) viewModel.toggleSelection(it.id) else onFileSelected(it) },
                            onFileLongClick = { viewModel.enterSelectionMode(it.id) },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                            onAddTagClick = { showAddToTagDialog = it }
                        )
                    }
                    LibraryTab.FOLDERS -> {
                        FoldersExplorerSection(
                            currentPath = currentFolderPath,
                            items = folderContent,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onNavigate = { viewModel.navigateToFolder(it) },
                            onNavigateUp = { viewModel.navigateUp() },
                            onFileClick = { if (isSelectionMode) viewModel.toggleSelection(it.id) else onFileSelected(it) },
                            onFileLongClick = { viewModel.enterSelectionMode(it.id) },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                            onAddTagClick = { showAddToTagDialog = it },
                            onAddScanFolder = { folderPickerLauncher.launch(null) },
                            onRemoveScanFolder = { folder -> // 需要通过路径找到对应的 ScanFolder 对象
                                val scanFolder = scanFolders.find { it.path == folder.path }
                                scanFolder?.let { viewModel.removeScanFolder(it) }
                            },
                            onScanAll = { viewModel.scanAllFolders() }
                        )
                    }
                }
            }
        }
    }

    // Dialogs
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

    // 单个添加标签
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

    // [新增] 批量添加标签
    if (showBatchAddTagDialog) {
        AddToTagDialog(
            audioFile = AudioFile(0, "", "", 0), // 这里的 file 仅作占位，不影响 UI 显示
            tags = allTags,
            onDismiss = { showBatchAddTagDialog = false },
            onTagSelected = { tag ->
                viewModel.addTagsToSelected(tag.id)
                showBatchAddTagDialog = false
            }
        )
    }
}



// ---------------- 辅助组件 ----------------

// [新增] 文件夹浏览器视图
@Composable
fun FoldersExplorerSection(
    currentPath: String?,
    items: List<FileSystemItem>,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onNavigate: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    onFavoriteClick: (AudioFile) -> Unit,
    onAddTagClick: (AudioFile) -> Unit,
    onAddScanFolder: () -> Unit,
    onRemoveScanFolder: (FileSystemItem.Folder) -> Unit,
    onScanAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部导航栏
        if (currentPath == null) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddScanFolder) { Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("添加文件夹") }
                OutlinedButton(onClick = onScanAll) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("扫描全部") }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { onNavigateUp() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("返回上一级", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Divider()
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items) { item ->
                when (item) {
                    is FileSystemItem.Folder -> {
                        ListItem(
                            headlineContent = { Text(item.name) },
                            leadingContent = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.secondary) },
                            modifier = Modifier.clickable { onNavigate(item.path) },
                            trailingContent = if (currentPath == null) {
                                { IconButton(onClick = { onRemoveScanFolder(item) }) { Icon(Icons.Default.Delete, null) } }
                            } else null
                        )
                    }
                    is FileSystemItem.File -> {
                        AudioFileItem(
                            audioFile = item.audioFile,
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedIds.contains(item.audioFile.id),
                            onClick = { onFileClick(item.audioFile) },
                            onLongClick = { onFileLongClick(item.audioFile) },
                            onFavoriteClick = { onFavoriteClick(item.audioFile) },
                            onDeleteClick = null, // 文件夹视图暂不支持直接删除文件
                            onAddTagClick = onAddTagClick
                        )
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

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
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
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
                        leadingContent = { Icon(if (depth == 0) Icons.Default.Label else Icons.Default.SubdirectoryArrowRight, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { tagToEdit = tag }) { Icon(Icons.Default.Edit, "重命名") }
                                IconButton(onClick = { tagToDelete = tag }) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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

@Composable
fun AudioFileList(
    audioFiles: List<AudioFile>,
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

// 修改 AudioFileItem 以支持跑马灯

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioFileItem(
    audioFile: AudioFile,
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
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (isSelectionMode) onClick() else onLongClick()
            }
        ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            } else {
                Icon(Icons.Default.AudioFile, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // [修改] 使用 basicMarquee 显示完整文件名
                Text(
                    text = audioFile.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee() // 跑马灯效果
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
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onTagClick: (Long?) -> Unit,
    onManageTags: () -> Unit,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    onFavoriteClick: (AudioFile) -> Unit,
    onAddTagClick: (AudioFile) -> Unit
) {
    val rootTags = remember(allTags) { allTags.filter { it.parentId == null } }
    val activeRootId = remember(selectedTagId, allTags) {
        if (selectedTagId == null) return@remember null
        val selected = allTags.find { it.id == selectedTagId } ?: return@remember null
        if (selected.parentId == null) selected.id else selected.parentId
    }
    val displayedSubTags = remember(activeRootId, allTags) {
        if (activeRootId == null) emptyList() else allTags.filter { it.parentId == activeRootId }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isSelectionMode) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = selectedTagId == null, onClick = { onTagClick(null) }, label = { Text("全部") }) }
                    items(rootTags) { tag ->
                        FilterChip(
                            selected = activeRootId == tag.id,
                            onClick = { onTagClick(tag.id) },
                            label = { Text(tag.name) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        )
                    }
                }
                IconButton(onClick = onManageTags) { Icon(Icons.Default.Settings, "管理标签", tint = MaterialTheme.colorScheme.primary) }
            }

            if (displayedSubTags.isNotEmpty()) {
                LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayedSubTags) { tag ->
                        FilterChip(selected = selectedTagId == tag.id, onClick = { onTagClick(tag.id) }, label = { Text(tag.name) })
                    }
                }
            }
            Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }

        AudioFileList(
            audioFiles = audioFiles,
            isSelectionMode = isSelectionMode,
            selectedIds = selectedIds,
            onFileClick = onFileClick,
            onFileLongClick = onFileLongClick,
            onFavoriteClick = onFavoriteClick,
            onDeleteClick = if(!isSelectionMode) {{ /* 删除由外部处理 */ }} else null, // TagsSection 的删除暂不支持单项删除或需传递
            onAddTagClick = if(!isSelectionMode) onAddTagClick else null
        )
    }
}

@Composable
fun FoldersSection(folders: List<ScanFolder>, onAddFolder: () -> Unit, onRemoveFolder: (ScanFolder) -> Unit, onScanFolder: (ScanFolder) -> Unit, onScanAll: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddFolder) { Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("添加文件夹") }
            OutlinedButton(onClick = onScanAll) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("扫描全部") }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(folders, key = { it.id }) { folder ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                        Text(folder.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { onScanFolder(folder) }) { Icon(Icons.Default.Refresh, "扫描") }
                        IconButton(onClick = { onRemoveFolder(folder) }) { Icon(Icons.Default.Delete, "删除") }
                    }
                }
            }
        }
    }
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
                        ListItem(modifier = Modifier.clickable { onTagSelected(tag) }.padding(start = (depth * 24).dp), headlineContent = { Text(tag.name) }, leadingContent = { Icon(Icons.Default.Label, null) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}