package com.example.shadowplayer.data.dao

import androidx.room.*
import com.example.shadowplayer.data.entity.AudioFile
import com.example.shadowplayer.data.entity.AudioTag
import com.example.shadowplayer.data.entity.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE parentId IS NULL ORDER BY `order` ASC, name ASC")
    fun getRootTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE parentId = :parentId ORDER BY `order` ASC, name ASC")
    fun getChildTags(parentId: Long): Flow<List<Tag>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): Tag?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

    // 标签关联
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToAudio(audioTag: AudioTag)

    @Delete
    suspend fun removeTagFromAudio(audioTag: AudioTag)

    @Query("""
        SELECT af.* FROM audio_files af
        INNER JOIN audio_tags at ON af.id = at.audioId
        WHERE at.tagId = :tagId
        ORDER BY af.title ASC
    """)
    fun getAudioFilesByTag(tagId: Long): Flow<List<AudioFile>>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN audio_tags at ON t.id = at.tagId
        WHERE at.audioId = :audioId
    """)
    fun getTagsForAudio(audioId: Long): Flow<List<Tag>>
}
