package com.example.shadowplayer.data.repository;

import com.example.shadowplayer.data.dao.AudioFileDao;
import com.example.shadowplayer.data.dao.BookmarkDao;
import com.example.shadowplayer.data.dao.ScanFolderDao;
import com.example.shadowplayer.data.dao.TagDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class AudioRepository_Factory implements Factory<AudioRepository> {
  private final Provider<AudioFileDao> audioFileDaoProvider;

  private final Provider<TagDao> tagDaoProvider;

  private final Provider<BookmarkDao> bookmarkDaoProvider;

  private final Provider<ScanFolderDao> scanFolderDaoProvider;

  public AudioRepository_Factory(Provider<AudioFileDao> audioFileDaoProvider,
      Provider<TagDao> tagDaoProvider, Provider<BookmarkDao> bookmarkDaoProvider,
      Provider<ScanFolderDao> scanFolderDaoProvider) {
    this.audioFileDaoProvider = audioFileDaoProvider;
    this.tagDaoProvider = tagDaoProvider;
    this.bookmarkDaoProvider = bookmarkDaoProvider;
    this.scanFolderDaoProvider = scanFolderDaoProvider;
  }

  @Override
  public AudioRepository get() {
    return newInstance(audioFileDaoProvider.get(), tagDaoProvider.get(), bookmarkDaoProvider.get(), scanFolderDaoProvider.get());
  }

  public static AudioRepository_Factory create(Provider<AudioFileDao> audioFileDaoProvider,
      Provider<TagDao> tagDaoProvider, Provider<BookmarkDao> bookmarkDaoProvider,
      Provider<ScanFolderDao> scanFolderDaoProvider) {
    return new AudioRepository_Factory(audioFileDaoProvider, tagDaoProvider, bookmarkDaoProvider, scanFolderDaoProvider);
  }

  public static AudioRepository newInstance(AudioFileDao audioFileDao, TagDao tagDao,
      BookmarkDao bookmarkDao, ScanFolderDao scanFolderDao) {
    return new AudioRepository(audioFileDao, tagDao, bookmarkDao, scanFolderDao);
  }
}
