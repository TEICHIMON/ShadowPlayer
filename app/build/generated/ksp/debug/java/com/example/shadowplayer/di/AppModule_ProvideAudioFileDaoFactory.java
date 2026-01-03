package com.example.shadowplayer.di;

import com.example.shadowplayer.data.AppDatabase;
import com.example.shadowplayer.data.dao.AudioFileDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class AppModule_ProvideAudioFileDaoFactory implements Factory<AudioFileDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideAudioFileDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public AudioFileDao get() {
    return provideAudioFileDao(dbProvider.get());
  }

  public static AppModule_ProvideAudioFileDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideAudioFileDaoFactory(dbProvider);
  }

  public static AudioFileDao provideAudioFileDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideAudioFileDao(db));
  }
}
