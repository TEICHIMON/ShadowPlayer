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

    // [新增] 获取历史记录：按播放时间倒序，且必须播放过
    @Query("SELECT * FROM audio_files WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC")
    fun getHistory(): Flow<List<AudioFile>>

    @Query("SELECT * FROM audio_files WHERE id = :id")
    suspend fun getById(id: Long): AudioFile?

    @Query("SELECT * FROM audio_files WHERE path = :path")
    suspend fun getByPath(path: String): AudioFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audioFile: AudioFile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(audioFiles: List<AudioFile>)

    @Query("SELECT path FROM audio_files")
    suspend fun getAllPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(audioFiles: List<AudioFile>)

    @Update
    suspend fun update(audioFile: AudioFile)

    @Query("UPDATE audio_files SET lastPosition = :position WHERE id = :id")
    suspend fun updateLastPosition(id: Long, position: Long)

    @Query("UPDATE audio_files SET playCount = playCount + 1 WHERE id = :id")
    suspend fun incrementPlayCount(id: Long)

    // [新增] 更新最近播放时间
    @Query("UPDATE audio_files SET lastPlayedAt = :timestamp WHERE id = :id")
    suspend fun updateLastPlayedAt(id: Long, timestamp: Long)

    @Query("UPDATE audio_files SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE audio_files SET lrcOffset = :offset WHERE id = :id")
    suspend fun updateLrcOffset(id: Long, offset: Long)

    @Query("UPDATE audio_files SET duration = :duration WHERE id = :id")
    suspend fun updateDuration(id: Long, duration: Long)

    @Delete
    suspend fun delete(audioFile: AudioFile)

    @Query("DELETE FROM audio_files WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM audio_files WHERE path LIKE :pathPrefix || '%'")
    suspend fun deleteByPathPrefix(pathPrefix: String)

    @Query("DELETE FROM audio_files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE audio_files SET lastPlayedAt = NULL WHERE id IN (:ids)")
    suspend fun clearPlayHistory(ids: List<Long>)
}