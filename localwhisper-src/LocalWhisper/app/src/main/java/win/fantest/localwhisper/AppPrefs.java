package win.fantest.localwhisper;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String NAME = "local_whisper";
    private static final String KEY_MODEL = "model";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_OUTPUT = "output";
    private static final String KEY_TIMESTAMPS = "timestamps";
    private static final String KEY_KEEP_AUDIO = "keep_audio";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_DOWNLOAD_MODEL = "download_model";

    public static final String OUTPUT_ORIGINAL = "original";
    public static final String OUTPUT_ENGLISH = "english";
    public static final String OUTPUT_BOTH = "both";

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public String model() { return prefs.getString(KEY_MODEL, ModelCatalog.QUALITY); }
    public void setModel(String value) { prefs.edit().putString(KEY_MODEL, value).apply(); }

    public String language() { return prefs.getString(KEY_LANGUAGE, "auto"); }
    public void setLanguage(String value) { prefs.edit().putString(KEY_LANGUAGE, value).apply(); }

    public String outputMode() { return prefs.getString(KEY_OUTPUT, OUTPUT_ORIGINAL); }
    public void setOutputMode(String value) { prefs.edit().putString(KEY_OUTPUT, value).apply(); }

    public boolean timestamps() { return prefs.getBoolean(KEY_TIMESTAMPS, false); }
    public void setTimestamps(boolean value) { prefs.edit().putBoolean(KEY_TIMESTAMPS, value).apply(); }

    public boolean keepAudio() { return prefs.getBoolean(KEY_KEEP_AUDIO, false); }
    public void setKeepAudio(boolean value) { prefs.edit().putBoolean(KEY_KEEP_AUDIO, value).apply(); }

    public long downloadId() { return prefs.getLong(KEY_DOWNLOAD_ID, -1L); }
    public String downloadModel() { return prefs.getString(KEY_DOWNLOAD_MODEL, ""); }
    public void setDownload(long id, String model) {
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).putString(KEY_DOWNLOAD_MODEL, model).apply();
    }
    public void clearDownload() {
        prefs.edit().remove(KEY_DOWNLOAD_ID).remove(KEY_DOWNLOAD_MODEL).apply();
    }
}
