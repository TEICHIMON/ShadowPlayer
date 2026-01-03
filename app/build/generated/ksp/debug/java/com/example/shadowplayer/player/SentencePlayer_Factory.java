package com.example.shadowplayer.player;

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

  public SentencePlayer_Factory(Provider<AudioPlayer> audioPlayerProvider) {
    this.audioPlayerProvider = audioPlayerProvider;
  }

  @Override
  public SentencePlayer get() {
    return newInstance(audioPlayerProvider.get());
  }

  public static SentencePlayer_Factory create(Provider<AudioPlayer> audioPlayerProvider) {
    return new SentencePlayer_Factory(audioPlayerProvider);
  }

  public static SentencePlayer newInstance(AudioPlayer audioPlayer) {
    return new SentencePlayer(audioPlayer);
  }
}
