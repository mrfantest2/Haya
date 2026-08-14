package com.fantest.pokertrainer;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(4,23,17));
        getWindow().setNavigationBarColor(Color.rgb(4,23,17));
        setContentView(new PokerView());
    }

    private final class PokerView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rng = new Random();
        private final Game game = new Game();
        private boolean arabic = false;
        private boolean learn = false;
        private RectF foldBtn = new RectF(), callBtn = new RectF(), raiseBtn = new RectF();
        private RectF newBtn = new RectF(), learnBtn = new RectF(), langBtn = new RectF();

        PokerView() {
            super(MainActivity.this);
            setBackgroundColor(Color.rgb(4,23,17));
            game.newHand();
        }

        private String t(String en, String ar) { return arabic ? ar : en; }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            drawBackground(c, w, h);
            if (learn) drawLearn(c, w, h); else drawGame(c, w, h);
        }

        private void drawBackground(Canvas c, float w, float h) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(4,23,17)); c.drawRect(0,0,w,h,p);
            p.setColor(Color.rgb(8,47,34)); c.drawCircle(w*.5f,h*.42f,Math.max(w,h)*.48f,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3);
            p.setColor(Color.rgb(30,101,72)); c.drawOval(new RectF(w*.04f,h*.15f,w*.96f,h*.72f),p);
            p.setStyle(Paint.Style.FILL);
        }

        private void drawGame(Canvas c, float w, float h) {
            text(c,"POKER TRAINER",w*.05f,h*.055f,24,Color.WHITE,Paint.Align.LEFT,true);
            text(c,"v0.1.0",w*.05f,h*.083f,12,Color.rgb(174,202,190),Paint.Align.LEFT,false);
            float topY=h*.03f;
            langBtn = button(c,w*.68f,topY,w*.80f,topY+44,arabic?"EN":"ع",false);
            learnBtn = button(c,w*.815f,topY,w*.965f,topY+44,t("LEARN","تعلم"),false);

            text(c,t("COMPUTER","الكمبيوتر"),w*.5f,h*.135f,16,Color.WHITE,Paint.Align.CENTER,true);
            text(c,"$"+game.botChips,w*.5f,h*.162f,14,Color.rgb(245,200,76),Paint.Align.CENTER,true);
            drawCards(c,game.bot, w*.5f, h*.205f, game.showdown, true);

            text(c,t(game.stageName(),game.stageNameAr()),w*.5f,h*.298f,13,Color.rgb(186,207,198),Paint.Align.CENTER,true);
            text(c,t("POT ","الرهان ")+"$"+game.pot,w*.5f,h*.33f,24,Color.rgb(245,200,76),Paint.Align.CENTER,true);

            drawCommunity(c,w,h);

            if (!game.message.isEmpty()) {
                round(c,w*.08f,h*.495f,w*.92f,h*.565f,16,Color.argb(210,5,28,20));
                text(c,arabic?game.messageAr:game.message,w*.5f,h*.535f,15,Color.WHITE,Paint.Align.CENTER,true);
            }

            text(c,t("YOU","أنت"),w*.5f,h*.615f,17,Color.WHITE,Paint.Align.CENTER,true);
            text(c,"$"+game.playerChips,w*.5f,h*.642f,14,Color.rgb(245,200,76),Paint.Align.CENTER,true);
            drawCards(c,game.player,w*.5f,h*.69f,true,false);

            if (game.handOver) {
                newBtn = button(c,w*.18f,h*.79f,w*.82f,h*.855f,t("NEW HAND","يد جديدة"),true);
                text(c,t("Tap New Hand to continue","اضغط يد جديدة للمتابعة"),w*.5f,h*.89f,13,Color.rgb(181,202,193),Paint.Align.CENTER,false);
            } else {
                float y1=h*.79f,y2=h*.855f,gap=w*.018f;
                float bw=(w*.90f-2*gap)/3f;
                float x=w*.05f;
                foldBtn=button(c,x,y1,x+bw,y2,t("FOLD","انسحب"),false); x+=bw+gap;
                callBtn=button(c,x,y1,x+bw,y2,t("CHECK / CALL","تمرير / دفع"),true); x+=bw+gap;
                raiseBtn=button(c,x,y1,x+bw,y2,t("RAISE +40","ارفع +40"),false);
                text(c,t("Offline heads-up Texas Hold'em","تكساس هولدم ضد الكمبيوتر بدون إنترنت"),w*.5f,h*.91f,12,Color.rgb(142,173,159),Paint.Align.CENTER,false);
            }

            text(c,t("Best five-card hand wins • No real money","أفضل خمس أوراق تفوز • بدون أموال حقيقية"),w*.5f,h*.965f,11,Color.rgb(112,145,130),Paint.Align.CENTER,false);
        }

        private void drawCommunity(Canvas c,float w,float h) {
            if (game.community.isEmpty()) {
                text(c,t("Community cards","الأوراق المشتركة"),w*.5f,h*.405f,13,Color.rgb(156,187,174),Paint.Align.CENTER,false);
                for(int i=0;i<5;i++) drawEmptyCard(c,w*.5f+(i-2)*58,h*.425f);
            } else {
                for(int i=0;i<5;i++) {
                    float x=w*.5f+(i-2)*58;
                    if(i<game.community.size()) drawCard(c,game.community.get(i),x,h*.425f,true);
                    else drawEmptyCard(c,x,h*.425f);
                }
            }
        }

        private void drawLearn(Canvas c,float w,float h) {
            text(c,t("POKER HANDS","ترتيب أيدي البوكر"),w*.05f,h*.058f,25,Color.WHITE,Paint.Align.LEFT,true);
            langBtn=button(c,w*.68f,h*.03f,w*.80f,h*.082f,arabic?"EN":"ع",false);
            learnBtn=button(c,w*.815f,h*.03f,w*.965f,h*.082f,t("GAME","اللعبة"),true);
            String[][] hands = {
                {"1","Royal Flush","رويال فلاش","A K Q J 10 • same suit"},
                {"2","Straight Flush","ستريت فلاش","Five in sequence • same suit"},
                {"3","Four of a Kind","أربع متشابهة","Four cards of one rank"},
                {"4","Full House","فول هاوس","Three of a kind + a pair"},
                {"5","Flush","فلاش","Five cards • same suit"},
                {"6","Straight","ستريت","Five cards in sequence"},
                {"7","Three of a Kind","ثلاث متشابهة","Three cards of one rank"},
                {"8","Two Pair","زوجان","Two different pairs"},
                {"9","One Pair","زوج","Two cards of one rank"},
                {"10","High Card","أعلى ورقة","Highest card when no hand forms"}
            };
            float y=h*.115f; float row=(h*.80f)/10f;
            for(String[] a:hands){
                round(c,w*.04f,y,w*.96f,y+row*.84f,14,Color.rgb(10,52,38));
                p.setColor(Color.rgb(245,200,76)); c.drawCircle(w*.10f,y+row*.42f,18,p);
                text(c,a[0],w*.10f,y+row*.47f,13,Color.rgb(30,24,5),Paint.Align.CENTER,true);
                text(c,arabic?a[2]:a[1],w*.17f,y+row*.34f,15,Color.WHITE,Paint.Align.LEFT,true);
                String d=arabic?learnArabic(a[0]):a[3];
                text(c,d,w*.17f,y+row*.62f,11,Color.rgb(178,203,192),Paint.Align.LEFT,false);
                y+=row;
            }
            text(c,t("Tap GAME to return","اضغط اللعبة للعودة"),w*.5f,h*.965f,12,Color.rgb(153,183,170),Paint.Align.CENTER,false);
        }

        private String learnArabic(String n){
            switch(n){
                case "1": return "A K Q J 10 من نفس النوع";
                case "2": return "خمس أوراق متتالية من نفس النوع";
                case "3": return "أربع أوراق من نفس القيمة";
                case "4": return "ثلاث متشابهة + زوج";
                case "5": return "خمس أوراق من نفس النوع";
                case "6": return "خمس أوراق متتالية";
                case "7": return "ثلاث أوراق من نفس القيمة";
                case "8": return "زوجان مختلفان";
                case "9": return "ورقتان من نفس القيمة";
                default: return "أعلى ورقة عند عدم تكوّن يد";
            }
        }

        private void drawCards(Canvas c,List<Card> cards,float center,float y,boolean face,boolean botCards){
            float gap=64;
            if(cards.size()<2)return;
            drawCard(c,cards.get(0),center-gap/2,y,face);
            drawCard(c,cards.get(1),center+gap/2,y,face);
        }

        private void drawCard(Canvas c,Card card,float cx,float cy,boolean face){
            float cw=52,ch=72; RectF r=new RectF(cx-cw/2,cy-ch/2,cx+cw/2,cy+ch/2);
            p.setShadowLayer(8,0,4,Color.argb(100,0,0,0)); setLayerType(View.LAYER_TYPE_SOFTWARE,p);
            p.setColor(face?Color.rgb(255,253,247):Color.rgb(38,73,116)); c.drawRoundRect(r,8,8,p); p.clearShadowLayer();
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(face?Color.rgb(220,220,215):Color.rgb(117,151,194)); c.drawRoundRect(r,8,8,p); p.setStyle(Paint.Style.FILL);
            if(face){
                int col=(card.suit=='♥'||card.suit=='♦')?Color.rgb(207,38,38):Color.rgb(20,25,23);
                text(c,card.rankLabel(),cx-17,cy-13,16,col,Paint.Align.LEFT,true);
                text(c,String.valueOf(card.suit),cx,cy+22,25,col,Paint.Align.CENTER,false);
            } else {
                p.setColor(Color.rgb(245,200,76)); c.drawCircle(cx,cy,10,p);
                text(c,"♠",cx,cy+6,18,Color.rgb(20,51,78),Paint.Align.CENTER,true);
            }
        }

        private void drawEmptyCard(Canvas c,float cx,float cy){
            RectF r=new RectF(cx-26,cy-36,cx+26,cy+36); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(Color.rgb(58,112,86)); c.drawRoundRect(r,8,8,p); p.setStyle(Paint.Style.FILL);
        }

        private RectF button(Canvas c,float l,float top,float r,float b,String label,boolean primary){
            int color=primary?Color.rgb(245,200,76):Color.rgb(15,62,46); round(c,l,top,r,b,13,color);
            text(c,label,(l+r)/2,(top+b)/2+5,12,primary?Color.rgb(32,27,8):Color.WHITE,Paint.Align.CENTER,true);
            return new RectF(l,top,r,b);
        }

        private void round(Canvas c,float l,float t,float r,float b,float rad,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRoundRect(new RectF(l,t,r,b),rad,rad,p);}
        private void text(Canvas c,String s,float x,float y,float size,int color,Paint.Align align,boolean bold){p.setColor(color);p.setTextSize(size*getResources().getDisplayMetrics().scaledDensity/ getResources().getDisplayMetrics().density);p.setTextAlign(align);p.setTypeface(bold?android.graphics.Typeface.DEFAULT_BOLD:android.graphics.Typeface.DEFAULT);c.drawText(s,x,y,p);}

        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX(),y=e.getY();
            if(langBtn.contains(x,y)){arabic=!arabic;invalidate();return true;}
            if(learnBtn.contains(x,y)){learn=!learn;invalidate();return true;}
            if(learn)return true;
            if(game.handOver && newBtn.contains(x,y)){game.newHand();invalidate();return true;}
            if(!game.handOver){
                if(foldBtn.contains(x,y)){game.playerFold();invalidate();}
                else if(callBtn.contains(x,y)){game.playerCheckCall();invalidate();}
                else if(raiseBtn.contains(x,y)){game.playerRaise();invalidate();}
            }
            return true;
        }

        private final class Game {
            List<Card> deck=new ArrayList<>(),player=new ArrayList<>(),bot=new ArrayList<>(),community=new ArrayList<>();
            int playerChips=1000,botChips=1000,pot=0,stage=0;
            boolean handOver=false,showdown=false;
            String message="",messageAr="";

            void newHand(){
                if(playerChips<20||botChips<20){playerChips=1000;botChips=1000;}
                deck.clear();player.clear();bot.clear();community.clear();
                char[] suits={'♠','♥','♦','♣'}; for(char s:suits)for(int r=2;r<=14;r++)deck.add(new Card(r,s));
                Collections.shuffle(deck,rng); stage=0;handOver=false;showdown=false;message="";messageAr="";pot=0;
                // Heads-up blinds: player 10, computer 20.
                payPlayer(10);payBot(20);
                player.add(draw());bot.add(draw());player.add(draw());bot.add(draw());
                message="Your turn • blinds 10 / 20"; messageAr="دورك • الرهانات 10 / 20";
            }
            Card draw(){return deck.remove(deck.size()-1);}
            void payPlayer(int n){int a=Math.min(n,playerChips);playerChips-=a;pot+=a;}
            void payBot(int n){int a=Math.min(n,botChips);botChips-=a;pot+=a;}

            void playerFold(){
                botChips+=pot;pot=0;handOver=true;message="You folded • Computer wins";messageAr="انسحبت • الكمبيوتر يفوز";
            }

            void playerCheckCall(){
                if(stage==0)payPlayer(10);
                botAction(false);
                if(!handOver)advance();
            }

            void playerRaise(){
                int amount=Math.min(40,playerChips); if(amount<=0){playerCheckCall();return;}
                payPlayer(amount);
                // Bot folds more often with weak pre-flop cards, otherwise calls.
                int strength=preflopBotStrength();
                boolean fold=rng.nextInt(100)<(strength<5?32:12);
                if(fold){playerChips+=pot;pot=0;handOver=true;message="Computer folded • You win";messageAr="الكمبيوتر انسحب • أنت تفوز";return;}
                payBot(Math.min(amount,botChips));
                message="Computer calls your raise";messageAr="الكمبيوتر دفع الزيادة";
                advance();
            }

            void botAction(boolean afterRaise){
                if(handOver)return;
                if(stage>0 && rng.nextInt(100)<20 && botChips>=20){payBot(20);payPlayer(Math.min(20,playerChips));message="Computer bets 20 • You call";messageAr="الكمبيوتر يراهن 20 • تم الدفع";}
                else {message="Computer checks";messageAr="الكمبيوتر يمرر";}
            }

            int preflopBotStrength(){
                Card a=bot.get(0),b=bot.get(1);int high=Math.max(a.rank,b.rank);int s=high-8;
                if(a.rank==b.rank)s+=5;if(a.suit==b.suit)s+=1;if(Math.abs(a.rank-b.rank)<=2)s+=1;return s;
            }

            void advance(){
                if(stage==0){community.add(draw());community.add(draw());community.add(draw());stage=1;message="Flop dealt • Your turn";messageAr="تم فتح الفلوب • دورك";}
                else if(stage==1){community.add(draw());stage=2;message="Turn dealt • Your turn";messageAr="تم فتح التيرن • دورك";}
                else if(stage==2){community.add(draw());stage=3;message="River dealt • Your turn";messageAr="تم فتح الريفر • دورك";}
                else showdown();
            }

            void showdown(){
                stage=4;showdown=true;handOver=true;
                List<Card> a=new ArrayList<>(player);a.addAll(community);List<Card>b=new ArrayList<>(bot);b.addAll(community);
                HandValue ph=HandEvaluator.evaluate(a),bh=HandEvaluator.evaluate(b);int cmp=ph.compareTo(bh);
                if(cmp>0){playerChips+=pot;message="You win • "+ph.name;messageAr="أنت تفوز • "+ph.nameAr;}
                else if(cmp<0){botChips+=pot;message="Computer wins • "+bh.name;messageAr="الكمبيوتر يفوز • "+bh.nameAr;}
                else {int half=pot/2;playerChips+=half;botChips+=pot-half;message="Split pot • "+ph.name;messageAr="تعادل وتقسيم الرهان • "+ph.nameAr;}
                pot=0;
            }
            String stageName(){return switch(stage){case 0->"PRE-FLOP";case 1->"FLOP";case 2->"TURN";case 3->"RIVER";default->"SHOWDOWN";};}
            String stageNameAr(){return switch(stage){case 0->"قبل الفلوب";case 1->"فلوب";case 2->"تيرن";case 3->"ريفر";default->"كشف الأوراق";};}
        }
    }

    private static final class Card {
        final int rank; final char suit;
        Card(int rank,char suit){this.rank=rank;this.suit=suit;}
        String rankLabel(){if(rank<=10)return String.valueOf(rank);return switch(rank){case 11->"J";case 12->"Q";case 13->"K";default->"A";};}
    }

    private static final class HandValue implements Comparable<HandValue> {
        final int category; final List<Integer> tie; final String name,nameAr;
        HandValue(int c,List<Integer> t,String n,String ar){category=c;tie=t;name=n;nameAr=ar;}
        @Override public int compareTo(HandValue o){if(category!=o.category)return Integer.compare(category,o.category);for(int i=0;i<Math.min(tie.size(),o.tie.size());i++){int x=Integer.compare(tie.get(i),o.tie.get(i));if(x!=0)return x;}return Integer.compare(tie.size(),o.tie.size());}
    }

    private static final class HandEvaluator {
        static HandValue evaluate(List<Card> seven){
            HandValue best=null;int n=seven.size();
            for(int a=0;a<n-4;a++)for(int b=a+1;b<n-3;b++)for(int c=b+1;c<n-2;c++)for(int d=c+1;d<n-1;d++)for(int e=d+1;e<n;e++){
                List<Card> five=new ArrayList<>();five.add(seven.get(a));five.add(seven.get(b));five.add(seven.get(c));five.add(seven.get(d));five.add(seven.get(e));HandValue v=five(five);if(best==null||v.compareTo(best)>0)best=v;
            }
            return best;
        }

        static HandValue five(List<Card> cards){
            List<Integer> ranks=new ArrayList<>();Map<Integer,Integer> count=new HashMap<>();
            boolean flush=true;char suit=cards.get(0).suit;
            for(Card c:cards){ranks.add(c.rank);count.put(c.rank,count.getOrDefault(c.rank,0)+1);if(c.suit!=suit)flush=false;}
            ranks.sort(Collections.reverseOrder());
            int straightHigh=straightHigh(ranks);boolean straight=straightHigh>0;
            if(flush&&straight){if(straightHigh==14&&ranks.contains(10))return hv(9,list(14),"Royal Flush","رويال فلاش");return hv(8,list(straightHigh),"Straight Flush","ستريت فلاش");}
            int four=rankWithCount(count,4);if(four>0)return hv(7,list(four,highestExcept(ranks,four)),"Four of a Kind","أربع متشابهة");
            int three=rankWithCount(count,3),pair=rankWithCountExcept(count,2,-1);if(three>0&&pair>0)return hv(6,list(three,pair),"Full House","فول هاوس");
            if(flush)return hv(5,new ArrayList<>(ranks),"Flush","فلاش");
            if(straight)return hv(4,list(straightHigh),"Straight","ستريت");
            if(three>0){List<Integer> t=new ArrayList<>();t.add(three);for(int r:ranks)if(r!=three&&!t.contains(r))t.add(r);return hv(3,t,"Three of a Kind","ثلاث متشابهة");}
            List<Integer> pairs=new ArrayList<>();for(int r=14;r>=2;r--)if(count.getOrDefault(r,0)==2)pairs.add(r);
            if(pairs.size()>=2){int p1=pairs.get(0),p2=pairs.get(1);return hv(2,list(p1,p2,highestExcept(ranks,p1,p2)),"Two Pair","زوجان");}
            if(pairs.size()==1){int pr=pairs.get(0);List<Integer> t=new ArrayList<>();t.add(pr);for(int r:ranks)if(r!=pr&&!t.contains(r))t.add(r);return hv(1,t,"One Pair","زوج");}
            return hv(0,new ArrayList<>(ranks),"High Card","أعلى ورقة");
        }

        static int straightHigh(List<Integer> desc){
            List<Integer> u=new ArrayList<>();for(int r:desc)if(!u.contains(r))u.add(r);if(u.contains(14))u.add(1);int run=1;for(int i=1;i<u.size();i++){if(u.get(i-1)-1==u.get(i)){run++;if(run>=5)return u.get(i)+4;}else run=1;}return 0;
        }
        static int rankWithCount(Map<Integer,Integer> m,int n){for(int r=14;r>=2;r--)if(m.getOrDefault(r,0)==n)return r;return 0;}
        static int rankWithCountExcept(Map<Integer,Integer> m,int n,int ex){for(int r=14;r>=2;r--)if(r!=ex&&m.getOrDefault(r,0)==n)return r;return 0;}
        static int highestExcept(List<Integer> r,int... ex){outer:for(int x:r){for(int e:ex)if(x==e)continue outer;return x;}return 0;}
        static List<Integer> list(int... v){List<Integer>x=new ArrayList<>();for(int n:v)x.add(n);return x;}
        static HandValue hv(int c,List<Integer>t,String n,String ar){return new HandValue(c,t,n,ar);}
    }
}
