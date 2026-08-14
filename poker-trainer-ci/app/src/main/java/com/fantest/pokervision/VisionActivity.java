package com.fantest.pokervision;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class VisionActivity extends ComponentActivity {
    private static final int CAMERA_REQ = 41;
    private static final int BG = Color.rgb(4, 23, 17);
    private static final int PANEL = Color.rgb(11, 48, 35);
    private static final int PANEL_2 = Color.rgb(15, 61, 44);
    private static final int GOLD = Color.rgb(245, 200, 76);
    private static final int MUTED = Color.rgb(190, 205, 198);
    private static final int RED = Color.rgb(196, 42, 42);
    private static final int SIMULATIONS = 25000;

    private final ArrayList<PokerMath.Card> hole = new ArrayList<>();
    private final ArrayList<PokerMath.Card> board = new ArrayList<>();
    private boolean targetHole = true;
    private boolean arabic = false;
    private int opponents = 1;

    private FrameLayout rootFrame, scannerOverlay;
    private LinearLayout dashboard, holeCardsRow, boardCardsRow, candidatesRow, resultPanel;
    private PreviewView previewView;
    private TextView modeHint, targetLabel, opponentsText, scanStatus;
    private TextView resultHand, resultEquity, resultWin, resultTie, resultLose, resultDetail, resultMeta;
    private Button targetHoleButton, targetBoardButton, languageButton, scanButton, manualButton, calculateButton;

    private ExecutorService cameraExecutor;
    private TextRecognizer recognizer;
    private ProcessCameraProvider cameraProvider;
    private boolean cameraRunning = false;
    private final AtomicBoolean analyzing = new AtomicBoolean(false);
    private long lastAnalyzeMs = 0L;
    private String lastCandidateKey = "";
    private long lastCandidateMs = 0L;

    private PokerMath.Result lastResult;
    private int lastResultOpponents = 1;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (bars != null) {
            bars.setAppearanceLightStatusBars(false);
            bars.setAppearanceLightNavigationBars(false);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(BG);
        ViewCompat.setOnApplyWindowInsetsListener(rootFrame, (v, insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, i.top, 0, i.bottom);
            return insets;
        });

        dashboard = buildDashboard();
        rootFrame.addView(dashboard, new FrameLayout.LayoutParams(-1, -1));
        scannerOverlay = buildScannerOverlay();
        scannerOverlay.setVisibility(View.GONE);
        rootFrame.addView(scannerOverlay, new FrameLayout.LayoutParams(-1, -1));
        setContentView(rootFrame);
        updateTexts();
        refreshCards();
        renderEmptyResult();
    }

    private LinearLayout buildDashboard() {
        LinearLayout screen = column();
        screen.setBackgroundColor(BG);
        screen.setPadding(dp(12), dp(6), dp(12), dp(10));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("POKER VISION", 23, Color.WHITE, true);
        header.addView(title, weight());

        ImageButton reset = iconButton(R.drawable.ic_reset, "Reset cards");
        reset.setOnClickListener(v -> resetAll());
        header.addView(reset, new LinearLayout.LayoutParams(dp(46), dp(42)));
        header.addView(space(6));

        languageButton = compactButton("عربي");
        languageButton.setOnClickListener(v -> {
            arabic = !arabic;
            updateTexts();
            if (lastResult != null) renderResult(lastResult, lastResultOpponents);
        });
        header.addView(languageButton, new LinearLayout.LayoutParams(dp(86), dp(42)));
        screen.addView(header, lp(-1, dp(46), 0, 0, 0, 2));

        modeHint = text("Camera OFF • tap any card to remove it.", 11, MUTED, false);
        screen.addView(modeHint, lp(-1, dp(22), 0, 0, 0, 4));

        LinearLayout cardsPanel = column();
        cardsPanel.setPadding(dp(10), dp(7), dp(10), dp(8));
        cardsPanel.setBackground(rounded(PANEL, 14));

        TextView mineLabel = text("MY CARDS", 10, GOLD, true);
        mineLabel.setTag("mineLabel");
        cardsPanel.addView(mineLabel, lp(-1, dp(18), 0, 0, 0, 2));
        holeCardsRow = row();
        holeCardsRow.setGravity(Gravity.CENTER);
        holeCardsRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        cardsPanel.addView(holeCardsRow, lp(-1, dp(58), 0, 0, 0, 4));

        TextView boardLabel = text("BOARD", 10, GOLD, true);
        boardLabel.setTag("boardLabel");
        cardsPanel.addView(boardLabel, lp(-1, dp(18), 0, 0, 0, 2));
        boardCardsRow = row();
        boardCardsRow.setGravity(Gravity.CENTER);
        boardCardsRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        cardsPanel.addView(boardCardsRow, lp(-1, dp(58), 0, 0, 0, 0));
        screen.addView(cardsPanel, lp(-1, dp(166), 0, 0, 0, 6));

        LinearLayout targetBar = row();
        targetBar.setGravity(Gravity.CENTER_VERTICAL);
        targetLabel = text("ADD TO", 10, MUTED, true);
        targetBar.addView(targetLabel, weight());
        targetHoleButton = segmentButton("My Cards");
        targetBoardButton = segmentButton("Board");
        targetHoleButton.setOnClickListener(v -> { targetHole = true; updateTargetButtons(); });
        targetBoardButton.setOnClickListener(v -> { targetHole = false; updateTargetButtons(); });
        targetBar.addView(targetHoleButton, new LinearLayout.LayoutParams(dp(104), dp(36)));
        targetBar.addView(space(4));
        targetBar.addView(targetBoardButton, new LinearLayout.LayoutParams(dp(88), dp(36)));
        screen.addView(targetBar, lp(-1, dp(40), 0, 0, 0, 5));

        LinearLayout actionRow = row();
        scanButton = primaryButton("Scan cards");
        scanButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_camera, 0, 0, 0);
        scanButton.setCompoundDrawablePadding(dp(7));
        scanButton.setOnClickListener(v -> openScanner());
        manualButton = primaryButton("Add manually");
        manualButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_edit_card, 0, 0, 0);
        manualButton.setCompoundDrawablePadding(dp(7));
        manualButton.setOnClickListener(v -> showManualCardPicker());
        actionRow.addView(scanButton, weight());
        actionRow.addView(space(8));
        actionRow.addView(manualButton, weight());
        screen.addView(actionRow, lp(-1, dp(48), 0, 0, 0, 7));

        LinearLayout estimateBar = row();
        estimateBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout stepper = row();
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        stepper.setPadding(dp(3), dp(2), dp(3), dp(2));
        stepper.setBackground(rounded(PANEL, 12));
        Button minus = squareButton("−");
        Button plus = squareButton("+");
        minus.setOnClickListener(v -> { if (opponents > 1) { opponents--; updateOpponentText(); } });
        plus.setOnClickListener(v -> { if (opponents < 5) { opponents++; updateOpponentText(); } });
        opponentsText = text("1 opponent", 13, Color.WHITE, true);
        opponentsText.setGravity(Gravity.CENTER);
        stepper.addView(minus, new LinearLayout.LayoutParams(dp(38), dp(38)));
        stepper.addView(opponentsText, new LinearLayout.LayoutParams(dp(104), dp(38)));
        stepper.addView(plus, new LinearLayout.LayoutParams(dp(38), dp(38)));
        estimateBar.addView(stepper, new LinearLayout.LayoutParams(dp(180), dp(42)));
        estimateBar.addView(space(8));

        calculateButton = new Button(this);
        calculateButton.setAllCaps(false);
        calculateButton.setText("Estimate equity");
        calculateButton.setTextSize(14);
        calculateButton.setTypeface(Typeface.DEFAULT_BOLD);
        calculateButton.setTextColor(BG);
        calculateButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GOLD));
        calculateButton.setOnClickListener(v -> calculate());
        estimateBar.addView(calculateButton, weight());
        screen.addView(estimateBar, lp(-1, dp(46), 0, 0, 0, 7));

        resultPanel = column();
        resultPanel.setPadding(dp(14), dp(9), dp(14), dp(9));
        resultPanel.setBackground(rounded(PANEL_2, 14));
        resultHand = text("", 16, Color.WHITE, true);
        resultEquity = text("", 30, GOLD, true);
        resultEquity.setGravity(Gravity.CENTER);

        LinearLayout odds = row();
        odds.setGravity(Gravity.CENTER);
        resultWin = statView();
        resultTie = statView();
        resultLose = statView();
        odds.addView(resultWin, weight());
        odds.addView(space(5));
        odds.addView(resultTie, weight());
        odds.addView(space(5));
        odds.addView(resultLose, weight());

        resultDetail = text("", 12, Color.WHITE, false);
        resultMeta = text("", 11, MUTED, false);
        resultPanel.addView(resultHand, lp(-1, dp(22), 0, 0, 0, 0));
        resultPanel.addView(resultEquity, lp(-1, dp(44), 0, 0, 0, 3));
        resultPanel.addView(odds, lp(-1, dp(43), 0, 0, 0, 5));
        resultPanel.addView(resultDetail, lp(-1, dp(20), 0, 0, 0, 2));
        resultPanel.addView(resultMeta, lp(-1, dp(18), 0, 0, 0, 0));
        screen.addView(resultPanel, lp(-1, dp(163), 0, 0, 0, 0));
        return screen;
    }

    private FrameLayout buildScannerOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.BLACK);
        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        overlay.addView(previewView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(8), dp(10), dp(8));
        top.setBackgroundColor(0xD9041711);
        Button close = compactButton("←");
        close.setTextSize(23);
        close.setOnClickListener(v -> closeScanner());
        top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(42)));
        TextView scannerTitle = text("CARD SCANNER", 17, Color.WHITE, true);
        scannerTitle.setGravity(Gravity.CENTER);
        scannerTitle.setTag("scannerTitle");
        top.addView(scannerTitle, weight());
        ImageButton scannerManual = iconButton(R.drawable.ic_edit_card, "Add manually");
        scannerManual.setOnClickListener(v -> showManualCardPicker());
        top.addView(scannerManual, new LinearLayout.LayoutParams(dp(48), dp(42)));
        overlay.addView(top, new FrameLayout.LayoutParams(-1, dp(58), Gravity.TOP));

        LinearLayout bottom = column();
        bottom.setPadding(dp(12), dp(10), dp(12), dp(10));
        bottom.setBackgroundColor(0xE8041711);
        LinearLayout targetRow = row();
        Button scanMine = segmentButton("My Cards");
        Button scanBoard = segmentButton("Board");
        scanMine.setTag("scanMine");
        scanBoard.setTag("scanBoard");
        scanMine.setOnClickListener(v -> { targetHole = true; updateTargetButtons(); updateScannerTargetButtons(scanMine, scanBoard); });
        scanBoard.setOnClickListener(v -> { targetHole = false; updateTargetButtons(); updateScannerTargetButtons(scanMine, scanBoard); });
        targetRow.addView(scanMine, weight());
        targetRow.addView(space(8));
        targetRow.addView(scanBoard, weight());
        bottom.addView(targetRow, lp(-1, dp(40), 0, 0, 0, 5));

        scanStatus = text("Camera is off", 13, GOLD, true);
        scanStatus.setGravity(Gravity.CENTER);
        bottom.addView(scanStatus, lp(-1, dp(40), 0, 0, 0, 3));
        candidatesRow = row();
        candidatesRow.setGravity(Gravity.CENTER);
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.addView(candidatesRow);
        bottom.addView(hsv, lp(-1, dp(54), 0, 0, 0, 3));
        TextView tip = text("Aim at the printed rank + suit corner, then tap a detected card.", 11, MUTED, false);
        tip.setGravity(Gravity.CENTER);
        tip.setTag("scannerTip");
        bottom.addView(tip, lp(-1, dp(32), 0, 0, 0, 0));
        overlay.addView(bottom, new FrameLayout.LayoutParams(-1, dp(184), Gravity.BOTTOM));
        overlay.setTag(new Button[]{scanMine, scanBoard});
        return overlay;
    }

    private void openScanner() {
        scannerOverlay.setVisibility(View.VISIBLE);
        Button[] bs = (Button[]) scannerOverlay.getTag();
        updateScannerTargetButtons(bs[0], bs[1]);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else {
            scanStatus.setText(arabic ? "اسمح باستخدام الكاميرا فقط أثناء المسح." : "Camera permission is needed only while scanning.");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQ);
        }
    }

    private void closeScanner() {
        stopCamera();
        scannerOverlay.setVisibility(View.GONE);
        if (candidatesRow != null) candidatesRow.removeAllViews();
    }

    private void startCamera() {
        if (cameraRunning) return;
        scanStatus.setText(arabic ? "جاري تشغيل الكاميرا…" : "Starting camera…");
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new android.util.Size(1280, 720)).build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                cameraRunning = true;
                runOnUiThread(() -> scanStatus.setText(arabic ? "وجّه الكاميرا إلى زاوية البطاقة." : "Aim at a card corner. Detection runs only here."));
            } catch (Exception e) {
                cameraRunning = false;
                runOnUiThread(() -> scanStatus.setText("Camera error: " + e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void stopCamera() {
        cameraRunning = false;
        analyzing.set(false);
        if (cameraProvider != null) cameraProvider.unbindAll();
    }

    private void analyzeFrame(ImageProxy proxy) {
        if (!cameraRunning) { proxy.close(); return; }
        long now = SystemClock.elapsedRealtime();
        if (now - lastAnalyzeMs < 450 || !analyzing.compareAndSet(false, true)) { proxy.close(); return; }
        lastAnalyzeMs = now;
        if (proxy.getImage() == null) { analyzing.set(false); proxy.close(); return; }
        InputImage image = InputImage.fromMediaImage(proxy.getImage(), proxy.getImageInfo().getRotationDegrees());
        recognizer.process(image)
                .addOnSuccessListener(text -> onRecognized(CardRecognizer.extract(text), text))
                .addOnFailureListener(e -> runOnUiThread(() -> scanStatus.setText("OCR: " + e.getClass().getSimpleName())))
                .addOnCompleteListener(t -> { analyzing.set(false); proxy.close(); });
    }

    private void onRecognized(List<PokerMath.Card> cards, Text raw) {
        runOnUiThread(() -> {
            if (scannerOverlay.getVisibility() != View.VISIBLE) return;
            if (!cards.isEmpty()) {
                StringBuilder key = new StringBuilder();
                for (PokerMath.Card c : cards) key.append(c.code()).append(',');
                if (!key.toString().equals(lastCandidateKey) || SystemClock.elapsedRealtime() - lastCandidateMs > 1200) {
                    lastCandidateKey = key.toString();
                    lastCandidateMs = SystemClock.elapsedRealtime();
                    showCandidates(cards);
                }
                scanStatus.setText((arabic ? "تم اكتشاف: " : "Detected: ") + prettyList(cards));
            } else scanStatus.setText(arabic ? "لم يتم التعرف بعد. قرّب زاوية البطاقة." : "No card yet — move closer to the rank + suit corner.");
        });
    }

    private void showCandidates(List<PokerMath.Card> cards) {
        candidatesRow.removeAllViews();
        for (PokerMath.Card c : cards) {
            Button b = cardCandidateButton(c);
            b.setOnClickListener(v -> {
                if (!addCard(c)) return;
                if (targetHole && hole.size() >= 2) { targetHole = false; updateTargetButtons(); }
                Button[] scanButtons = (Button[]) scannerOverlay.getTag();
                updateScannerTargetButtons(scanButtons[0], scanButtons[1]);
                if (board.size() >= 5) { toast(arabic ? "اكتملت بطاقات الطاولة" : "Board complete"); closeScanner(); }
            });
            candidatesRow.addView(b);
            candidatesRow.addView(space(6));
        }
    }

    private void showManualCardPicker() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = column();
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(rounded(Color.WHITE, 18));
        TextView title = text(arabic ? "إضافة بطاقة يدويًا" : "Add card manually", 20, BG, true);
        box.addView(title, lp(-1, dp(36), 0, 0, 0, 8));

        LinearLayout targetRow = row();
        Button mine = dialogSegmentButton(arabic ? "بطاقاتي" : "My Cards");
        Button table = dialogSegmentButton(arabic ? "الطاولة" : "Board");
        targetRow.addView(mine, weight()); targetRow.addView(space(8)); targetRow.addView(table, weight());
        box.addView(targetRow, lp(-1, dp(44), 0, 0, 0, 10));

        TextView helper = text(arabic ? "اختر البطاقة الكاملة: القيمة + رمز النوع." : "Choose the exact card: rank + suit.", 12, Color.DKGRAY, false);
        box.addView(helper, lp(-1, -2, 0, 0, 0, 8));
        Spinner spinner = new Spinner(this);
        CardOptionAdapter adapter = new CardOptionAdapter(buildCardOptions());
        spinner.setAdapter(adapter);
        box.addView(spinner, lp(-1, dp(58), 0, 0, 0, 12));
        TextView preview = text("A♠", 38, Color.BLACK, true);
        preview.setGravity(Gravity.CENTER);
        preview.setBackground(rounded(Color.rgb(248,248,248), 12));
        box.addView(preview, lp(-1, dp(74), 0, 0, 0, 12));

        Runnable refreshTarget = () -> { mine.setAlpha(targetHole ? 1f : .5f); table.setAlpha(targetHole ? .5f : 1f); };
        mine.setOnClickListener(v -> { targetHole = true; updateTargetButtons(); refreshTarget.run(); });
        table.setOnClickListener(v -> { targetHole = false; updateTargetButtons(); refreshTarget.run(); });
        refreshTarget.run();

        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                CardOption o = adapter.getItem(position);
                if (o != null) { preview.setText(o.card.pretty()); preview.setTextColor(isRedSuit(o.card) ? RED : Color.BLACK); }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        LinearLayout buttons = row();
        Button done = dialogSecondaryButton(arabic ? "تم" : "Done");
        Button add = dialogPrimaryButton(arabic ? "إضافة البطاقة" : "Add card");
        done.setOnClickListener(v -> dialog.dismiss());
        add.setOnClickListener(v -> {
            CardOption o = (CardOption) spinner.getSelectedItem();
            if (o == null) return;
            if (addCard(o.card)) {
                if (targetHole && hole.size() >= 2) { targetHole = false; updateTargetButtons(); refreshTarget.run(); }
                toast(arabic ? "تمت الإضافة" : o.card.pretty() + " added");
                if (board.size() >= 5 && scannerOverlay.getVisibility() == View.VISIBLE) closeScanner();
            }
        });
        buttons.addView(done, weight()); buttons.addView(space(8)); buttons.addView(add, weight());
        box.addView(buttons, lp(-1, dp(48), 0, 0, 0, 0));

        dialog.setContentView(box);
        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawableResource(android.R.color.transparent);
                WindowManager.LayoutParams p = new WindowManager.LayoutParams();
                p.copyFrom(w.getAttributes());
                p.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(520));
                p.height = WindowManager.LayoutParams.WRAP_CONTENT;
                w.setAttributes(p);
            }
        });
        dialog.show();
    }

    private ArrayList<CardOption> buildCardOptions() {
        ArrayList<CardOption> out = new ArrayList<>();
        int[] ranks = {14,13,12,11,10,9,8,7,6,5,4,3,2};
        String[] names = {"Ace","King","Queen","Jack","Ten","Nine","Eight","Seven","Six","Five","Four","Three","Two"};
        String[] suits = {"Spades","Hearts","Diamonds","Clubs"};
        for (int i=0;i<ranks.length;i++) for (int s=0;s<4;s++) {
            PokerMath.Card c = new PokerMath.Card(ranks[i], s);
            out.add(new CardOption(c, c.pretty() + "   " + names[i] + " of " + suits[s]));
        }
        return out;
    }

    private boolean addCard(PokerMath.Card c) {
        if (contains(c)) { toast(arabic ? "هذه البطاقة موجودة بالفعل" : "That card is already selected"); return false; }
        if (targetHole) {
            if (hole.size() >= 2) { toast(arabic ? "لديك بطاقتان بالفعل" : "Your two-card hand is already full"); return false; }
            hole.add(c);
        } else {
            if (board.size() >= 5) { toast(arabic ? "الطاولة تحتوي خمس بطاقات" : "The board is already full"); return false; }
            board.add(c);
        }
        invalidateResult();
        refreshCards();
        return true;
    }

    private void removeCard(boolean fromHole, PokerMath.Card c) {
        if (fromHole) hole.remove(c); else board.remove(c);
        invalidateResult(); refreshCards();
    }
    private boolean contains(PokerMath.Card c) { return hole.contains(c) || board.contains(c); }
    private void resetAll() {
        hole.clear(); board.clear(); targetHole = true;
        if (candidatesRow != null) candidatesRow.removeAllViews();
        invalidateResult(); updateTargetButtons(); refreshCards();
    }
    private void invalidateResult() { lastResult = null; renderEmptyResult(); }

    private void refreshCards() {
        if (holeCardsRow == null || boardCardsRow == null) return;
        renderSlots(holeCardsRow, hole, 2, true);
        renderSlots(boardCardsRow, board, 5, false);
    }
    private void renderSlots(LinearLayout row, List<PokerMath.Card> cards, int total, boolean fromHole) {
        row.removeAllViews();
        for (int i=0;i<total;i++) {
            if (i < cards.size()) {
                PokerMath.Card c = cards.get(i);
                TextView v = cardView(c);
                v.setOnClickListener(x -> removeCard(fromHole, c));
                row.addView(v);
            } else row.addView(emptyCardView());
            if (i < total-1) row.addView(space(4));
        }
    }

    private void calculate() {
        if (hole.size() != 2) { toast(arabic ? "أضف بطاقتين لك أولاً" : "Add exactly two hole cards first"); return; }
        calculateButton.setEnabled(false);
        calculateButton.setText(arabic ? "جاري الحساب…" : "Calculating…");
        resultHand.setText(arabic ? "حساب الاحتمالات…" : "Calculating equity…");
        resultEquity.setText("…");
        ArrayList<PokerMath.Card> h = new ArrayList<>(hole);
        ArrayList<PokerMath.Card> b = new ArrayList<>(board);
        int opp = opponents;
        new Thread(() -> {
            try {
                PokerMath.Result r = PokerMath.calculate(h, b, opp, SIMULATIONS, System.nanoTime());
                runOnUiThread(() -> {
                    lastResult = r; lastResultOpponents = opp; renderResult(r, opp);
                    calculateButton.setEnabled(true);
                    calculateButton.setText(arabic ? "احسب الاحتمال" : "Estimate equity");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    resultHand.setText("Error"); resultEquity.setText(""); resultDetail.setText(e.getMessage());
                    calculateButton.setEnabled(true);
                    calculateButton.setText(arabic ? "احسب الاحتمال" : "Estimate equity");
                });
            }
        }).start();
    }

    private void renderEmptyResult() {
        if (resultHand == null) return;
        resultHand.setText(arabic ? "جاهز للحساب" : "Ready to estimate");
        resultEquity.setText("—");
        resultWin.setText(arabic ? "فوز\n—" : "WIN\n—");
        resultTie.setText(arabic ? "تعادل\n—" : "TIE\n—");
        resultLose.setText(arabic ? "خسارة\n—" : "LOSE\n—");
        resultDetail.setText(arabic ? "أضف بطاقتيك ثم بطاقات الطاولة إن وجدت." : "Add your 2 cards, then optionally add the board.");
        resultMeta.setText(arabic ? "الكاميرا متوقفة حتى تضغط «مسح البطاقات»." : "Camera stays off until you tap Scan cards.");
        applyResultDirection();
    }

    private void renderResult(PokerMath.Result r, int opp) {
        resultHand.setText(arabic ? handArabic(r.currentHand) : r.currentHand);
        resultEquity.setText(String.format(Locale.US, "%.1f%%", r.equity));
        resultWin.setText(String.format(Locale.US, "%s\n%.1f%%", arabic ? "فوز" : "WIN", r.win));
        resultTie.setText(String.format(Locale.US, "%s\n%.1f%%", arabic ? "تعادل" : "TIE", r.tie));
        resultLose.setText(String.format(Locale.US, "%s\n%.1f%%", arabic ? "خسارة" : "LOSE", r.lose));
        String stage = stageText(board.size());
        if (board.size() >= 5) resultDetail.setText(arabic ? stage + " • لا توجد سحوبات متبقية" : stage + " • no remaining draws");
        else resultDetail.setText(arabic ? stage + " • " + drawArabic(r.draws) + " • المخارج " + r.outs : stage + " • " + r.draws + " • outs " + r.outs);
        resultMeta.setText(arabic
                ? String.format(Locale.US, "ضد %d %s • %,d محاكاة", opp, opp == 1 ? "خصم" : "خصوم", r.simulations)
                : String.format(Locale.US, "%d opponent%s • %,d simulations", opp, opp == 1 ? "" : "s", r.simulations));
        applyResultDirection();
    }

    private String stageText(int n) {
        if (arabic) {
            if (n==0) return "قبل الفلوب"; if (n==3) return "الفلوب"; if (n==4) return "التيرن"; if (n>=5) return "الريفر مكتمل"; return "الطاولة جزئية";
        }
        if (n==0) return "Pre-flop"; if (n==3) return "Flop"; if (n==4) return "Turn"; if (n>=5) return "River complete"; return "Partial board";
    }
    private void applyResultDirection() {
        int dir = arabic ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;
        resultPanel.setLayoutDirection(dir);
        resultHand.setTextDirection(arabic ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        resultDetail.setTextDirection(arabic ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        resultMeta.setTextDirection(arabic ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
    }

    private String handArabic(String s) {
        Map<String,String> m = new HashMap<>();
        m.put("Pre-flop","قبل الفلوب"); m.put("High Card","أعلى ورقة"); m.put("One Pair","زوج"); m.put("Two Pair","زوجان");
        m.put("Three of a Kind","ثلاثة من نفس الرتبة"); m.put("Straight","ستريت"); m.put("Flush","فلاش"); m.put("Full House","فول هاوس");
        m.put("Four of a Kind","أربعة من نفس الرتبة"); m.put("Straight Flush","ستريت فلاش");
        return m.getOrDefault(s,s);
    }
    private String drawArabic(String s) {
        return s.replace("Flush draw","سحب فلاش").replace("Straight draw","سحب ستريت")
                .replace("No major draw detected","لا يوجد سحب رئيسي").replace("No board draw yet","لا توجد أوراق طاولة كافية");
    }

    private void updateTexts() {
        if (languageButton == null) return;
        dashboard.setLayoutDirection(arabic ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        languageButton.setText(arabic ? "English" : "عربي");
        modeHint.setText(arabic ? "الكاميرا متوقفة • اضغط أي بطاقة لحذفها." : "Camera OFF • tap any card to remove it.");
        targetLabel.setText(arabic ? "الإضافة إلى" : "ADD TO");
        targetHoleButton.setText(arabic ? "بطاقاتي" : "My Cards");
        targetBoardButton.setText(arabic ? "الطاولة" : "Board");
        scanButton.setText(arabic ? "مسح البطاقات" : "Scan cards");
        manualButton.setText(arabic ? "إضافة يدويًا" : "Add manually");
        calculateButton.setText(arabic ? "احسب الاحتمال" : "Estimate equity");

        TextView ml = dashboard.findViewWithTag("mineLabel");
        TextView bl = dashboard.findViewWithTag("boardLabel");
        if (ml != null) { ml.setText(arabic ? "بطاقاتي" : "MY CARDS"); ml.setGravity(arabic ? Gravity.RIGHT : Gravity.LEFT); }
        if (bl != null) { bl.setText(arabic ? "الطاولة" : "BOARD"); bl.setGravity(arabic ? Gravity.RIGHT : Gravity.LEFT); }

        TextView st = scannerOverlay == null ? null : scannerOverlay.findViewWithTag("scannerTitle");
        TextView tip = scannerOverlay == null ? null : scannerOverlay.findViewWithTag("scannerTip");
        Button sm = scannerOverlay == null ? null : scannerOverlay.findViewWithTag("scanMine");
        Button sb = scannerOverlay == null ? null : scannerOverlay.findViewWithTag("scanBoard");
        if (st != null) st.setText(arabic ? "ماسح البطاقات" : "CARD SCANNER");
        if (tip != null) tip.setText(arabic ? "وجّه الكاميرا إلى زاوية الرقم + النوع، ثم اضغط البطاقة المكتشفة." : "Aim at the printed rank + suit corner, then tap a detected card.");
        if (sm != null) sm.setText(arabic ? "بطاقاتي" : "My Cards");
        if (sb != null) sb.setText(arabic ? "الطاولة" : "Board");
        updateOpponentText(); updateTargetButtons(); refreshCards();
        if (lastResult == null) renderEmptyResult();
    }

    private void updateOpponentText() {
        if (opponentsText == null) return;
        opponentsText.setText(arabic ? opponents + (opponents==1 ? " خصم" : " خصوم") : opponents + (opponents==1 ? " opponent" : " opponents"));
    }
    private void updateTargetButtons() {
        if (targetHoleButton == null) return;
        targetHoleButton.setAlpha(targetHole ? 1f : .52f);
        targetBoardButton.setAlpha(targetHole ? .52f : 1f);
        targetHoleButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(targetHole ? PANEL_2 : PANEL));
        targetBoardButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(targetHole ? PANEL : PANEL_2));
    }
    private void updateScannerTargetButtons(Button mine, Button boardButton) {
        if (mine == null || boardButton == null) return;
        mine.setAlpha(targetHole ? 1f : .52f); boardButton.setAlpha(targetHole ? .52f : 1f);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (scannerOverlay.getVisibility() == View.VISIBLE) startCamera();
            } else scanStatus.setText(arabic ? "تم رفض صلاحية الكاميرا. استخدم الإضافة اليدوية." : "Camera permission denied. Use Add manually instead.");
        }
    }
    @Override protected void onPause() { super.onPause(); if (cameraRunning) stopCamera(); }
    @Override protected void onResume() {
        super.onResume();
        if (scannerOverlay != null && scannerOverlay.getVisibility() == View.VISIBLE
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
    }
    @Override protected void onDestroy() {
        stopCamera(); if (recognizer != null) recognizer.close(); if (cameraExecutor != null) cameraExecutor.shutdown(); super.onDestroy();
    }

    private LinearLayout column(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private TextView text(String s,float sp,int color,boolean bold){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if(bold)v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView statView(){ TextView v=text("",12,Color.WHITE,true); v.setGravity(Gravity.CENTER); v.setBackground(rounded(PANEL,10)); return v; }
    private Button primaryButton(String s){ Button b=new Button(this); b.setAllCaps(false); b.setText(s); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor(BG); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(236,239,237))); return b; }
    private Button compactButton(String s){ Button b=primaryButton(s); b.setTextSize(13); b.setPadding(dp(6),0,dp(6),0); return b; }
    private ImageButton iconButton(int res,String desc){ ImageButton b=new ImageButton(this); b.setImageResource(res); b.setContentDescription(desc); b.setPadding(dp(11),dp(11),dp(11),dp(11)); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(236,239,237))); return b; }
    private Button segmentButton(String s){ Button b=new Button(this); b.setAllCaps(false); b.setText(s); b.setTextSize(12); b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL_2)); b.setPadding(dp(4),0,dp(4),0); return b; }
    private Button squareButton(String s){ Button b=new Button(this); b.setAllCaps(false); b.setText(s); b.setTextSize(22); b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL_2)); b.setPadding(0,0,0,0); return b; }
    private Button cardCandidateButton(PokerMath.Card c){ Button b=new Button(this); b.setAllCaps(false); b.setText(c.pretty()); b.setTextSize(20); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor(isRedSuit(c)?RED:Color.BLACK); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE)); b.setMinWidth(dp(64)); return b; }
    private TextView cardView(PokerMath.Card c){ TextView v=text(c.pretty(),17,isRedSuit(c)?RED:Color.BLACK,true); v.setGravity(Gravity.CENTER); v.setBackground(rounded(Color.WHITE,8)); v.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(54))); return v; }
    private TextView emptyCardView(){ TextView v=text("—",17,Color.rgb(135,145,140),true); v.setGravity(Gravity.CENTER); GradientDrawable g=rounded(Color.rgb(225,231,228),8); g.setStroke(dp(1),Color.rgb(160,170,165)); v.setBackground(g); v.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(54))); return v; }
    private boolean isRedSuit(PokerMath.Card c){ return c.suit==1 || c.suit==2; }
    private Button dialogSegmentButton(String s){ Button b=new Button(this); b.setAllCaps(false); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(13); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL_2)); return b; }
    private Button dialogPrimaryButton(String s){ Button b=new Button(this); b.setAllCaps(false); b.setText(s); b.setTextColor(BG); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GOLD)); return b; }
    private Button dialogSecondaryButton(String s){ Button b=new Button(this); b.setAllCaps(false); b.setText(s); b.setTextColor(BG); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(230,234,232))); return b; }
    private GradientDrawable rounded(int color,int radius){ GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private Space space(int n){ Space s=new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(dp(n),1)); return s; }
    private LinearLayout.LayoutParams weight(){ return new LinearLayout.LayoutParams(0,-1,1f); }
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p; }
    private int dp(int n){ return Math.round(n*getResources().getDisplayMetrics().density); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    private String prettyList(List<PokerMath.Card> cards){ StringBuilder b=new StringBuilder(); for(PokerMath.Card c:cards){ if(b.length()>0)b.append("  "); b.append(c.pretty()); } return b.toString(); }

    private static final class CardOption {
        final PokerMath.Card card; final String label;
        CardOption(PokerMath.Card card,String label){this.card=card;this.label=label;}
        @Override public String toString(){return label;}
    }
    private final class CardOptionAdapter extends ArrayAdapter<CardOption> {
        CardOptionAdapter(List<CardOption> options){ super(VisionActivity.this,android.R.layout.simple_spinner_item,options); setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); }
        @Override public View getView(int position,View convertView,android.view.ViewGroup parent){ return style(super.getView(position,convertView,parent),position,false); }
        @Override public View getDropDownView(int position,View convertView,android.view.ViewGroup parent){ return style(super.getDropDownView(position,convertView,parent),position,true); }
        private View style(View view,int position,boolean dropdown){ TextView t=(TextView)view; CardOption o=getItem(position); t.setTextSize(dropdown?18:19); t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(dp(14),dp(dropdown?12:8),dp(14),dp(dropdown?12:8)); t.setBackgroundColor(Color.WHITE); t.setTextColor(o!=null&&isRedSuit(o.card)?RED:Color.BLACK); return t; }
    }
}
