package win.fantest.localwhisper;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 9001;
    private static final int REQUEST_MODEL_FILE = 9002;

    private TranscriptDb db;
    private AppPrefs prefs;
    private ModelManager models;
    private Spinner modelSpinner;
    private Spinner languageSpinner;
    private Spinner outputSpinner;
    private CheckBox timestamps;
    private CheckBox keepAudio;
    private TextView status;
    private TextView modelDetail;
    private LinearLayout history;
    private boolean shareHandled;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean maintenanceRunning = new AtomicBoolean(false);

    private final BroadcastReceiver changed = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };

    private final BroadcastReceiver downloadChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (prefs != null && id == prefs.downloadId()) maintainModelAsync(true);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new TranscriptDb(this);
        prefs = new AppPrefs(this);
        models = new ModelManager(this);
        setContentView(buildUi());
        bindPrefs();
        requestNotifications();
        handleShare(getIntent());
        refresh();
        maintainModelAsync(false);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        shareHandled = false;
        handleShare(intent);
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter changedFilter = new IntentFilter(TranscriptionService.ACTION_CHANGED);
        IntentFilter downloadFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(changed, changedFilter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(downloadChanged, downloadFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(changed, changedFilter);
            registerReceiver(downloadChanged, downloadFilter);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null) {
            refresh();
            maintainModelAsync(false);
        }
    }

    @Override protected void onStop() {
        try { unregisterReceiver(changed); } catch (Exception ignored) {}
        try { unregisterReceiver(downloadChanged); } catch (Exception ignored) {}
        super.onStop();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MODEL_FILE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        savePrefs();
        String modelId = prefs.model();
        modelDetail.setText("Importing and verifying existing model…");
        io.submit(() -> {
            boolean ok = false;
            String error = "";
            try {
                ok = models.importExistingModel(modelId, uri);
                if (!ok) error = "The selected file does not match the selected Whisper model";
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            boolean imported = ok;
            String finalError = error;
            runOnUiThread(() -> {
                refresh();
                toast(imported ? "Existing model imported — no download needed" : "Import failed: " + finalError);
                if (imported) startProcessing();
            });
        });
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        TextView title = text("Local Whisper", 26, true);
        root.addView(title);
        TextView intro = text("Share a WhatsApp voice note here. Transcription stays on this phone.", 15, false);
        intro.setTextColor(Color.DKGRAY);
        root.addView(intro);

        modelSpinner = spinner(new String[]{"Quality · Large v3 Turbo Q5", "Fast · Base multilingual"});
        languageSpinner = spinner(new String[]{"Auto detect", "Arabic", "English"});
        outputSpinner = spinner(new String[]{"Original language", "English translation", "Original + English"});
        timestamps = new CheckBox(this); timestamps.setText("Include timestamps");
        keepAudio = new CheckBox(this); keepAudio.setText("Keep imported audio");

        root.addView(label("Model")); root.addView(modelSpinner);
        modelDetail = text("", 13, false);
        modelDetail.setTextColor(Color.DKGRAY);
        root.addView(modelDetail);

        Button download = button("Download / restore selected model");
        download.setOnClickListener(v -> {
            savePrefs();
            String modelId = prefs.model();
            if (models.isInstalled(modelId)) { toast("Model already installed"); return; }
            if (models.hasPersistentBackup(modelId)) {
                modelDetail.setText("Restoring persistent model backup…");
                maintainModelAsync(true);
                return;
            }
            try {
                long id = models.enqueue(modelId);
                prefs.setDownload(id, modelId);
                modelDetail.setText("Model download started. It will be backed up persistently after verification.");
                toast("Model download started");
            } catch (Exception e) { toast("Download failed: " + e.getMessage()); }
        });
        root.addView(download);

        Button existing = button("Use existing model file (no download)");
        existing.setOnClickListener(v -> {
            savePrefs();
            Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            open.addCategory(Intent.CATEGORY_OPENABLE);
            open.setType("*/*");
            startActivityForResult(open, REQUEST_MODEL_FILE);
        });
        root.addView(existing);

        root.addView(label("Language")); root.addView(languageSpinner);
        root.addView(label("Output")); root.addView(outputSpinner);
        root.addView(timestamps); root.addView(keepAudio);

        Button save = button("Save settings");
        save.setOnClickListener(v -> { savePrefs(); refresh(); toast("Settings saved"); });
        root.addView(save);

        Button process = button("Process pending audio");
        process.setOnClickListener(v -> startProcessing());
        root.addView(process);

        status = text("", 14, true);
        root.addView(status);
        root.addView(label("Recent transcripts"));
        history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        root.addView(history);
        return scroll;
    }

    private void bindPrefs() {
        modelSpinner.setSelection(ModelCatalog.FAST.equals(prefs.model()) ? 1 : 0);
        languageSpinner.setSelection("ar".equals(prefs.language()) ? 1 : ("en".equals(prefs.language()) ? 2 : 0));
        outputSpinner.setSelection(AppPrefs.OUTPUT_ENGLISH.equals(prefs.outputMode()) ? 1 : (AppPrefs.OUTPUT_BOTH.equals(prefs.outputMode()) ? 2 : 0));
        timestamps.setChecked(prefs.timestamps());
        keepAudio.setChecked(prefs.keepAudio());
    }

    private void savePrefs() {
        prefs.setModel(modelSpinner.getSelectedItemPosition() == 1 ? ModelCatalog.FAST : ModelCatalog.QUALITY);
        int lp = languageSpinner.getSelectedItemPosition();
        prefs.setLanguage(lp == 1 ? "ar" : (lp == 2 ? "en" : "auto"));
        int op = outputSpinner.getSelectedItemPosition();
        prefs.setOutputMode(op == 1 ? AppPrefs.OUTPUT_ENGLISH : (op == 2 ? AppPrefs.OUTPUT_BOTH : AppPrefs.OUTPUT_ORIGINAL));
        prefs.setTimestamps(timestamps.isChecked());
        prefs.setKeepAudio(keepAudio.isChecked());
    }

    private void handleShare(Intent intent) {
        if (intent == null || shareHandled) return;
        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) return;
        shareHandled = true;
        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri u = parcelable(intent, Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        } else {
            ArrayList<Uri> list = parcelableList(intent, Intent.EXTRA_STREAM);
            if (list != null) uris.addAll(list);
        }
        if (uris.isEmpty()) { toast("No audio attached"); return; }
        io.submit(() -> {
            int count = 0;
            for (Uri uri : uris) {
                try { ShareImporter.importUri(this, uri, db); count++; }
                catch (IOException ignored) {}
            }
            int imported = count;
            runOnUiThread(() -> {
                toast(imported + " audio file" + (imported == 1 ? "" : "s") + " queued");
                refresh();
                if (imported > 0 && models.isInstalled(prefs.model())) startProcessing();
            });
        });
    }

    private void startProcessing() {
        savePrefs();
        if (!models.isInstalled(prefs.model())) {
            if (models.hasPersistentBackup(prefs.model())) {
                toast("Restoring the saved model first");
                maintainModelAsync(true);
            } else {
                toast("Download or import the selected offline model first");
            }
            return;
        }
        if (db.pendingCount() == 0) { toast("No pending audio"); return; }
        Intent i = new Intent(this, TranscriptionService.class).setAction(TranscriptionService.ACTION_PROCESS);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private void maintainModelAsync(boolean userRequested) {
        if (!maintenanceRunning.compareAndSet(false, true)) return;
        savePrefs();
        String selected = prefs.model();
        io.submit(() -> {
            boolean restored = false;
            boolean backedUp = false;
            String message = "";
            try {
                long downloadId = prefs.downloadId();
                if (downloadId >= 0) {
                    ModelManager.DownloadState state = models.query(downloadId);
                    if (state.status == DownloadManager.STATUS_RUNNING || state.status == DownloadManager.STATUS_PENDING) {
                        message = state.total > 0 ? "Downloading model · " + (state.downloaded * 100 / state.total) + "%" : "Downloading model…";
                    } else if (state.status == DownloadManager.STATUS_SUCCESSFUL) {
                        String downloadedModel = prefs.downloadModel();
                        if (downloadedModel == null || downloadedModel.isEmpty()) downloadedModel = selected;
                        if (!models.verifyAndMark(downloadedModel)) {
                            models.remove(downloadedModel);
                            message = "Downloaded model failed checksum verification";
                        } else {
                            models.backupInstalledModel(downloadedModel);
                            backedUp = true;
                            message = "Model verified and persistent backup saved";
                        }
                        prefs.clearDownload();
                    } else if (state.status == DownloadManager.STATUS_FAILED) {
                        message = "Model download failed · code " + state.reason;
                        prefs.clearDownload();
                    }
                }
                if (!models.isInstalled(selected) && models.hasPersistentBackup(selected)) {
                    restored = models.restorePersistentBackup(selected);
                    if (restored) message = "Model restored locally from persistent backup";
                }
                if (!models.isInstalled(selected) && models.hasRawLocalModel(selected)) {
                    if (models.verifyAndMark(selected)) {
                        models.backupInstalledModel(selected);
                        backedUp = true;
                        message = "Existing model verified and persistent backup saved";
                    }
                } else if (models.isInstalled(selected) && !models.hasPersistentBackup(selected)) {
                    models.backupInstalledModel(selected);
                    backedUp = true;
                    message = "Persistent model backup saved";
                }
            } catch (Exception e) {
                message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            boolean didRestore = restored;
            boolean didBackup = backedUp;
            String finalMessage = message;
            maintenanceRunning.set(false);
            runOnUiThread(() -> {
                refresh();
                if (!finalMessage.isEmpty()) modelDetail.setText(finalMessage);
                if (userRequested && didRestore) toast("Model restored — no download needed");
                else if (userRequested && didBackup) toast("Persistent model backup ready");
            });
        });
    }

    private void refresh() {
        if (status == null) return;
        String selected = prefs.model();
        boolean installed = models.isInstalled(selected);
        boolean backup = models.hasPersistentBackup(selected);
        status.setText((installed ? "model ready" : "model not installed") + " · queue: " + db.pendingCount());
        if (modelDetail != null && !maintenanceRunning.get()) {
            if (installed && backup) modelDetail.setText("Installed · persistent backup ready for future reinstall");
            else if (installed) modelDetail.setText("Installed · creating persistent backup…");
            else if (backup) modelDetail.setText("Persistent backup found · tap Download / restore");
            else modelDetail.setText("Not installed");
        }
        history.removeAllViews();
        List<TranscriptDb.Job> jobs = db.recent(30);
        if (jobs.isEmpty()) { history.addView(text("No transcripts yet.", 14, false)); return; }
        for (TranscriptDb.Job job : jobs) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(10), dp(10), dp(10));
            card.setBackgroundColor(Color.rgb(245, 247, 250));
            card.addView(text(job.name + " · " + job.status, 14, true));
            String body = TranscriptDb.DONE.equals(job.status) ? job.text : (TranscriptDb.FAILED.equals(job.status) ? "Error: " + job.error : "Waiting…");
            TextView bodyView = text(body, 15, false);
            bodyView.setTextIsSelectable(true);
            card.addView(bodyView);
            if (TranscriptDb.DONE.equals(job.status)) {
                LinearLayout actions = new LinearLayout(this);
                Button copy = button("Copy");
                copy.setOnClickListener(v -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("Transcript", job.text));
                    toast("Copied");
                });
                Button share = button("Share");
                share.setOnClickListener(v -> {
                    Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain");
                    s.putExtra(Intent.EXTRA_TEXT, job.text);
                    startActivity(Intent.createChooser(s, "Share transcript"));
                });
                actions.addView(copy); actions.addView(share); card.addView(actions);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8);
            history.addView(card, lp);
        }
    }

    private TextView label(String s) { TextView v = text(s, 14, true); v.setPadding(0, dp(12), 0, dp(3)); return v; }
    private TextView text(String s, int sp, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(20,24,30)); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setMinHeight(dp(48)); return b; }
    private Spinner spinner(String[] values) { Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); s.setMinimumHeight(dp(48)); return s; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @SuppressWarnings("deprecation")
    private static Uri parcelable(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(key, Uri.class);
        return intent.getParcelableExtra(key);
    }

    @SuppressWarnings("deprecation")
    private static ArrayList<Uri> parcelableList(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableArrayListExtra(key, Uri.class);
        return intent.getParcelableArrayListExtra(key);
    }
}
