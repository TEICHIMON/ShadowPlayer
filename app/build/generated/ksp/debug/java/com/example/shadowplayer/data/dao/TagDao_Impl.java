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
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.example.shadowplayer.data.entity.AudioFile;
import com.example.shadowplayer.data.entity.AudioTag;
import com.example.shadowplayer.data.entity.Tag;
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
public final class TagDao_Impl implements TagDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Tag> __insertionAdapterOfTag;

  private final EntityInsertionAdapter<AudioTag> __insertionAdapterOfAudioTag;

  private final EntityDeletionOrUpdateAdapter<Tag> __deletionAdapterOfTag;

  private final EntityDeletionOrUpdateAdapter<AudioTag> __deletionAdapterOfAudioTag;

  private final EntityDeletionOrUpdateAdapter<Tag> __updateAdapterOfTag;

  public TagDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTag = new EntityInsertionAdapter<Tag>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `tags` (`id`,`name`,`color`,`parentId`,`order`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Tag entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getColor());
        if (entity.getParentId() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getParentId());
        }
        statement.bindLong(5, entity.getOrder());
        statement.bindLong(6, entity.getCreatedAt());
      }
    };
    this.__insertionAdapterOfAudioTag = new EntityInsertionAdapter<AudioTag>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `audio_tags` (`audioId`,`tagId`) VALUES (?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AudioTag entity) {
        statement.bindLong(1, entity.getAudioId());
        statement.bindLong(2, entity.getTagId());
      }
    };
    this.__deletionAdapterOfTag = new EntityDeletionOrUpdateAdapter<Tag>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `tags` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Tag entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__deletionAdapterOfAudioTag = new EntityDeletionOrUpdateAdapter<AudioTag>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `audio_tags` WHERE `audioId` = ? AND `tagId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AudioTag entity) {
        statement.bindLong(1, entity.getAudioId());
        statement.bindLong(2, entity.getTagId());
      }
    };
    this.__updateAdapterOfTag = new EntityDeletionOrUpdateAdapter<Tag>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `tags` SET `id` = ?,`name` = ?,`color` = ?,`parentId` = ?,`order` = ?,`createdAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Tag entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getColor());
        if (entity.getParentId() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getParentId());
        }
        statement.bindLong(5, entity.getOrder());
        statement.bindLong(6, entity.getCreatedAt());
        statement.bindLong(7, entity.getId());
      }
    };
  }

  @Override
  public Object insert(final Tag tag, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfTag.insertAndReturnId(tag);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object addTagToAudio(final AudioTag audioTag,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAudioTag.insert(audioTag);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final Tag tag, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfTag.handle(tag);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object removeTagFromAudio(final AudioTag audioTag,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfAudioTag.handle(audioTag);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final Tag tag, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfTag.handle(tag);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Tag>> getRootTags() {
    final String _sql = "SELECT * FROM tags WHERE parentId IS NULL ORDER BY `order` ASC, name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tags"}, new Callable<List<Tag>>() {
      @Override
      @NonNull
      public List<Tag> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "order");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<Tag> _result = new ArrayList<Tag>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Tag _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpColor;
            _tmpColor = _cursor.getString(_cursorIndexOfColor);
            final Long _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getLong(_cursorIndexOfParentId);
            }
            final int _tmpOrder;
            _tmpOrder = _cursor.getInt(_cursorIndexOfOrder);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new Tag(_tmpId,_tmpName,_tmpColor,_tmpParentId,_tmpOrder,_tmpCreatedAt);
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
  public Flow<List<Tag>> getChildTags(final long parentId) {
    final String _sql = "SELECT * FROM tags WHERE parentId = ? ORDER BY `order` ASC, name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, parentId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tags"}, new Callable<List<Tag>>() {
      @Override
      @NonNull
      public List<Tag> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "order");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<Tag> _result = new ArrayList<Tag>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Tag _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpColor;
            _tmpColor = _cursor.getString(_cursorIndexOfColor);
            final Long _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getLong(_cursorIndexOfParentId);
            }
            final int _tmpOrder;
            _tmpOrder = _cursor.getInt(_cursorIndexOfOrder);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new Tag(_tmpId,_tmpName,_tmpColor,_tmpParentId,_tmpOrder,_tmpCreatedAt);
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
  public Flow<List<Tag>> getAllTags() {
    final String _sql = "SELECT * FROM tags ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tags"}, new Callable<List<Tag>>() {
      @Override
      @NonNull
      public List<Tag> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "order");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<Tag> _result = new ArrayList<Tag>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Tag _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpColor;
            _tmpColor = _cursor.getString(_cursorIndexOfColor);
            final Long _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getLong(_cursorIndexOfParentId);
            }
            final int _tmpOrder;
            _tmpOrder = _cursor.getInt(_cursorIndexOfOrder);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new Tag(_tmpId,_tmpName,_tmpColor,_tmpParentId,_tmpOrder,_tmpCreatedAt);
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
  public Object getById(final long id, final Continuation<? super Tag> $completion) {
    final String _sql = "SELECT * FROM tags WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Tag>() {
      @Override
      @Nullable
      public Tag call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "order");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final Tag _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpColor;
            _tmpColor = _cursor.getString(_cursorIndexOfColor);
            final Long _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getLong(_cursorIndexOfParentId);
            }
            final int _tmpOrder;
            _tmpOrder = _cursor.getInt(_cursorIndexOfOrder);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new Tag(_tmpId,_tmpName,_tmpColor,_tmpParentId,_tmpOrder,_tmpCreatedAt);
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
  public Flow<List<AudioFile>> getAudioFilesByTag(final long tagId) {
    final String _sql = "\n"
            + "        SELECT af.* FROM audio_files af\n"
            + "        INNER JOIN audio_tags at ON af.id = at.audioId\n"
            + "        WHERE at.tagId = ?\n"
            + "        ORDER BY af.title ASC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, tagId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"audio_files",
        "audio_tags"}, new Callable<List<AudioFile>>() {
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
  public Flow<List<AudioFile>> getAudioFilesWithAnyTag() {
    final String _sql = "\n"
            + "        SELECT DISTINCT af.* FROM audio_files af\n"
            + "        INNER JOIN audio_tags at ON af.id = at.audioId\n"
            + "        ORDER BY af.title ASC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"audio_files",
        "audio_tags"}, new Callable<List<AudioFile>>() {
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
  public Flow<List<Tag>> getTagsForAudio(final long audioId) {
    final String _sql = "\n"
            + "        SELECT t.* FROM tags t\n"
            + "        INNER JOIN audio_tags at ON t.id = at.tagId\n"
            + "        WHERE at.audioId = ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, audioId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tags",
        "audio_tags"}, new Callable<List<Tag>>() {
      @Override
      @NonNull
      public List<Tag> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "order");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<Tag> _result = new ArrayList<Tag>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Tag _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpColor;
            _tmpColor = _cursor.getString(_cursorIndexOfColor);
            final Long _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getLong(_cursorIndexOfParentId);
            }
            final int _tmpOrder;
            _tmpOrder = _cursor.getInt(_cursorIndexOfOrder);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new Tag(_tmpId,_tmpName,_tmpColor,_tmpParentId,_tmpOrder,_tmpCreatedAt);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
