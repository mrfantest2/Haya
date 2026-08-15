from pathlib import Path

src = Path(__file__).resolve().parents[1] / "app/src/main/java/com/fantest/pokervision/VisionActivityV023.java"
s = src.read_text(encoding="utf-8")

replacements = [
    (
        '''        cardsPanel.setPadding(dp(10),dp(7),dp(10),dp(8));''',
        '''        cardsPanel.setPadding(dp(10),dp(6),dp(10),dp(5));'''
    ),
    (
        '''        cardsPanel.addView(mineLabel,lp(-1,dp(18),0,0,0,2));''',
        '''        cardsPanel.addView(mineLabel,lp(-1,-2,0,0,0,2));'''
    ),
    (
        '''        cardsPanel.addView(holeRow,lp(-1,dp(58),0,0,0,4));''',
        '''        cardsPanel.addView(holeRow,lp(-1,dp(66),0,0,0,3));'''
    ),
    (
        '''        cardsPanel.addView(boardLabel,lp(-1,dp(18),0,0,0,2));''',
        '''        cardsPanel.addView(boardLabel,lp(-1,-2,0,0,0,2));'''
    ),
    (
        '''        cardsPanel.addView(boardRow,lp(-1,dp(58),0,0,0,0));
        screen.addView(cardsPanel,lp(-1,dp(166),0,0,0,6));''',
        '''        cardsPanel.addView(boardRow,lp(-1,dp(66),0,0,0,0));
        screen.addView(cardsPanel,lp(-1,-2,0,0,0,4));'''
    ),
    (
        '''            if(i<cards.size()){
                PokerMath.Card c=cards.get(i); TextView v=cardView(c); v.setOnClickListener(x->removeCard(fromHole,c)); row.addView(v);
            }else row.addView(emptyCardView());
            if(i<total-1)row.addView(space(4));''',
        '''            if(i<cards.size()){
                PokerMath.Card c=cards.get(i); TextView v=cardView(c); v.setOnClickListener(x->removeCard(fromHole,c)); row.addView(v);
            }else row.addView(emptyCardView());
            if(i<total-1)row.addView(space(5));'''
    ),
    (
        '''        TextView preview=text("A♠",38,Color.BLACK,true);
        preview.setGravity(Gravity.CENTER);
        preview.setBackground(rounded(Color.rgb(248,248,248),12));''',
        '''        TextView preview=text(playingCardGlyph(new PokerMath.Card(14,0)),52,Color.BLACK,true);
        preview.setGravity(Gravity.CENTER);
        preview.setBackground(rounded(Color.rgb(248,248,248),12));'''
    ),
    (
        '''            PokerMath.Card c=new PokerMath.Card(selectedRank[0],selectedSuit[0]);
            preview.setText(c.pretty());
            preview.setTextColor(isRedSuit(c)?RED:Color.BLACK);''',
        '''            PokerMath.Card c=new PokerMath.Card(selectedRank[0],selectedSuit[0]);
            preview.setText(playingCardGlyph(c));
            preview.setTextColor(isRedSuit(c)?RED:Color.BLACK);'''
    ),
    (
        '''    private TextView cardView(PokerMath.Card c){TextView v=text(c.pretty(),17,isRedSuit(c)?RED:Color.BLACK,true);v.setGravity(Gravity.CENTER);v.setBackground(rounded(Color.WHITE,8));v.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(54)));return v;}
    private TextView emptyCardView(){TextView v=text("—",17,Color.rgb(135,145,140),true);v.setGravity(Gravity.CENTER);GradientDrawable g=rounded(Color.rgb(225,231,228),8);g.setStroke(dp(1),Color.rgb(160,170,165));v.setBackground(g);v.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(54)));return v;}''',
        '''    private String playingCardGlyph(PokerMath.Card c){
        int base=c.suit==0?0x1F0A0:c.suit==1?0x1F0B0:c.suit==2?0x1F0C0:0x1F0D0;
        int off;
        if(c.rank==14)off=1; else if(c.rank<=10)off=c.rank; else if(c.rank==11)off=0xB; else if(c.rank==12)off=0xD; else off=0xE;
        return new String(Character.toChars(base+off));
    }
    private TextView cardView(PokerMath.Card c){
        TextView v=text(playingCardGlyph(c),36,isRedSuit(c)?RED:Color.BLACK,true);
        v.setGravity(Gravity.CENTER);
        GradientDrawable g=rounded(Color.WHITE,8);g.setStroke(dp(1),Color.rgb(195,203,199));
        v.setBackground(g);
        v.setIncludeFontPadding(false);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(50),dp(64)));
        return v;
    }
    private TextView emptyCardView(){
        TextView v=text("—",18,Color.rgb(135,145,140),true);v.setGravity(Gravity.CENTER);
        GradientDrawable g=rounded(Color.rgb(225,231,228),8);g.setStroke(dp(1),Color.rgb(160,170,165));v.setBackground(g);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(50),dp(64)));return v;
    }'''
    ),
]

for old, new in replacements:
    if old not in s:
        raise SystemExit("Expected v0.2.3 source block not found:\n" + old[:180])
    s = s.replace(old, new, 1)

src.write_text(s, encoding="utf-8")
print("Applied Poker Vision v0.2.4 card-layout/card-icon patch")
