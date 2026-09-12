package win.fantest.localwhisper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TranscriptionService extends Service {
    public static final String ACTION_PROCESS = "win.fantest.localwhisper.PROCESS";
    public static final String ACTION_CHANGED = "win.fantest.localwhisper.CHANGED";

    private static final String CHANNEL_ID = "transcription";
    private static final int NOTIFICATION_ID = 4101;

    private ExecutorService executor;
    private final AtomicBoolean draining = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat(notification("Preparing local transcription…", 0, 0));
        if (draining.compareAndSet(false, true)) {
            executor.submit(() -> {
                try {
                    drainQueue();
                } finally {
                    draining.set(false);
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelfResult(startId);
                }
            });
        }
        return START_REDELIVER_INTENT;
    }

    private void drainQueue() {
        TranscriptDb db = new TranscriptDb(this);
        db.resetInterrupted();
        AppPrefs prefs = new AppPrefs(this);
        ModelManager modelManager = new ModelManager(this);

        String modelId = prefs.model();
        File model = modelManager.modelFile(modelId);
        if (!modelManager.isInstalled(modelId)) {
            notifyChanged();
            return;
        }

        long engine = WhisperNative.create(model.getAbsolutePath());
        if (engine == 0L) {
            failAllPending(db, "Could not load Whisper model");
            notifyChanged();
            return;
        }

        try {
            TranscriptDb.Job job;
            while ((job = db.nextPending()) != null) {
                db.markProcessing(job.id);
                int remaining = db.pendingCount();
                updateNotification("Decoding " + job.name, 0, remaining);
                notifyChanged();

                File audio = new File(job.audioPath);
                long started = System.currentTimeMillis();
                try {
                    AudioDecoder.DecodedAudio decoded = AudioDecoder.decode(audio);
                    updateNotification("Transcribing " + job.name, 0, remaining);

                    String language = prefs.language();
                    boolean timestamps = prefs.timestamps();
                    String mode = prefs.outputMode();

                    String result;
                    String detected;

                    if (AppPrefs.OUTPUT_ENGLISH.equals(mode)) {
                        result = WhisperNative.transcribe(engine, decoded.samples, language, true, timestamps);
                        detected = WhisperNative.detectedLanguage(engine);
                    } else if (AppPrefs.OUTPUT_BOTH.equals(mode)) {
                        String original = WhisperNative.transcribe(engine, decoded.samples, language, false, timestamps);
                        detected = WhisperNative.detectedLanguage(engine);
                        String english = WhisperNative.transcribe(engine, decoded.samples, language, true, timestamps);
                        result = "Original:\n" + original + "\n\nEnglish:\n" + english;
                    } else {
                        result = WhisperNative.transcribe(engine, decoded.samples, language, false, timestamps);
                        detected = WhisperNative.detectedLanguage(engine);
                    }

                    if (result == null || result.trim().isEmpty()) {
                        throw new IllegalStateException("Whisper returned no text");
                    }

                    db.markDone(job.id, result.trim(), detected, decoded.durationMs);
                    if (!prefs.keepAudio()) audio.delete();
                } catch (Throwable t) {
                    db.markFailed(job.id, readableError(t));
                }

                long elapsed = Math.max(1L, System.currentTimeMillis() - started);
                updateNotification("Finished " + job.name + " · " + elapsed / 1000L + "s", 0, db.pendingCount());
                notifyChanged();
            }
        } finally {
            WhisperNative.destroy(engine);
            notifyChanged();
        }
    }

    private void failAllPending(TranscriptDb db, String message) {
        TranscriptDb.Job job;
        while ((job = db.nextPending()) != null) db.markFailed(job.id, message);
    }

    private static String readableError(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) message = t.getClass().getSimpleName();
        return message;
    }

    private void notifyChanged() {
        sendBroadcast(new Intent(ACTION_CHANGED).setPackage(getPackageName()));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.channel_description));
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private Notification notification(String text, int progress, int max) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setContentTitle("Local Whisper")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        if (max > 0) builder.setProgress(max, progress, false);
        else builder.setProgress(0, 0, true);

        return builder.build();
    }

    private void updateNotification(String text, int progress, int max) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, notification(text, progress, max));
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onDestroy() {
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
