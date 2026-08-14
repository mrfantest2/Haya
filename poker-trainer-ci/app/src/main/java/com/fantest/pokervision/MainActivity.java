package com.fantest.pokervision;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
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

public class MainActivity extends ComponentActivity {
    private static final int CAMERA_REQ = 41;
    private static final int BG = Color.rgb(4, 23, 17);
    private static final int PANEL = Color.rgb(11, 48, 35);
    private static final int PANEL_2 = Color.rgb(15, 61, 44);
    private static final int GOLD = Color.rgb(245, 200, 76);
    private static final int MUTED = Color.rgb(190, 205, 198);
    private static final int RED = Color.rgb(196, 42, 42);

    private final ArrayList<PokerMath.Card> hole = new ArrayList<>();
    private final ArrayList<PokerMath.Card> board = new ArrayList<>();

    private boolean targetHole = true;
    private boolean arabic = false;
    private int opponents = 1;

    private FrameLayout rootFrame;
    private LinearLayout dashboard;
    private FrameLayout scannerOverlay;
    private PreviewView previewView;
    private LinearLayout holeCardsRow;
    private LinearLayout boardCardsRow;
    private LinearLayout candidatesRow;

    private TextView scanStatus;
    private TextView resultText;
    private TextView opponentsText;
    private TextView targetLabel;
    private TextView modeHint;

    private Button targetHoleButton;
    private Button targetBoardButton;
    private Button languageButton;
    private Button scanButton;
    private Button manualButton;
    private Button calculateButton;

    private ExecutorService cameraExecutor;
    private TextRecognizer recognizer;
    private ProcessCameraProvider cameraProvider;
    private boolean cameraRunning = false;

    private final AtomicBoolean analyzing = new AtomicBoolean(false);
    private long lastAnalyzeMs = 0L;
    private String lastCandidateKey = "";
    private long lastCandidateMs = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, systemBars.bottom);
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
    }

    private LinearLayout buildDashboard() {
        LinearLayout screen = column();
        screen.setBackgroundColor(BG);
        screen.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("POKER VISION", 23, Color.WHITE, true);
        header.addView(title, weight());
        Button clearButton = compactButton("↺");
        clearButton.setTextSize(20);
        clearButton.setContentDescription("Reset cards");
        clearButton.setOnClickListener(v -> resetAll());
        header.addView(clearButton, new LinearLayout.LayoutParams(dp(46), dp(42)));
        header.addView(space(6));
        languageButton = compactButton("عربي");
        languageButton.setOnClickListener(v -> {
            arabic = !arabic;
            updateTexts();
        });
        header.addView(languageButton, new LinearLayout.LayoutParams(dp(82), dp(42)));
        screen.addView(header, lp(-1, dp(46), 0, 0, 0, 4));

        modeHint = text("Camera OFF • tap a selected card to remove it.", 12, MUTED, false);
        screen.addView(modeHint, lp(-1, dp(24), 0, 0, 0, 4));

        LinearLayout cardsPanel = column();
        cardsPanel.setPadding(dp(10), dp(8), dp(10), dp(8));
        cardsPanel.setBackground(rounded(PANEL, 14));

        LinearLayout holeLine = row();
        holeLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView mine = text("MY CARDS", 11, GOLD, true);
        mine.setTag("mineLabel");
        holeLine.addView(mine, new LinearLayout.LayoutParams(dp(72), -2));
        holeCardsRow = row();
        holeCardsRow.setGravity(Gravity.CENTER_VERTICAL);
        holeLine.addView(holeCardsRow, weight());
        cardsPanel.addView(holeLine, lp(-1, dp(64), 0, 0, 0, 4));

        LinearLayout boardLine = row();
        boardLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView boardLabel = text("BOARD", 11, GOLD, true);
        boardLabel.setTag("boardLabel");
        boardLine.addView(boardLabel, new LinearLayout.LayoutParams(dp(72), -2));
        boardCardsRow = row();
        boardCardsRow.setGravity(Gravity.CENTER_VERTICAL);
        boardLine.addView(boardCardsRow, weight());
        cardsPanel.addView(boardLine, lp(-1, dp(64), 0, 0, 0, 0));

        screen.addView(cardsPanel, lp(-1, dp(144), 0, 0, 0, 6));

        LinearLayout targetHeader = row();
        targetHeader.setGravity(Gravity.CENTER_VERTICAL);
        targetLabel = text("ADD TO", 11, MUTED, true);
        targetHeader.addView(targetLabel, weight());

        targetHoleButton = segmentButton("My 2 Cards");
        targetBoardButton = segmentButton("Board");
        targetHoleButton.setOnClickListener(v -> {
            targetHole = true;
            updateTargetButtons();
        });
        targetBoardButton.setOnClickListener(v -> {
            targetHole = false;
            updateTargetButtons();
        });
        targetHeader.addView(targetHoleButton, new LinearLayout.LayoutParams(dp(110), dp(38)));
        targetHeader.addView(space(6));
        targetHeader.addView(targetBoardButton, new LinearLayout.LayoutParams(dp(92), dp(38)));
        screen.addView(targetHeader, lp(-1, dp(42), 0, 0, 0, 6));

        LinearLayout actionRow = row();
        scanButton = primaryButton("📷  Scan cards");
        manualButton = primaryButton("🂠  Add manually");
        scanButton.setOnClickListener(v -> openScanner());
        manualButton.setOnClickListener(v -> showManualCardPicker());
        actionRow.addView(scanButton, weight());
        actionRow.addView(space(8));
        actionRow.addView(manualButton, weight());
        screen.addView(actionRow, lp(-1, dp(50), 0, 0, 0, 8));

        LinearLayout estimateBar = row();
        estimateBar.setGravity(Gravity.CENTER_VERTICAL);
        Button minus = squareButton("−");
        Button plus = squareButton("+");
        minus.setOnClickListener(v -> {
            if (opponents > 1) {
                opponents--;
                updateOpponentText();
            }
        });
        plus.setOnClickListener(v -> {
            if (opponents < 5) {
                opponents++;
                updateOpponentText();
            }
        });
        opponentsText = text("1 opponent", 14, Color.WHITE, true);
        opponentsText.setGravity(Gravity.CENTER);
        estimateBar.addView(minus, new LinearLayout.LayoutParams(dp(42), dp(42)));
        estimateBar.addView(opponentsText, new LinearLayout.LayoutParams(dp(104), dp(42)));
        estimateBar.addView(plus, new LinearLayout.LayoutParams(dp(42), dp(42)));
        estimateBar.addView(space(8));

        calculateButton = new Button(this);
        calculateButton.setAllCaps(false);
        calculateButton.setText("Estimate equity");
        calculateButton.setTextSize(15);
        calculateButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        calculateButton.setTextColor(BG);
        calculateButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GOLD));
        calculateButton.setOnClickListener(v -> calculate());
        estimateBar.addView(calculateButton, weight());
        screen.addView(estimateBar, lp(-1, dp(50), 0, 0, 0, 8));

        resultText = text("Add your 2 cards, then optionally add the board.", 15, Color.WHITE, false);
        resultText.setGravity(Gravity.CENTER_VERTICAL);
        resultText.setPadding(dp(14), dp(10), dp(14), dp(10));
        resultText.setBackground(rounded(PANEL_2, 14));
        resultText.setTextIsSelectable(true);
        LinearLayout.LayoutParams resultLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        resultLp.setMargins(0, 0, 0, 0);
        screen.addView(resultText, resultLp);

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

        Button scannerManual = compactButton("🂠");
        scannerManual.setTextSize(20);
        scannerManual.setOnClickListener(v -> showManualCardPicker());
        top.addView(scannerManual, new LinearLayout.LayoutParams(dp(52), dp(42)));

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, dp(58), Gravity.TOP);
        overlay.addView(top, topLp);

        LinearLayout bottom = column();
        bottom.setPadding(dp(12), dp(10), dp(12), dp(10));
        bottom.setBackgroundColor(0xE8041711);

        LinearLayout targetRow = row();
        targetRow.setGravity(Gravity.CENTER_VERTICAL);
        Button scanMine = segmentButton("My Cards");
        Button scanBoard = segmentButton("Board");
        scanMine.setTag("scanMine");
        scanBoard.setTag("scanBoard");
        scanMine.setOnClickListener(v -> {
            targetHole = true;
            updateTargetButtons();
            updateScannerTargetButtons(scanMine, scanBoard);
        });
        scanBoard.setOnClickListener(v -> {
            targetHole = false;
            updateTargetButtons();
            updateScannerTargetButtons(scanMine, scanBoard);
        });
        targetRow.addView(scanMine, weight());
        targetRow.addView(space(8));
        targetRow.addView(scanBoard, weight());
        bottom.addView(targetRow, lp(-1, dp(40), 0, 0, 0, 6));

        scanStatus = text("Camera is off", 13, GOLD, true);
        scanStatus.setGravity(Gravity.CENTER);
        bottom.addView(scanStatus, lp(-1, dp(44), 0, 0, 0, 4));

        candidatesRow = row();
        candidatesRow.setGravity(Gravity.CENTER);
        HorizontalScrollView candidateScroll = new HorizontalScrollView(this);
        candidateScroll.setHorizontalScrollBarEnabled(false);
        candidateScroll.addView(candidatesRow);
        bottom.addView(candidateScroll, lp(-1, dp(58), 0, 0, 0, 4));

        TextView tip = text("Aim at the printed rank + suit corner. Tap a detected card to add it.", 11, MUTED, false);
        tip.setGravity(Gravity.CENTER);
        tip.setTag("scannerTip");
        bottom.addView(tip, lp(-1, dp(34), 0, 0, 0, 0));

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, dp(196), Gravity.BOTTOM);
        overlay.addView(bottom, bottomLp);

        overlay.setTag(new Button[]{scanMine, scanBoard});
        return overlay;
    }

    private void openScanner() {
        scannerOverlay.setVisibility(View.VISIBLE);
        Button[] buttons = (Button[]) scannerOverlay.getTag();
        updateScannerTargetButtons(buttons[0], buttons[1]);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            scanStatus.setText(arabic ? "اسمح باستخدام الكاميرا للمسح." : "Camera permission is needed only while scanning.");
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
                        .setTargetResolution(new android.util.Size(1280, 720))
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                cameraRunning = true;

                runOnUiThread(() -> scanStatus.setText(
                        arabic ? "وجّه الكاميرا إلى زاوية البطاقة." : "Aim at a card corner. Detection runs only on this screen."
                ));
            } catch (Exception e) {
                cameraRunning = false;
                runOnUiThread(() -> scanStatus.setText("Camera error: " + e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void stopCamera() {
        cameraRunning = false;
        analyzing.set(false);
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    private void analyzeFrame(ImageProxy proxy) {
        if (!cameraRunning) {
            proxy.close();
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastAnalyzeMs < 450 || !analyzing.compareAndSet(false, true)) {
            proxy.close();
            return;
        }
        lastAnalyzeMs = now;

        if (proxy.getImage() == null) {
            analyzing.set(false);
            proxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                proxy.getImage(),
                proxy.getImageInfo().getRotationDegrees()
        );

        recognizer.process(image)
                .addOnSuccessListener(text -> onRecognized(CardRecognizer.extract(text), text))
                .addOnFailureListener(e -> runOnUiThread(() ->
                        scanStatus.setText("OCR: " + e.getClass().getSimpleName())))
                .addOnCompleteListener(t -> {
                    analyzing.set(false);
                    proxy.close();
                });
    }

    private void onRecognized(List<PokerMath.Card> cards, Text raw) {
        runOnUiThread(() -> {
            if (scannerOverlay.getVisibility() != View.VISIBLE) return;

            if (!cards.isEmpty()) {
                StringBuilder key = new StringBuilder();
                for (PokerMath.Card c : cards) key.append(c.code()).append(',');

                if (!key.toString().equals(lastCandidateKey)
                        || SystemClock.elapsedRealtime() - lastCandidateMs > 1200) {
                    lastCandidateKey = key.toString();
                    lastCandidateMs = SystemClock.elapsedRealtime();
                    showCandidates(cards);
                }

                scanStatus.setText((arabic ? "تم اكتشاف: " : "Detected: ") + prettyList(cards));
            } else {
                scanStatus.setText(arabic
                        ? "لم يتم التعرف بعد. قرّب زاوية البطاقة."
                        : "No card yet — move closer to the rank + suit corner."
                );
            }
        });
    }

    private void showCandidates(List<PokerMath.Card> cards) {
        candidatesRow.removeAllViews();
        for (PokerMath.Card c : cards) {
            Button b = cardCandidateButton(c);
            b.setOnClickListener(v -> {
                if (addCard(c) && targetHole && hole.size() >= 2) {
                    targetHole = false;
                    updateTargetButtons();
                    Button[] scanButtons = (Button[]) scannerOverlay.getTag();
                    updateScannerTargetButtons(scanButtons[0], scanButtons[1]);
                }
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
        targetRow.addView(mine, weight());
        targetRow.addView(space(8));
        targetRow.addView(table, weight());
        box.addView(targetRow, lp(-1, dp(44), 0, 0, 0, 10));

        TextView helper = text(
                arabic ? "اختر البطاقة من القائمة مع رمز النوع الصحيح." : "Choose the exact card. Suit icons are shown in the picker.",
                12, Color.DKGRAY, false
        );
        box.addView(helper, lp(-1, -2, 0, 0, 0, 8));

        Spinner cardSpinner = new Spinner(this);
        CardOptionAdapter adapter = new CardOptionAdapter(buildCardOptions());
        cardSpinner.setAdapter(adapter);
        box.addView(cardSpinner, lp(-1, dp(58), 0, 0, 0, 12));

        TextView preview = text("A♠", 36, Color.BLACK, true);
        preview.setGravity(Gravity.CENTER);
        preview.setBackground(rounded(Color.rgb(248, 248, 248), 12));
        box.addView(preview, lp(-1, dp(74), 0, 0, 0, 12));

        Runnable refreshDialogTarget = () -> {
            mine.setAlpha(targetHole ? 1f : .55f);
            table.setAlpha(targetHole ? .55f : 1f);
        };

        mine.setOnClickListener(v -> {
            targetHole = true;
            updateTargetButtons();
            refreshDialogTarget.run();
        });
        table.setOnClickListener(v -> {
            targetHole = false;
            updateTargetButtons();
            refreshDialogTarget.run();
        });
        refreshDialogTarget.run();

        cardSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                CardOption opt = adapter.getItem(position);
                if (opt != null) {
                    preview.setText(opt.card.pretty());
                    preview.setTextColor(isRedSuit(opt.card) ? RED : Color.BLACK);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        LinearLayout buttons = row();
        Button done = dialogSecondaryButton(arabic ? "تم" : "Done");
        Button add = dialogPrimaryButton(arabic ? "إضافة البطاقة" : "Add card");
        done.setOnClickListener(v -> dialog.dismiss());
        add.setOnClickListener(v -> {
            CardOption option = (CardOption) cardSpinner.getSelectedItem();
            if (option == null) return;
            boolean added = addCard(option.card);
            if (added) {
                if (targetHole && hole.size() >= 2) {
                    targetHole = false;
                    updateTargetButtons();
                    refreshDialogTarget.run();
                }
                toast(arabic ? "تمت الإضافة" : option.card.pretty() + " added");
            }
        });
        buttons.addView(done, weight());
        buttons.addView(space(8));
        buttons.addView(add, weight());
        box.addView(buttons, lp(-1, dp(48), 0, 0, 0, 0));

        dialog.setContentView(box);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                WindowManager.LayoutParams p = new WindowManager.LayoutParams();
                p.copyFrom(window.getAttributes());
                p.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(520));
                p.height = WindowManager.LayoutParams.WRAP_CONTENT;
                window.setAttributes(p);
            }
        });
        dialog.show();
    }

    private ArrayList<CardOption> buildCardOptions() {
        ArrayList<CardOption> options = new ArrayList<>();
        int[] ranks = {14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2};
        String[] rankNames = {
                "Ace", "King", "Queen", "Jack", "Ten", "Nine", "Eight",
                "Seven", "Six", "Five", "Four", "Three", "Two"
        };
        String[] suitNames = {"Spades", "Hearts", "Diamonds", "Clubs"};

        for (int i = 0; i < ranks.length; i++) {
            for (int suit = 0; suit < 4; suit++) {
                PokerMath.Card card = new PokerMath.Card(ranks[i], suit);
                String label = card.pretty() + "   " + rankNames[i] + " of " + suitNames[suit];
                options.add(new CardOption(card, label));
            }
        }
        return options;
    }

    private boolean addCard(PokerMath.Card c) {
        if (contains(c)) {
            toast(arabic ? "هذه البطاقة موجودة بالفعل" : "That card is already selected");
            return false;
        }

        if (targetHole) {
            if (hole.size() >= 2) {
                toast(arabic ? "لديك بطاقتان بالفعل" : "Your two-card hand is already full");
                return false;
            }
            hole.add(c);
        } else {
            if (board.size() >= 5) {
                toast(arabic ? "الطاولة تحتوي خمس بطاقات" : "The board is already full");
                return false;
            }
            board.add(c);
        }

        refreshCards();
        return true;
    }

    private void removeCard(boolean fromHole, PokerMath.Card card) {
        if (fromHole) hole.remove(card);
        else board.remove(card);
        refreshCards();
    }

    private boolean contains(PokerMath.Card c) {
        return hole.contains(c) || board.contains(c);
    }

    private void resetAll() {
        hole.clear();
        board.clear();
        if (candidatesRow != null) candidatesRow.removeAllViews();
        resultText.setText(arabic
                ? "أضف بطاقتيك أولاً، ثم بطاقات الطاولة إن وجدت."
                : "Add your 2 cards, then optionally add the board.");
        refreshCards();
    }

    private void refreshCards() {
        if (holeCardsRow == null || boardCardsRow == null) return;
        renderSlots(holeCardsRow, hole, 2, true);
        renderSlots(boardCardsRow, board, 5, false);
    }

    private void renderSlots(LinearLayout row, List<PokerMath.Card> cards, int total, boolean fromHole) {
        row.removeAllViews();
        for (int i = 0; i < total; i++) {
            if (i < cards.size()) {
                PokerMath.Card card = cards.get(i);
                TextView chip = cardView(card);
                chip.setOnClickListener(v -> removeCard(fromHole, card));
                row.addView(chip);
            } else {
                row.addView(emptyCardView());
            }
            if (i < total - 1) row.addView(space(4));
        }
    }

    private void calculate() {
        if (hole.size() != 2) {
            toast(arabic ? "أضف بطاقتين لك أولاً" : "Add exactly two hole cards first");
            return;
        }

        calculateButton.setEnabled(false);
        calculateButton.setText(arabic ? "جاري الحساب…" : "Calculating…");
        resultText.setText(arabic ? "حساب الاحتمالات…" : "Running 8,000 offline simulations…");

        ArrayList<PokerMath.Card> h = new ArrayList<>(hole);
        ArrayList<PokerMath.Card> b = new ArrayList<>(board);
        int opp = opponents;

        new Thread(() -> {
            try {
                PokerMath.Result r = PokerMath.calculate(h, b, opp, 8000, System.nanoTime());
                runOnUiThread(() -> {
                    resultText.setText(formatResult(r, opp));
                    calculateButton.setEnabled(true);
                    calculateButton.setText(arabic ? "احسب الاحتمال" : "Estimate equity");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    resultText.setText("Error: " + e.getMessage());
                    calculateButton.setEnabled(true);
                    calculateButton.setText(arabic ? "احسب الاحتمال" : "Estimate equity");
                });
            }
        }).start();
    }

    private String formatResult(PokerMath.Result r, int opp) {
        if (arabic) {
            return String.format(
                    Locale.US,
                    "%s\n\nفوز %.1f%%   •   تعادل %.1f%%   •   خسارة %.1f%%\nEquity %.1f%%\n\nالسحوبات: %s\nOuts: %d   •   ضد %d خصم/خصوم",
                    handArabic(r.currentHand),
                    r.win, r.tie, r.lose, r.equity,
                    drawArabic(r.draws), r.outs, opp
            );
        }

        return String.format(
                Locale.US,
                "%s\n\nWIN %.1f%%   •   TIE %.1f%%   •   LOSE %.1f%%\nEQUITY %.1f%%\n\nDraws: %s\nOuts: %d   •   %d opponent%s",
                r.currentHand,
                r.win, r.tie, r.lose, r.equity,
                r.draws, r.outs, opp, opp == 1 ? "" : "s"
        );
    }

    private String handArabic(String s) {
        Map<String, String> m = new HashMap<>();
        m.put("Pre-flop", "قبل الفلوب");
        m.put("High Card", "أعلى ورقة");
        m.put("One Pair", "زوج");
        m.put("Two Pair", "زوجان");
        m.put("Three of a Kind", "ثلاثة من نفس الرتبة");
        m.put("Straight", "ستريت");
        m.put("Flush", "فلاش");
        m.put("Full House", "فول هاوس");
        m.put("Four of a Kind", "أربعة من نفس الرتبة");
        m.put("Straight Flush", "ستريت فلاش");
        return m.getOrDefault(s, s);
    }

    private String drawArabic(String s) {
        return s.replace("Flush draw", "سحب فلاش")
                .replace("Straight draw", "سحب ستريت")
                .replace("No major draw detected", "لا يوجد سحب رئيسي")
                .replace("No board draw yet", "لا توجد أوراق طاولة كافية");
    }

    private void updateTexts() {
        if (languageButton == null) return;

        languageButton.setText(arabic ? "English" : "عربي");
        modeHint.setText(arabic
                ? "الكاميرا متوقفة • اضغط البطاقة المحددة لحذفها."
                : "Camera OFF • tap a selected card to remove it.");

        targetLabel.setText(arabic ? "الإضافة إلى" : "ADD TO");
        targetHoleButton.setText(arabic ? "بطاقاتي (2)" : "My 2 Cards");
        targetBoardButton.setText(arabic ? "الطاولة" : "Board");

        scanButton.setText(arabic ? "📷  مسح البطاقات" : "📷  Scan cards");
        manualButton.setText(arabic ? "🂠  إضافة يدويًا" : "🂠  Add manually");
        calculateButton.setText(arabic ? "احسب الاحتمال" : "Estimate equity");

        TextView mineLabel = dashboard.findViewWithTag("mineLabel");
        TextView boardLabel = dashboard.findViewWithTag("boardLabel");
        if (mineLabel != null) mineLabel.setText(arabic ? "بطاقاتي" : "MY CARDS");
        if (boardLabel != null) boardLabel.setText(arabic ? "الطاولة" : "BOARD");

        TextView scannerTitle = scannerOverlay == null ? null : scannerOverlay.findViewWithTag("scannerTitle");
        TextView scannerTip = scannerOverlay == null ? null : scannerOverlay.findViewWithTag("scannerTip");
        Button scannerMine = scannerOverlay == null ? null : scannerOverlay.findViewWithTag("scanMine");
        Button scannerBoard = scannerOverlay == null ? null : scannerOverlay.findViewWithTag("scanBoard");

        if (scannerTitle != null) scannerTitle.setText(arabic ? "ماسح البطاقات" : "CARD SCANNER");
        if (scannerTip != null) scannerTip.setText(arabic
                ? "وجّه الكاميرا إلى زاوية الرقم + النوع، ثم اضغط البطاقة المكتشفة."
                : "Aim at the printed rank + suit corner. Tap a detected card to add it.");
        if (scannerMine != null) scannerMine.setText(arabic ? "بطاقاتي" : "My Cards");
        if (scannerBoard != null) scannerBoard.setText(arabic ? "الطاولة" : "Board");

        updateOpponentText();
        updateTargetButtons();
        refreshCards();
    }

    private void updateOpponentText() {
        if (opponentsText == null) return;
        if (arabic) {
            opponentsText.setText(opponents + (opponents == 1 ? " خصم" : " خصوم"));
        } else {
            opponentsText.setText(opponents + (opponents == 1 ? " opponent" : " opponents"));
        }
    }

    private void updateTargetButtons() {
        if (targetHoleButton == null) return;
        targetHoleButton.setAlpha(targetHole ? 1f : .50f);
        targetBoardButton.setAlpha(targetHole ? .50f : 1f);
    }

    private void updateScannerTargetButtons(Button mine, Button boardButton) {
        if (mine == null || boardButton == null) return;
        mine.setAlpha(targetHole ? 1f : .50f);
        boardButton.setAlpha(targetHole ? .50f : 1f);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_REQ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (scannerOverlay.getVisibility() == View.VISIBLE) startCamera();
            } else {
                scanStatus.setText(arabic
                        ? "تم رفض صلاحية الكاميرا. استخدم الإضافة اليدوية."
                        : "Camera permission denied. Use Add manually instead.");
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraRunning) stopCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scannerOverlay != null
                && scannerOverlay.getVisibility() == View.VISIBLE
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @Override
    protected void onDestroy() {
        stopCamera();
        if (recognizer != null) recognizer.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        super.onDestroy();
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button primaryButton(String s) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setTextSize(14);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setTextColor(BG);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(236, 239, 237)));
        return b;
    }

    private Button compactButton(String s) {
        Button b = primaryButton(s);
        b.setTextSize(13);
        b.setPadding(dp(6), 0, dp(6), 0);
        return b;
    }

    private Button segmentButton(String s) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL_2));
        b.setPadding(dp(4), 0, dp(4), 0);
        return b;
    }

    private Button squareButton(String s) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setTextSize(22);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL_2));
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private Button cardCandidateButton(PokerMath.Card c) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(c.pretty());
        b.setTextSize(20);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setTextColor(isRedSuit(c) ? RED : Color.BLACK);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        b.setMinWidth(dp(64));
        return b;
    }

    private TextView cardView(PokerMath.Card card) {
        TextView v = text(card.pretty(), 18, isRedSuit(card) ? RED : Color.BLACK, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(rounded(Color.WHITE, 8));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44), dp(58));
        v.setLayoutParams(p);
        return v;
    }

    private TextView emptyCardView() {
        TextView v = text("—", 18, Color.rgb(135, 145, 140), true);
        v.setGravity(Gravity.CENTER);
        GradientDrawable g = rounded(Color.rgb(225, 231, 228), 8);
        g.setStroke(dp(1), Color.rgb(160, 170, 165));
        v.setBackground(g);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(58)));
        return v;
    }

    private boolean isRedSuit(PokerMath.Card card) {
        return card.suit == 1 || card.suit == 2;
    }

    private Button dialogSegmentButton(String s) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL_2));
        return b;
    }

    private Button dialogPrimaryButton(String s) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setTextColor(BG);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GOLD));
        return b;
    }

    private Button dialogSecondaryButton(String s) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setTextColor(BG);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(230, 234, 232)));
        return b;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private Space space(int dpValue) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(dp(dpValue), 1));
        return s;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -1, 1f);
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private String prettyList(List<PokerMath.Card> cards) {
        StringBuilder b = new StringBuilder();
        for (PokerMath.Card c : cards) {
            if (b.length() > 0) b.append("  ");
            b.append(c.pretty());
        }
        return b.toString();
    }

    private static final class CardOption {
        final PokerMath.Card card;
        final String label;

        CardOption(PokerMath.Card card, String label) {
            this.card = card;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final class CardOptionAdapter extends ArrayAdapter<CardOption> {
        CardOptionAdapter(List<CardOption> options) {
            super(MainActivity.this, android.R.layout.simple_spinner_item, options);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            return styleOptionView(super.getView(position, convertView, parent), position, false);
        }

        @Override
        public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
            return styleOptionView(super.getDropDownView(position, convertView, parent), position, true);
        }

        private View styleOptionView(View view, int position, boolean dropdown) {
            TextView text = (TextView) view;
            CardOption option = getItem(position);
            text.setTextSize(dropdown ? 18 : 19);
            text.setGravity(Gravity.CENTER_VERTICAL);
            text.setPadding(dp(14), dp(dropdown ? 12 : 8), dp(14), dp(dropdown ? 12 : 8));
            text.setBackgroundColor(Color.WHITE);
            text.setTextColor(option != null && isRedSuit(option.card) ? RED : Color.BLACK);
            return text;
        }
    }
}
