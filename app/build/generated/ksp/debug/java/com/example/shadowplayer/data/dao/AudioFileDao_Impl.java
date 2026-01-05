package com.example.shadowplayer.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.example.shadowplayer.data.entity.AudioFile;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AudioFileDao_Impl implements AudioFileDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<AudioFile> __insertionAdapterOfAudioFile;

  private final EntityInsertionAdapter<AudioFile> __insertionAdapterOfAudioFile_1;

  private final EntityDeletionOrUpdateAdapter<AudioFile> __deletionAdapterOfAudioFile;

  private final EntityDeletionOrUpdateAdapter<AudioFile> __updateAdapterOfAudioFile;

  private final SharedSQLiteStatement __preparedStmtOfUpdateLastPosition;

  private final SharedSQLiteStatement __preparedStmtOfIncrementPlayCount;

  private final SharedSQLiteStatement __preparedStmtOfUpdateFavorite;

  private final SharedSQLiteStatement __preparedStmtOfUpdateLrcOffset;

  private final SharedSQLiteStatement __preparedStmtOfUpdateDuration;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByPath;

  public AudioFileDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAudioFile = new EntityInsertionAdapter<AudioFile>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `audio_files` (`id`,`path`,`title`,`duration`,`lrcPath`,`lrcOffset`,`lastPosition`,`playCount`,`isFavorite`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AudioFile entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getPath());
        statement.bindString(3, entity.getTitle());
        statement.bindLong(4, entity.getDuration());
        if (entity.getLrcPath() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getLrcPath());
        }
        statement.bindLong(6, entity.getLrcOffset());
        statement.bindLong(7, entity.getLastPosition());
        statement.bindLong(8, entity.getPlayCount());
        final int _tmp = entity.isFavorite() ? 1 : 0;
        statement.bindLong(9, _tmp);
        statement.bindLong(10, entity.getCreatedAt());
      }
    };
    this.__insertionAdapterOfAudioFile_1 = new EntityInsertionAdapter<AudioFile>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `audio_files` (`id`,`path`,`title`,`duration`,`lrcPath`,`lrcOffset`,`lastPosition`,`playCount`,`isFavorite`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AudioFile entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getPath());
        statement.bindString(3, entity.getTitle());
        statement.bindLong(4, entity.getDuration());
        if (entity.getLrcPath() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getLrcPath());
        }
        statement.bindLong(6, entity.getLrcOffset());
        statement.bindLong(7, entity.getLastPosition());
        statement.bindLong(8, entity.getPlayCount());
        final int _tmp = entity.isFavorite() ? 1 : 0;
        statement.bindLong(9, _tmp);
        statement.bindLong(10, entity.getCreatedAt());
      }
    };
    this.__deletionAdapterOfAudioFile = new EntityDeletionOrUpdateAdapter<AudioFile>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `audio_files` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AudioFile entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfAudioFile = new EntityDeletionOrUpdateAdapter<AudioFile>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `audio_files` SET `id` = ?,`path` = ?,`title` = ?,`duration` = ?,`lrcPath` = ?,`lrcOffset` = ?,`lastPosition` = ?,`playCount` = ?,`isFavorite` = ?,`createdAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AudioFile entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getPath());
        statement.bindString(3, entity.getTitle());
        statement.bindLong(4, entity.getDuration());
        if (entity.getLrcPath() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getLrcPath());
        }
        statement.bindLong(6, entity.getLrcOffset());
        statement.bindLong(7, entity.getLastPosition());
        statement.bindLong(8, entity.getPlayCount());
        final int _tmp = entity.isFavorite() ? 1 : 0;
        statement.bindLong(9, _tmp);
        statement.bindLong(10, entity.getCreatedAt());
        statement.bindLong(11, entity.getId());
      }
    };
    this.__preparedStmtOfUpdateLastPosition = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE audio_files SET lastPosition = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfIncrementPlayCount = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE audio_files SET playCount = playCount + 1 WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateFavorite = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE audio_files SET isFavorite = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateLrcOffset = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE audio_files SET lrcOffset = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateDuration = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE audio_files SET duration = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteByPath = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM audio_files WHERE path = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final AudioFile audioFile, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfAudioFile.insertAndReturnId(audioFile);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<AudioFile> audioFiles,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAudioFile.insert(audioFiles);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAllIgnore(final List<AudioFile> audioFiles,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAudioFile_1.insert(audioFiles);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final AudioFile audioFile, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfAudioFile.handle(audioFile);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final AudioFile audioFile, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfAudioFile.handle(audioFile);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateLastPosition(final long id, final long position,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateLastPosition.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, position);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateLastPosition.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object incrementPlayCount(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfIncrementPlayCount.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfIncrementPlayCount.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateFavorite(final long id, final boolean isFavorite,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateFavorite.acquire();
        int _argIndex = 1;
        final int _tmp = isFavorite ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateFavorite.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateLrcOffset(final long id, final long offset,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateLrcOffset.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, offset);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateLrcOffset.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateDuration(final long id, final long duration,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateDuration.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, duration);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateDuration.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteByPath(final String path, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByPath.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, path);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteByPath.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<AudioFile>> getAllAudioFiles() {
    final String _sql = "SELECT * FROM audio_files ORDER BY title ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"audio_files"}, new Callable<List<AudioFile>>() {
      @Override
      @NonNull
      public List<AudioFile> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfLrcPath = CursorUtil.getColumnIndexOrThrow(_cursor, "lrcPath");
          final int _cursorIndexOfLrcOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "lrcOffset");
          final int _cursorIndexOfLastPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "lastPosition");
          final int _cursorIndexOfPlayCount = CursorUtil.getColumnIndexOrThrow(_cursor, "playCount");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<AudioFile> _result = new ArrayList<AudioFile>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AudioFile _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpDuration;
            _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            final String _tmpLrcPath;
            if (_cursor.isNull(_cursorIndexOfLrcPath)) {
              _tmpLrcPath = null;
            } else {
              _tmpLrcPath = _cursor.getString(_cursorIndexOfLrcPath);
            }
            final long _tmpLrcOffset;
            _tmpLrcOffset = _cursor.getLong(_cursorIndexOfLrcOffset);
            final long _tmpLastPosition;
            _tmpLastPosition = _cursor.getLong(_cursorIndexOfLastPosition);
            final int _tmpPlayCount;
            _tmpPlayCount = _cursor.getInt(_cursorIndexOfPlayCount);
            final boolean _tmpIsFavorite;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new AudioFile(_tmpId,_tmpPath,_tmpTitle,_tmpDuration,_tmpLrcPath,_tmpLrcOffset,_tmpLastPosition,_tmpPlayCount,_tmpIsFavorite,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<AudioFile>> getFavorites() {
    final String _sql = "SELECT * FROM audio_files WHERE isFavorite = 1 ORDER BY title ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"audio_files"}, new Callable<List<AudioFile>>() {
      @Override
      @NonNull
      public List<AudioFile> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfLrcPath = CursorUtil.getColumnIndexOrThrow(_cursor, "lrcPath");
          final int _cursorIndexOfLrcOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "lrcOffset");
          final int _cursorIndexOfLastPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "lastPosition");
          final int _cursorIndexOfPlayCount = CursorUtil.getColumnIndexOrThrow(_cursor, "playCount");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<AudioFile> _result = new ArrayList<AudioFile>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AudioFile _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpDuration;
            _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            final String _tmpLrcPath;
            if (_cursor.isNull(_cursorIndexOfLrcPath)) {
              _tmpLrcPath = null;
            } else {
              _tmpLrcPath = _cursor.getString(_cursorIndexOfLrcPath);
            }
            final long _tmpLrcOffset;
            _tmpLrcOffset = _cursor.getLong(_cursorIndexOfLrcOffset);
            final long _tmpLastPosition;
            _tmpLastPosition = _cursor.getLong(_cursorIndexOfLastPosition);
            final int _tmpPlayCount;
            _tmpPlayCount = _cursor.getInt(_cursorIndexOfPlayCount);
            final boolean _tmpIsFavorite;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new AudioFile(_tmpId,_tmpPath,_tmpTitle,_tmpDuration,_tmpLrcPath,_tmpLrcOffset,_tmpLastPosition,_tmpPlayCount,_tmpIsFavorite,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getById(final long id, final Continuation<? super AudioFile> $completion) {
    final String _sql = "SELECT * FROM audio_files WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<AudioFile>() {
      @Override
      @Nullable
      public AudioFile call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfLrcPath = CursorUtil.getColumnIndexOrThrow(_cursor, "lrcPath");
          final int _cursorIndexOfLrcOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "lrcOffset");
          final int _cursorIndexOfLastPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "lastPosition");
          final int _cursorIndexOfPlayCount = CursorUtil.getColumnIndexOrThrow(_cursor, "playCount");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final AudioFile _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpDuration;
            _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            final String _tmpLrcPath;
            if (_cursor.isNull(_cursorIndexOfLrcPath)) {
              _tmpLrcPath = null;
            } else {
              _tmpLrcPath = _cursor.getString(_cursorIndexOfLrcPath);
            }
            final long _tmpLrcOffset;
            _tmpLrcOffset = _cursor.getLong(_cursorIndexOfLrcOffset);
            final long _tmpLastPosition;
            _tmpLastPosition = _cursor.getLong(_cursorIndexOfLastPosition);
            final int _tmpPlayCount;
            _tmpPlayCount = _cursor.getInt(_cursorIndexOfPlayCount);
            final boolean _tmpIsFavorite;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new AudioFile(_tmpId,_tmpPath,_tmpTitle,_tmpDuration,_tmpLrcPath,_tmpLrcOffset,_tmpLastPosition,_tmpPlayCount,_tmpIsFavorite,_tmpCreatedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getByPath(final String path, final Continuation<? super AudioFile> $completion) {
    final String _sql = "SELECT * FROM audio_files WHERE path = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, path);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<AudioFile>() {
      @Override
      @Nullable
      public AudioFile call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfLrcPath = CursorUtil.getColumnIndexOrThrow(_cursor, "lrcPath");
          final int _cursorIndexOfLrcOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "lrcOffset");
          final int _cursorIndexOfLastPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "lastPosition");
          final int _cursorIndexOfPlayCount = CursorUtil.getColumnIndexOrThrow(_cursor, "playCount");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final AudioFile _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpDuration;
            _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            final String _tmpLrcPath;
            if (_cursor.isNull(_cursorIndexOfLrcPath)) {
              _tmpLrcPath = null;
            } else {
              _tmpLrcPath = _cursor.getString(_cursorIndexOfLrcPath);
            }
            final long _tmpLrcOffset;
            _tmpLrcOffset = _cursor.getLong(_cursorIndexOfLrcOffset);
            final long _tmpLastPosition;
            _tmpLastPosition = _cursor.getLong(_cursorIndexOfLastPosition);
            final int _tmpPlayCount;
            _tmpPlayCount = _cursor.getInt(_cursorIndexOfPlayCount);
            final boolean _tmpIsFavorite;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new AudioFile(_tmpId,_tmpPath,_tmpTitle,_tmpDuration,_tmpLrcPath,_tmpLrcOffset,_tmpLastPosition,_tmpPlayCount,_tmpIsFavorite,_tmpCreatedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllPaths(final Continuation<? super List<String>> $completion) {
    final String _sql = "SELECT path FROM audio_files";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<String>>() {
      @Override
      @NonNull
      public List<String> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<String> _result = new ArrayList<String>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final String _item;
            _item = _cursor.getString(0);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
