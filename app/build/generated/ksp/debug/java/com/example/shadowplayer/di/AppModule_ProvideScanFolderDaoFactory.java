package com.example.shadowplayer.di;

import com.example.shadowplayer.data.AppDatabase;
import com.example.shadowplayer.data.dao.ScanFolderDao;
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
public final class AppModule_ProvideScanFolderDaoFactory implements Factory<ScanFolderDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideScanFolderDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ScanFolderDao get() {
    return provideScanFolderDao(dbProvider.get());
  }

  public static AppModule_ProvideScanFolderDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideScanFolderDaoFactory(dbProvider);
  }

  public static ScanFolderDao provideScanFolderDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideScanFolderDao(db));
  }
}
