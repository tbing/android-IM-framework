package com.im.framework.app.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.Bundle;

public class BackStorage {
  private static final String DATABASE_NAME = "IM.db";
  private static final int DATABASE_VERSION = 1;
  private static final String SEND_TABLE_NAME = "SEND_MESSAGE";
  private static final String RECEIVE_TABLE_NAME = "RECEIVE_MESSAGE";
  private static Map<String, String> projectionMap;
  private final QueryComparator queryComparator = new QueryComparator();

  private SQLiteDatabase dbInstance;
  private DatabaseHelper mDatabase;
  private final ReentrantReadWriteLock dbLock = new ReentrantReadWriteLock();

  public BackStorage(Context context) {
    mDatabase = new DatabaseHelper(context);
  }

  private static class DatabaseHelper extends SQLiteOpenHelper {

    DatabaseHelper(Context context) {
      super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL("CREATE TABLE IF NOT EXISTS " + SEND_TABLE_NAME + " (" + MessageItem._ID + " INTEGER PRIMARY KEY," + MessageItem.WHO + " TEXT," + MessageItem.TYPE + " INTEGER,"
          + MessageItem.CONTENTS + " BLOB," + MessageItem.SEQNUM + " BIGINT," + MessageItem.TIMESTAMP + " BIGINT," + MessageItem.RELATE + " TEXT" + ");");
      db.execSQL("CREATE TABLE IF NOT EXISTS " + RECEIVE_TABLE_NAME + " (" + MessageItem._ID + " INTEGER PRIMARY KEY," + MessageItem.WHO + " TEXT," + MessageItem.TYPE + " INTEGER,"
          + MessageItem.CONTENTS + " BLOB," + MessageItem.SEQNUM + " BIGINT," + MessageItem.TIMESTAMP + " BIGINT," + MessageItem.RELATE + " TEXT" + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      // use when we update the table schema,don't implement now.
      if (oldVersion >= newVersion) return;
    }

  }

  static {
    projectionMap = new HashMap<String, String>();
    projectionMap.put(MessageItem._ID, MessageItem._ID);
    projectionMap.put(MessageItem.WHO, MessageItem.WHO);
    projectionMap.put(MessageItem.TYPE, MessageItem.TYPE);
    projectionMap.put(MessageItem.CONTENTS, MessageItem.CONTENTS);
    projectionMap.put(MessageItem.SEQNUM, MessageItem.SEQNUM);
    projectionMap.put(MessageItem.TIMESTAMP, MessageItem.TIMESTAMP);
    projectionMap.put(MessageItem.RELATE, MessageItem.RELATE);
  }

  static class MessageItem {
    static final String _ID = "_id";
    static final String WHO = "who";
    static final String TYPE = "type";
    static final String CONTENTS = "contents";
    static final String SEQNUM = "seqnum";
    static final String TIMESTAMP = "timestamp";
    static final String RELATE = "relate";
  }

  private SQLiteDatabase getDatabase(boolean write) {
    SQLiteDatabase db;
    dbLock.readLock().lock();
    try {
      db = write ? mDatabase.getWritableDatabase() : mDatabase.getReadableDatabase();
      if (dbInstance != db) {
        dbInstance = db;
      }
    } finally {
      dbLock.readLock().unlock();
    }
    return db;
  }

  public void close() {
    dbLock.writeLock().lock();
    try {
      dbInstance.close();
    } finally {
      dbLock.writeLock().unlock();
    }
  }

  private void put(SMessage message, long seq, long time, String user, boolean send) {
    ContentValues values = new ContentValues();
    if (send) {
      values.put(MessageItem.WHO, user);
      values.put(MessageItem.RELATE, message.getWho());
    } else {
      values.put(MessageItem.WHO, message.getWho());
      values.put(MessageItem.RELATE, user);
    }
    values.put(MessageItem.TYPE, message.getType());
    values.put(MessageItem.CONTENTS, message.getContents());
    values.put(MessageItem.SEQNUM, seq);
    values.put(MessageItem.TIMESTAMP, time);
    SQLiteDatabase db = getDatabase(true);
    String table = send ? SEND_TABLE_NAME : RECEIVE_TABLE_NAME;
    long id = db.insert(table, null, values);
    if (id < 0) {
      throw new SQLException("Failed to insert row into " + table);
    }
  }

  public void putWithRetry(SMessage message, long seq, long time, String user, boolean send) {
    try {
      put(message, seq, time, user, send);
    } catch (SQLException sqle) {
      close();
      try {
        put(message, seq, time, user, send);
      } catch (Exception e) {
        return;
      }
    }
  }

  public List<Bundle> scanByTime(long startTimestamp, long endTimestamp, String user, String relate) {
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    // do receive table query;
    qb.setTables(RECEIVE_TABLE_NAME);
    qb.setProjectionMap(projectionMap);
    String[] projectionIn = {MessageItem.TYPE, MessageItem.CONTENTS, MessageItem.TIMESTAMP, MessageItem.SEQNUM};
    if (endTimestamp < 0) {
      endTimestamp = Long.MAX_VALUE;
    }
    String[] selectionArgsForReceive = {relate, user, Long.toString(startTimestamp), Long.toString(endTimestamp)};
    List<Bundle> receiveMessages = new ArrayList<Bundle>();
    String where = MessageItem.WHO + " =? AND " + MessageItem.RELATE + " =? AND " + "( " + MessageItem.TIMESTAMP + " >=? AND " + MessageItem.TIMESTAMP + " <? )";
    processQuery(qb, projectionIn, where, selectionArgsForReceive, receiveMessages, true);
    // do send table query;
    qb = new SQLiteQueryBuilder();
    qb.setTables(SEND_TABLE_NAME);
    qb.setProjectionMap(projectionMap);
    String[] selectionArgsForSend = {user, relate, Long.toString(startTimestamp), Long.toString(endTimestamp)};
    List<Bundle> sendMessages = new ArrayList<Bundle>();
    processQuery(qb, projectionIn, where, selectionArgsForSend, sendMessages, false);
    // merge sort all results.
    List<Bundle> allResults = new ArrayList<Bundle>();
    allResults.addAll(receiveMessages);
    allResults.addAll(sendMessages);
    Collections.sort(allResults, queryComparator);
    return allResults;
  }

  private void processQuery(SQLiteQueryBuilder qb, String[] projectionIn, String selection, String[] selectionArgs, List<Bundle> results, boolean receive) {
    SQLiteDatabase db = getDatabase(false);
    Cursor c = qb.query(db, projectionIn, selection, selectionArgs, null, null, MessageItem.SEQNUM + " ASC");
    if (c.moveToFirst()) {
      do {
        Bundle m = new Bundle();
        m.putInt(MessageItem.TYPE, c.getInt(c.getColumnIndex(MessageItem.TYPE)));
        m.putByteArray(MessageItem.CONTENTS, c.getBlob(c.getColumnIndex(MessageItem.CONTENTS)));
        m.putLong(MessageItem.TIMESTAMP, c.getLong(c.getColumnIndex(MessageItem.TIMESTAMP)));
        m.putLong(MessageItem.SEQNUM, c.getLong(c.getColumnIndex(MessageItem.SEQNUM)));
        if (projectionIn == null) {
          m.putString(MessageItem.WHO, c.getString(c.getColumnIndex(MessageItem.WHO)));
          m.putString(MessageItem.RELATE, c.getString(c.getColumnIndex(MessageItem.RELATE)));
        }
        if (receive) {
          m.putByte("RS", (byte) 1);
        } else {
          m.putByte("RS", (byte) 0);
        }
        results.add(m);
      } while (c.moveToNext());
    }
  }

  public List<Bundle> scanWithMaxSeq(long maxSeq) {
    // only query the receive table.
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    qb.setTables(RECEIVE_TABLE_NAME);
    qb.setProjectionMap(projectionMap);
    String where = MessageItem.SEQNUM + " >?";
    List<Bundle> result = new ArrayList<Bundle>();
    processQuery(qb, null, where, new String[] {Long.toString(maxSeq)}, result, true);
    return result;
  }

  public long getMaxSeq(String user) {
    try {
      SQLiteDatabase db = getDatabase(false);
      String sql = "SELECT MAX( " + MessageItem.SEQNUM + " ) FROM " + RECEIVE_TABLE_NAME + " WHERE " + MessageItem.RELATE + " =?";
      Cursor result = db.rawQuery(sql, new String[] {user});
      long receiveMax = 0;
      if (result.moveToFirst()) {
        receiveMax = result.getLong(0);
      }
      sql = "SELECT MAX( " + MessageItem.SEQNUM + " ) FROM " + SEND_TABLE_NAME + " WHERE " + MessageItem.WHO + " =?";
      result = db.rawQuery(sql, new String[] {user});
      long sendMax = 0;
      if (result.moveToFirst()) {
        sendMax = result.getLong(0);
      }
      return Math.max(receiveMax, sendMax);
    } catch (Exception e) {
      return 0;
    }
  }

  private final class QueryComparator implements Comparator<Bundle> {

    @Override
    public int compare(Bundle l, Bundle r) {
      if (l.getLong(MessageItem.SEQNUM) > r.getLong(MessageItem.SEQNUM)) return 1;
      if (l.getLong(MessageItem.SEQNUM) < r.getLong(MessageItem.SEQNUM)) return -1;
      return 0;
    }

  }
}
