package com.example.shadowplayer.data.repository

import com.example.shadowplayer.data.dao.AudioFileDao
import com.example.shadowplayer.data.dao.BookmarkDao
import com.example.shadowplayer.data.dao.ScanFolderDao
import com.example.shadowplayer.data.dao.TagDao
import com.example.shadowplayer.data.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRepository @Inject constructor(
    private val audioFileDao: AudioFileDao,
    private val tagDao: TagDao,
    private val bookmarkDao: BookmarkDao,
    private val scanFolderDao: ScanFolderDao
) {
    // AudioFile
    fun getAllAudioFiles(): Flow<List<AudioFile>> = audioFileDao.getAllAudioFiles()
    fun getFavorites(): Flow<List<AudioFile>> = audioFileDao.getFavorites()
    suspend fun getAudioById(id: Long): AudioFile? = audioFileDao.getById(id)
    suspend fun getAudioByPath(path: String): AudioFile? = audioFileDao.getByPath(path)
    suspend fun insertAudio(audioFile: AudioFile): Long = audioFileDao.insert(audioFile)
    suspend fun insertAllAudio(audioFiles: List<AudioFile>) = audioFileDao.insertAll(audioFiles)
    suspend fun updateAudio(audioFile: AudioFile) = audioFileDao.update(audioFile)
    suspend fun updateLastPosition(id: Long, position: Long) = audioFileDao.updateLastPosition(id, position)
    suspend fun incrementPlayCount(id: Long) = audioFileDao.incrementPlayCount(id)
    suspend fun updateFavorite(id: Long, isFavorite: Boolean) = audioFileDao.updateFavorite(id, isFavorite)
    suspend fun updateLrcOffset(id: Long, offset: Long) = audioFileDao.updateLrcOffset(id, offset)
    suspend fun deleteAudio(audioFile: AudioFile) = audioFileDao.delete(audioFile)

    // Tags
    fun getRootTags(): Flow<List<Tag>> = tagDao.getRootTags()
    fun getChildTags(parentId: Long): Flow<List<Tag>> = tagDao.getChildTags(parentId)
    fun getAllTags(): Flow<List<Tag>> = tagDao.getAllTags()
    suspend fun insertTag(tag: Tag): Long = tagDao.insert(tag)
    suspend fun updateTag(tag: Tag) = tagDao.update(tag)
    suspend fun deleteTag(tag: Tag) = tagDao.delete(tag)
    suspend fun addTagToAudio(audioId: Long, tagId: Long) = tagDao.addTagToAudio(AudioTag(audioId, tagId))
    suspend fun removeTagFromAudio(audioId: Long, tagId: Long) = tagDao.removeTagFromAudio(AudioTag(audioId, tagId))
    fun getAudioFilesByTag(tagId: Long): Flow<List<AudioFile>> = tagDao.getAudioFilesByTag(tagId)
    fun getTagsForAudio(audioId: Long): Flow<List<Tag>> = tagDao.getTagsForAudio(audioId)

    // Bookmarks
    fun getBookmarksForAudio(audioId: Long): Flow<List<Bookmark>> = bookmarkDao.getBookmarksForAudio(audioId)
    fun getAllBookmarks(): Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()
    suspend fun insertBookmark(bookmark: Bookmark): Long = bookmarkDao.insert(bookmark)
    suspend fun updateBookmark(bookmark: Bookmark) = bookmarkDao.update(bookmark)
    suspend fun deleteBookmark(bookmark: Bookmark) = bookmarkDao.delete(bookmark)

    // Scan Folders
    fun getAllScanFolders(): Flow<List<ScanFolder>> = scanFolderDao.getAllFolders()
    suspend fun insertScanFolder(folder: ScanFolder): Long = scanFolderDao.insert(folder)
    suspend fun deleteScanFolder(folder: ScanFolder) = scanFolderDao.delete(folder)
}
