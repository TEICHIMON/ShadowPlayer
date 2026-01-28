package com.example.shadowplayer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.shadowplayer.player.PlaybackSettings

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val settings by viewModel.settings.collectAsState()

    // 弹出菜单状态
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showRepeatMenu by remember { mutableStateOf(false) }
    var showIntervalMenu by remember { mutableStateOf(false) }
    var showSeekIntervalMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 播放设置
        SettingsSection(title = "播放设置") {
            // 播放速度
            Box {
                SettingsItem(
                    icon = Icons.Default.Speed,
                    title = "默认播放速度",
                    subtitle = "${settings.speed}x",
                    onClick = { showSpeedMenu = true }
                )
                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false }
                ) {
                    PlaybackSettings.SPEED_OPTIONS.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}x") },
                            onClick = {
                                viewModel.setSpeed(speed)
                                showSpeedMenu = false
                            }
                        )
                    }
                }
            }

            // 重复次数
            Box {
                SettingsItem(
                    icon = Icons.Default.Repeat,
                    title = "默认重复次数",
                    subtitle = "${settings.repeatCount}次",
                    onClick = { showRepeatMenu = true }
                )
                DropdownMenu(
                    expanded = showRepeatMenu,
                    onDismissRequest = { showRepeatMenu = false }
                ) {
                    PlaybackSettings.REPEAT_OPTIONS.forEach { count ->
                        DropdownMenuItem(
                            text = { Text("${count}次") },
                            onClick = {
                                viewModel.setRepeatCount(count)
                                showRepeatMenu = false
                            }
                        )
                    }
                }
            }

            // 跟读间隔
            Box {
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = "默认跟读间隔",
                    subtitle = "${settings.repeatInterval / 1000}秒",
                    onClick = { showIntervalMenu = true }
                )
                DropdownMenu(
                    expanded = showIntervalMenu,
                    onDismissRequest = { showIntervalMenu = false }
                ) {
                    PlaybackSettings.INTERVAL_OPTIONS.forEach { interval ->
                        DropdownMenuItem(
                            text = { Text("${interval / 1000}秒") },
                            onClick = {
                                viewModel.setRepeatInterval(interval)
                                showIntervalMenu = false
                            }
                        )
                    }
                }
            }

            // 快进快退间隔
            Box {
                SettingsItem(
                    icon = Icons.Default.FastForward,
                    title = "快进快退间隔",
                    subtitle = "${settings.seekInterval / 1000}秒",
                    onClick = { showSeekIntervalMenu = true }
                )
                DropdownMenu(
                    expanded = showSeekIntervalMenu,
                    onDismissRequest = { showSeekIntervalMenu = false }
                ) {
                    PlaybackSettings.SEEK_INTERVAL_OPTIONS.forEach { interval ->
                        DropdownMenuItem(
                            text = { Text("${interval / 1000}秒") },
                            onClick = {
                                viewModel.setSeekInterval(interval)
                                showSeekIntervalMenu = false
                            }
                        )
                    }
                }
            }

            // 自动播放下一句
            SettingsToggleItem(
                icon = Icons.Default.SkipNext,
                title = "自动播放下一句",
                subtitle = "播放完当前句后自动继续",
                checked = settings.autoNext,
                onCheckedChange = { viewModel.setAutoNext(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 控制设置
        SettingsSection(title = "控制设置") {
            SettingsToggleItem(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = "音量键控制",
                subtitle = "开启后：有字幕时切换句子，无字幕时快进快退",
                checked = settings.volumeKeyEnabled,
                onCheckedChange = { viewModel.setVolumeKeyEnabled(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 显示设置
        SettingsSection(title = "显示设置") {
            SettingsToggleItem(
                icon = Icons.Default.Subtitles,
                title = "默认显示字幕",
                checked = settings.showSubtitle,
                onCheckedChange = { viewModel.toggleSubtitle() }
            )
            // 深色模式暂时保持跟随系统，后续实现
            SettingsItem(
                icon = Icons.Default.DarkMode,
                title = "深色模式",
                subtitle = "跟随系统"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 关于
        SettingsSection(title = "关于") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "版本",
                subtitle = "1.0.0"
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "ShadowPlayer",
                subtitle = "影子跟读播放器"
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            content = content
        )
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}