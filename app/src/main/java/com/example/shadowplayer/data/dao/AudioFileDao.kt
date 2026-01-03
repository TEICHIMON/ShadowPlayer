package com.example.shadowplayer.data.dao

import androidx.room.*
import com.example.shadowplayer.data.entity.AudioFile
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioFileDao {
    @Query("SELECT * FROM audio_files ORDER BY title ASC")
    fun getAllAudioFiles(): Flow<List<AudioFile>>

    @Query("SELECT * FROM audio_files WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<AudioFile>>

    @Query("SELECT * FROM audio_files WHERE id = :id")
    suspend fun getById(id: Long): AudioFile?

    @Query("SELECT * FROM audio_files WHERE path = :path")
    suspend fun getByPath(path: String): AudioFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audioFile: AudioFile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(audioFiles: List<AudioFile>)

    @Update
    suspend fun update(audioFile: AudioFile)

    @Query("UPDATE audio_files SET lastPosition = :position WHERE id = :id")
    suspend fun updateLastPosition(id: Long, position: Long)

    @Query("UPDATE audio_files SET playCount = playCount + 1 WHERE id = :id")
    suspend fun incrementPlayCount(id: Long)

    @Query("UPDATE audio_files SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE audio_files SET lrcOffset = :offset WHERE id = :id")
    suspend fun updateLrcOffset(id: Long, offset: Long)

    // 新增：更新音频时长
    @Query("UPDATE audio_files SET duration = :duration WHERE id = :id")
    suspend fun updateDuration(id: Long, duration: Long)

    @Delete
    suspend fun delete(audioFile: AudioFile)

    @Query("DELETE FROM audio_files WHERE path = :path")
    suspend fun deleteByPath(path: String)
}