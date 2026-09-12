package win.fantest.localwhisper;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ShareImporter {
    private ShareImporter() {}

    public static long importUri(Context context, Uri uri, TranscriptDb db) throws IOException {
        String name = displayName(context.getContentResolver(), uri);
        if (name == null || name.trim().isEmpty()) name = "audio_" + System.currentTimeMillis();

        File inbox = new File(context.getFilesDir(), "inbox");
        if (!inbox.exists() && !inbox.mkdirs()) throw new IOException("Could not create local inbox");

        String safe = name.replaceAll("[^A-Za-z0-9._\\-\\u0600-\\u06FF]", "_");
        File target = new File(inbox, System.currentTimeMillis() + "_" + safe);

        try (
                InputStream in = context.getContentResolver().openInputStream(uri);
                FileOutputStream out = new FileOutputStream(target)
        ) {
            if (in == null) throw new IOException("Could not open shared audio");
            byte[] buffer = new byte[256 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        } catch (IOException e) {
            target.delete();
            throw e;
        }

        if (target.length() == 0) {
            target.delete();
            throw new IOException("Shared audio is empty");
        }

        return db.insertPending(name, target.getAbsolutePath());
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor c = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) return c.getString(0);
            } catch (Exception ignored) {}
        }
        String path = uri.getLastPathSegment();
        return path == null ? null : path;
    }
}
