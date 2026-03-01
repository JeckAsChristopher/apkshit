package com.locai.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent memory for LOCAI.
 * Stores every message in SQLite so the AI remembers everything across sessions.
 */
public class MemoryManager extends SQLiteOpenHelper {

    private static final String DB_NAME    = "locai_memory.db";
    private static final int    DB_VERSION = 1;

    private static final String TABLE      = "messages";
    private static final String COL_ID     = "id";
    private static final String COL_ROLE   = "role";
    private static final String COL_CONTENT= "content";
    private static final String COL_TS     = "timestamp";

    private static MemoryManager instance;

    public static synchronized MemoryManager getInstance(Context ctx) {
        if (instance == null) {
            instance = new MemoryManager(ctx.getApplicationContext());
        }
        return instance;
    }

    private MemoryManager(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID      + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_ROLE    + " TEXT NOT NULL, " +
                COL_CONTENT + " TEXT NOT NULL, " +
                COL_TS      + " INTEGER NOT NULL" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    /** Save a single message to memory. */
    public void save(Message msg) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues  cv = new ContentValues();
        cv.put(COL_ROLE,    msg.getRole());
        cv.put(COL_CONTENT, msg.getContent());
        cv.put(COL_TS,      msg.getTimestamp());
        db.insert(TABLE, null, cv);
    }

    /** Load ALL messages from memory (full history). */
    public List<Message> loadAll() {
        List<Message> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null, COL_TS + " ASC");
        if (c.moveToFirst()) {
            do {
                long   id      = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                String role    = c.getString(c.getColumnIndexOrThrow(COL_ROLE));
                String content = c.getString(c.getColumnIndexOrThrow(COL_CONTENT));
                long   ts      = c.getLong(c.getColumnIndexOrThrow(COL_TS));
                list.add(new Message(id, role, content, ts));
            } while (c.moveToNext());
        }
        c.close();
        return list;
    }

    /**
     * Load the last N messages to send as context window to the model.
     * Keeps RAM usage bounded while still giving the AI recent memory.
     */
    public List<Message> loadLastN(int n) {
        List<Message> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null,
                COL_TS + " DESC", String.valueOf(n));
        // Reverse order so oldest-first
        List<Message> reversed = new ArrayList<>();
        if (c.moveToFirst()) {
            do {
                long   id      = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                String role    = c.getString(c.getColumnIndexOrThrow(COL_ROLE));
                String content = c.getString(c.getColumnIndexOrThrow(COL_CONTENT));
                long   ts      = c.getLong(c.getColumnIndexOrThrow(COL_TS));
                reversed.add(0, new Message(id, role, content, ts));
            } while (c.moveToNext());
        }
        c.close();
        return reversed;
    }

    /** Wipe all memory (clear chat). */
    public void clearAll() {
        getWritableDatabase().execSQL("DELETE FROM " + TABLE);
    }

    /** Total message count. */
    public int count() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE, null);
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }
}
