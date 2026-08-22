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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class VisionActivityV030 extends ComponentActivity {
    private static final int CAMERA_REQ = 41;
    private static final int BG = Color.rgb(4, 23, 17);
    private static final int PANEL = Color.rgb(11, 48, 35);
    private static final int PANEL2 = Color.rgb(15, 61, 44);
    private static final int GOLD = Color.rgb(245, 200, 76);
    private static final int MUTED = Color.rgb(190, 205, 198);
    private static final int RED = Color.rgb(196, 42, 42);
    private static final int SIMULATIONS = 25000;

    private final PokerTableState table = new PokerTableState();
    private final ScanReviewState review = new ScanReviewState();
    private final CardFaceView[] holeViews = new CardFaceView[2];
    private final CardFaceView[] boardViews = new CardFaceView[5];

    private boolean arabic;
    private int opponents = 1;
    private boolean cameraRunning;
    private boolean reviewFrozen;

    private FrameLayout root;
    private FrameLayout scanner;
    private PreviewView previewView;
    private ScannerOverlayView scannerOverlay;
    private LinearLayout proposalRow;
    private TextView titleText, selectedHint, playersText, resultText, scanStatus, scanReviewText;
    private TextView mineLabel, boardLabel, pickerHint;
    private Button languageButton, scanButton, manualButton, calculateButton, confirmButton, rescanButton, scannerManualButton, scannerCloseButton;

    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private VisionPipeline pipeline;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        cameraExecutor = Executors.newSingleThreadExecutor();
        try {
            if (OpenCVLoader.initDebug()) pipeline = new VisionPipeline();
        } catch (Throwable ignored) {
            pipeline = null;
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.addView(buildDashboard(), new FrameLayout.LayoutParams(-1, -1));
        scanner = buildScanner();
        scanner.setVisibility(View.GONE);
        root.addView(scanner, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        refreshCards();
        updateTexts();
    }

    private View buildDashboard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout screen = column();
        screen.setPadding(dp(14), dp(12), dp(14), dp(20));
        screen.setBackgroundColor(BG);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        titleText = text("POKER VISION", 23, Color.WHITE, true);
        header.addView(titleText, weight());
        Button reset = darkButton("Reset");
        reset.setOnClickListener(v -> { table.clearAll(); refreshCards(); renderReady(); });
        header.addView(reset, new LinearLayout.LayoutParams(dp(78), dp(42)));
        header.addView(space(6));
        languageButton = darkButton("عربي");
        languageButton.setOnClickListener(v -> { arabic = !arabic; updateTexts(); });
        header.addView(languageButton, new LinearLayout.LayoutParams(dp(82), dp(42)));
        screen.addView(header, lp(-1, dp(48), 0, 0, 0, 8));

        pickerHint = text("Choose suit → choose value. Card saves immediately.", 12, MUTED, false);
        screen.addView(pickerHint, lp(-1, -2, 0, 0, 0, 10));

        LinearLayout cards = column();
        cards.setPadding(dp(12), dp(10), dp(12), dp(10));
        cards.setBackground(rounded(PANEL, 15));
        mineLabel = text("YOUR CARDS", 11, GOLD, true);
        cards.addView(mineLabel);
        LinearLayout holeRow = row();
        holeRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < 2; i++) {
            final int index = i;
            holeViews[i] = new CardFaceView(this);
            holeViews[i].setOnClickListener(v -> { table.selectHole(index); refreshCards(); showDashboardPicker(); });
            holeViews[i].setOnLongClickListener(v -> { table.selectHole(index); table.clearSelected(); refreshCards(); return true; });
            holeRow.addView(holeViews[i], new LinearLayout.LayoutParams(dp(76), dp(98)));
            if (i == 0) holeRow.addView(space(12));
        }
        cards.addView(holeRow, lp(-1, dp(100), 0, 6, 0, 12));

        boardLabel = text("BOARD", 11, GOLD, true);
        cards.addView(boardLabel);
        LinearLayout boardRow = row();
        boardRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < 5; i++) {
            final int index = i;
            boardViews[i] = new CardFaceView(this);
            boardViews[i].setCompact(true);
            boardViews[i].setOnClickListener(v -> { table.selectBoard(index); refreshCards(); showDashboardPicker(); });
            boardViews[i].setOnLongClickListener(v -> { table.selectBoard(index); table.clearSelected(); refreshCards(); return true; });
            boardRow.addView(boardViews[i], new LinearLayout.LayoutParams(dp(56), dp(78)));
            if (i < 4) boardRow.addView(space(5));
        }
        cards.addView(boardRow, lp(-1, dp(80), 0, 6, 0, 0));
        screen.addView(cards, new LinearLayout.LayoutParams(-1, -2));

        selectedHint = text("", 12, GOLD, true);
        screen.addView(selectedHint, lp(-1, dp(34), 2, 5, 2, 6));

        LinearLayout actions = row();
        scanButton = primaryButton("Scan cards");
        scanButton.setOnClickListener(v -> openScanner());
        manualButton = primaryButton("Manual");
        manualButton.setOnClickListener(v -> showDashboardPicker());
        actions.addView(scanButton, weight());
        actions.addView(space(8));
        actions.addView(manualButton, weight());
        screen.addView(actions, lp(-1, dp(48), 0, 0, 0, 10));

        LinearLayout estimate = row();
        estimate.setGravity(Gravity.CENTER_VERTICAL);
        Button minus = darkButton("−");
        Button plus = darkButton("+");
        minus.setOnClickListener(v -> { if (opponents > 1) { opponents--; updatePlayers(); renderReady(); } });
        plus.setOnClickListener(v -> { if (opponents < 9) { opponents++; updatePlayers(); renderReady(); } });
        playersText = text("", 13, Color.WHITE, true);
        playersText.setGravity(Gravity.CENTER);
        estimate.addView(minus, new LinearLayout.LayoutParams(dp(44), dp(42)));
        estimate.addView(playersText, new LinearLayout.LayoutParams(dp(112), dp(42)));
        estimate.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(42)));
        estimate.addView(space(10));
        calculateButton = primaryButton("Estimate equity");
        calculateButton.setOnClickListener(v -> calculate());
        estimate.addView(calculateButton, weight());
        screen.addView(estimate, lp(-1, dp(46), 0, 0, 0, 10));

        resultText = text("Ready to estimate", 15, Color.WHITE, true);
        resultText.setPadding(dp(14), dp(14), dp(14), dp(14));
        resultText.setBackground(rounded(PANEL2, 14));
        screen.addView(resultText, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(screen);
        return scroll;
    }

    private FrameLayout buildScanner() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        frame.addView(previewView, new FrameLayout.LayoutParams(-1, -1));

        scannerOverlay = new ScannerOverlayView(this);
        frame.addView(scannerOverlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(8), dp(10), dp(8));
        top.setBackgroundColor(0xD9041711);
        scannerCloseButton = darkButton("←");
        scannerCloseButton.setOnClickListener(v -> closeScanner());
        top.addView(scannerCloseButton, new LinearLayout.LayoutParams(dp(48), dp(42)));
        TextView title = text("MULTI-CARD SCANNER", 16, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setTag("scannerTitle");
        top.addView(title, weight());
        scannerManualButton = darkButton("Manual");
        scannerManualButton.setOnClickListener(v -> showDashboardPicker());
        top.addView(scannerManualButton, new LinearLayout.LayoutParams(dp(88), dp(42)));
        frame.addView(top, new FrameLayout.LayoutParams(-1, dp(58), Gravity.TOP));

        LinearLayout bottom = column();
        bottom.setPadding(dp(10), dp(8), dp(10), dp(8));
        bottom.setBackgroundColor(0xE8041711);
        scanStatus = text("Place cards inside the guides", 12, Color.WHITE, true);
        scanStatus.setGravity(Gravity.CENTER);
        bottom.addView(scanStatus, lp(-1, dp(26), 0, 0, 0, 3));
        scanReviewText = text("", 11, MUTED, false);
        scanReviewText.setGravity(Gravity.CENTER);
        bottom.addView(scanReviewText, lp(-1, dp(36), 0, 0, 0, 3));
        proposalRow = row();
        proposalRow.setGravity(Gravity.CENTER);
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.addView(proposalRow);
        bottom.addView(hsv, lp(-1, dp(44), 0, 0, 0, 4));
        LinearLayout scanActions = row();
        confirmButton = primaryButton("Confirm all");
        confirmButton.setOnClickListener(v -> confirmScan());
        rescanButton = darkButton("Rescan");
        rescanButton.setOnClickListener(v -> rescan());
        scanActions.addView(confirmButton, weight());
        scanActions.addView(space(7));
        scanActions.addView(rescanButton, weight());
        bottom.addView(scanActions, lp(-1, dp(44), 0, 0, 0, 0));
        frame.addView(bottom, new FrameLayout.LayoutParams(-1, dp(170), Gravity.BOTTOM));
        return frame;
    }

    private void showDashboardPicker() {
        PokerMath.Card current = table.selectedCard();
        Set<PokerMath.Card> blocked = allTableCards();
        showCardPicker(current, blocked, card -> {
            if (table.setSelected(card)) {
                table.selectNextEmpty();
                refreshCards();
                renderReady();
            } else toast(t("That card is already selected", "هذه البطاقة مستخدمة بالفعل"));
        }, current == null ? null : () -> { table.clearSelected(); refreshCards(); renderReady(); });
    }

    private void showCardPicker(PokerMath.Card currentCard,
                                Set<PokerMath.Card> blocked,
                                Consumer<PokerMath.Card> onChosen,
                                Runnable onRemove) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = column();
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(rounded(Color.WHITE, 18));
        CardPickerModel model = new CardPickerModel(blocked, currentCard);
        Runnable[] renderSuit = new Runnable[1];
        Runnable[] renderRank = new Runnable[1];

        renderSuit[0] = () -> {
            box.removeAllViews();
            TextView title = text(t("Choose suit", "اختر النوع"), 21, BG, true);
            box.addView(title, lp(-1, dp(38), 0, 0, 0, 8));
            String[] symbols = {"♠", "♥", "♦", "♣"};
            String[] namesEn = {"Spades", "Hearts", "Diamonds", "Clubs"};
            String[] namesAr = {"سباتي", "قلوب", "ديناري", "كلوب"};
            for (int i = 0; i < 4; i++) {
                final int suit = i;
                Button b = new Button(this);
                b.setAllCaps(false);
                b.setText(symbols[i] + "   " + (arabic ? namesAr[i] : namesEn[i]));
                b.setTextSize(19);
                b.setTypeface(Typeface.DEFAULT_BOLD);
                b.setTextColor((i == 1 || i == 2) ? RED : Color.BLACK);
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(238, 242, 240)));
                b.setOnClickListener(v -> { model.chooseSuit(suit); renderRank[0].run(); });
                box.addView(b, lp(-1, dp(54), 0, 0, 0, 7));
            }
            if (currentCard != null && onRemove != null) {
                Button remove = darkDialogButton(t("Remove card", "حذف البطاقة"));
                remove.setOnClickListener(v -> { onRemove.run(); dialog.dismiss(); });
                box.addView(remove, lp(-1, dp(46), 0, 3, 0, 0));
            }
        };

        renderRank[0] = () -> {
            box.removeAllViews();
            LinearLayout h = row();
            Button back = darkDialogButton("←");
            back.setOnClickListener(v -> { model.backToSuit(); renderSuit[0].run(); });
            h.addView(back, new LinearLayout.LayoutParams(dp(48), dp(42)));
            TextView title = text(t("Choose value", "اختر القيمة"), 21, BG, true);
            title.setGravity(Gravity.CENTER);
            h.addView(title, weight());
            box.addView(h, lp(-1, dp(46), 0, 0, 0, 9));

            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(4);
            int[] ranks = {14,13,12,11,10,9,8,7,6,5,4,3,2};
            for (int rank : ranks) {
                Button b = new Button(this);
                b.setAllCaps(false);
                b.setText(PokerMath.rankText(rank));
                b.setTextSize(18);
                b.setTypeface(Typeface.DEFAULT_BOLD);
                boolean enabled = model.isRankEnabled(rank);
                b.setEnabled(enabled);
                b.setAlpha(enabled ? 1f : .25f);
                b.setTextColor(BG);
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(232, 237, 234)));
                b.setOnClickListener(v -> {
                    try {
                        PokerMath.Card chosen = model.chooseRank(rank);
                        onChosen.accept(chosen);
                        dialog.dismiss();
                    } catch (IllegalArgumentException ex) {
                        toast(t("Already selected", "مستخدمة بالفعل"));
                    }
                });
                GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
                gp.width = 0; gp.height = dp(50); gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                gp.setMargins(dp(3), dp(3), dp(3), dp(3));
                grid.addView(b, gp);
            }
            box.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        };

        dialog.setContentView(box);
        dialog.setOnShowListener(x -> {
            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawableResource(android.R.color.transparent);
                WindowManager.LayoutParams p = new WindowManager.LayoutParams();
                p.copyFrom(w.getAttributes());
                p.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(520));
                p.height = WindowManager.LayoutParams.WRAP_CONTENT;
                w.setAttributes(p);
            }
            renderSuit[0].run();
        });
        dialog.show();
    }

    private void openScanner() {
        review.clear();
        reviewFrozen = false;
        if (pipeline != null) pipeline.clear();
        scannerOverlay.clear();
        updateReviewUi();
        scanner.setVisibility(View.VISIBLE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            scanStatus.setText(t("Camera permission is required for scanning", "يلزم السماح بالكاميرا للمسح"));
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQ);
        }
    }

    private void startCamera() {
        if (cameraRunning) return;
        scanStatus.setText(pipeline == null
                ? t("Scanner initialization error — Manual is still available", "خطأ في تهيئة الماسح — الإدخال اليدوي متاح")
                : t("Starting camera…", "جاري تشغيل الكاميرا…"));
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                cameraProvider.unbindAll();
                if (pipeline != null) {
                    ImageAnalysis analysis = new ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetResolution(new android.util.Size(1280, 720))
                            .build();
                    analysis.setAnalyzer(cameraExecutor, proxy -> pipeline.process(proxy, SystemClock.elapsedRealtime(), new VisionPipeline.Callback() {
                        @Override public void onResult(List<ScanModels.TrackedDetection> detections, TableZoneMapper.Result zones) {
                            runOnUiThread(() -> onVisionResult(detections, zones));
                        }
                        @Override public void onError(String message) {
                            runOnUiThread(() -> scanStatus.setText(t("Scanner: ", "الماسح: ") + message));
                        }
                    }));
                    cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                } else {
                    cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview);
                }
                cameraRunning = true;
                runOnUiThread(() -> scanStatus.setText(pipeline == null
                        ? t("Scanner unavailable — use Manual", "الماسح غير متاح — استخدم الإدخال اليدوي")
                        : t("Place cards inside the guides", "ضع البطاقات داخل الإطارات")));
            } catch (Exception e) {
                cameraRunning = false;
                runOnUiThread(() -> scanStatus.setText(t("Camera error: ", "خطأ بالكاميرا: ") + e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void onVisionResult(List<ScanModels.TrackedDetection> detections, TableZoneMapper.Result zones) {
        if (scanner.getVisibility() != View.VISIBLE) return;
        scannerOverlay.setDetections(detections, zones);
        if (zones != null && zones.capacityError) {
            scanStatus.setText(t("Too many cards in this area", "بطاقات كثيرة في هذه المنطقة"));
        } else if (detections == null || detections.isEmpty()) {
            scanStatus.setText(t("Place cards inside the guides", "ضع البطاقات داخل الإطارات"));
        } else {
            long stable = detections.stream().filter(d -> d.status == ScanModels.Status.STABLE).count();
            scanStatus.setText(t(stable + " stable • Tap a card to correct it",
                    stable + " ثابتة • اضغط على بطاقة لتصحيحها"));
        }
        if (!reviewFrozen && pipeline != null && zones != null && !zones.capacityError) {
            review.replaceProposals(pipeline.proposals(zones));
        }
        updateReviewUi();
    }

    private void updateReviewUi() {
        List<ScanModels.Proposal> proposals = review.proposals();
        StringBuilder hole = new StringBuilder();
        StringBuilder board = new StringBuilder();
        proposalRow.removeAllViews();
        for (ScanModels.Proposal p : proposals) {
            String card = p.card == null ? "?" : p.card.pretty();
            if (p.area == PokerTableState.AREA_HOLE) {
                if (hole.length() > 0) hole.append(' ');
                hole.append(card);
            } else {
                if (board.length() > 0) board.append(' ');
                board.append(card);
            }
            Button b = darkButton((p.area == PokerTableState.AREA_HOLE ? "H" : "B") + (p.index + 1) + " " + card);
            if (p.status == ScanModels.Status.AMBER) b.setAlpha(.82f);
            else if (p.status == ScanModels.Status.NEUTRAL) b.setAlpha(.55f);
            b.setOnClickListener(v -> correctProposal(p));
            proposalRow.addView(b, new LinearLayout.LayoutParams(dp(82), dp(40)));
            proposalRow.addView(space(5));
        }
        scanReviewText.setText(t("Your Cards: ", "بطاقاتك: ") + (hole.length() == 0 ? "—" : hole)
                + "   •   " + t("Board: ", "الطاولة: ") + (board.length() == 0 ? "—" : board));
        boolean confirmable = review.validateAgainst(table);
        confirmButton.setEnabled(confirmable);
        confirmButton.setAlpha(confirmable ? 1f : .40f);
    }

    private void correctProposal(ScanModels.Proposal proposal) {
        HashSet<PokerMath.Card> blocked = new HashSet<>(allTableCards());
        PokerMath.Card tableTarget = proposal.area == PokerTableState.AREA_HOLE
                ? table.holeAt(proposal.index) : table.boardAt(proposal.index);
        if (tableTarget != null) blocked.remove(tableTarget);
        for (ScanModels.Proposal other : review.proposals()) {
            if (other == proposal) continue;
            if (other.card != null) blocked.add(other.card);
        }
        showCardPicker(proposal.card, blocked, card -> {
            review.correct(proposal.area, proposal.index, card);
            reviewFrozen = true;
            updateReviewUi();
        }, null);
    }

    private void confirmScan() {
        try {
            List<PokerTableState.SlotUpdate> updates = review.prepareCommit(table);
            if (!table.applyBatch(updates)) throw new IllegalStateException("Batch rejected");
            table.selectFirstEmpty();
            refreshCards();
            renderReady();
            toast(t("Cards confirmed", "تم تأكيد البطاقات"));
            closeScanner();
        } catch (Exception e) {
            scanStatus.setText(t("Fix uncertain or duplicate cards before confirming", "صحح البطاقات غير المؤكدة أو المكررة أولاً"));
        }
    }

    private void rescan() {
        review.clear();
        reviewFrozen = false;
        if (pipeline != null) pipeline.clear();
        scannerOverlay.clear();
        scanStatus.setText(t("Place cards inside the guides", "ضع البطاقات داخل الإطارات"));
        updateReviewUi();
    }

    private void closeScanner() {
        stopCamera();
        review.clear();
        reviewFrozen = false;
        scannerOverlay.clear();
        scanner.setVisibility(View.GONE);
    }

    private void stopCamera() {
        cameraRunning = false;
        if (cameraProvider != null) cameraProvider.unbindAll();
    }

    private void calculate() {
        List<PokerMath.Card> hole = table.holeCards();
        if (hole.size() != 2) {
            toast(t("Fill both of your cards first", "أكمل بطاقتيك أولاً"));
            return;
        }
        List<PokerMath.Card> board = table.boardCards();
        int opp = opponents;
        calculateButton.setEnabled(false);
        calculateButton.setText(t("Calculating…", "جاري الحساب…"));
        resultText.setText(t("Calculating equity…", "جاري حساب الاحتمال…"));
        new Thread(() -> {
            try {
                PokerMath.Result r = PokerMath.calculate(new ArrayList<>(hole), new ArrayList<>(board), opp, SIMULATIONS, System.nanoTime());
                runOnUiThread(() -> {
                    int playersTotal = opp + 1;
                    resultText.setText(String.format(Locale.US,
                            "%s\n%.1f%% %s\nWIN %.1f%%   TIE %.1f%%   LOSE %.1f%%\n%s\n%d %s • %,d %s",
                            r.currentHand,
                            r.equity, t("equity", "احتمال"),
                            r.win, r.tie, r.lose,
                            r.draws,
                            playersTotal, t("players", "لاعبين"), r.simulations, t("simulations", "محاكاة")));
                    calculateButton.setEnabled(true);
                    calculateButton.setText(t("Estimate equity", "احسب الاحتمال"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    resultText.setText("Error: " + e.getMessage());
                    calculateButton.setEnabled(true);
                    calculateButton.setText(t("Estimate equity", "احسب الاحتمال"));
                });
            }
        }).start();
    }

    private void refreshCards() {
        for (int i = 0; i < holeViews.length; i++) {
            if (holeViews[i] == null) continue;
            holeViews[i].setCard(table.holeAt(i));
            holeViews[i].setSelectedSlot(table.selectedArea() == PokerTableState.AREA_HOLE && table.selectedIndex() == i);
        }
        for (int i = 0; i < boardViews.length; i++) {
            if (boardViews[i] == null) continue;
            boardViews[i].setCard(table.boardAt(i));
            boardViews[i].setSelectedSlot(table.selectedArea() == PokerTableState.AREA_BOARD && table.selectedIndex() == i);
        }
        String slot = table.selectedArea() == PokerTableState.AREA_HOLE
                ? t("My card ", "بطاقتي ") + (table.selectedIndex() + 1)
                : t("Board ", "الطاولة ") + (table.selectedIndex() + 1);
        PokerMath.Card c = table.selectedCard();
        selectedHint.setText(t("Selected: ", "المحدد: ") + slot + (c == null ? "" : " • " + c.pretty()));
    }

    private Set<PokerMath.Card> allTableCards() {
        HashSet<PokerMath.Card> out = new HashSet<>();
        out.addAll(table.holeCards());
        out.addAll(table.boardCards());
        return out;
    }

    private void updateTexts() {
        languageButton.setText(arabic ? "English" : "عربي");
        mineLabel.setText(t("YOUR CARDS", "بطاقاتك"));
        boardLabel.setText(t("BOARD", "الطاولة"));
        pickerHint.setText(t("Choose suit → choose value. Card saves immediately.", "اختر النوع ← ثم القيمة. تُحفظ البطاقة فوراً."));
        scanButton.setText(t("Scan cards", "مسح البطاقات"));
        manualButton.setText(t("Manual", "يدوي"));
        calculateButton.setText(t("Estimate equity", "احسب الاحتمال"));
        scannerManualButton.setText(t("Manual", "يدوي"));
        confirmButton.setText(t("Confirm all", "تأكيد الكل"));
        rescanButton.setText(t("Rescan", "إعادة المسح"));
        scannerOverlay.setArabic(arabic);
        TextView scannerTitle = scanner.findViewWithTag("scannerTitle");
        if (scannerTitle != null) scannerTitle.setText(t("MULTI-CARD SCANNER", "ماسح البطاقات"));
        updatePlayers();
        refreshCards();
        updateReviewUi();
    }

    private void updatePlayers() {
        int playersTotal = opponents + 1;
        playersText.setText(arabic ? playersTotal + " لاعبين" : playersTotal + " players");
    }

    private void renderReady() {
        resultText.setText(t("Ready to estimate", "جاهز للحساب"));
    }

    private String t(String en, String ar) { return arabic ? ar : en; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && scanner.getVisibility() == View.VISIBLE) startCamera();
            else scanStatus.setText(t("Camera permission denied — use Manual", "تم رفض الكاميرا — استخدم الإدخال اليدوي"));
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (scanner != null && scanner.getVisibility() == View.VISIBLE) stopCamera();
    }

    @Override protected void onDestroy() {
        stopCamera();
        if (pipeline != null) pipeline.release();
        if (cameraExecutor != null) cameraExecutor.shutdownNow();
        super.onDestroy();
    }

    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private View space(int dp) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(dp), 1)); return v; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -1, 1f); }
    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p; }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private Button primaryButton(String value) {
        Button b = new Button(this);
        b.setAllCaps(false); b.setText(value); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(BG); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GOLD));
        return b;
    }

    private Button darkButton(String value) {
        Button b = new Button(this);
        b.setAllCaps(false); b.setText(value); b.setTextSize(12); b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL2));
        return b;
    }

    private Button darkDialogButton(String value) {
        Button b = new Button(this);
        b.setAllCaps(false); b.setText(value); b.setTextSize(13); b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL2));
        return b;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radiusDp)); return d;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
