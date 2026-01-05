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
    val selectedTagId by viewModel.selectedTagId.collectAsState()
    val audioFilesByTag by viewModel.audioFilesByTag.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var selectedTab by remember { mutableStateOf(LibraryTab.ALL) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showAddToTagDialog by remember { mutableStateOf<AudioFile?>(null) }

    // 新增：控制是否显示标签管理页面
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

    // 如果处于管理页面，拦截返回键
    BackHandler(enabled = showTagManager) {
        showTagManager = false
    }

    if (showTagManager) {
        // 显示独立的标签管理页面
        TagManagementScreen(
            tags = rootTags,
            onBack = { showTagManager = false },
            onDeleteTag = { viewModel.deleteTag(it) },
            onUpdateTag = { viewModel.updateTag(it) },
            onCreateTag = { viewModel.createTag(it) }
        )
    } else {
        // 显示主界面
        Column(modifier = Modifier.fillMaxSize()) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == LibraryTab.ALL,
                    onClick = { selectedTab = LibraryTab.ALL },
                    text = { Text("全部 (${audioFiles.size})") }
                )
                Tab(
                    selected = selectedTab == LibraryTab.FAVORITES,
                    onClick = { selectedTab = LibraryTab.FAVORITES },
                    text = { Text("收藏 (${favorites.size})") }
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
                    // 标签视图
                    TagsSection(
                        tags = rootTags,
                        selectedTagId = selectedTagId,
                        audioFiles = audioFilesByTag,
                        onTagClick = { viewModel.selectTag(it) },
                        onManageTags = { showTagManager = true }, // 进入管理页
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

    if (showAddTagDialog) {
        AddTagDialog(
            onDismiss = { showAddTagDialog = false },
            onConfirm = { name ->
                viewModel.createTag(name)
                showAddTagDialog = false
            }
        )
    }

    showAddToTagDialog?.let { audioFile ->
        AddToTagDialog(
            audioFile = audioFile,
            tags = rootTags,
            onDismiss = { showAddToTagDialog = null },
            onTagSelected = { tag ->
                viewModel.addTagToAudio(audioFile.id, tag.id)
                showAddToTagDialog = null
            }
        )
    }
}

/**
 * 独立的标签管理页面 (Option B)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagementScreen(
    tags: List<Tag>,
    onBack: () -> Unit,
    onDeleteTag: (Tag) -> Unit,
    onUpdateTag: (Tag) -> Unit,
    onCreateTag: (String) -> Unit
) {
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }
    var tagToEdit by remember { mutableStateOf<Tag?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("标签管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建标签")
            }
        }
    ) { padding ->
        if (tags.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无标签",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp) // 避让FAB
            ) {
                items(tags, key = { it.id }) { tag ->
                    ListItem(
                        headlineContent = { Text(tag.name) },
                        leadingContent = {
                            Icon(Icons.Default.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Row {
                                // 编辑按钮
                                IconButton(onClick = { tagToEdit = tag }) {
                                    Icon(Icons.Default.Edit, contentDescription = "重命名")
                                }
                                // 删除按钮
                                IconButton(onClick = { tagToDelete = tag }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }

    // 删除确认弹窗
    tagToDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text("删除标签") },
            text = { Text("确定要删除标签 \"${tag.name}\" 吗？\n所有文件将失去此分类，但文件本身不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTag(tag)
                        tagToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) { Text("取消") }
            }
        )
    }

    // 编辑/重命名弹窗
    tagToEdit?.let { tag ->
        EditTagDialog(
            initialName = tag.name,
            onDismiss = { tagToEdit = null },
            onConfirm = { newName ->
                onUpdateTag(tag.copy(name = newName))
                tagToEdit = null
            }
        )
    }

    // 新建弹窗
    if (showCreateDialog) {
        AddTagDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreateTag(name)
                showCreateDialog = false
            }
        )
    }
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无音频文件\n请先添加扫描文件夹",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    onClick = { onFileClick(audioFile) },
                    onFavoriteClick = { onFavoriteClick(audioFile) },
                    onDeleteClick = onDeleteClick,
                    onAddTagClick = onAddTagClick
                )
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                }
            )
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = audioFile.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row {
                        if (audioFile.lrcPath != null) {
                            Text(
                                text = "有字幕",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (audioFile.playCount > 0) {
                            Text(
                                text = "播放${audioFile.playCount}次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = if (audioFile.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (audioFile.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (onAddTagClick != null) {
                    DropdownMenuItem(
                        text = { Text("添加到标签") },
                        onClick = {
                            showMenu = false
                            onAddTagClick(audioFile)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Label, contentDescription = null)
                        }
                    )
                }

                if (onDeleteClick != null) {
                    DropdownMenuItem(
                        text = { Text("删除记录") },
                        onClick = {
                            showMenu = false
                            showDeleteConfirmDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    )
                } else {
                    if (onAddTagClick == null) {
                        DropdownMenuItem(
                            text = { Text("暂无操作") },
                            onClick = { showMenu = false }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要从列表中移除音频 \"${audioFile.title}\" 吗？\n（这不会删除本地源文件）") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick?.invoke(audioFile)
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun TagsSection(
    tags: List<Tag>,
    selectedTagId: Long?,
    audioFiles: List<AudioFile>,
    onTagClick: (Long?) -> Unit,
    onManageTags: () -> Unit, // 修改：不再传入删除回调，而是管理回调
    onFileClick: (AudioFile) -> Unit,
    onFavoriteClick: (AudioFile) -> Unit,
    onAddTagClick: (AudioFile) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 标签列表
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedTagId == null,
                        onClick = { onTagClick(null) },
                        label = { Text("全部") }
                    )
                }
                items(tags) { tag ->
                    // 纯净的筛选 Chip，不再包含删除按钮
                    FilterChip(
                        selected = selectedTagId == tag.id,
                        onClick = { onTagClick(tag.id) },
                        label = { Text(tag.name) }
                    )
                }
            }

            // 入口：标签管理
            IconButton(onClick = onManageTags) {
                Icon(
                    imageVector = Icons.Default.Settings, // 或者 Icons.Default.Edit
                    contentDescription = "管理标签",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // 音频列表
        if (selectedTagId != null) {
            // 显示选中标签下的文件
            AudioFileList(
                audioFiles = audioFiles,
                onFileClick = onFileClick,
                onFavoriteClick = onFavoriteClick,
                onAddTagClick = onAddTagClick
            )
        } else {
            // 选中“全部”时也显示引导或者空状态，或者可以选择显示所有文件。
            // 按照原逻辑，Tag Tab 下只有选中 Tag 才显示文件，未选中显示提示。
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "请选择一个标签查看文件",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FoldersSection(
    folders: List<ScanFolder>,
    onAddFolder: () -> Unit,
    onRemoveFolder: (ScanFolder) -> Unit,
    onScanFolder: (ScanFolder) -> Unit,
    onScanAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onAddFolder) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加文件夹")
            }
            OutlinedButton(onClick = onScanAll) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("扫描全部")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(folders, key = { it.id }) { folder ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = folder.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { onScanFolder(folder) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "扫描")
                        }
                        IconButton(onClick = { onRemoveFolder(folder) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddTagDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var tagName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建新标签") },
        text = {
            OutlinedTextField(
                value = tagName,
                onValueChange = { tagName = it },
                label = { Text("标签名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (tagName.isNotBlank()) onConfirm(tagName) },
                enabled = tagName.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// 新增：编辑标签对话框
@Composable
fun EditTagDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var tagName by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名标签") },
        text = {
            OutlinedTextField(
                value = tagName,
                onValueChange = { tagName = it },
                label = { Text("标签名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (tagName.isNotBlank()) onConfirm(tagName) },
                enabled = tagName.isNotBlank() && tagName != initialName
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun AddToTagDialog(
    audioFile: AudioFile,
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onTagSelected: (Tag) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到标签") },
        text = {
            if (tags.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Text("暂无可用标签，请先在“标签”页面创建")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(tags) { tag ->
                        ListItem(
                            headlineContent = { Text(tag.name) },
                            leadingContent = {
                                Icon(Icons.Default.Label, contentDescription = null)
                            },
                            modifier = Modifier
                                .clickable { onTagSelected(tag) }
                                .fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}