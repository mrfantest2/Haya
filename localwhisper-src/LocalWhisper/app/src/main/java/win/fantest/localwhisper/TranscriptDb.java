package win.fantest.localwhisper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class TranscriptDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "transcripts.db";
    private static final int DB_VERSION = 1;

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    public static final class Job {
        public long id;
        public String name;
        public String audioPath;
        public String text;
        public String language;
        public String status;
        public long createdAt;
        public String error;
        public long durationMs;
    }

    public TranscriptDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE transcript (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "audio_path TEXT NOT NULL," +
                        "text TEXT NOT NULL DEFAULT ''," +
                        "language TEXT NOT NULL DEFAULT ''," +
                        "status TEXT NOT NULL," +
                        "created_at INTEGER NOT NULL," +
                        "error TEXT NOT NULL DEFAULT ''," +
                        "duration_ms INTEGER NOT NULL DEFAULT 0" +
                        ")"
        );
        db.execSQL("CREATE INDEX idx_transcript_status_id ON transcript(status, id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public long insertPending(String name, String audioPath) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("audio_path", audioPath);
        values.put("status", PENDING);
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("transcript", null, values);
    }

    public void resetInterrupted() {
        ContentValues values = new ContentValues();
        values.put("status", PENDING);
        getWritableDatabase().update("transcript", values, "status=?", new String[]{PROCESSING});
    }

    public Job nextPending() {
        try (Cursor c = getReadableDatabase().query(
                "transcript",
                null,
                "status=?",
                new String[]{PENDING},
                null,
                null,
                "id ASC",
                "1"
        )) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public int pendingCount() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transcript WHERE status IN (?, ?)",
                new String[]{PENDING, PROCESSING}
        )) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void markProcessing(long id) {
        ContentValues values = new ContentValues();
        values.put("status", PROCESSING);
        getWritableDatabase().update("transcript", values, "id=?", new String[]{String.valueOf(id)});
    }

    public void markDone(long id, String text, String language, long durationMs) {
        ContentValues values = new ContentValues();
        values.put("status", DONE);
        values.put("text", text == null ? "" : text);
        values.put("language", language == null ? "" : language);
        values.put("duration_ms", durationMs);
        values.put("error", "");
        getWritableDatabase().update("transcript", values, "id=?", new String[]{String.valueOf(id)});
    }

    public void markFailed(long id, String error) {
        ContentValues values = new ContentValues();
        values.put("status", FAILED);
        values.put("error", error == null ? "Unknown error" : error);
        getWritableDatabase().update("transcript", values, "id=?", new String[]{String.valueOf(id)});
    }

    public List<Job> recent(int limit) {
        List<Job> jobs = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "transcript",
                null,
                null,
                null,
                null,
                null,
                "id DESC",
                String.valueOf(limit)
        )) {
            while (c.moveToNext()) jobs.add(fromCursor(c));
        }
        return jobs;
    }

    public void delete(long id) {
        getWritableDatabase().delete("transcript", "id=?", new String[]{String.valueOf(id)});
    }

    private static Job fromCursor(Cursor c) {
        Job job = new Job();
        job.id = c.getLong(c.getColumnIndexOrThrow("id"));
        job.name = c.getString(c.getColumnIndexOrThrow("name"));
        job.audioPath = c.getString(c.getColumnIndexOrThrow("audio_path"));
        job.text = c.getString(c.getColumnIndexOrThrow("text"));
        job.language = c.getString(c.getColumnIndexOrThrow("language"));
        job.status = c.getString(c.getColumnIndexOrThrow("status"));
        job.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        job.error = c.getString(c.getColumnIndexOrThrow("error"));
        job.durationMs = c.getLong(c.getColumnIndexOrThrow("duration_ms"));
        return job;
    }
}
