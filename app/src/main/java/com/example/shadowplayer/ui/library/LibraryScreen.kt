package com.example.shadowplayer.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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

    val context = LocalContext.current

    // 文件夹选择器
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // 持久化权限
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            viewModel.addScanFolder(it)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标签栏
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

        // 扫描中提示
        if (isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // 内容区域
        when (selectedTab) {
            LibraryTab.ALL -> {
                AudioFileList(
                    audioFiles = audioFiles,
                    onFileClick = onFileSelected,
                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                    onDeleteClick = { viewModel.deleteAudio(it) } // 传入删除回调
                )
            }
            LibraryTab.FAVORITES -> {
                AudioFileList(
                    audioFiles = favorites,
                    onFileClick = onFileSelected,
                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                    onDeleteClick = null // 收藏夹一般不直接删除文件记录，或者你可以选择加上
                )
            }
            LibraryTab.TAGS -> {
                TagsSection(
                    tags = rootTags,
                    selectedTagId = selectedTagId,
                    audioFiles = audioFilesByTag,
                    onTagClick = { viewModel.selectTag(it) },
                    onAddTag = { showAddTagDialog = true },
                    onDeleteTag = { viewModel.deleteTag(it) },
                    onFileClick = onFileSelected,
                    onFavoriteClick = { viewModel.toggleFavorite(it) }
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

    // 添加标签对话框
    if (showAddTagDialog) {
        AddTagDialog(
            onDismiss = { showAddTagDialog = false },
            onConfirm = { name ->
                viewModel.createTag(name)
                showAddTagDialog = false
            }
        )
    }
}

@Composable
fun AudioFileList(
    audioFiles: List<AudioFile>,
    onFileClick: (AudioFile) -> Unit,
    onFavoriteClick: (AudioFile) -> Unit,
    onDeleteClick: ((AudioFile) -> Unit)? = null // 新增删除回调参数
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
                    onDeleteClick = onDeleteClick
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
    onDeleteClick: ((AudioFile) -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 使用 combinedClickable 实现长按
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

            // 长按菜单
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
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
                    DropdownMenuItem(
                        text = { Text("暂无操作") },
                        onClick = { showMenu = false }
                    )
                }
            }
        }
    }

    // 删除确认对话框
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
    onAddTag: () -> Unit,
    onDeleteTag: (Tag) -> Unit,
    onFileClick: (AudioFile) -> Unit,
    onFavoriteClick: (AudioFile) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 标签列表
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                FilterChip(
                    selected = selectedTagId == tag.id,
                    onClick = { onTagClick(tag.id) },
                    label = { Text(tag.name) },
                    trailingIcon = {
                        IconButton(
                            onClick = { onDeleteTag(tag) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                )
            }
            item {
                AssistChip(
                    onClick = onAddTag,
                    label = { Text("添加") },
                    leadingIcon = {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                )
            }
        }

        // 音频列表
        if (selectedTagId != null) {
            AudioFileList(
                audioFiles = audioFiles,
                onFileClick = onFileClick,
                onFavoriteClick = onFavoriteClick
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "请选择一个标签",
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
        // 操作按钮
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

        // 文件夹列表
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
        title = { Text("添加标签") },
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