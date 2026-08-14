package com.fantest.pokervision;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends ComponentActivity {
    private static final int CAMERA_REQ = 41;
    private static final int BG = Color.rgb(4, 23, 17);
    private static final int PANEL = Color.rgb(11, 48, 35);
    private static final int GOLD = Color.rgb(245, 200, 76);
    private static final int MUTED = Color.rgb(190, 205, 198);

    private final ArrayList<PokerMath.Card> hole = new ArrayList<>();
    private final ArrayList<PokerMath.Card> board = new ArrayList<>();
    private boolean targetHole = true;
    private boolean arabic = false;

    private PreviewView previewView;
    private TextView scanStatus, holeText, boardText, resultText, opponentsText, targetLabel;
    private LinearLayout candidatesRow;
    private Spinner rankSpinner, suitSpinner;
    private SeekBar opponentsSeek;
    private Button targetHoleButton, targetBoardButton, languageButton;

    private ExecutorService cameraExecutor;
    private TextRecognizer recognizer;
    private final AtomicBoolean analyzing = new AtomicBoolean(false);
    private long lastAnalyzeMs = 0L;
    private String lastCandidateKey = "";
    private long lastCandidateMs = 0L;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        cameraExecutor = Executors.newSingleThreadExecutor();
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        setContentView(buildUi());
        updateTexts();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQ);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column(); root.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = row();
        TextView title = text("POKER VISION", 26, Color.WHITE, true); header.addView(title, weight());
        languageButton = button("عربي"); languageButton.setOnClickListener(v -> { arabic = !arabic; updateTexts(); });
        header.addView(languageButton);
        root.addView(header);
        TextView subtitle = text("Live card scan + offline equity estimate", 14, MUTED, false); subtitle.setTag("subtitle"); root.addView(subtitle, lp(-1, -2, 0, 4, 0, 14));

        previewView = new PreviewView(this); previewView.setBackgroundColor(Color.BLACK); previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, lp(-1, dp(300), 0, 0, 0, 8));
        scanStatus = text("Camera starting…", 14, GOLD, true); root.addView(scanStatus, lp(-1,-2,0,0,0,10));

        targetLabel = text("SCAN TARGET", 12, MUTED, true); root.addView(targetLabel);
        LinearLayout targetRow = row();
        targetHoleButton = button("My 2 Cards"); targetBoardButton = button("Table / Board");
        targetHoleButton.setOnClickListener(v -> { targetHole=true; updateTargetButtons(); });
        targetBoardButton.setOnClickListener(v -> { targetHole=false; updateTargetButtons(); });
        targetRow.addView(targetHoleButton, weight()); targetRow.addView(space(8)); targetRow.addView(targetBoardButton, weight());
        root.addView(targetRow, lp(-1,-2,0,4,0,10));

        candidatesRow = row(); candidatesRow.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false); hsv.addView(candidatesRow);
        root.addView(hsv, lp(-1,-2,0,0,0,12));

        root.addView(section("DETECTED CARDS"));
        holeText = text("My cards: — —", 22, Color.WHITE, true); root.addView(cardPanel(holeText));
        boardText = text("Board: — — — — —", 21, Color.WHITE, true); root.addView(cardPanel(boardText));

        root.addView(section("MANUAL CONFIRM / CORRECT"));
        LinearLayout manual = row();
        rankSpinner = spinner(new String[]{"A","K","Q","J","10","9","8","7","6","5","4","3","2"});
        suitSpinner = spinner(new String[]{"♠ Spades","♥ Hearts","♦ Diamonds","♣ Clubs"});
        manual.addView(rankSpinner, weight()); manual.addView(space(8)); manual.addView(suitSpinner, weight());
        Button add = button("Add"); add.setOnClickListener(v -> addManual()); manual.addView(space(8)); manual.addView(add);
        root.addView(manual, lp(-1,-2,0,0,0,8));
        LinearLayout correction = row();
        Button undo = button("Undo last"); undo.setOnClickListener(v -> undoLast());
        Button reset = button("Reset all"); reset.setOnClickListener(v -> resetAll());
        correction.addView(undo, weight()); correction.addView(space(8)); correction.addView(reset, weight());
        root.addView(correction, lp(-1,-2,0,0,0,14));

        root.addView(section("EQUITY ESTIMATE"));
        opponentsText = text("Opponents: 1",16,Color.WHITE,true); root.addView(opponentsText);
        opponentsSeek = new SeekBar(this); opponentsSeek.setMax(4); opponentsSeek.setProgress(0);
        opponentsSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){updateOpponentText();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        root.addView(opponentsSeek);
        Button calculate = button("CALCULATE 8,000 SIMULATIONS"); calculate.setTextColor(BG); calculate.setBackgroundColor(GOLD); calculate.setOnClickListener(v -> calculate());
        root.addView(calculate, lp(-1,dp(52),0,8,0,12));
        resultText = text("Scan or enter exactly two hole cards, then add 0–5 board cards.", 16, Color.WHITE, false);
        resultText.setPadding(dp(14),dp(14),dp(14),dp(14)); resultText.setBackgroundColor(PANEL); root.addView(resultText, lp(-1,-2,0,0,0,12));
        TextView note = text("Estimate assumes unknown opponents hold random legal cards. Camera recognition is best-effort; verify every detected card before relying on the result.", 12, MUTED, false); note.setTag("note"); root.addView(note);
        updateTargetButtons();
        return scroll;
    }

    private void startCamera() {
        scanStatus.setText(arabic ? "جاري تشغيل الكاميرا…" : "Starting camera…");
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new android.util.Size(1280,720)).build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                runOnUiThread(() -> scanStatus.setText(arabic ? "وجّه الكاميرا نحو زاوية البطاقة (الرقم + النوع)" : "Aim at card corners (rank + suit). Tap a detected card to add it."));
            } catch (Exception e) {
                runOnUiThread(() -> scanStatus.setText("Camera error: " + e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(ImageProxy proxy) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastAnalyzeMs < 450 || !analyzing.compareAndSet(false,true)) { proxy.close(); return; }
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
            if (!cards.isEmpty()) {
                StringBuilder key = new StringBuilder(); for (PokerMath.Card c:cards) key.append(c.code()).append(',');
                if (!key.toString().equals(lastCandidateKey) || SystemClock.elapsedRealtime()-lastCandidateMs>1200) {
                    lastCandidateKey=key.toString(); lastCandidateMs=SystemClock.elapsedRealtime(); showCandidates(cards);
                }
                scanStatus.setText((arabic ? "تم اكتشاف: " : "Detected: ") + prettyList(cards));
            } else {
                String t=raw==null?"":raw.getText().replace('\n',' ').trim();
                if(t.length()>36)t=t.substring(0,36)+"…";
                scanStatus.setText(arabic ? "لم أجد رقم + نوع واضح. قرّب زاوية البطاقة أو استخدم الإدخال اليدوي." : "No clear rank+suit yet. Move closer to the card corner or use manual correction." + (t.isEmpty()?"":"  OCR: "+t));
            }
        });
    }

    private void showCandidates(List<PokerMath.Card> cards) {
        candidatesRow.removeAllViews();
        for (PokerMath.Card c : cards) {
            Button b=button("+ " + c.pretty()); b.setTextSize(18); b.setOnClickListener(v -> addCard(c));
            candidatesRow.addView(b); candidatesRow.addView(space(6));
        }
    }

    private void addManual() {
        int r=PokerMath.rankFromText(String.valueOf(rankSpinner.getSelectedItem()));
        int s=suitSpinner.getSelectedItemPosition(); addCard(new PokerMath.Card(r,s));
    }

    private void addCard(PokerMath.Card c) {
        if (contains(c)) { toast(arabic?"هذه البطاقة موجودة بالفعل":"That card is already selected"); return; }
        if (targetHole) {
            if(hole.size()>=2){toast(arabic?"لديك بطاقتان بالفعل":"Hole cards are full");return;} hole.add(c);
        } else {
            if(board.size()>=5){toast(arabic?"الطاولة تحتوي خمس بطاقات":"Board is full");return;} board.add(c);
        }
        refreshCards();
    }

    private boolean contains(PokerMath.Card c){return hole.contains(c)||board.contains(c);}
    private void undoLast(){if(targetHole&&!hole.isEmpty())hole.remove(hole.size()-1);else if(!targetHole&&!board.isEmpty())board.remove(board.size()-1);else if(!board.isEmpty())board.remove(board.size()-1);else if(!hole.isEmpty())hole.remove(hole.size()-1);refreshCards();}
    private void resetAll(){hole.clear();board.clear();candidatesRow.removeAllViews();refreshCards();resultText.setText(arabic?"ابدأ بمسح بطاقتيك.":"Scan or enter exactly two hole cards, then add 0–5 board cards.");}

    private void refreshCards(){
        holeText.setText((arabic?"بطاقاتي: ":"My cards: ") + slots(hole,2));
        boardText.setText((arabic?"الطاولة: ":"Board: ") + slots(board,5));
    }
    private String slots(List<PokerMath.Card> cards,int n){StringBuilder s=new StringBuilder();for(int i=0;i<n;i++){if(i>0)s.append("   ");s.append(i<cards.size()?cards.get(i).pretty():"—");}return s.toString();}

    private void calculate(){
        if(hole.size()!=2){toast(arabic?"يلزم تحديد بطاقتين لك":"Select exactly two hole cards");return;}
        int opponents=opponentsSeek.getProgress()+1;
        resultText.setText(arabic?"جاري الحساب…":"Calculating…");
        ArrayList<PokerMath.Card> h=new ArrayList<>(hole), b=new ArrayList<>(board);
        new Thread(() -> {
            try {
                PokerMath.Result r=PokerMath.calculate(h,b,opponents,8000,System.nanoTime());
                runOnUiThread(() -> resultText.setText(formatResult(r,opponents)));
            } catch(Exception e){runOnUiThread(() -> resultText.setText("Error: "+e.getMessage()));}
        }).start();
    }

    private String formatResult(PokerMath.Result r,int opponents){
        if(arabic) return String.format(Locale.US,
                "اليد الحالية: %s\n\nالفوز: %.1f%%\nالتعادل: %.1f%%\nالخسارة: %.1f%%\n\nEquity: %.1f%%\nالفرص لتحسين فئة اليد: %d\nالسحوبات: %s\n\nضد %d خصم/خصوم عشوائيين — %,d محاكاة.",
                handArabic(r.currentHand),r.win,r.tie,r.lose,r.equity,r.outs,drawArabic(r.draws),opponents,r.simulations);
        return String.format(Locale.US,
                "Current hand: %s\n\nWIN   %.1f%%\nTIE     %.1f%%\nLOSE  %.1f%%\n\nEQUITY  %.1f%%\nHand-category improving outs: %d\nDraws: %s\n\nAgainst %d random opponent(s) — %,d Monte Carlo simulations.",
                r.currentHand,r.win,r.tie,r.lose,r.equity,r.outs,r.draws,opponents,r.simulations);
    }

    private String handArabic(String s){Map<String,String>m=new HashMap<>();m.put("Pre-flop","قبل الفلوب");m.put("High Card","أعلى ورقة");m.put("One Pair","زوج");m.put("Two Pair","زوجان");m.put("Three of a Kind","ثلاثة من نفس الرتبة");m.put("Straight","ستريت");m.put("Flush","فلاش");m.put("Full House","فول هاوس");m.put("Four of a Kind","أربعة من نفس الرتبة");m.put("Straight Flush","ستريت فلاش");return m.getOrDefault(s,s);}
    private String drawArabic(String s){return s.replace("Flush draw","سحب فلاش").replace("Straight draw","سحب ستريت").replace("No major draw detected","لا يوجد سحب رئيسي").replace("No board draw yet","لا توجد أوراق طاولة كافية");}

    private void updateTexts(){
        languageButton.setText(arabic?"English":"عربي");
        targetLabel.setText(arabic?"اختر مكان البطاقات":"SCAN TARGET");
        targetHoleButton.setText(arabic?"بطاقاتي (2)":"My 2 Cards");
        targetBoardButton.setText(arabic?"بطاقات الطاولة":"Table / Board");
        updateOpponentText(); refreshCards(); updateTargetButtons();
    }
    private void updateOpponentText(){if(opponentsText!=null)opponentsText.setText((arabic?"عدد الخصوم: ":"Opponents: ")+(opponentsSeek==null?1:opponentsSeek.getProgress()+1));}
    private void updateTargetButtons(){if(targetHoleButton==null)return;targetHoleButton.setAlpha(targetHole?1f:.55f);targetBoardButton.setAlpha(targetHole?.55f:1f);}

    @Override public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==CAMERA_REQ&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)startCamera();else scanStatus.setText(arabic?"صلاحية الكاميرا مطلوبة. يمكنك استخدام الإدخال اليدوي.":"Camera permission denied. Manual entry still works.");}
    @Override protected void onDestroy(){super.onDestroy();if(recognizer!=null)recognizer.close();if(cameraExecutor!=null)cameraExecutor.shutdown();}

    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView text(String s,float sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return v;}
    private TextView section(String s){TextView v=text(s,12,GOLD,true);v.setPadding(0,dp(10),0,dp(6));return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private View cardPanel(TextView t){LinearLayout p=column();p.setBackgroundColor(PANEL);p.setPadding(dp(14),dp(14),dp(14),dp(14));p.addView(t);p.setLayoutParams(lp(-1,-2,0,0,0,8));return p;}
    private Spinner spinner(String[] vals){Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,vals);s.setAdapter(a);return s;}
    private Space space(int dp){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(dp(dp),1));return s;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1f);}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private String prettyList(List<PokerMath.Card> cs){StringBuilder b=new StringBuilder();for(PokerMath.Card c:cs){if(b.length()>0)b.append("  ");b.append(c.pretty());}return b.toString();}
}
