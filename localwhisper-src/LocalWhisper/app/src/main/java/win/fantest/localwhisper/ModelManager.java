package win.fantest.localwhisper;

import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ModelManager {
    private static final String BACKUP_RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/LocalWhisper/models/";
    private static final long MIN_MODEL_BYTES = 16L * 1024L * 1024L;

    public static final class DownloadState {
        public final int status;
        public final long downloaded;
        public final long total;
        public final String reason;

        public DownloadState(int status, long downloaded, long total, String reason) {
            this.status = status;
            this.downloaded = downloaded;
            this.total = total;
            this.reason = reason;
        }
    }

    private final Context context;
    private final DownloadManager downloadManager;
    private final ContentResolver resolver;

    public ModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        this.resolver = this.context.getContentResolver();
    }

    public File modelDir() {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (base == null) base = context.getFilesDir();
        File dir = new File(base, "models");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public File modelFile(String modelId) {
        return new File(modelDir(), ModelCatalog.get(modelId).fileName);
    }

    private File markerFile(String modelId) {
        return new File(modelDir(), ModelCatalog.get(modelId).fileName + ".verified");
    }

    public boolean hasRawLocalModel(String modelId) {
        File file = modelFile(modelId);
        return file.isFile() && file.length() >= MIN_MODEL_BYTES;
    }

    public boolean isInstalled(String modelId) {
        File file = modelFile(modelId);
        return file.isFile() && file.length() >= MIN_MODEL_BYTES && markerFile(modelId).isFile();
    }

    public long enqueue(String modelId) {
        ModelCatalog.ModelInfo info = ModelCatalog.get(modelId);
        removeLocal(modelId);
        File target = modelFile(modelId);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(info.url));
        request.setTitle("Local Whisper · " + info.title);
        request.setDescription("Downloading offline speech model");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setAllowedOverMetered(true);
        request.setAllowedOverRoaming(false);
        request.setDestinationUri(Uri.fromFile(target));
        return downloadManager.enqueue(request);
    }

    public DownloadState query(long id) {
        if (id < 0) return new DownloadState(-1, 0, 0, "");
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) return new DownloadState(-1, 0, 0, "Download not found");
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            int reasonCode = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
            return new DownloadState(status, downloaded, total, String.valueOf(reasonCode));
        }
    }

    public boolean verify(String modelId) throws IOException {
        ModelCatalog.ModelInfo info = ModelCatalog.get(modelId);
        File file = modelFile(modelId);
        if (!file.isFile()) return false;
        return info.sha1.equalsIgnoreCase(sha1(file));
    }

    public boolean verifyAndMark(String modelId) throws IOException {
        boolean ok = verify(modelId);
        if (!ok) {
            markerFile(modelId).delete();
            return false;
        }
        try (FileOutputStream out = new FileOutputStream(markerFile(modelId), false)) {
            out.write(ModelCatalog.get(modelId).sha1.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
        return true;
    }

    public boolean hasPersistentBackup(String modelId) {
        return findPersistentUri(modelId) != null;
    }

    public boolean restorePersistentBackup(String modelId) throws IOException {
        Uri source = findPersistentUri(modelId);
        if (source == null) return false;
        return importFromUriInternal(modelId, source, false);
    }

    public boolean importExistingModel(String modelId, Uri source) throws IOException {
        if (source == null) return false;
        boolean ok = importFromUriInternal(modelId, source, true);
        if (ok) backupInstalledModel(modelId);
        return ok;
    }

    public void backupInstalledModel(String modelId) throws IOException {
        if (Build.VERSION.SDK_INT < 29) return;
        if (!isInstalled(modelId) && !verifyAndMark(modelId)) throw new IOException("Model checksum failed before backup");
        ModelCatalog.ModelInfo info = ModelCatalog.get(modelId);
        Uri existing = findPersistentUri(modelId);
        if (existing != null) {
            try (InputStream in = resolver.openInputStream(existing)) {
                if (in != null && info.sha1.equalsIgnoreCase(sha1(in))) return;
            } catch (SecurityException ignored) {}
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, info.fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, BACKUP_RELATIVE_PATH);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri destination = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (destination == null) throw new IOException("Could not create persistent model backup");
        boolean committed = false;
        try (InputStream in = new FileInputStream(modelFile(modelId)); OutputStream out = resolver.openOutputStream(destination, "w")) {
            if (out == null) throw new IOException("Could not open persistent model backup");
            copy(in, out);
            committed = true;
        } finally {
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, committed ? 0 : 1);
            try { resolver.update(destination, done, null, null); } catch (Exception ignored) {}
            if (!committed) try { resolver.delete(destination, null, null); } catch (Exception ignored) {}
        }
    }

    public void remove(String modelId) { removeLocal(modelId); }

    private void removeLocal(String modelId) {
        File file = modelFile(modelId);
        if (file.exists()) file.delete();
        markerFile(modelId).delete();
        File tmp = new File(modelDir(), ModelCatalog.get(modelId).fileName + ".restore");
        if (tmp.exists()) tmp.delete();
    }

    private boolean importFromUriInternal(String modelId, Uri source, boolean userSelected) throws IOException {
        ModelCatalog.ModelInfo info = ModelCatalog.get(modelId);
        File target = modelFile(modelId);
        File temp = new File(modelDir(), info.fileName + ".restore");
        markerFile(modelId).delete();
        if (temp.exists()) temp.delete();
        try (InputStream in = resolver.openInputStream(source); OutputStream out = new FileOutputStream(temp)) {
            if (in == null) return false;
            copy(in, out);
        } catch (SecurityException e) {
            if (userSelected) throw new IOException("Android denied access to the selected model file", e);
            return false;
        }
        if (!info.sha1.equalsIgnoreCase(sha1(temp))) {
            temp.delete();
            return false;
        }
        if (target.exists() && !target.delete()) {
            temp.delete();
            throw new IOException("Could not replace local model copy");
        }
        if (!temp.renameTo(target)) {
            try (InputStream in = new FileInputStream(temp); OutputStream out = new FileOutputStream(target)) { copy(in, out); }
            temp.delete();
        }
        return verifyAndMark(modelId);
    }

    private Uri findPersistentUri(String modelId) {
        if (Build.VERSION.SDK_INT < 29) return null;
        ModelCatalog.ModelInfo info = ModelCatalog.get(modelId);
        String[] projection = { MediaStore.MediaColumns._ID };
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] args = { info.fileName, BACKUP_RELATIVE_PATH };
        String order = MediaStore.MediaColumns.DATE_ADDED + " DESC";
        try (Cursor cursor = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, order)) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
            return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
        } catch (SecurityException e) {
            return null;
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024 * 1024];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        out.flush();
    }

    private static String sha1(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) { return sha1(in); }
    }

    private static String sha1(InputStream in) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
            StringBuilder out = new StringBuilder();
            for (byte b : digest.digest()) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
