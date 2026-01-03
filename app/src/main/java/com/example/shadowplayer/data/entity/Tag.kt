package com.example.shadowplayer.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    foreignKeys = [
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentId")]
)
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: String = "#1976D2",
    val parentId: Long? = null,  // 支持嵌套标签
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

// 音频文件与标签的多对多关系
@Entity(
    tableName = "audio_tags",
    primaryKeys = ["audioId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = AudioFile::class,
            parentColumns = ["id"],
            childColumns = ["audioId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("audioId"), Index("tagId")]
)
data class AudioTag(
    val audioId: Long,
    val tagId: Long
)
