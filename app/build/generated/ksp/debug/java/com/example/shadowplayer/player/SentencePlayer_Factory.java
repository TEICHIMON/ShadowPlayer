package com.example.shadowplayer.player;

import android.content.SharedPreferences;
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

  public SentencePlayer_Factory(Provider<AudioPlayer> audioPlayerProvider,
      Provider<SharedPreferences> prefsProvider) {
    this.audioPlayerProvider = audioPlayerProvider;
    this.prefsProvider = prefsProvider;
  }

  @Override
  public SentencePlayer get() {
    return newInstance(audioPlayerProvider.get(), prefsProvider.get());
  }

  public static SentencePlayer_Factory create(Provider<AudioPlayer> audioPlayerProvider,
      Provider<SharedPreferences> prefsProvider) {
    return new SentencePlayer_Factory(audioPlayerProvider, prefsProvider);
  }

  public static SentencePlayer newInstance(AudioPlayer audioPlayer, SharedPreferences prefs) {
    return new SentencePlayer(audioPlayer, prefs);
  }
}
