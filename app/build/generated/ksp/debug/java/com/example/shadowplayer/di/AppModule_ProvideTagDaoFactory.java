package com.example.shadowplayer.di;

import com.example.shadowplayer.data.AppDatabase;
import com.example.shadowplayer.data.dao.TagDao;
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
public final class AppModule_ProvideTagDaoFactory implements Factory<TagDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideTagDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public TagDao get() {
    return provideTagDao(dbProvider.get());
  }

  public static AppModule_ProvideTagDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideTagDaoFactory(dbProvider);
  }

  public static TagDao provideTagDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideTagDao(db));
  }
}
