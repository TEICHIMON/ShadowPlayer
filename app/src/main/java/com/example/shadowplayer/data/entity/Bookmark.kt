package com.example.shadowplayer.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = AudioFile::class,
            parentColumns = ["id"],
            childColumns = ["audioId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("audioId")]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val audioId: Long,
    val position: Long,  // 时间点(毫秒)
    val sentenceIndex: Int? = null,  // 对应字幕第几句
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
