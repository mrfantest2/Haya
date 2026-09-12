package win.fantest.localwhisper;

import android.Manifest;
import android.app.Activity;
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

public final class MainActivity extends Activity {
    private TranscriptDb db;
    private AppPrefs prefs;
    private ModelManager models;
    private Spinner modelSpinner;
    private Spinner languageSpinner;
    private Spinner outputSpinner;
    private CheckBox timestamps;
    private CheckBox keepAudio;
    private TextView status;
    private LinearLayout history;
    private boolean shareHandled;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final BroadcastReceiver changed = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
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
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        shareHandled = false;
        handleShare(intent);
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(TranscriptionService.ACTION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changed, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(changed, filter);
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null) refresh();
    }

    @Override protected void onStop() {
        try { unregisterReceiver(changed); } catch (Exception ignored) {}
        super.onStop();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
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
        root.addView(label("Language")); root.addView(languageSpinner);
        root.addView(label("Output")); root.addView(outputSpinner);
        root.addView(timestamps); root.addView(keepAudio);

        Button save = button("Save settings");
        save.setOnClickListener(v -> { savePrefs(); refresh(); toast("Settings saved"); });
        root.addView(save);

        Button download = button("Download selected model");
        download.setOnClickListener(v -> {
            savePrefs();
            if (models.isInstalled(prefs.model())) { toast("Model already installed"); return; }
            try {
                long id = models.enqueue(prefs.model());
                prefs.setDownload(id, prefs.model());
                toast("Model download started");
            } catch (Exception e) { toast("Download failed: " + e.getMessage()); }
        });
        root.addView(download);

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
        if (!models.isInstalled(prefs.model())) { toast("Download the selected model first"); return; }
        if (db.pendingCount() == 0) { toast("No pending audio"); return; }
        Intent i = new Intent(this, TranscriptionService.class).setAction(TranscriptionService.ACTION_PROCESS);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private void refresh() {
        if (status == null) return;
        String model = models.isInstalled(prefs.model()) ? "model ready" : "model not installed";
        status.setText(model + " · queue: " + db.pendingCount());
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
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9001);
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
