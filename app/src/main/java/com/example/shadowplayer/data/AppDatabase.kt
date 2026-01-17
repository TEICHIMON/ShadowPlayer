package com.example.shadowplayer.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.shadowplayer.data.dao.*
import com.example.shadowplayer.data.entity.*

@Database(
    entities = [
        AudioFile::class,
        Tag::class,
        AudioTag::class,
        Bookmark::class,
        ScanFolder::class
    ],
    version = 2, // 升级版本号
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun audioFileDao(): AudioFileDao
    abstract fun tagDao(): TagDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun scanFolderDao(): ScanFolderDao
}