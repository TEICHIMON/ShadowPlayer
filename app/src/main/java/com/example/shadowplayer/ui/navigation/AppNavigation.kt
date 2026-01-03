package com.example.shadowplayer.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.shadowplayer.ui.library.LibraryScreen
import com.example.shadowplayer.ui.player.PlayerScreen
import com.example.shadowplayer.ui.settings.SettingsScreen

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Player, Icons.Default.PlayCircle, "播放"),
    BottomNavItem(Screen.Library, Icons.Default.Folder, "文件库"),
    BottomNavItem(Screen.Settings, Icons.Default.Settings, "设置")
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any {
                            // 注意：这里用 route 前缀匹配，因为 Player 现在可能带有参数
                            it.route?.startsWith(item.screen.route) == true
                        } == true,
                        onClick = {
                            // Library 是 startDestination，用 popBackStack 回到栈底
                            // 避免 restoreState 恢复之前保存的 Player 子图状态
                            if (item.screen == Screen.Library) {
                                val popped = navController.popBackStack(
                                    route = Screen.Library.route,
                                    inclusive = false
                                )
                                // 如果 popBackStack 失败（已经在 Library），不做任何操作
                                if (!popped) {
                                    // 已经在 Library，无需操作
                                }
                            } else {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 1. 修改播放器路由：添加可选参数 ?audioId={audioId}
            composable(
                route = "${Screen.Player.route}?audioId={audioId}",
                arguments = listOf(
                    navArgument("audioId") {
                        type = NavType.LongType
                        defaultValue = -1L // 默认值 -1，表示没有通过列表选择，直接点击Tab进来的
                    }
                )
            ) {
                PlayerScreen()
            }

            composable(Screen.Library.route) {
                LibraryScreen(
                    onFileSelected = { audioFile ->
                        // 2. 传递参数：将文件 ID 拼接到路由中
                        navController.navigate("${Screen.Player.route}?audioId=${audioFile.id}")
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}