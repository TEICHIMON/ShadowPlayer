package com.example.shadowplayer.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.example.shadowplayer.data.AppDatabase
import com.example.shadowplayer.data.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "shadow_player_db"
        )
            // 允许破坏性迁移：数据库结构变化时会清空数据重建
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("shadow_player_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    fun provideAudioFileDao(db: AppDatabase): AudioFileDao = db.audioFileDao()

    @Provides
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()

    @Provides
    fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideScanFolderDao(db: AppDatabase): ScanFolderDao = db.scanFolderDao()
}