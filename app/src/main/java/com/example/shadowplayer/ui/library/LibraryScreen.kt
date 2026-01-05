package com.example.shadowplayer.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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

enum class LibraryTab {
    ALL, FAVORITES, TAGS, FOLDERS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onFileSelected: (AudioFile) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val audioFiles by viewModel.audioFiles.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val scanFolders by viewModel.scanFolders.collectAsState()
    val rootTags by viewModel.rootTags.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val selectedTagId by viewModel.selectedTagId.collectAsState()
    val audioFilesByTag by viewModel.audioFilesByTag.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    // 获取搜索状态
    val searchQuery by viewModel.searchQuery.collectAsState()

    var selectedTab by remember { mutableStateOf(LibraryTab.ALL) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showAddToTagDialog by remember { mutableStateOf<AudioFile?>(null) }
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

    BackHandler(enabled = showTagManager) {
        showTagManager = false
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
        Column(modifier = Modifier.fillMaxSize()) {

            // 1. 新增：搜索栏 (固定在顶部)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索音频...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(Icons.Default.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // Tab 栏
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 16.dp
            ) {
                Tab(
                    selected = selectedTab == LibraryTab.ALL,
                    onClick = { selectedTab = LibraryTab.ALL },
                    text = { Text("全部") }
                )
                Tab(
                    selected = selectedTab == LibraryTab.FAVORITES,
                    onClick = { selectedTab = LibraryTab.FAVORITES },
                    text = { Text("收藏") }
                )
                Tab(
                    selected = selectedTab == LibraryTab.TAGS,
                    onClick = { selectedTab = LibraryTab.TAGS },
                    text = { Text("标签") }
                )
                Tab(
                    selected = selectedTab == LibraryTab.FOLDERS,
                    onClick = { selectedTab = LibraryTab.FOLDERS },
                    text = { Text("文件夹") }
                )
            }

            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (selectedTab) {
                LibraryTab.ALL -> {
                    // 这里的 audioFiles 已经是经过 search 过滤的了
                    AudioFileList(
                        audioFiles = audioFiles,
                        onFileClick = onFileSelected,
                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                        onDeleteClick = { viewModel.deleteAudio(it) },
                        onAddTagClick = { showAddToTagDialog = it }
                    )
                }
                LibraryTab.FAVORITES -> {
                    AudioFileList(
                        audioFiles = favorites,
                        onFileClick = onFileSelected,
                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                        onDeleteClick = null,
                        onAddTagClick = { showAddToTagDialog = it }
                    )
                }
                LibraryTab.TAGS -> {
                    TagsSection(
                        allTags = allTags,
                        selectedTagId = selectedTagId,
                        audioFiles = audioFilesByTag, // 也支持搜索过滤
                        onTagClick = { viewModel.selectTag(it) },
                        onManageTags = { showTagManager = true },
                        onFileClick = onFileSelected,
                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                        onAddTagClick = { showAddToTagDialog = it }
                    )
                }
                LibraryTab.FOLDERS -> {
                    FoldersSection(
                        folders = scanFolders,
                        onAddFolder = { folderPickerLauncher.launch(null) },
                        onRemoveFolder = { viewModel.removeScanFolder(it) },
                        onScanFolder = { viewModel.scanFolder(it) },
                        onScanAll = { viewModel.scanAllFolders() }
                    )
                }
            }
        }
    }

    // ... (AddTagDialog, AddToTagDialog 等保持不变，包含在你之前的代码中)
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
}

// ... (TagManagementScreen, AudioFileList, TagsSection 等组件代码，复用上一次回答中的代码)
// 这里为了篇幅只列出了有变动的 LibraryScreen 主体部分。
// 下面补充必须存在的辅助组件，确保文件完整可用。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagementScreen(
    allTags: List<Tag>,
    onBack: () -> Unit,
    onDeleteTag: (Tag) -> Unit,
    onUpdateTag: (Tag) -> Unit,
    onCreateTag: (String, Long?) -> Unit
) {
    // (代码与上一次回答一致，此处略，请保留上一次的完整实现)
    // 如果需要我再次完整贴出，请告诉我。
    // 为了方便你直接复制，这里简略处理，重点是上面的 SearchBar。
    // ...
    // 但考虑到你可能直接覆盖文件，我还是把关键的 TagsSection 和 AudioFileList 完整贴上。

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
    onFileClick: (AudioFile) -> Unit,
    onFavoriteClick: (AudioFile) -> Unit,
    onDeleteClick: ((AudioFile) -> Unit)? = null,
    onAddTagClick: ((AudioFile) -> Unit)? = null
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
                AudioFileItem(audioFile = audioFile, onClick = { onFileClick(audioFile) }, onFavoriteClick = { onFavoriteClick(audioFile) }, onDeleteClick = onDeleteClick, onAddTagClick = onAddTagClick)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioFileItem(
    audioFile: AudioFile,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDeleteClick: ((AudioFile) -> Unit)?,
    onAddTagClick: ((AudioFile) -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showMenu = true })) {
        Box {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AudioFile, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = audioFile.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row {
                        if (audioFile.lrcPath != null) { Text("有字幕", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)) }
                        if (audioFile.playCount > 0) Text("播放${audioFile.playCount}次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onFavoriteClick) { Icon(if (audioFile.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏", tint = if (audioFile.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                onAddTagClick?.let {
                    DropdownMenuItem(text = { Text("添加到标签") }, onClick = { showMenu = false; it(audioFile) }, leadingIcon = { Icon(Icons.Default.Label, null) })
                }
                onDeleteClick?.let {
                    DropdownMenuItem(text = { Text("删除记录") }, onClick = { showMenu = false; showDeleteConfirmDialog = true }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                }
            }
        }
    }
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要从列表中移除音频 \"${audioFile.title}\" 吗？") },
            confirmButton = { TextButton(onClick = { onDeleteClick?.invoke(audioFile); showDeleteConfirmDialog = false }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
fun TagsSection(
    allTags: List<Tag>,
    selectedTagId: Long?,
    audioFiles: List<AudioFile>,
    onTagClick: (Long?) -> Unit,
    onManageTags: () -> Unit,
    onFileClick: (AudioFile) -> Unit,
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
        AudioFileList(audioFiles = audioFiles, onFileClick = onFileClick, onFavoriteClick = onFavoriteClick, onAddTagClick = onAddTagClick)
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