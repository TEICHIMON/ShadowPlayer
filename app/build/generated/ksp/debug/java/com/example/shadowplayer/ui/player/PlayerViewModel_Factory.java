package com.example.shadowplayer.ui.player;

import android.content.Context;
import androidx.lifecycle.SavedStateHandle;
import com.example.shadowplayer.data.repository.AudioRepository;
import com.example.shadowplayer.player.AudioPlayer;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class PlayerViewModel_Factory implements Factory<PlayerViewModel> {
  private final Provider<AudioPlayer> audioPlayerProvider;

  private final Provider<AudioRepository> repositoryProvider;

  private final Provider<Context> contextProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public PlayerViewModel_Factory(Provider<AudioPlayer> audioPlayerProvider,
      Provider<AudioRepository> repositoryProvider, Provider<Context> contextProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.audioPlayerProvider = audioPlayerProvider;
    this.repositoryProvider = repositoryProvider;
    this.contextProvider = contextProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public PlayerViewModel get() {
    return newInstance(audioPlayerProvider.get(), repositoryProvider.get(), contextProvider.get(), savedStateHandleProvider.get());
  }

  public static PlayerViewModel_Factory create(Provider<AudioPlayer> audioPlayerProvider,
      Provider<AudioRepository> repositoryProvider, Provider<Context> contextProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new PlayerViewModel_Factory(audioPlayerProvider, repositoryProvider, contextProvider, savedStateHandleProvider);
  }

  public static PlayerViewModel newInstance(AudioPlayer audioPlayer, AudioRepository repository,
      Context context, SavedStateHandle savedStateHandle) {
    return new PlayerViewModel(audioPlayer, repository, context, savedStateHandle);
  }
}
