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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
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

public final class VisionActivityV025 extends ComponentActivity {
    private static final int CAMERA_REQ = 41;
    private static final int BG = Color.rgb(4, 23, 17);
    private static final int PANEL = Color.rgb(11, 48, 35);
    private static final int PANEL2 = Color.rgb(15, 61, 44);
    private static final int GOLD = Color.rgb(245, 200, 76);
    private static final int MUTED = Color.rgb(190, 205, 198);
    private static final int RED = Color.rgb(196, 42, 42);
    private static final int SIMULATIONS = 25000;

    private final PokerTableState table = new PokerTableState();
    private final CardFaceView[] holeViews = new CardFaceView[2];
    private final CardFaceView[] boardViews = new CardFaceView[5];

    private boolean arabic = false;
    private int opponents = 1;

    private FrameLayout root, scanner;
    private LinearLayout dashboard, candidates, resultPanel;
    private PreviewView previewView;
    private TextView modeHint, selectedHint, opponentsText, scanStatus;
    private TextView resultHand, resultStage, resultEquity, resultWin, resultTie, resultLose, resultDetail, resultMeta;
    private Button language, scanButton, manualButton, guideButton, calculateButton, clearSelectedButton;

    private ExecutorService cameraExecutor;
    private TextRecognizer recognizer;
    private ProcessCameraProvider cameraProvider;
    private boolean cameraRunning = false;
    private final AtomicBoolean analyzing = new AtomicBoolean(false);
    private long lastAnalyzeMs = 0L, lastCandidateMs = 0L;
    private String lastCandidateKey = "";

    private PokerMath.Result lastResult;
    private int lastResultOpponents = 1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, i.top, 0, i.bottom);
            return insets;
        });

        dashboard = buildDashboard();
        root.addView(dashboard, new FrameLayout.LayoutParams(-1, -1));
        scanner = buildScanner();
        scanner.setVisibility(View.GONE);
        root.addView(scanner, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        updateTexts();
        refreshCards();
        renderEmptyResult();
    }

    private LinearLayout buildDashboard() {
        LinearLayout screen = column();
        screen.setBackgroundColor(BG);
        screen.setPadding(dp(12), dp(6), dp(12), dp(10));
        screen.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LinearLayout header = row();
        header.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("POKER VISION", 23, Color.WHITE, true);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(title, weight());

        ImageButton guideIcon = iconButton(R.drawable.ic_guide_cards, "Guide");
        guideIcon.setOnClickListener(v -> showGuide());
        header.addView(guideIcon, new LinearLayout.LayoutParams(dp(44), dp(42)));
        header.addView(space(5));

        ImageButton reset = iconButton(R.drawable.ic_reset, "Reset cards");
        reset.setOnClickListener(v -> resetAll());
        header.addView(reset, new LinearLayout.LayoutParams(dp(44), dp(42)));
        header.addView(space(5));

        language = compactButton("عربي");
        language.setOnClickListener(v -> {
            arabic = !arabic;
            updateTexts();
            if (lastResult != null) renderResult(lastResult, lastResultOpponents);
        });
        header.addView(language, new LinearLayout.LayoutParams(dp(80), dp(42)));
        screen.addView(header, lp(-1, dp(46), 0, 0, 0, 2));

        modeHint = text("", 11, MUTED, false);
        screen.addView(modeHint, lp(-1, dp(22), 0, 0, 0, 4));

        LinearLayout cardsPanel = column();
        cardsPanel.setPadding(dp(10), dp(6), dp(10), dp(6));
        cardsPanel.setBackground(rounded(PANEL, 14));

        TextView mineLabel = text("", 10, GOLD, true);
        mineLabel.setTag("mineLabel");
        cardsPanel.addView(mineLabel, lp(-1, -2, 0, 0, 0, 2));

        LinearLayout holeRow = row();
        holeRow.setGravity(Gravity.CENTER);
        holeRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (int i = 0; i < holeViews.length; i++) {
            final int index = i;
            holeViews[i] = slotCard(false);
            holeViews[i].setOnClickListener(v -> selectHole(index));
            holeViews[i].setOnLongClickListener(v -> { selectHole(index); clearSelectedCard(); return true; });
            holeRow.addView(holeViews[i], new LinearLayout.LayoutParams(dp(68), dp(84)));
            if (i < holeViews.length - 1) holeRow.addView(space(8));
        }
        cardsPanel.addView(holeRow, lp(-1, dp(84), 0, 0, 0, 4));

        TextView boardLabel = text("", 10, GOLD, true);
        boardLabel.setTag("boardLabel");
        cardsPanel.addView(boardLabel, lp(-1, -2, 0, 0, 0, 2));

        LinearLayout boardRow = row();
        boardRow.setGravity(Gravity.CENTER);
        boardRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (int i = 0; i < boardViews.length; i++) {
            final int index = i;
            boardViews[i] = slotCard(true);
            boardViews[i].setOnClickListener(v -> selectBoard(index));
            boardViews[i].setOnLongClickListener(v -> { selectBoard(index); clearSelectedCard(); return true; });
            boardRow.addView(boardViews[i], new LinearLayout.LayoutParams(dp(52), dp(70)));
            if (i < boardViews.length - 1) boardRow.addView(space(5));
        }
        cardsPanel.addView(boardRow, lp(-1, dp(70), 0, 0, 0, 0));
        screen.addView(cardsPanel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout selectionRow = row();
        selectionRow.setGravity(Gravity.CENTER_VERTICAL);
        selectedHint = text("", 11, GOLD, true);
        selectionRow.addView(selectedHint, weight());
        clearSelectedButton = smallDarkButton("");
        clearSelectedButton.setOnClickListener(v -> clearSelectedCard());
        selectionRow.addView(clearSelectedButton, new LinearLayout.LayoutParams(dp(96), dp(34)));
        screen.addView(selectionRow, lp(-1, dp(38), 0, 3, 0, 5));

        LinearLayout actions = row();
        scanButton = primaryButton("");
        scanButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_camera, 0, 0, 0);
        scanButton.setCompoundDrawablePadding(dp(5));
        scanButton.setOnClickListener(v -> openScanner());
        manualButton = primaryButton("");
        manualButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_edit_card, 0, 0, 0);
        manualButton.setCompoundDrawablePadding(dp(5));
        manualButton.setOnClickListener(v -> showManualPicker());
        guideButton = primaryButton("");
        guideButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_guide_cards, 0, 0, 0);
        guideButton.setCompoundDrawablePadding(dp(5));
        guideButton.setOnClickListener(v -> showGuide());
        actions.addView(scanButton, weight());
        actions.addView(space(6));
        actions.addView(manualButton, weight());
        actions.addView(space(6));
        actions.addView(guideButton, weight());
        screen.addView(actions, lp(-1, dp(46), 0, 0, 0, 7));

        LinearLayout estimate = row();
        estimate.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout stepper = row();
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        stepper.setPadding(dp(3), dp(2), dp(3), dp(2));
        stepper.setBackground(rounded(PANEL, 12));
        Button minus = squareButton("−"), plus = squareButton("+");
        minus.setOnClickListener(v -> { if (opponents > 1) { opponents--; updateOpponentText(); invalidateResult(); } });
        plus.setOnClickListener(v -> { if (opponents < 5) { opponents++; updateOpponentText(); invalidateResult(); } });
        opponentsText = text("", 12, Color.WHITE, true);
        opponentsText.setGravity(Gravity.CENTER);
        stepper.addView(minus, new LinearLayout.LayoutParams(dp(36), dp(36)));
        stepper.addView(opponentsText, new LinearLayout.LayoutParams(dp(96), dp(36)));
        stepper.addView(plus, new LinearLayout.LayoutParams(dp(36), dp(36)));
        estimate.addView(stepper, new LinearLayout.LayoutParams(dp(168), dp(40)));
        estimate.addView(space(7));

        calculateButton = new Button(this);
        calculateButton.setAllCaps(false);
        calculateButton.setTextSize(13);
        calculateButton.setTypeface(Typeface.DEFAULT_BOLD);
        calculateButton.setTextColor(BG);
        calculateButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GOLD));
        calculateButton.setOnClickListener(v -> calculate());
        estimate.addView(calculateButton, weight());
        screen.addView(estimate, lp(-1, dp(44), 0, 0, 0, 7));

        resultPanel = column();
        resultPanel.setPadding(dp(14), dp(9), dp(14), dp(9));
        resultPanel.setBackground(rounded(PANEL2, 14));
        resultPanel.setMinimumHeight(dp(168));

        LinearLayout resultHeader = row();
        resultHeader.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        resultHand = text("", 16, Color.WHITE, true);
        resultStage = text("", 11, MUTED, true);
        resultStage.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        resultHeader.addView(resultHand, weight());
        resultHeader.addView(resultStage, new LinearLayout.LayoutParams(dp(100), -1));
        resultPanel.addView(resultHeader, lp(-1, dp(24), 0, 0, 0, 1));

        resultEquity = text("", 30, GOLD, true);
        resultEquity.setGravity(Gravity.CENTER);
        resultPanel.addView(resultEquity, lp(-1, dp(42), 0, 0, 0, 3));

        LinearLayout odds = row();
        resultWin = statView(); resultTie = statView(); resultLose = statView();
        odds.addView(resultWin, weight()); odds.addView(space(5)); odds.addView(resultTie, weight()); odds.addView(space(5)); odds.addView(resultLose, weight());
        resultPanel.addView(odds, lp(-1, dp(43), 0, 0, 0, 5));

        resultDetail = text("", 12, Color.WHITE, false);
        resultMeta = text("", 11, MUTED, false);
        resultPanel.addView(resultDetail, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
        metaLp.setMargins(0, dp(3), 0, 0);
        resultPanel.addView(resultMeta, metaLp);
        screen.addView(resultPanel, new LinearLayout.LayoutParams(-1, -2));
        return screen;
    }

    private FrameLayout buildScanner() {
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
        close.setTextSize(22);
        close.setOnClickListener(v -> closeScanner());
        top.addView(close, new LinearLayout.LayoutParams(dp(46), dp(40)));
        TextView scannerTitle = text("CARD SCANNER", 17, Color.WHITE, true);
        scannerTitle.setGravity(Gravity.CENTER);
        scannerTitle.setTag("scannerTitle");
        top.addView(scannerTitle, weight());
        ImageButton manual = iconButton(R.drawable.ic_edit_card, "Add manually");
        manual.setOnClickListener(v -> showManualPicker());
        top.addView(manual, new LinearLayout.LayoutParams(dp(46), dp(40)));
        overlay.addView(top, new FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP));

        LinearLayout bottom = column();
        bottom.setPadding(dp(12), dp(9), dp(12), dp(9));
        bottom.setBackgroundColor(0xE8041711);
        TextView target = text("", 12, GOLD, true);
        target.setGravity(Gravity.CENTER);
        target.setTag("scannerTarget");
        bottom.addView(target, lp(-1, dp(28), 0, 0, 0, 2));
        scanStatus = text("", 13, Color.WHITE, true);
        scanStatus.setGravity(Gravity.CENTER);
        bottom.addView(scanStatus, lp(-1, dp(36), 0, 0, 0, 3));
        candidates = row();
        candidates.setGravity(Gravity.CENTER);
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.addView(candidates);
        bottom.addView(hsv, lp(-1, dp(54), 0, 0, 0, 3));
        TextView tip = text("", 11, MUTED, false);
        tip.setGravity(Gravity.CENTER);
        tip.setTag("scannerTip");
        bottom.addView(tip, lp(-1, dp(30), 0, 0, 0, 0));
        overlay.addView(bottom, new FrameLayout.LayoutParams(-1, dp(162), Gravity.BOTTOM));
        return overlay;
    }

    private CardFaceView slotCard(boolean compact) {
        CardFaceView v = new CardFaceView(this);
        v.setCompact(compact);
        return v;
    }

    private void selectHole(int index) {
        table.selectHole(index);
        refreshCards();
        updateSelectedHint();
    }

    private void selectBoard(int index) {
        table.selectBoard(index);
        refreshCards();
        updateSelectedHint();
    }

    private void clearSelectedCard() {
        if (table.selectedCard() == null) return;
        table.clearSelected();
        invalidateResult();
        refreshCards();
        toast(arabic ? "تم مسح الخانة المحددة" : "Selected slot cleared");
    }

    private boolean placeSelected(PokerMath.Card card, boolean advance) {
        if (!table.setSelected(card)) {
            toast(arabic ? "هذه البطاقة مستخدمة في خانة أخرى" : "That card is already used in another slot");
            return false;
        }
        invalidateResult();
        if (advance) table.selectNextEmpty();
        refreshCards();
        updateSelectedHint();
        return true;
    }

    private void refreshCards() {
        for (int i = 0; i < holeViews.length; i++) {
            holeViews[i].setCard(table.holeAt(i));
            holeViews[i].setSelectedSlot(table.selectedArea() == PokerTableState.AREA_HOLE && table.selectedIndex() == i);
        }
        for (int i = 0; i < boardViews.length; i++) {
            boardViews[i].setCard(table.boardAt(i));
            boardViews[i].setSelectedSlot(table.selectedArea() == PokerTableState.AREA_BOARD && table.selectedIndex() == i);
        }
        updateSelectedHint();
        updateScannerTarget();
    }

    private void updateSelectedHint() {
        if (selectedHint == null) return;
        String label = slotLabel(table.selectedArea(), table.selectedIndex());
        boolean filled = table.selectedCard() != null;
        selectedHint.setText((arabic ? "الخانة المحددة: " : "Selected: ") + label + (filled ? "  •  " + table.selectedCard().pretty() : ""));
        selectedHint.setTextDirection(arabic ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        selectedHint.setGravity(arabic ? Gravity.RIGHT : Gravity.LEFT);
        clearSelectedButton.setEnabled(filled);
        clearSelectedButton.setAlpha(filled ? 1f : .45f);
    }

    private String slotLabel(int area, int index) {
        if (arabic) return area == PokerTableState.AREA_HOLE ? "بطاقتي " + (index + 1) : "الطاولة " + (index + 1);
        return area == PokerTableState.AREA_HOLE ? "My card " + (index + 1) : "Board " + (index + 1);
    }

    private void showManualPicker() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = column();
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(rounded(Color.WHITE, 18));

        TextView title = text(arabic ? "إضافة أو استبدال بطاقة" : "Add or replace card", 20, BG, true);
        box.addView(title, lp(-1, dp(34), 0, 0, 0, 4));
        TextView target = text((arabic ? "الخانة: " : "Slot: ") + slotLabel(table.selectedArea(), table.selectedIndex()), 12, Color.DKGRAY, true);
        target.setGravity(arabic ? Gravity.RIGHT : Gravity.LEFT);
        box.addView(target, lp(-1, dp(28), 0, 0, 0, 7));

        final int[] selectedRank = {14};
        final int[] selectedSuit = {0};
        PokerMath.Card existing = table.selectedCard();
        if (existing != null) { selectedRank[0] = existing.rank; selectedSuit[0] = existing.suit; }
        ArrayList<Button> rankButtons = new ArrayList<>();
        ArrayList<Button> suitButtons = new ArrayList<>();

        TextView helper = text(arabic ? "اختر القيمة ثم النوع." : "Choose rank, then suit.", 12, Color.DKGRAY, false);
        helper.setGravity(arabic ? Gravity.RIGHT : Gravity.LEFT);
        box.addView(helper, lp(-1, -2, 0, 0, 0, 6));

        CardFaceView preview = new CardFaceView(this);
        preview.setCompact(false);

        Runnable refreshSelection = () -> {
            for (Button b : rankButtons) {
                int rank = (Integer)b.getTag();
                boolean on = rank == selectedRank[0];
                b.setAlpha(on ? 1f : .52f);
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(on ? PANEL2 : Color.rgb(222, 228, 225)));
                b.setTextColor(on ? Color.WHITE : BG);
            }
            for (Button b : suitButtons) {
                int suit = (Integer)b.getTag();
                boolean on = suit == selectedSuit[0];
                b.setAlpha(on ? 1f : .52f);
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(on ? PANEL2 : Color.rgb(222, 228, 225)));
                b.setTextColor((suit == 1 || suit == 2) ? RED : Color.BLACK);
            }
            preview.setCard(new PokerMath.Card(selectedRank[0], selectedSuit[0]));
        };

        int[][] rankRows = {{14,13,12,11,10,9,8}, {7,6,5,4,3,2}};
        for (int[] rr : rankRows) {
            LinearLayout r = row();
            for (int i = 0; i < rr.length; i++) {
                Button b = miniChoice(PokerMath.rankText(rr[i]));
                b.setTag(rr[i]);
                b.setOnClickListener(v -> { selectedRank[0] = (Integer)v.getTag(); refreshSelection.run(); });
                rankButtons.add(b);
                r.addView(b, weight());
                if (i < rr.length - 1) r.addView(space(4));
            }
            box.addView(r, lp(-1, dp(39), 0, 0, 0, 5));
        }

        LinearLayout suitRow = row();
        String[] suits = {"♠", "♥", "♦", "♣"};
        for (int s = 0; s < 4; s++) {
            Button b = miniChoice(suits[s]);
            b.setTag(s);
            b.setTextSize(22);
            b.setOnClickListener(v -> { selectedSuit[0] = (Integer)v.getTag(); refreshSelection.run(); });
            suitButtons.add(b);
            suitRow.addView(b, weight());
            if (s < 3) suitRow.addView(space(6));
        }
        box.addView(suitRow, lp(-1, dp(44), 0, 2, 0, 8));

        FrameLayout previewHolder = new FrameLayout(this);
        previewHolder.setBackground(rounded(Color.rgb(248, 248, 248), 12));
        FrameLayout.LayoutParams previewLp = new FrameLayout.LayoutParams(dp(82), dp(108), Gravity.CENTER);
        previewHolder.addView(preview, previewLp);
        box.addView(previewHolder, lp(-1, dp(120), 0, 0, 0, 10));

        LinearLayout bottom = row();
        Button remove = dialogSecondaryButton(arabic ? "حذف" : "Remove");
        remove.setEnabled(existing != null);
        remove.setAlpha(existing != null ? 1f : .45f);
        remove.setOnClickListener(v -> { clearSelectedCard(); dialog.dismiss(); });
        Button done = dialogSecondaryButton(arabic ? "تم" : "Done");
        done.setOnClickListener(v -> dialog.dismiss());
        Button add = dialogPrimaryButton(existing == null ? (arabic ? "إضافة" : "Add") : (arabic ? "استبدال" : "Replace"));
        add.setOnClickListener(v -> {
            PokerMath.Card c = new PokerMath.Card(selectedRank[0], selectedSuit[0]);
            if (placeSelected(c, true)) {
                toast(arabic ? "تم حفظ البطاقة" : c.pretty() + " saved");
                dialog.dismiss();
            }
        });
        bottom.addView(remove, weight()); bottom.addView(space(5)); bottom.addView(done, weight()); bottom.addView(space(5)); bottom.addView(add, weight());
        box.addView(bottom, lp(-1, dp(46), 0, 0, 0, 0));

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
            refreshSelection.run();
        });
        dialog.show();
    }

    private void showGuide() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = column();
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackground(rounded(Color.WHITE, 18));

        LinearLayout titleRow = row();
        TextView title = text(arabic ? "دليل البوكر" : "Poker Guide", 20, BG, true);
        titleRow.addView(title, weight());
        Button close = dialogSecondaryButton("×");
        close.setTextSize(21);
        close.setOnClickListener(v -> dialog.dismiss());
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(44), dp(38)));
        box.addView(titleRow, lp(-1, dp(42), 0, 0, 0, 6));

        LinearLayout tabs = row();
        Button deckTab = dialogSegmentButton(arabic ? "52 بطاقة" : "52 Cards");
        Button handsTab = dialogSegmentButton(arabic ? "ترتيب الأيدي" : "Hands");
        Button scanTab = dialogSegmentButton(arabic ? "المسح" : "Scan");
        tabs.addView(deckTab, weight()); tabs.addView(space(5)); tabs.addView(handsTab, weight()); tabs.addView(space(5)); tabs.addView(scanTab, weight());
        box.addView(tabs, lp(-1, dp(42), 0, 0, 0, 7));

        FrameLayout content = new FrameLayout(this);
        box.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        Runnable showDeck = () -> {
            content.removeAllViews();
            ScrollView scroll = new ScrollView(this);
            LinearLayout list = column();
            TextView help = text(arabic ? "اضغط أي بطاقة لوضعها مباشرة في الخانة المحددة." : "Tap any card to place it directly in the selected slot.", 12, Color.DKGRAY, false);
            help.setPadding(dp(4), 0, dp(4), dp(8));
            list.addView(help);
            String[] suitNames = arabic ? new String[]{"البستوني ♠", "القلوب ♥", "الديناري ♦", "السباتي ♣"} : new String[]{"Spades ♠", "Hearts ♥", "Diamonds ♦", "Clubs ♣"};
            for (int suit = 0; suit < 4; suit++) {
                TextView label = text(suitNames[suit], 14, (suit == 1 || suit == 2) ? RED : BLACK(), true);
                label.setPadding(dp(4), dp(5), dp(4), dp(3));
                list.addView(label);
                HorizontalScrollView hsv = new HorizontalScrollView(this);
                hsv.setHorizontalScrollBarEnabled(false);
                LinearLayout cards = row();
                for (int rank = 14; rank >= 2; rank--) {
                    PokerMath.Card c = new PokerMath.Card(rank, suit);
                    CardFaceView face = new CardFaceView(this);
                    face.setCompact(true);
                    face.setCard(c);
                    face.setOnClickListener(v -> {
                        if (placeSelected(c, true)) {
                            toast((arabic ? "تمت إضافة " : "Added ") + c.pretty());
                            dialog.dismiss();
                        }
                    });
                    cards.addView(face, new LinearLayout.LayoutParams(dp(54), dp(74)));
                    cards.addView(space(4));
                }
                hsv.addView(cards);
                list.addView(hsv, lp(-1, dp(78), 0, 0, 0, 4));
            }
            scroll.addView(list);
            content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
            setTabAlpha(deckTab, handsTab, scanTab, 0);
        };

        Runnable showHands = () -> {
            content.removeAllViews();
            ScrollView scroll = new ScrollView(this);
            LinearLayout list = column();
            String[][] rows = arabic ? new String[][]{
                    {"1. رويال فلاش", "10♠  J♠  Q♠  K♠  A♠", "أعلى يد: عشرة إلى آس من نفس النوع."},
                    {"2. ستريت فلاش", "5♣  6♣  7♣  8♣  9♣", "خمس أوراق متتالية من نفس النوع."},
                    {"3. أربعة من نفس الرتبة", "A♠  A♥  A♦  A♣  8♠", "أربع أوراق بالقيمة نفسها."},
                    {"4. فول هاوس", "Q♠  Q♥  Q♦  6♣  6♥", "ثلاثة من رتبة + زوج من رتبة أخرى."},
                    {"5. فلاش", "2♦  5♦  8♦  J♦  K♦", "خمس أوراق من نفس النوع غير متتالية."},
                    {"6. ستريت", "3♠  4♥  5♦  6♣  7♠", "خمس أوراق متتالية من أنواع مختلفة."},
                    {"7. ثلاثة من نفس الرتبة", "K♠  K♥  K♦  5♣  8♠", "ثلاث أوراق بالقيمة نفسها."},
                    {"8. زوجان", "9♠  9♥  5♦  5♣  3♠", "زوجان من رتبتين مختلفتين."},
                    {"9. زوج واحد", "A♠  A♥  8♦  6♣  2♠", "ورقتان بالقيمة نفسها."},
                    {"10. أعلى ورقة", "A♠  J♥  8♦  6♣  3♠", "عندما لا تنطبق أي يد أخرى."}
            } : new String[][]{
                    {"1. Royal Flush", "10♠  J♠  Q♠  K♠  A♠", "Ten through Ace, all the same suit."},
                    {"2. Straight Flush", "5♣  6♣  7♣  8♣  9♣", "Five consecutive cards of the same suit."},
                    {"3. Four of a Kind", "A♠  A♥  A♦  A♣  8♠", "Four cards of the same rank."},
                    {"4. Full House", "Q♠  Q♥  Q♦  6♣  6♥", "Three of one rank plus a pair."},
                    {"5. Flush", "2♦  5♦  8♦  J♦  K♦", "Five cards of the same suit, not consecutive."},
                    {"6. Straight", "3♠  4♥  5♦  6♣  7♠", "Five consecutive ranks with mixed suits."},
                    {"7. Three of a Kind", "K♠  K♥  K♦  5♣  8♠", "Three cards of the same rank."},
                    {"8. Two Pair", "9♠  9♥  5♦  5♣  3♠", "Two separate pairs."},
                    {"9. One Pair", "A♠  A♥  8♦  6♣  2♠", "Two cards of the same rank."},
                    {"10. High Card", "A♠  J♥  8♦  6♣  3♠", "Used when no stronger hand applies."}
            };
            for (String[] r : rows) {
                LinearLayout card = column();
                card.setPadding(dp(10), dp(8), dp(10), dp(8));
                card.setBackground(rounded(Color.rgb(245, 247, 246), 10));
                TextView h = text(r[0], 14, BG, true);
                TextView ex = text(r[1], 17, Color.DKGRAY, true);
                ex.setTextDirection(View.TEXT_DIRECTION_LTR);
                TextView d = text(r[2], 11, Color.DKGRAY, false);
                card.addView(h); card.addView(ex); card.addView(d);
                list.addView(card, lp(-1, -2, 0, 0, 0, 6));
            }
            scroll.addView(list);
            content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
            setTabAlpha(deckTab, handsTab, scanTab, 1);
        };

        Runnable showScan = () -> {
            content.removeAllViews();
            ScrollView scroll = new ScrollView(this);
            LinearLayout list = column();
            String body = arabic
                    ? "طريقة المسح الأفضل\n\n1. اضغط أولاً على خانة البطاقة التي تريد تعبئتها.\n\n2. اضغط «مسح»؛ الكاميرا تعمل فقط داخل شاشة المسح.\n\n3. وجّه الكاميرا إلى زاوية البطاقة التي تحتوي القيمة والنوع.\n\n4. قرّب البطاقة وتجنب الانعكاس والاهتزاز.\n\n5. اضغط البطاقة المكتشفة لتأكيدها. سيحدد التطبيق الخانة الفارغة التالية تلقائياً.\n\n6. إذا لم يتعرف عليها، استخدم «يدوي» أو دليل 52 بطاقة.\n\nنصيحة: الإضاءة المتجانسة وخلفية بسيطة تعطي أفضل نتيجة."
                    : "Best scanning method\n\n1. Tap the exact card slot you want to fill.\n\n2. Tap Scan; the camera runs only inside the scanner screen.\n\n3. Aim at the printed corner that contains the rank and suit.\n\n4. Move closer and avoid glare, motion blur and fingers covering the corner.\n\n5. Tap the detected card to confirm it. The app automatically selects the next empty slot.\n\n6. If recognition fails, use Manual or the 52-card guide.\n\nTip: even lighting and a simple background produce the most reliable OCR result.";
            TextView text = text(body, 14, Color.DKGRAY, false);
            text.setLineSpacing(dp(3), 1f);
            text.setPadding(dp(8), dp(8), dp(8), dp(8));
            list.addView(text);
            scroll.addView(list);
            content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
            setTabAlpha(deckTab, handsTab, scanTab, 2);
        };

        deckTab.setOnClickListener(v -> showDeck.run());
        handsTab.setOnClickListener(v -> showHands.run());
        scanTab.setOnClickListener(v -> showScan.run());

        dialog.setContentView(box);
        dialog.setOnShowListener(x -> {
            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawableResource(android.R.color.transparent);
                WindowManager.LayoutParams p = new WindowManager.LayoutParams();
                p.copyFrom(w.getAttributes());
                p.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(16), dp(620));
                p.height = (int)(getResources().getDisplayMetrics().heightPixels * 0.86f);
                w.setAttributes(p);
            }
            showDeck.run();
        });
        dialog.show();
    }

    private void setTabAlpha(Button a, Button b, Button c, int selected) {
        a.setAlpha(selected == 0 ? 1f : .55f);
        b.setAlpha(selected == 1 ? 1f : .55f);
        c.setAlpha(selected == 2 ? 1f : .55f);
    }

    private void openScanner() {
        scanner.setVisibility(View.VISIBLE);
        updateScannerTarget();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else {
            scanStatus.setText(arabic ? "اسمح باستخدام الكاميرا فقط أثناء المسح." : "Camera permission is needed only while scanning.");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQ);
        }
    }

    private void closeScanner() {
        stopCamera();
        scanner.setVisibility(View.GONE);
        if (candidates != null) candidates.removeAllViews();
    }

    private void updateScannerTarget() {
        if (scanner == null) return;
        TextView target = scanner.findViewWithTag("scannerTarget");
        if (target != null) target.setText((arabic ? "المسح إلى: " : "Scanning into: ") + slotLabel(table.selectedArea(), table.selectedIndex()));
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
                .addOnSuccessListener(t -> onRecognized(CardRecognizer.extract(t), t))
                .addOnFailureListener(e -> runOnUiThread(() -> scanStatus.setText("OCR: " + e.getClass().getSimpleName())))
                .addOnCompleteListener(t -> { analyzing.set(false); proxy.close(); });
    }

    private void onRecognized(List<PokerMath.Card> cards, Text raw) {
        runOnUiThread(() -> {
            if (scanner.getVisibility() != View.VISIBLE) return;
            if (cards.isEmpty()) {
                scanStatus.setText(arabic ? "لم يتم التعرف بعد. قرّب زاوية البطاقة." : "No card yet — move closer to the rank + suit corner.");
                return;
            }
            StringBuilder key = new StringBuilder();
            for (PokerMath.Card c : cards) key.append(c.code()).append(',');
            if (!key.toString().equals(lastCandidateKey) || SystemClock.elapsedRealtime() - lastCandidateMs > 1200) {
                lastCandidateKey = key.toString();
                lastCandidateMs = SystemClock.elapsedRealtime();
                showCandidates(cards);
            }
            scanStatus.setText((arabic ? "تم اكتشاف: " : "Detected: ") + prettyList(cards));
        });
    }

    private void showCandidates(List<PokerMath.Card> cards) {
        candidates.removeAllViews();
        for (PokerMath.Card c : cards) {
            Button b = cardCandidateButton(c);
            b.setOnClickListener(v -> {
                if (placeSelected(c, true)) {
                    toast((arabic ? "تم حفظ " : "Saved ") + c.pretty());
                    updateScannerTarget();
                    if (table.holeComplete() && table.boardComplete()) closeScanner();
                }
            });
            candidates.addView(b);
            candidates.addView(space(6));
        }
    }

    private void calculate() {
        List<PokerMath.Card> hole = table.holeCards();
        List<PokerMath.Card> board = table.boardCards();
        if (hole.size() != 2) {
            toast(arabic ? "أكمل بطاقتيك أولاً" : "Fill both of your cards first");
            return;
        }
        calculateButton.setEnabled(false);
        calculateButton.setText(arabic ? "جاري الحساب…" : "Calculating…");
        resultHand.setText(arabic ? "حساب الاحتمالات…" : "Calculating equity…");
        resultStage.setText("");
        resultEquity.setText("…");
        ArrayList<PokerMath.Card> h = new ArrayList<>(hole);
        ArrayList<PokerMath.Card> b = new ArrayList<>(board);
        int opp = opponents;
        new Thread(() -> {
            try {
                PokerMath.Result r = PokerMath.calculate(h, b, opp, SIMULATIONS, System.nanoTime());
                runOnUiThread(() -> {
                    lastResult = r;
                    lastResultOpponents = opp;
                    renderResult(r, opp);
                    calculateButton.setEnabled(true);
                    calculateButton.setText(arabic ? "احسب الاحتمال" : "Estimate equity");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    resultHand.setText("Error"); resultStage.setText(""); resultEquity.setText(""); resultDetail.setText(e.getMessage());
                    calculateButton.setEnabled(true);
                    calculateButton.setText(arabic ? "احسب الاحتمال" : "Estimate equity");
                });
            }
        }).start();
    }

    private void invalidateResult() {
        lastResult = null;
        renderEmptyResult();
    }

    private void renderEmptyResult() {
        if (resultHand == null) return;
        resultHand.setText(arabic ? "جاهز للحساب" : "Ready to estimate");
        resultStage.setText("");
        resultEquity.setText("—");
        resultWin.setText(arabic ? "فوز\n—" : "WIN\n—");
        resultTie.setText(arabic ? "تعادل\n—" : "TIE\n—");
        resultLose.setText(arabic ? "خسارة\n—" : "LOSE\n—");
        resultDetail.setText(arabic ? "اضغط خانة بطاقة، ثم استخدم المسح أو اليدوي أو الدليل." : "Tap a card slot, then use Scan, Manual or Guide.");
        resultMeta.setText(arabic ? "الكاميرا تبقى متوقفة حتى تفتح شاشة المسح." : "Camera stays off until you open the scanner.");
        applyResultDirection();
    }

    private void renderResult(PokerMath.Result r, int opp) {
        int boardCount = table.boardCount();
        resultHand.setText(arabic ? handArabic(r.currentHand) : r.currentHand);
        resultStage.setText(stageText(boardCount));
        resultEquity.setText(String.format(Locale.US, "%.1f%%", r.equity));
        resultWin.setText(String.format(Locale.US, "%s\n%.1f%%", arabic ? "فوز" : "WIN", r.win));
        resultTie.setText(String.format(Locale.US, "%s\n%.1f%%", arabic ? "تعادل" : "TIE", r.tie));
        resultLose.setText(String.format(Locale.US, "%s\n%.1f%%", arabic ? "خسارة" : "LOSE", r.lose));
        if (boardCount >= 5) resultDetail.setText(arabic ? "لا توجد سحوبات متبقية." : "No remaining draws.");
        else {
            String draw = arabic ? drawArabic(r.draws) : r.draws;
            resultDetail.setText(arabic ? draw + " • بطاقات تحسين اليد " + r.outs : draw + " • hand-improving cards " + r.outs);
        }
        resultMeta.setText(arabic
                ? String.format(Locale.US, "ضد %d %s • %,d محاكاة", opp, opp == 1 ? "خصم" : "خصوم", r.simulations)
                : String.format(Locale.US, "%d opponent%s • %,d simulations", opp, opp == 1 ? "" : "s", r.simulations));
        applyResultDirection();
    }

    private String stageText(int n) {
        if (arabic) {
            if (n == 0) return "قبل الفلوب";
            if (n == 3) return "الفلوب";
            if (n == 4) return "التيرن";
            if (n >= 5) return "الريفر";
            return "طاولة جزئية";
        }
        if (n == 0) return "PRE-FLOP";
        if (n == 3) return "FLOP";
        if (n == 4) return "TURN";
        if (n >= 5) return "RIVER";
        return "PARTIAL";
    }

    private void applyResultDirection() {
        int td = arabic ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR;
        resultHand.setTextDirection(td); resultDetail.setTextDirection(td); resultMeta.setTextDirection(td);
        resultHand.setGravity(arabic ? Gravity.RIGHT : Gravity.LEFT);
        resultDetail.setGravity(arabic ? Gravity.RIGHT : Gravity.LEFT);
        resultMeta.setGravity(arabic ? Gravity.RIGHT : Gravity.LEFT);
    }

    private String handArabic(String s) {
        Map<String,String> m = new HashMap<>();
        m.put("Pre-flop", "قبل الفلوب"); m.put("High Card", "أعلى ورقة"); m.put("One Pair", "زوج"); m.put("Two Pair", "زوجان");
        m.put("Three of a Kind", "ثلاثة من نفس الرتبة"); m.put("Straight", "ستريت"); m.put("Flush", "فلاش");
        m.put("Full House", "فول هاوس"); m.put("Four of a Kind", "أربعة من نفس الرتبة"); m.put("Straight Flush", "ستريت فلاش");
        return m.getOrDefault(s, s);
    }

    private String drawArabic(String s) {
        return s.replace("Flush draw", "سحب فلاش").replace("Straight draw", "سحب ستريت")
                .replace("No major draw detected", "لا يوجد سحب رئيسي").replace("No board draw yet", "لا توجد أوراق طاولة كافية");
    }

    private void resetAll() {
        table.clearAll();
        if (candidates != null) candidates.removeAllViews();
        invalidateResult();
        refreshCards();
    }

    private void updateTexts() {
        if (language == null) return;
        dashboard.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        language.setText(arabic ? "English" : "عربي");
        modeHint.setText(arabic ? "اضغط أي خانة لاختيارها • ضغطة مطولة لمسح البطاقة." : "Tap any slot to select it • long-press a filled slot to clear.");
        modeHint.setTextDirection(arabic ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        modeHint.setGravity(arabic ? Gravity.RIGHT : Gravity.LEFT);
        scanButton.setText(arabic ? "مسح" : "Scan");
        manualButton.setText(arabic ? "يدوي" : "Manual");
        guideButton.setText(arabic ? "دليل" : "Guide");
        clearSelectedButton.setText(arabic ? "مسح الخانة" : "Clear slot");
        calculateButton.setText(arabic ? "احسب الاحتمال" : "Estimate equity");

        TextView mineLabel = dashboard.findViewWithTag("mineLabel");
        TextView boardLabel = dashboard.findViewWithTag("boardLabel");
        if (mineLabel != null) { mineLabel.setText(arabic ? "بطاقاتي" : "MY CARDS"); mineLabel.setGravity(arabic ? Gravity.RIGHT : Gravity.LEFT); }
        if (boardLabel != null) { boardLabel.setText(arabic ? "الطاولة" : "BOARD"); boardLabel.setGravity(arabic ? Gravity.RIGHT : Gravity.LEFT); }

        TextView scannerTitle = scanner == null ? null : scanner.findViewWithTag("scannerTitle");
        TextView scannerTip = scanner == null ? null : scanner.findViewWithTag("scannerTip");
        if (scannerTitle != null) scannerTitle.setText(arabic ? "ماسح البطاقات" : "CARD SCANNER");
        if (scannerTip != null) scannerTip.setText(arabic ? "وجّه الكاميرا إلى زاوية القيمة + النوع، ثم اضغط البطاقة المكتشفة." : "Aim at rank + suit corner, then tap the detected card.");
        updateOpponentText();
        refreshCards();
        if (lastResult == null) renderEmptyResult();
    }

    private void updateOpponentText() {
        if (opponentsText == null) return;
        opponentsText.setText(arabic ? opponents + (opponents == 1 ? " خصم" : " خصوم") : opponents + (opponents == 1 ? " opponent" : " opponents"));
    }

    @Override public void onRequestPermissionsResult(int req, @NonNull String[] permissions, @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, permissions, grants);
        if (req == CAMERA_REQ) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                if (scanner.getVisibility() == View.VISIBLE) startCamera();
            } else scanStatus.setText(arabic ? "تم رفض صلاحية الكاميرا. استخدم اليدوي أو الدليل." : "Camera permission denied. Use Manual or Guide.");
        }
    }

    @Override protected void onPause() { super.onPause(); if (cameraRunning) stopCamera(); }
    @Override protected void onResume() {
        super.onResume();
        if (scanner != null && scanner.getVisibility() == View.VISIBLE && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
    }
    @Override protected void onDestroy() {
        stopCamera();
        if (recognizer != null) recognizer.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        super.onDestroy();
    }

    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private TextView text(String s, float sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView statView() { TextView v = text("", 12, Color.WHITE, true); v.setGravity(Gravity.CENTER); v.setBackground(rounded(PANEL, 10)); return v; }
    private Button primaryButton(String s) { Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setTextSize(12); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor(BG); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(236,239,237))); b.setPadding(dp(5),0,dp(5),0); return b; }
    private Button compactButton(String s) { Button b = primaryButton(s); b.setTextSize(12); return b; }
    private ImageButton iconButton(int res, String desc) { ImageButton b = new ImageButton(this); b.setImageResource(res); b.setContentDescription(desc); b.setPadding(dp(10),dp(10),dp(10),dp(10)); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(236,239,237))); return b; }
    private Button squareButton(String s) { Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setTextSize(21); b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL2)); b.setPadding(0,0,0,0); return b; }
    private Button smallDarkButton(String s) { Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setTextSize(10); b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL2)); b.setPadding(dp(4),0,dp(4),0); return b; }
    private Button miniChoice(String s) { Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT_BOLD); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(2),0,dp(2),0); return b; }
    private Button cardCandidateButton(PokerMath.Card c) { Button b = new Button(this); b.setAllCaps(false); b.setText(c.pretty()); b.setTextSize(20); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor((c.suit==1||c.suit==2)?RED:Color.BLACK); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE)); b.setMinWidth(dp(64)); return b; }
    private Button dialogSegmentButton(String s) { Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(12); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL2)); return b; }
    private Button dialogPrimaryButton(String s) { Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setTextColor(BG); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextSize(11); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GOLD)); return b; }
    private Button dialogSecondaryButton(String s) { Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setTextColor(BG); b.setTextSize(11); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(230,234,232))); return b; }
    private GradientDrawable rounded(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private Space space(int n) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(dp(n), 1)); return s; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -1, 1f); }
    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p; }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private int BLACK() { return Color.rgb(18,22,20); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private String prettyList(List<PokerMath.Card> cards) { StringBuilder b = new StringBuilder(); for (PokerMath.Card c : cards) { if (b.length()>0) b.append("  "); b.append(c.pretty()); } return b.toString(); }
}
