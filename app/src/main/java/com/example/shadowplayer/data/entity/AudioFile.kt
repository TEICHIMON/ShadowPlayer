package com.example.shadowplayer.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val createdAt: Long = System.currentTimeMillis(),
    // 新增字段：最近播放时间，用于历史记录排序
    val lastPlayedAt: Long? = null
)