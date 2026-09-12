package win.fantest.localwhisper;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ModelManager {
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

    public ModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
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

    public boolean isInstalled(String modelId) {
        File file = modelFile(modelId);
        return file.isFile() && file.length() > 1024L * 1024L;
    }

    public long enqueue(String modelId) {
        ModelCatalog.ModelInfo info = ModelCatalog.get(modelId);
        File target = modelFile(modelId);
        if (target.exists()) target.delete();

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
            if (cursor == null || !cursor.moveToFirst()) {
                return new DownloadState(-1, 0, 0, "Download not found");
            }
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

    public void remove(String modelId) {
        File file = modelFile(modelId);
        if (file.exists()) file.delete();
    }

    private static String sha1(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[1024 * 1024];
            try (FileInputStream in = new FileInputStream(file)) {
                int count;
                while ((count = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            StringBuilder out = new StringBuilder();
            for (byte b : digest.digest()) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
