package com.kmz.taskmanager.data;

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
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalStateException;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.time.LocalDateTime;
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
public final class TaskDao_Impl implements TaskDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Task> __insertionAdapterOfTask;

  private final Converters __converters = new Converters();

  private final EntityInsertionAdapter<Folder> __insertionAdapterOfFolder;

  private final EntityDeletionOrUpdateAdapter<Task> __deletionAdapterOfTask;

  private final EntityDeletionOrUpdateAdapter<Folder> __deletionAdapterOfFolder;

  private final EntityDeletionOrUpdateAdapter<Task> __updateAdapterOfTask;

  private final EntityDeletionOrUpdateAdapter<Folder> __updateAdapterOfFolder;

  private final SharedSQLiteStatement __preparedStmtOfNukeTasks;

  private final SharedSQLiteStatement __preparedStmtOfNukeFolders;

  public TaskDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTask = new EntityInsertionAdapter<Task>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `tasks` (`id`,`folderId`,`label`,`type`,`dueDate`,`alarmLevel`,`priority`,`isDone`,`repeatInterval`,`repeatUnit`,`warningInterval`,`warningUnit`,`warningRepeatInterval`,`warningRepeatUnit`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Task entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getFolderId());
        statement.bindString(3, entity.getLabel());
        final String _tmp = __converters.taskTypeToString(entity.getType());
        statement.bindString(4, _tmp);
        final String _tmp_1 = __converters.dateToTimestamp(entity.getDueDate());
        if (_tmp_1 == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, _tmp_1);
        }
        final String _tmp_2 = __converters.alarmLevelToString(entity.getAlarmLevel());
        statement.bindString(6, _tmp_2);
        final String _tmp_3 = __converters.priorityToString(entity.getPriority());
        statement.bindString(7, _tmp_3);
        final int _tmp_4 = entity.isDone() ? 1 : 0;
        statement.bindLong(8, _tmp_4);
        if (entity.getRepeatInterval() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getRepeatInterval());
        }
        final String _tmp_5 = __converters.repeatUnitToString(entity.getRepeatUnit());
        if (_tmp_5 == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, _tmp_5);
        }
        statement.bindLong(11, entity.getWarningInterval());
        final String _tmp_6 = __converters.repeatUnitToString(entity.getWarningUnit());
        if (_tmp_6 == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, _tmp_6);
        }
        if (entity.getWarningRepeatInterval() == null) {
          statement.bindNull(13);
        } else {
          statement.bindLong(13, entity.getWarningRepeatInterval());
        }
        final String _tmp_7 = __converters.repeatUnitToString(entity.getWarningRepeatUnit());
        if (_tmp_7 == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, _tmp_7);
        }
        final String _tmp_8 = __converters.dateToTimestamp(entity.getCreatedAt());
        if (_tmp_8 == null) {
          statement.bindNull(15);
        } else {
          statement.bindString(15, _tmp_8);
        }
      }
    };
    this.__insertionAdapterOfFolder = new EntityInsertionAdapter<Folder>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `folders` (`id`,`name`,`color`) VALUES (nullif(?, 0),?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Folder entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        if (entity.getColor() == null) {
          statement.bindNull(3);
        } else {
          statement.bindLong(3, entity.getColor());
        }
      }
    };
    this.__deletionAdapterOfTask = new EntityDeletionOrUpdateAdapter<Task>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `tasks` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Task entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__deletionAdapterOfFolder = new EntityDeletionOrUpdateAdapter<Folder>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `folders` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Folder entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfTask = new EntityDeletionOrUpdateAdapter<Task>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `tasks` SET `id` = ?,`folderId` = ?,`label` = ?,`type` = ?,`dueDate` = ?,`alarmLevel` = ?,`priority` = ?,`isDone` = ?,`repeatInterval` = ?,`repeatUnit` = ?,`warningInterval` = ?,`warningUnit` = ?,`warningRepeatInterval` = ?,`warningRepeatUnit` = ?,`createdAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Task entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getFolderId());
        statement.bindString(3, entity.getLabel());
        final String _tmp = __converters.taskTypeToString(entity.getType());
        statement.bindString(4, _tmp);
        final String _tmp_1 = __converters.dateToTimestamp(entity.getDueDate());
        if (_tmp_1 == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, _tmp_1);
        }
        final String _tmp_2 = __converters.alarmLevelToString(entity.getAlarmLevel());
        statement.bindString(6, _tmp_2);
        final String _tmp_3 = __converters.priorityToString(entity.getPriority());
        statement.bindString(7, _tmp_3);
        final int _tmp_4 = entity.isDone() ? 1 : 0;
        statement.bindLong(8, _tmp_4);
        if (entity.getRepeatInterval() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getRepeatInterval());
        }
        final String _tmp_5 = __converters.repeatUnitToString(entity.getRepeatUnit());
        if (_tmp_5 == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, _tmp_5);
        }
        statement.bindLong(11, entity.getWarningInterval());
        final String _tmp_6 = __converters.repeatUnitToString(entity.getWarningUnit());
        if (_tmp_6 == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, _tmp_6);
        }
        if (entity.getWarningRepeatInterval() == null) {
          statement.bindNull(13);
        } else {
          statement.bindLong(13, entity.getWarningRepeatInterval());
        }
        final String _tmp_7 = __converters.repeatUnitToString(entity.getWarningRepeatUnit());
        if (_tmp_7 == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, _tmp_7);
        }
        final String _tmp_8 = __converters.dateToTimestamp(entity.getCreatedAt());
        if (_tmp_8 == null) {
          statement.bindNull(15);
        } else {
          statement.bindString(15, _tmp_8);
        }
        statement.bindLong(16, entity.getId());
      }
    };
    this.__updateAdapterOfFolder = new EntityDeletionOrUpdateAdapter<Folder>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `folders` SET `id` = ?,`name` = ?,`color` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Folder entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        if (entity.getColor() == null) {
          statement.bindNull(3);
        } else {
          statement.bindLong(3, entity.getColor());
        }
        statement.bindLong(4, entity.getId());
      }
    };
    this.__preparedStmtOfNukeTasks = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM tasks";
        return _query;
      }
    };
    this.__preparedStmtOfNukeFolders = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM folders";
        return _query;
      }
    };
  }

  @Override
  public Object insertTask(final Task task, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfTask.insertAndReturnId(task);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertFolder(final Folder folder, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfFolder.insertAndReturnId(folder);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteTask(final Task task, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfTask.handle(task);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteFolder(final Folder folder, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfFolder.handle(folder);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateTask(final Task task, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfTask.handle(task);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateFolder(final Folder folder, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfFolder.handle(folder);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object nukeTasks(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfNukeTasks.acquire();
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
          __preparedStmtOfNukeTasks.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object nukeFolders(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfNukeFolders.acquire();
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
          __preparedStmtOfNukeFolders.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Task>> getAllTasks() {
    final String _sql = "SELECT * FROM tasks ORDER BY isDone ASC, priority DESC, dueDate ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tasks"}, new Callable<List<Task>>() {
      @Override
      @NonNull
      public List<Task> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "folderId");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDate");
          final int _cursorIndexOfAlarmLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "alarmLevel");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final int _cursorIndexOfIsDone = CursorUtil.getColumnIndexOrThrow(_cursor, "isDone");
          final int _cursorIndexOfRepeatInterval = CursorUtil.getColumnIndexOrThrow(_cursor, "repeatInterval");
          final int _cursorIndexOfRepeatUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "repeatUnit");
          final int _cursorIndexOfWarningInterval = CursorUtil.getColumnIndexOrThrow(_cursor, "warningInterval");
          final int _cursorIndexOfWarningUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "warningUnit");
          final int _cursorIndexOfWarningRepeatInterval = CursorUtil.getColumnIndexOrThrow(_cursor, "warningRepeatInterval");
          final int _cursorIndexOfWarningRepeatUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "warningRepeatUnit");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<Task> _result = new ArrayList<Task>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Task _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpFolderId;
            _tmpFolderId = _cursor.getLong(_cursorIndexOfFolderId);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final TaskType _tmpType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfType);
            _tmpType = __converters.fromTaskType(_tmp);
            final LocalDateTime _tmpDueDate;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfDueDate)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfDueDate);
            }
            _tmpDueDate = __converters.fromTimestamp(_tmp_1);
            final AlarmLevel _tmpAlarmLevel;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfAlarmLevel);
            _tmpAlarmLevel = __converters.fromAlarmLevel(_tmp_2);
            final Priority _tmpPriority;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfPriority);
            _tmpPriority = __converters.fromPriority(_tmp_3);
            final boolean _tmpIsDone;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsDone);
            _tmpIsDone = _tmp_4 != 0;
            final Integer _tmpRepeatInterval;
            if (_cursor.isNull(_cursorIndexOfRepeatInterval)) {
              _tmpRepeatInterval = null;
            } else {
              _tmpRepeatInterval = _cursor.getInt(_cursorIndexOfRepeatInterval);
            }
            final RepeatUnit _tmpRepeatUnit;
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfRepeatUnit)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfRepeatUnit);
            }
            _tmpRepeatUnit = __converters.fromRepeatUnit(_tmp_5);
            final int _tmpWarningInterval;
            _tmpWarningInterval = _cursor.getInt(_cursorIndexOfWarningInterval);
            final RepeatUnit _tmpWarningUnit;
            final String _tmp_6;
            if (_cursor.isNull(_cursorIndexOfWarningUnit)) {
              _tmp_6 = null;
            } else {
              _tmp_6 = _cursor.getString(_cursorIndexOfWarningUnit);
            }
            final RepeatUnit _tmp_7 = __converters.fromRepeatUnit(_tmp_6);
            if (_tmp_7 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.kmz.taskmanager.data.RepeatUnit', but it was NULL.");
            } else {
              _tmpWarningUnit = _tmp_7;
            }
            final Integer _tmpWarningRepeatInterval;
            if (_cursor.isNull(_cursorIndexOfWarningRepeatInterval)) {
              _tmpWarningRepeatInterval = null;
            } else {
              _tmpWarningRepeatInterval = _cursor.getInt(_cursorIndexOfWarningRepeatInterval);
            }
            final RepeatUnit _tmpWarningRepeatUnit;
            final String _tmp_8;
            if (_cursor.isNull(_cursorIndexOfWarningRepeatUnit)) {
              _tmp_8 = null;
            } else {
              _tmp_8 = _cursor.getString(_cursorIndexOfWarningRepeatUnit);
            }
            _tmpWarningRepeatUnit = __converters.fromRepeatUnit(_tmp_8);
            final LocalDateTime _tmpCreatedAt;
            final String _tmp_9;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_9 = null;
            } else {
              _tmp_9 = _cursor.getString(_cursorIndexOfCreatedAt);
            }
            final LocalDateTime _tmp_10 = __converters.fromTimestamp(_tmp_9);
            if (_tmp_10 == null) {
              throw new IllegalStateException("Expected NON-NULL 'java.time.LocalDateTime', but it was NULL.");
            } else {
              _tmpCreatedAt = _tmp_10;
            }
            _item = new Task(_tmpId,_tmpFolderId,_tmpLabel,_tmpType,_tmpDueDate,_tmpAlarmLevel,_tmpPriority,_tmpIsDone,_tmpRepeatInterval,_tmpRepeatUnit,_tmpWarningInterval,_tmpWarningUnit,_tmpWarningRepeatInterval,_tmpWarningRepeatUnit,_tmpCreatedAt);
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
  public Flow<List<Task>> getTasksByFolder(final long folderId) {
    final String _sql = "SELECT * FROM tasks WHERE folderId = ? ORDER BY isDone ASC, priority DESC, dueDate ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, folderId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tasks"}, new Callable<List<Task>>() {
      @Override
      @NonNull
      public List<Task> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "folderId");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDate");
          final int _cursorIndexOfAlarmLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "alarmLevel");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final int _cursorIndexOfIsDone = CursorUtil.getColumnIndexOrThrow(_cursor, "isDone");
          final int _cursorIndexOfRepeatInterval = CursorUtil.getColumnIndexOrThrow(_cursor, "repeatInterval");
          final int _cursorIndexOfRepeatUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "repeatUnit");
          final int _cursorIndexOfWarningInterval = CursorUtil.getColumnIndexOrThrow(_cursor, "warningInterval");
          final int _cursorIndexOfWarningUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "warningUnit");
          final int _cursorIndexOfWarningRepeatInterval = CursorUtil.getColumnIndexOrThrow(_cursor, "warningRepeatInterval");
          final int _cursorIndexOfWarningRepeatUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "warningRepeatUnit");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<Task> _result = new ArrayList<Task>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Task _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpFolderId;
            _tmpFolderId = _cursor.getLong(_cursorIndexOfFolderId);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final TaskType _tmpType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfType);
            _tmpType = __converters.fromTaskType(_tmp);
            final LocalDateTime _tmpDueDate;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfDueDate)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfDueDate);
            }
            _tmpDueDate = __converters.fromTimestamp(_tmp_1);
            final AlarmLevel _tmpAlarmLevel;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfAlarmLevel);
            _tmpAlarmLevel = __converters.fromAlarmLevel(_tmp_2);
            final Priority _tmpPriority;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfPriority);
            _tmpPriority = __converters.fromPriority(_tmp_3);
            final boolean _tmpIsDone;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsDone);
            _tmpIsDone = _tmp_4 != 0;
            final Integer _tmpRepeatInterval;
            if (_cursor.isNull(_cursorIndexOfRepeatInterval)) {
              _tmpRepeatInterval = null;
            } else {
              _tmpRepeatInterval = _cursor.getInt(_cursorIndexOfRepeatInterval);
            }
            final RepeatUnit _tmpRepeatUnit;
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfRepeatUnit)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfRepeatUnit);
            }
            _tmpRepeatUnit = __converters.fromRepeatUnit(_tmp_5);
            final int _tmpWarningInterval;
            _tmpWarningInterval = _cursor.getInt(_cursorIndexOfWarningInterval);
            final RepeatUnit _tmpWarningUnit;
            final String _tmp_6;
            if (_cursor.isNull(_cursorIndexOfWarningUnit)) {
              _tmp_6 = null;
            } else {
              _tmp_6 = _cursor.getString(_cursorIndexOfWarningUnit);
            }
            final RepeatUnit _tmp_7 = __converters.fromRepeatUnit(_tmp_6);
            if (_tmp_7 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.kmz.taskmanager.data.RepeatUnit', but it was NULL.");
            } else {
              _tmpWarningUnit = _tmp_7;
            }
            final Integer _tmpWarningRepeatInterval;
            if (_cursor.isNull(_cursorIndexOfWarningRepeatInterval)) {
              _tmpWarningRepeatInterval = null;
            } else {
              _tmpWarningRepeatInterval = _cursor.getInt(_cursorIndexOfWarningRepeatInterval);
            }
            final RepeatUnit _tmpWarningRepeatUnit;
            final String _tmp_8;
            if (_cursor.isNull(_cursorIndexOfWarningRepeatUnit)) {
              _tmp_8 = null;
            } else {
              _tmp_8 = _cursor.getString(_cursorIndexOfWarningRepeatUnit);
            }
            _tmpWarningRepeatUnit = __converters.fromRepeatUnit(_tmp_8);
            final LocalDateTime _tmpCreatedAt;
            final String _tmp_9;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_9 = null;
            } else {
              _tmp_9 = _cursor.getString(_cursorIndexOfCreatedAt);
            }
            final LocalDateTime _tmp_10 = __converters.fromTimestamp(_tmp_9);
            if (_tmp_10 == null) {
              throw new IllegalStateException("Expected NON-NULL 'java.time.LocalDateTime', but it was NULL.");
            } else {
              _tmpCreatedAt = _tmp_10;
            }
            _item = new Task(_tmpId,_tmpFolderId,_tmpLabel,_tmpType,_tmpDueDate,_tmpAlarmLevel,_tmpPriority,_tmpIsDone,_tmpRepeatInterval,_tmpRepeatUnit,_tmpWarningInterval,_tmpWarningUnit,_tmpWarningRepeatInterval,_tmpWarningRepeatUnit,_tmpCreatedAt);
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
  public Object getTaskById(final long id, final Continuation<? super Task> $completion) {
    final String _sql = "SELECT * FROM tasks WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Task>() {
      @Override
      @Nullable
      public Task call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "folderId");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDate");
          final int _cursorIndexOfAlarmLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "alarmLevel");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final int _cursorIndexOfIsDone = CursorUtil.getColumnIndexOrThrow(_cursor, "isDone");
          final int _cursorIndexOfRepeatInterval = CursorUtil.getColumnIndexOrThrow(_cursor, "repeatInterval");
          final int _cursorIndexOfRepeatUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "repeatUnit");
          final int _cursorIndexOfWarningInterval = CursorUtil.getColumnIndexOrThrow(_cursor, "warningInterval");
          final int _cursorIndexOfWarningUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "warningUnit");
          final int _cursorIndexOfWarningRepeatInterval = CursorUtil.getColumnIndexOrThrow(_cursor, "warningRepeatInterval");
          final int _cursorIndexOfWarningRepeatUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "warningRepeatUnit");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final Task _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpFolderId;
            _tmpFolderId = _cursor.getLong(_cursorIndexOfFolderId);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final TaskType _tmpType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfType);
            _tmpType = __converters.fromTaskType(_tmp);
            final LocalDateTime _tmpDueDate;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfDueDate)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfDueDate);
            }
            _tmpDueDate = __converters.fromTimestamp(_tmp_1);
            final AlarmLevel _tmpAlarmLevel;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfAlarmLevel);
            _tmpAlarmLevel = __converters.fromAlarmLevel(_tmp_2);
            final Priority _tmpPriority;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfPriority);
            _tmpPriority = __converters.fromPriority(_tmp_3);
            final boolean _tmpIsDone;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsDone);
            _tmpIsDone = _tmp_4 != 0;
            final Integer _tmpRepeatInterval;
            if (_cursor.isNull(_cursorIndexOfRepeatInterval)) {
              _tmpRepeatInterval = null;
            } else {
              _tmpRepeatInterval = _cursor.getInt(_cursorIndexOfRepeatInterval);
            }
            final RepeatUnit _tmpRepeatUnit;
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfRepeatUnit)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfRepeatUnit);
            }
            _tmpRepeatUnit = __converters.fromRepeatUnit(_tmp_5);
            final int _tmpWarningInterval;
            _tmpWarningInterval = _cursor.getInt(_cursorIndexOfWarningInterval);
            final RepeatUnit _tmpWarningUnit;
            final String _tmp_6;
            if (_cursor.isNull(_cursorIndexOfWarningUnit)) {
              _tmp_6 = null;
            } else {
              _tmp_6 = _cursor.getString(_cursorIndexOfWarningUnit);
            }
            final RepeatUnit _tmp_7 = __converters.fromRepeatUnit(_tmp_6);
            if (_tmp_7 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.kmz.taskmanager.data.RepeatUnit', but it was NULL.");
            } else {
              _tmpWarningUnit = _tmp_7;
            }
            final Integer _tmpWarningRepeatInterval;
            if (_cursor.isNull(_cursorIndexOfWarningRepeatInterval)) {
              _tmpWarningRepeatInterval = null;
            } else {
              _tmpWarningRepeatInterval = _cursor.getInt(_cursorIndexOfWarningRepeatInterval);
            }
            final RepeatUnit _tmpWarningRepeatUnit;
            final String _tmp_8;
            if (_cursor.isNull(_cursorIndexOfWarningRepeatUnit)) {
              _tmp_8 = null;
            } else {
              _tmp_8 = _cursor.getString(_cursorIndexOfWarningRepeatUnit);
            }
            _tmpWarningRepeatUnit = __converters.fromRepeatUnit(_tmp_8);
            final LocalDateTime _tmpCreatedAt;
            final String _tmp_9;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_9 = null;
            } else {
              _tmp_9 = _cursor.getString(_cursorIndexOfCreatedAt);
            }
            final LocalDateTime _tmp_10 = __converters.fromTimestamp(_tmp_9);
            if (_tmp_10 == null) {
              throw new IllegalStateException("Expected NON-NULL 'java.time.LocalDateTime', but it was NULL.");
            } else {
              _tmpCreatedAt = _tmp_10;
            }
            _result = new Task(_tmpId,_tmpFolderId,_tmpLabel,_tmpType,_tmpDueDate,_tmpAlarmLevel,_tmpPriority,_tmpIsDone,_tmpRepeatInterval,_tmpRepeatUnit,_tmpWarningInterval,_tmpWarningUnit,_tmpWarningRepeatInterval,_tmpWarningRepeatUnit,_tmpCreatedAt);
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
  public Flow<List<Folder>> getAllFolders() {
    final String _sql = "SELECT * FROM folders";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"folders"}, new Callable<List<Folder>>() {
      @Override
      @NonNull
      public List<Folder> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final List<Folder> _result = new ArrayList<Folder>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Folder _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final Integer _tmpColor;
            if (_cursor.isNull(_cursorIndexOfColor)) {
              _tmpColor = null;
            } else {
              _tmpColor = _cursor.getInt(_cursorIndexOfColor);
            }
            _item = new Folder(_tmpId,_tmpName,_tmpColor);
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
  public Object getAllTasksSync(final Continuation<? super List<Task>> $completion) {
    final String _sql = "SELECT * FROM tasks";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Task>>() {
      @Override
      @NonNull
      public List<Task> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFolderId = CursorUtil.getColumnIndexOrThrow(_cursor, "folderId");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDate");
          final int _cursorIndexOfAlarmLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "alarmLevel");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final int _cursorIndexOfIsDone = CursorUtil.getColumnIndexOrThrow(_cursor, "isDone");
          final int _cursorIndexOfRepeatInterval = CursorUtil.getColumnIndexOrThrow(_cursor, "repeatInterval");
          final int _cursorIndexOfRepeatUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "repeatUnit");
          final int _cursorIndexOfWarningInterval = CursorUtil.getColumnIndexOrThrow(_cursor, "warningInterval");
          final int _cursorIndexOfWarningUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "warningUnit");
          final int _cursorIndexOfWarningRepeatInterval = CursorUtil.getColumnIndexOrThrow(_cursor, "warningRepeatInterval");
          final int _cursorIndexOfWarningRepeatUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "warningRepeatUnit");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<Task> _result = new ArrayList<Task>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Task _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpFolderId;
            _tmpFolderId = _cursor.getLong(_cursorIndexOfFolderId);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final TaskType _tmpType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfType);
            _tmpType = __converters.fromTaskType(_tmp);
            final LocalDateTime _tmpDueDate;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfDueDate)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfDueDate);
            }
            _tmpDueDate = __converters.fromTimestamp(_tmp_1);
            final AlarmLevel _tmpAlarmLevel;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfAlarmLevel);
            _tmpAlarmLevel = __converters.fromAlarmLevel(_tmp_2);
            final Priority _tmpPriority;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfPriority);
            _tmpPriority = __converters.fromPriority(_tmp_3);
            final boolean _tmpIsDone;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsDone);
            _tmpIsDone = _tmp_4 != 0;
            final Integer _tmpRepeatInterval;
            if (_cursor.isNull(_cursorIndexOfRepeatInterval)) {
              _tmpRepeatInterval = null;
            } else {
              _tmpRepeatInterval = _cursor.getInt(_cursorIndexOfRepeatInterval);
            }
            final RepeatUnit _tmpRepeatUnit;
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfRepeatUnit)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfRepeatUnit);
            }
            _tmpRepeatUnit = __converters.fromRepeatUnit(_tmp_5);
            final int _tmpWarningInterval;
            _tmpWarningInterval = _cursor.getInt(_cursorIndexOfWarningInterval);
            final RepeatUnit _tmpWarningUnit;
            final String _tmp_6;
            if (_cursor.isNull(_cursorIndexOfWarningUnit)) {
              _tmp_6 = null;
            } else {
              _tmp_6 = _cursor.getString(_cursorIndexOfWarningUnit);
            }
            final RepeatUnit _tmp_7 = __converters.fromRepeatUnit(_tmp_6);
            if (_tmp_7 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.kmz.taskmanager.data.RepeatUnit', but it was NULL.");
            } else {
              _tmpWarningUnit = _tmp_7;
            }
            final Integer _tmpWarningRepeatInterval;
            if (_cursor.isNull(_cursorIndexOfWarningRepeatInterval)) {
              _tmpWarningRepeatInterval = null;
            } else {
              _tmpWarningRepeatInterval = _cursor.getInt(_cursorIndexOfWarningRepeatInterval);
            }
            final RepeatUnit _tmpWarningRepeatUnit;
            final String _tmp_8;
            if (_cursor.isNull(_cursorIndexOfWarningRepeatUnit)) {
              _tmp_8 = null;
            } else {
              _tmp_8 = _cursor.getString(_cursorIndexOfWarningRepeatUnit);
            }
            _tmpWarningRepeatUnit = __converters.fromRepeatUnit(_tmp_8);
            final LocalDateTime _tmpCreatedAt;
            final String _tmp_9;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_9 = null;
            } else {
              _tmp_9 = _cursor.getString(_cursorIndexOfCreatedAt);
            }
            final LocalDateTime _tmp_10 = __converters.fromTimestamp(_tmp_9);
            if (_tmp_10 == null) {
              throw new IllegalStateException("Expected NON-NULL 'java.time.LocalDateTime', but it was NULL.");
            } else {
              _tmpCreatedAt = _tmp_10;
            }
            _item = new Task(_tmpId,_tmpFolderId,_tmpLabel,_tmpType,_tmpDueDate,_tmpAlarmLevel,_tmpPriority,_tmpIsDone,_tmpRepeatInterval,_tmpRepeatUnit,_tmpWarningInterval,_tmpWarningUnit,_tmpWarningRepeatInterval,_tmpWarningRepeatUnit,_tmpCreatedAt);
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

  @Override
  public Object getAllFoldersSync(final Continuation<? super List<Folder>> $completion) {
    final String _sql = "SELECT * FROM folders";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Folder>>() {
      @Override
      @NonNull
      public List<Folder> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final List<Folder> _result = new ArrayList<Folder>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Folder _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final Integer _tmpColor;
            if (_cursor.isNull(_cursorIndexOfColor)) {
              _tmpColor = null;
            } else {
              _tmpColor = _cursor.getInt(_cursorIndexOfColor);
            }
            _item = new Folder(_tmpId,_tmpName,_tmpColor);
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
