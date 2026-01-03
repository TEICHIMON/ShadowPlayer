package com.example.shadowplayer.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_folders")
data class ScanFolder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val path: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
