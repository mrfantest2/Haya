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
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

public class VisionActivityV023 extends ComponentActivity {
    private static final int CAMERA_REQ = 41;
    private static final int BG = Color.rgb(4,23,17);
    private static final int PANEL = Color.rgb(11,48,35);
    private static final int PANEL2 = Color.rgb(15,61,44);
    private static final int GOLD = Color.rgb(245,200,76);
    private static final int MUTED = Color.rgb(190,205,198);
    private static final int RED = Color.rgb(196,42,42);
    private static final int SIMULATIONS = 25000;

    private final ArrayList<PokerMath.Card> hole = new ArrayList<>();
    private final ArrayList<PokerMath.Card> board = new ArrayList<>();
    private boolean targetHole = true, arabic = false, cameraRunning = false;
    private int opponents = 1;

    private FrameLayout root, scanner;
    private LinearLayout dashboard, holeRow, boardRow, candidates;
    private PreviewView previewView;
    private TextView modeHint, targetLabel, opponentsText, scanStatus;
    private TextView resultHand, resultStage, resultEquity, resultWin, resultTie, resultLose, resultDetail, resultMeta;
    private Button myTarget, boardTarget, language, scanButton, manualButton, calculateButton;

    private ExecutorService cameraExecutor;
    private TextRecognizer recognizer;
    private ProcessCameraProvider cameraProvider;
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
        ViewCompat.setOnApplyWindowInsetsListener(root, (v,insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0,i.top,0,i.bottom);
            return insets;
        });

        dashboard = buildDashboard();
        root.addView(dashboard, new FrameLayout.LayoutParams(-1,-1));
        scanner = buildScanner();
        scanner.setVisibility(View.GONE);
        root.addView(scanner, new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);

        updateTexts();
        refreshCards();
        renderEmptyResult();
    }

    private LinearLayout buildDashboard() {
        LinearLayout screen = column();
        screen.setBackgroundColor(BG);
        screen.setPadding(dp(12),dp(6),dp(12),dp(10));
        screen.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LinearLayout header = row();
        header.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("POKER VISION",23,Color.WHITE,true);
        title.setTextDirection(View.TEXT_DIRECTION_LTR);
        title.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        header.addView(title, weight());

        ImageButton reset = iconButton(R.drawable.ic_reset,"Reset cards");
        reset.setOnClickListener(v -> resetAll());
        header.addView(reset, new LinearLayout.LayoutParams(dp(46),dp(42)));
        header.addView(space(6));

        language = compactButton("عربي");
        language.setOnClickListener(v -> {
            arabic = !arabic;
            updateTexts();
            if (lastResult != null) renderResult(lastResult,lastResultOpponents);
        });
        header.addView(language,new LinearLayout.LayoutParams(dp(86),dp(42)));
        screen.addView(header,lp(-1,dp(46),0,0,0,2));

        modeHint = text("",11,MUTED,false);
        screen.addView(modeHint,lp(-1,dp(22),0,0,0,4));

        LinearLayout cardsPanel = column();
        cardsPanel.setPadding(dp(10),dp(7),dp(10),dp(8));
        cardsPanel.setBackground(rounded(PANEL,14));

        TextView mineLabel = text("",10,GOLD,true); mineLabel.setTag("mineLabel");
        cardsPanel.addView(mineLabel,lp(-1,dp(18),0,0,0,2));
        holeRow = row(); holeRow.setGravity(Gravity.CENTER); holeRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        cardsPanel.addView(holeRow,lp(-1,dp(58),0,0,0,4));

        TextView boardLabel = text("",10,GOLD,true); boardLabel.setTag("boardLabel");
        cardsPanel.addView(boardLabel,lp(-1,dp(18),0,0,0,2));
        boardRow = row(); boardRow.setGravity(Gravity.CENTER); boardRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        cardsPanel.addView(boardRow,lp(-1,dp(58),0,0,0,0));
        screen.addView(cardsPanel,lp(-1,dp(166),0,0,0,6));

        LinearLayout targetBar = row();
        targetBar.setGravity(Gravity.CENTER_VERTICAL);
        targetLabel = text("",10,MUTED,true);
        targetBar.addView(targetLabel,weight());
        myTarget = segmentButton("");
        boardTarget = segmentButton("");
        myTarget.setOnClickListener(v -> { targetHole=true; updateTargetButtons(); });
        boardTarget.setOnClickListener(v -> { targetHole=false; updateTargetButtons(); });
        targetBar.addView(myTarget,new LinearLayout.LayoutParams(dp(104),dp(36)));
        targetBar.addView(space(4));
        targetBar.addView(boardTarget,new LinearLayout.LayoutParams(dp(88),dp(36)));
        screen.addView(targetBar,lp(-1,dp(40),0,0,0,5));

        LinearLayout actions = row();
        scanButton = primaryButton("");
        scanButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_camera,0,0,0);
        scanButton.setCompoundDrawablePadding(dp(7));
        scanButton.setOnClickListener(v -> openScanner());
        manualButton = primaryButton("");
        manualButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_edit_card,0,0,0);
        manualButton.setCompoundDrawablePadding(dp(7));
        manualButton.setOnClickListener(v -> showManualPicker());
        actions.addView(scanButton,weight()); actions.addView(space(8)); actions.addView(manualButton,weight());
        screen.addView(actions,lp(-1,dp(48),0,0,0,7));

        LinearLayout estimate = row();
        estimate.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout stepper = row();
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        stepper.setPadding(dp(3),dp(2),dp(3),dp(2));
        stepper.setBackground(rounded(PANEL,12));
        Button minus = squareButton("−"), plus = squareButton("+");
        minus.setOnClickListener(v -> { if(opponents>1){opponents--;updateOpponentText();invalidateResult();} });
        plus.setOnClickListener(v -> { if(opponents<5){opponents++;updateOpponentText();invalidateResult();} });
        opponentsText = text("",13,Color.WHITE,true); opponentsText.setGravity(Gravity.CENTER);
        stepper.addView(minus,new LinearLayout.LayoutParams(dp(38),dp(38)));
        stepper.addView(opponentsText,new LinearLayout.LayoutParams(dp(104),dp(38)));
        stepper.addView(plus,new LinearLayout.LayoutParams(dp(38),dp(38)));
        estimate.addView(stepper,new LinearLayout.LayoutParams(dp(180),dp(42)));
        estimate.addView(space(8));

        calculateButton = new Button(this);
        calculateButton.setAllCaps(false);
        calculateButton.setTextSize(14);
        calculateButton.setTypeface(Typeface.DEFAULT_BOLD);
        calculateButton.setTextColor(BG);
        calculateButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GOLD));
        calculateButton.setOnClickListener(v -> calculate());
        estimate.addView(calculateButton,weight());
        screen.addView(estimate,lp(-1,dp(46),0,0,0,7));

        LinearLayout result = column();
        result.setPadding(dp(14),dp(10),dp(14),dp(10));
        result.setBackground(rounded(PANEL2,14));
        result.setMinimumHeight(dp(176));

        LinearLayout resultHeader = row();
        resultHeader.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        resultHand = text("",16,Color.WHITE,true);
        resultStage = text("",11,MUTED,true);
        resultStage.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        resultHeader.addView(resultHand,weight());
        resultHeader.addView(resultStage,new LinearLayout.LayoutParams(dp(112),-1));

        resultEquity = text("",30,GOLD,true);
        resultEquity.setGravity(Gravity.CENTER);

        LinearLayout odds = row();
        odds.setGravity(Gravity.CENTER);
        resultWin=statView(); resultTie=statView(); resultLose=statView();
        odds.addView(resultWin,weight()); odds.addView(space(5)); odds.addView(resultTie,weight()); odds.addView(space(5)); odds.addView(resultLose,weight());

        resultDetail=text("",12,Color.WHITE,false);
        resultMeta=text("",11,MUTED,false);
        result.addView(resultHeader,lp(-1,dp(24),0,0,0,1));
        result.addView(resultEquity,lp(-1,dp(42),0,0,0,3));
        result.addView(odds,lp(-1,dp(43),0,0,0,6));
        result.addView(resultDetail,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1,-2); metaLp.setMargins(0,dp(3),0,0);
        result.addView(resultMeta,metaLp);
        screen.addView(result,new LinearLayout.LayoutParams(-1,-2));

        return screen;
    }

    private FrameLayout buildScanner() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.BLACK);
        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        overlay.addView(previewView,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=row(); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(10),dp(8),dp(10),dp(8)); top.setBackgroundColor(0xD9041711);
        Button close=compactButton("←"); close.setTextSize(23); close.setOnClickListener(v -> closeScanner());
        top.addView(close,new LinearLayout.LayoutParams(dp(48),dp(42)));
        TextView st=text("CARD SCANNER",17,Color.WHITE,true); st.setGravity(Gravity.CENTER); st.setTag("scannerTitle");
        top.addView(st,weight());
        ImageButton manual=iconButton(R.drawable.ic_edit_card,"Add manually"); manual.setOnClickListener(v -> showManualPicker());
        top.addView(manual,new LinearLayout.LayoutParams(dp(48),dp(42)));
        overlay.addView(top,new FrameLayout.LayoutParams(-1,dp(58),Gravity.TOP));

        LinearLayout bottom=column(); bottom.setPadding(dp(12),dp(10),dp(12),dp(10)); bottom.setBackgroundColor(0xE8041711);
        LinearLayout targets=row();
        Button sm=segmentButton("My Cards"), sb=segmentButton("Board"); sm.setTag("scanMine"); sb.setTag("scanBoard");
        sm.setOnClickListener(v -> {targetHole=true;updateTargetButtons();updateScannerTargets(sm,sb);});
        sb.setOnClickListener(v -> {targetHole=false;updateTargetButtons();updateScannerTargets(sm,sb);});
        targets.addView(sm,weight()); targets.addView(space(8)); targets.addView(sb,weight());
        bottom.addView(targets,lp(-1,dp(40),0,0,0,5));
        scanStatus=text("",13,GOLD,true); scanStatus.setGravity(Gravity.CENTER);
        bottom.addView(scanStatus,lp(-1,dp(40),0,0,0,3));
        candidates=row(); candidates.setGravity(Gravity.CENTER);
        HorizontalScrollView hsv=new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false); hsv.addView(candidates);
        bottom.addView(hsv,lp(-1,dp(54),0,0,0,3));
        TextView tip=text("",11,MUTED,false); tip.setGravity(Gravity.CENTER); tip.setTag("scannerTip");
        bottom.addView(tip,lp(-1,dp(32),0,0,0,0));
        overlay.addView(bottom,new FrameLayout.LayoutParams(-1,dp(184),Gravity.BOTTOM));
        overlay.setTag(new Button[]{sm,sb});
        return overlay;
    }

    private void showManualPicker() {
        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=column();
        box.setPadding(dp(16),dp(14),dp(16),dp(14));
        box.setBackground(rounded(Color.WHITE,18));

        TextView title=text(arabic?"إضافة بطاقة يدويًا":"Add card manually",20,BG,true);
        box.addView(title,lp(-1,dp(36),0,0,0,8));

        LinearLayout destination=row();
        Button mine=dialogSegmentButton(arabic?"بطاقاتي":"My Cards");
        Button table=dialogSegmentButton(arabic?"الطاولة":"Board");
        destination.addView(mine,weight()); destination.addView(space(8)); destination.addView(table,weight());
        box.addView(destination,lp(-1,dp(44),0,0,0,10));

        TextView helper=text(arabic?"اختر القيمة ثم النوع.":"Choose rank, then suit.",12,Color.DKGRAY,false);
        box.addView(helper,lp(-1,-2,0,0,0,8));

        final int[] selectedRank={14};
        final int[] selectedSuit={0};
        ArrayList<Button> rankButtons=new ArrayList<>();
        ArrayList<Button> suitButtons=new ArrayList<>();
        TextView preview=text("A♠",38,Color.BLACK,true);
        preview.setGravity(Gravity.CENTER);
        preview.setBackground(rounded(Color.rgb(248,248,248),12));

        Runnable refreshSelection=() -> {
            for(Button b:rankButtons){
                int r=(Integer)b.getTag();
                b.setAlpha(r==selectedRank[0]?1f:.48f);
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(r==selectedRank[0]?PANEL2:Color.rgb(220,226,223)));
                b.setTextColor(r==selectedRank[0]?Color.WHITE:BG);
            }
            for(Button b:suitButtons){
                int s=(Integer)b.getTag();
                b.setAlpha(s==selectedSuit[0]?1f:.48f);
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(s==selectedSuit[0]?PANEL2:Color.rgb(220,226,223)));
                b.setTextColor(s==1||s==2?RED:Color.BLACK);
            }
            PokerMath.Card c=new PokerMath.Card(selectedRank[0],selectedSuit[0]);
            preview.setText(c.pretty());
            preview.setTextColor(isRedSuit(c)?RED:Color.BLACK);
            mine.setAlpha(targetHole?1f:.5f); table.setAlpha(targetHole ? .5f : 1f);
        };

        int[][] rankRows={{14,13,12,11,10,9,8},{7,6,5,4,3,2}};
        for(int[] rr:rankRows){
            LinearLayout row=row();
            for(int i=0;i<rr.length;i++){
                int r=rr[i];
                Button b=miniChoice(PokerMath.rankText(r)); b.setTag(r);
                b.setOnClickListener(v->{selectedRank[0]=(Integer)v.getTag();refreshSelection.run();});
                rankButtons.add(b); row.addView(b,weight()); if(i<rr.length-1)row.addView(space(4));
            }
            box.addView(row,lp(-1,dp(40),0,0,0,5));
        }

        LinearLayout suitRow=row();
        String[] suits={"♠","♥","♦","♣"};
        for(int s=0;s<4;s++){
            Button b=miniChoice(suits[s]); b.setTag(s); b.setTextSize(22);
            b.setOnClickListener(v->{selectedSuit[0]=(Integer)v.getTag();refreshSelection.run();});
            suitButtons.add(b); suitRow.addView(b,weight()); if(s<3)suitRow.addView(space(6));
        }
        box.addView(suitRow,lp(-1,dp(46),0,3,0,10));
        box.addView(preview,lp(-1,dp(70),0,0,0,12));

        mine.setOnClickListener(v->{targetHole=true;updateTargetButtons();refreshSelection.run();});
        table.setOnClickListener(v->{targetHole=false;updateTargetButtons();refreshSelection.run();});

        LinearLayout bottom=row();
        Button done=dialogSecondaryButton(arabic?"تم":"Done");
        Button add=dialogPrimaryButton(arabic?"إضافة البطاقة":"Add card");
        done.setOnClickListener(v->dialog.dismiss());
        add.setOnClickListener(v->{
            PokerMath.Card c=new PokerMath.Card(selectedRank[0],selectedSuit[0]);
            if(addCard(c)){
                if(targetHole&&hole.size()>=2){targetHole=false;updateTargetButtons();}
                refreshSelection.run();
                toast(arabic?"تمت الإضافة":c.pretty()+" added");
                if(board.size()>=5&&scanner.getVisibility()==View.VISIBLE)closeScanner();
            }
        });
        bottom.addView(done,weight()); bottom.addView(space(8)); bottom.addView(add,weight());
        box.addView(bottom,lp(-1,dp(48),0,0,0,0));

        dialog.setContentView(box);
        dialog.setOnShowListener(x->{
            Window w=dialog.getWindow();
            if(w!=null){
                w.setBackgroundDrawableResource(android.R.color.transparent);
                WindowManager.LayoutParams p=new WindowManager.LayoutParams(); p.copyFrom(w.getAttributes());
                p.width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(28),dp(520));
                p.height=WindowManager.LayoutParams.WRAP_CONTENT; w.setAttributes(p);
            }
            refreshSelection.run();
        });
        dialog.show();
    }

    private void openScanner() {
        scanner.setVisibility(View.VISIBLE);
        Button[] b=(Button[])scanner.getTag(); updateScannerTargets(b[0],b[1]);
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();
        else{
            scanStatus.setText(arabic?"اسمح باستخدام الكاميرا فقط أثناء المسح.":"Camera permission is needed only while scanning.");
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},CAMERA_REQ);
        }
    }
    private void closeScanner(){ stopCamera(); scanner.setVisibility(View.GONE); if(candidates!=null)candidates.removeAllViews(); }

    private void startCamera() {
        if(cameraRunning)return;
        scanStatus.setText(arabic?"جاري تشغيل الكاميرا…":"Starting camera…");
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            try{
                cameraProvider=future.get();
                Preview preview=new Preview.Builder().build(); preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new android.util.Size(1280,720)).build();
                analysis.setAnalyzer(cameraExecutor,this::analyzeFrame);
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis);
                cameraRunning=true;
                runOnUiThread(()->scanStatus.setText(arabic?"وجّه الكاميرا إلى زاوية البطاقة.":"Aim at a card corner. Detection runs only here."));
            }catch(Exception e){
                cameraRunning=false;
                runOnUiThread(()->scanStatus.setText("Camera error: "+e.getMessage()));
            }
        },ContextCompat.getMainExecutor(this));
    }
    private void stopCamera(){ cameraRunning=false; analyzing.set(false); if(cameraProvider!=null)cameraProvider.unbindAll(); }

    private void analyzeFrame(ImageProxy proxy) {
        if(!cameraRunning){proxy.close();return;}
        long now=SystemClock.elapsedRealtime();
        if(now-lastAnalyzeMs<450||!analyzing.compareAndSet(false,true)){proxy.close();return;}
        lastAnalyzeMs=now;
        if(proxy.getImage()==null){analyzing.set(false);proxy.close();return;}
        InputImage image=InputImage.fromMediaImage(proxy.getImage(),proxy.getImageInfo().getRotationDegrees());
        recognizer.process(image)
                .addOnSuccessListener(t->onRecognized(CardRecognizer.extract(t),t))
                .addOnFailureListener(e->runOnUiThread(()->scanStatus.setText("OCR: "+e.getClass().getSimpleName())))
                .addOnCompleteListener(t->{analyzing.set(false);proxy.close();});
    }
    private void onRecognized(List<PokerMath.Card> cards, Text raw) {
        runOnUiThread(()->{
            if(scanner.getVisibility()!=View.VISIBLE)return;
            if(cards.isEmpty()){
                scanStatus.setText(arabic?"لم يتم التعرف بعد. قرّب زاوية البطاقة.":"No card yet — move closer to the rank + suit corner.");
                return;
            }
            StringBuilder key=new StringBuilder(); for(PokerMath.Card c:cards)key.append(c.code()).append(',');
            if(!key.toString().equals(lastCandidateKey)||SystemClock.elapsedRealtime()-lastCandidateMs>1200){
                lastCandidateKey=key.toString(); lastCandidateMs=SystemClock.elapsedRealtime(); showCandidates(cards);
            }
            scanStatus.setText((arabic?"تم اكتشاف: ":"Detected: ")+prettyList(cards));
        });
    }
    private void showCandidates(List<PokerMath.Card> cards) {
        candidates.removeAllViews();
        for(PokerMath.Card c:cards){
            Button b=cardCandidateButton(c);
            b.setOnClickListener(v->{
                if(!addCard(c))return;
                if(targetHole&&hole.size()>=2){targetHole=false;updateTargetButtons();}
                Button[] sb=(Button[])scanner.getTag(); updateScannerTargets(sb[0],sb[1]);
                if(board.size()>=5){toast(arabic?"اكتملت بطاقات الطاولة":"Board complete");closeScanner();}
            });
            candidates.addView(b); candidates.addView(space(6));
        }
    }

    private boolean addCard(PokerMath.Card c) {
        if(contains(c)){toast(arabic?"هذه البطاقة موجودة بالفعل":"That card is already selected");return false;}
        if(targetHole){
            if(hole.size()>=2){toast(arabic?"لديك بطاقتان بالفعل":"Your two-card hand is already full");return false;}
            hole.add(c);
        }else{
            if(board.size()>=5){toast(arabic?"الطاولة تحتوي خمس بطاقات":"The board is already full");return false;}
            board.add(c);
        }
        invalidateResult(); refreshCards(); return true;
    }
    private void removeCard(boolean fromHole,PokerMath.Card c){if(fromHole)hole.remove(c);else board.remove(c);invalidateResult();refreshCards();}
    private boolean contains(PokerMath.Card c){return hole.contains(c)||board.contains(c);}
    private void resetAll(){hole.clear();board.clear();targetHole=true;if(candidates!=null)candidates.removeAllViews();invalidateResult();updateTargetButtons();refreshCards();}
    private void invalidateResult(){lastResult=null;renderEmptyResult();}

    private void refreshCards(){
        if(holeRow==null||boardRow==null)return;
        renderSlots(holeRow,hole,2,true); renderSlots(boardRow,board,5,false);
    }
    private void renderSlots(LinearLayout row,List<PokerMath.Card> cards,int total,boolean fromHole){
        row.removeAllViews();
        for(int i=0;i<total;i++){
            if(i<cards.size()){
                PokerMath.Card c=cards.get(i); TextView v=cardView(c); v.setOnClickListener(x->removeCard(fromHole,c)); row.addView(v);
            }else row.addView(emptyCardView());
            if(i<total-1)row.addView(space(4));
        }
    }

    private void calculate(){
        if(hole.size()!=2){toast(arabic?"أضف بطاقتين لك أولاً":"Add exactly two hole cards first");return;}
        calculateButton.setEnabled(false);
        calculateButton.setText(arabic?"جاري الحساب…":"Calculating…");
        resultHand.setText(arabic?"حساب الاحتمالات…":"Calculating equity…"); resultStage.setText(""); resultEquity.setText("…");
        ArrayList<PokerMath.Card> h=new ArrayList<>(hole), b=new ArrayList<>(board);
        int opp=opponents;
        new Thread(()->{
            try{
                PokerMath.Result r=PokerMath.calculate(h,b,opp,SIMULATIONS,System.nanoTime());
                runOnUiThread(()->{
                    lastResult=r;lastResultOpponents=opp;renderResult(r,opp);
                    calculateButton.setEnabled(true); calculateButton.setText(arabic?"احسب الاحتمال":"Estimate equity");
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    resultHand.setText("Error");resultStage.setText("");resultEquity.setText("");resultDetail.setText(e.getMessage());
                    calculateButton.setEnabled(true);calculateButton.setText(arabic?"احسب الاحتمال":"Estimate equity");
                });
            }
        }).start();
    }

    private void renderEmptyResult(){
        if(resultHand==null)return;
        resultHand.setText(arabic?"جاهز للحساب":"Ready to estimate");
        resultStage.setText("");
        resultEquity.setText("—");
        resultWin.setText(arabic?"فوز\n—":"WIN\n—");
        resultTie.setText(arabic?"تعادل\n—":"TIE\n—");
        resultLose.setText(arabic?"خسارة\n—":"LOSE\n—");
        resultDetail.setText(arabic?"أضف بطاقتيك ثم بطاقات الطاولة إن وجدت.":"Add your 2 cards, then optionally add the board.");
        resultMeta.setText(arabic?"الكاميرا متوقفة حتى تضغط «مسح البطاقات».":"Camera stays off until you tap Scan cards.");
        applyResultDirection();
    }

    private void renderResult(PokerMath.Result r,int opp){
        resultHand.setText(arabic?handArabic(r.currentHand):r.currentHand);
        resultStage.setText(stageText(board.size()));
        resultEquity.setText(String.format(Locale.US,"%.1f%%",r.equity));
        resultWin.setText(String.format(Locale.US,"%s\n%.1f%%",arabic?"فوز":"WIN",r.win));
        resultTie.setText(String.format(Locale.US,"%s\n%.1f%%",arabic?"تعادل":"TIE",r.tie));
        resultLose.setText(String.format(Locale.US,"%s\n%.1f%%",arabic?"خسارة":"LOSE",r.lose));

        if(board.size()>=5){
            resultDetail.setText(arabic?"لا توجد سحوبات متبقية.":"No remaining draws.");
        }else{
            String draw=arabic?drawArabic(r.draws):r.draws;
            resultDetail.setText(arabic
                    ? draw+"\nبطاقات تحسين اليد: "+r.outs
                    : draw+"\nHand-improving cards: "+r.outs);
        }
        resultMeta.setText(arabic
                ? String.format(Locale.US,"ضد %d %s • %,d محاكاة",opp,opp==1?"خصم":"خصوم",r.simulations)
                : String.format(Locale.US,"%d opponent%s • %,d simulations",opp,opp==1?"":"s",r.simulations));
        applyResultDirection();
    }

    private String stageText(int n){
        if(arabic){
            if(n==0)return"قبل الفلوب"; if(n==3)return"الفلوب"; if(n==4)return"التيرن"; if(n>=5)return"الريفر"; return"طاولة جزئية";
        }
        if(n==0)return"PRE-FLOP"; if(n==3)return"FLOP"; if(n==4)return"TURN"; if(n>=5)return"RIVER"; return"PARTIAL BOARD";
    }
    private void applyResultDirection(){
        int td=arabic?View.TEXT_DIRECTION_RTL:View.TEXT_DIRECTION_LTR;
        resultHand.setTextDirection(td); resultDetail.setTextDirection(td); resultMeta.setTextDirection(td);
        resultHand.setGravity(arabic?Gravity.RIGHT:Gravity.LEFT);
        resultDetail.setGravity(arabic?Gravity.RIGHT:Gravity.LEFT);
        resultMeta.setGravity(arabic?Gravity.RIGHT:Gravity.LEFT);
    }

    private String handArabic(String s){
        Map<String,String>m=new HashMap<>();
        m.put("Pre-flop","قبل الفلوب");m.put("High Card","أعلى ورقة");m.put("One Pair","زوج");m.put("Two Pair","زوجان");
        m.put("Three of a Kind","ثلاثة من نفس الرتبة");m.put("Straight","ستريت");m.put("Flush","فلاش");
        m.put("Full House","فول هاوس");m.put("Four of a Kind","أربعة من نفس الرتبة");m.put("Straight Flush","ستريت فلاش");
        return m.getOrDefault(s,s);
    }
    private String drawArabic(String s){
        return s.replace("Flush draw","سحب فلاش").replace("Straight draw","سحب ستريت")
                .replace("No major draw detected","لا يوجد سحب رئيسي").replace("No board draw yet","لا توجد أوراق طاولة كافية");
    }

    private void updateTexts(){
        if(language==null)return;
        dashboard.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        language.setText(arabic?"English":"عربي");

        modeHint.setText(arabic?"الكاميرا متوقفة • اضغط أي بطاقة لحذفها.":"Camera OFF • tap any card to remove it.");
        modeHint.setTextDirection(arabic?View.TEXT_DIRECTION_RTL:View.TEXT_DIRECTION_LTR);
        modeHint.setGravity(arabic?Gravity.RIGHT:Gravity.LEFT);

        targetLabel.setText(arabic?"الإضافة إلى":"ADD TO");
        targetLabel.setTextDirection(arabic?View.TEXT_DIRECTION_RTL:View.TEXT_DIRECTION_LTR);
        targetLabel.setGravity(arabic?Gravity.RIGHT:Gravity.LEFT);
        myTarget.setText(arabic?"بطاقاتي":"My Cards"); boardTarget.setText(arabic?"الطاولة":"Board");
        scanButton.setText(arabic?"مسح البطاقات":"Scan cards");
        manualButton.setText(arabic?"إضافة يدويًا":"Add manually");
        calculateButton.setText(arabic?"احسب الاحتمال":"Estimate equity");

        TextView ml=dashboard.findViewWithTag("mineLabel"), bl=dashboard.findViewWithTag("boardLabel");
        if(ml!=null){ml.setText(arabic?"بطاقاتي":"MY CARDS");ml.setTextDirection(arabic?View.TEXT_DIRECTION_RTL:View.TEXT_DIRECTION_LTR);ml.setGravity(arabic?Gravity.RIGHT:Gravity.LEFT);}
        if(bl!=null){bl.setText(arabic?"الطاولة":"BOARD");bl.setTextDirection(arabic?View.TEXT_DIRECTION_RTL:View.TEXT_DIRECTION_LTR);bl.setGravity(arabic?Gravity.RIGHT:Gravity.LEFT);}

        TextView st=scanner==null?null:scanner.findViewWithTag("scannerTitle");
        TextView tip=scanner==null?null:scanner.findViewWithTag("scannerTip");
        Button sm=scanner==null?null:scanner.findViewWithTag("scanMine"), sb=scanner==null?null:scanner.findViewWithTag("scanBoard");
        if(st!=null)st.setText(arabic?"ماسح البطاقات":"CARD SCANNER");
        if(tip!=null)tip.setText(arabic?"وجّه الكاميرا إلى زاوية الرقم + النوع، ثم اضغط البطاقة المكتشفة.":"Aim at the printed rank + suit corner, then tap a detected card.");
        if(sm!=null)sm.setText(arabic?"بطاقاتي":"My Cards");
        if(sb!=null)sb.setText(arabic?"الطاولة":"Board");

        updateOpponentText();updateTargetButtons();refreshCards();
        if(lastResult==null)renderEmptyResult();
    }
    private void updateOpponentText(){
        if(opponentsText==null)return;
        opponentsText.setText(arabic?opponents+(opponents==1?" خصم":" خصوم"):opponents+(opponents==1?" opponent":" opponents"));
    }
    private void updateTargetButtons(){
        if(myTarget==null)return;
        myTarget.setAlpha(targetHole?1f:.52f);boardTarget.setAlpha(targetHole ? .52f : 1f);
        myTarget.setBackgroundTintList(android.content.res.ColorStateList.valueOf(targetHole?PANEL2:PANEL));
        boardTarget.setBackgroundTintList(android.content.res.ColorStateList.valueOf(targetHole?PANEL:PANEL2));
    }
    private void updateScannerTargets(Button mine,Button boardButton){
        if(mine==null||boardButton==null)return;
        mine.setAlpha(targetHole?1f:.52f);boardButton.setAlpha(targetHole ? .52f : 1f);
    }

    @Override public void onRequestPermissionsResult(int req,@NonNull String[] permissions,@NonNull int[] grants){
        super.onRequestPermissionsResult(req,permissions,grants);
        if(req==CAMERA_REQ){
            if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){
                if(scanner.getVisibility()==View.VISIBLE)startCamera();
            }else scanStatus.setText(arabic?"تم رفض صلاحية الكاميرا. استخدم الإضافة اليدوية.":"Camera permission denied. Use Add manually instead.");
        }
    }
    @Override protected void onPause(){super.onPause();if(cameraRunning)stopCamera();}
    @Override protected void onResume(){super.onResume();if(scanner!=null&&scanner.getVisibility()==View.VISIBLE&&ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();}
    @Override protected void onDestroy(){stopCamera();if(recognizer!=null)recognizer.close();if(cameraExecutor!=null)cameraExecutor.shutdown();super.onDestroy();}

    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView text(String s,float sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private TextView statView(){TextView v=text("",12,Color.WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(rounded(PANEL,10));return v;}
    private Button primaryButton(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(BG);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(236,239,237)));return b;}
    private Button compactButton(String s){Button b=primaryButton(s);b.setTextSize(13);b.setPadding(dp(6),0,dp(6),0);return b;}
    private ImageButton iconButton(int res,String desc){ImageButton b=new ImageButton(this);b.setImageResource(res);b.setContentDescription(desc);b.setPadding(dp(11),dp(11),dp(11),dp(11));b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(236,239,237)));return b;}
    private Button segmentButton(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(12);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL2));b.setPadding(dp(4),0,dp(4),0);return b;}
    private Button squareButton(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(22);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL2));b.setPadding(0,0,0,0);return b;}
    private Button miniChoice(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT_BOLD);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(2),0,dp(2),0);return b;}
    private Button cardCandidateButton(PokerMath.Card c){Button b=new Button(this);b.setAllCaps(false);b.setText(c.pretty());b.setTextSize(20);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(isRedSuit(c)?RED:Color.BLACK);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));b.setMinWidth(dp(64));return b;}
    private TextView cardView(PokerMath.Card c){TextView v=text(c.pretty(),17,isRedSuit(c)?RED:Color.BLACK,true);v.setGravity(Gravity.CENTER);v.setBackground(rounded(Color.WHITE,8));v.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(54)));return v;}
    private TextView emptyCardView(){TextView v=text("—",17,Color.rgb(135,145,140),true);v.setGravity(Gravity.CENTER);GradientDrawable g=rounded(Color.rgb(225,231,228),8);g.setStroke(dp(1),Color.rgb(160,170,165));v.setBackground(g);v.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(54)));return v;}
    private boolean isRedSuit(PokerMath.Card c){return c.suit==1||c.suit==2;}
    private Button dialogSegmentButton(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL2));return b;}
    private Button dialogPrimaryButton(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(BG);b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GOLD));return b;}
    private Button dialogSecondaryButton(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(BG);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(230,234,232)));return b;}
    private GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private Space space(int n){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(dp(n),1));return s;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-1,1f);}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private String prettyList(List<PokerMath.Card> cards){StringBuilder b=new StringBuilder();for(PokerMath.Card c:cards){if(b.length()>0)b.append("  ");b.append(c.pretty());}return b.toString();}
}
