package com.example.shadowplayer.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 修改点：添加 indices = [Index(value = ["path"], unique = true)]
@Entity(
    tableName = "audio_files",
    indices = [Index(value = ["path"], unique = true)]
)
data class AudioFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val path: String,
    val title: String,
    val duration: Long = 0,
    val lrcPath: String? = null,
    val lrcOffset: Long = 0,
    val lastPosition: Long = 0,
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)