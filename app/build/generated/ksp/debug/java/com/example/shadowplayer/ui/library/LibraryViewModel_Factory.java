package com.example.shadowplayer.ui.library;

import android.content.Context;
import com.example.shadowplayer.data.repository.AudioRepository;
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
public final class LibraryViewModel_Factory implements Factory<LibraryViewModel> {
  private final Provider<AudioRepository> repositoryProvider;

  private final Provider<Context> contextProvider;

  public LibraryViewModel_Factory(Provider<AudioRepository> repositoryProvider,
      Provider<Context> contextProvider) {
    this.repositoryProvider = repositoryProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public LibraryViewModel get() {
    return newInstance(repositoryProvider.get(), contextProvider.get());
  }

  public static LibraryViewModel_Factory create(Provider<AudioRepository> repositoryProvider,
      Provider<Context> contextProvider) {
    return new LibraryViewModel_Factory(repositoryProvider, contextProvider);
  }

  public static LibraryViewModel newInstance(AudioRepository repository, Context context) {
    return new LibraryViewModel(repository, context);
  }
}
