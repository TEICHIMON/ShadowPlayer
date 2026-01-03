package com.example.shadowplayer.data.dao

import androidx.room.*
import com.example.shadowplayer.data.entity.ScanFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanFolderDao {
    @Query("SELECT * FROM scan_folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<ScanFolder>>

    @Query("SELECT * FROM scan_folders WHERE id = :id")
    suspend fun getById(id: Long): ScanFolder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: ScanFolder): Long

    @Delete
    suspend fun delete(folder: ScanFolder)

    @Query("DELETE FROM scan_folders WHERE path = :path")
    suspend fun deleteByPath(path: String)
}
