package com.example.shadowplayer.player;

import android.content.SharedPreferences;
import com.example.shadowplayer.data.repository.AudioRepository;
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
public final class SentencePlayer_Factory implements Factory<SentencePlayer> {
  private final Provider<AudioPlayer> audioPlayerProvider;

  private final Provider<SharedPreferences> prefsProvider;

  private final Provider<AudioRepository> repositoryProvider;

  public SentencePlayer_Factory(Provider<AudioPlayer> audioPlayerProvider,
      Provider<SharedPreferences> prefsProvider, Provider<AudioRepository> repositoryProvider) {
    this.audioPlayerProvider = audioPlayerProvider;
    this.prefsProvider = prefsProvider;
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public SentencePlayer get() {
    return newInstance(audioPlayerProvider.get(), prefsProvider.get(), repositoryProvider.get());
  }

  public static SentencePlayer_Factory create(Provider<AudioPlayer> audioPlayerProvider,
      Provider<SharedPreferences> prefsProvider, Provider<AudioRepository> repositoryProvider) {
    return new SentencePlayer_Factory(audioPlayerProvider, prefsProvider, repositoryProvider);
  }

  public static SentencePlayer newInstance(AudioPlayer audioPlayer, SharedPreferences prefs,
      AudioRepository repository) {
    return new SentencePlayer(audioPlayer, prefs, repository);
  }
}
