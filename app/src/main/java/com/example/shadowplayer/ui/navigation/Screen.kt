package com.example.shadowplayer.ui.navigation

sealed class Screen(val route: String) {
    data object Player : Screen("player")
    data object Library : Screen("library")
    data object Settings : Screen("settings")
    data object FolderPicker : Screen("folder_picker")
}
