package com.example.shadowplayer.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_files")
data class AudioFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val path: String,
    val title: String,
    val duration: Long = 0,
    val lrcPath: String? = null,
    val lrcOffset: Long = 0,  // 字幕时间微调(毫秒)
    val lastPosition: Long = 0,  // 上次播放位置
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
