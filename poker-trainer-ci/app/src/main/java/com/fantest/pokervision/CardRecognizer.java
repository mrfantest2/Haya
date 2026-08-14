package com.fantest.pokervision;

import com.google.mlkit.vision.text.Text;
import java.util.*;
import java.util.regex.*;

public final class CardRecognizer {
    private CardRecognizer() {}
    public static List<PokerMath.Card> extract(Text result) {
        LinkedHashSet<PokerMath.Card> found = new LinkedHashSet<>();
        if (result == null) return new ArrayList<>();
        for (Text.TextBlock block : result.getTextBlocks()) {
            parse(block.getText(), found);
            for (Text.Line line : block.getLines()) parse(line.getText(), found);
        }
        parse(result.getText(), found);
        return new ArrayList<>(found);
    }
    private static void parse(String raw, Set<PokerMath.Card> out) {
        if (raw == null) return;
        String s = raw.toUpperCase(Locale.US)
                .replace("SPADES","S").replace("SPADE","S")
                .replace("HEARTS","H").replace("HEART","H")
                .replace("DIAMONDS","D").replace("DIAMOND","D")
                .replace("CLUBS","C").replace("CLUB","C")
                .replaceAll("\\s+", "");
        Pattern p = Pattern.compile("(10|[2-9AJQK])([SHDC♠♥♦♣])");
        Matcher m=p.matcher(s);
        while(m.find()) add(m.group(1), m.group(2).charAt(0), out);
        Pattern rev=Pattern.compile("([SHDC♠♥♦♣])(10|[2-9AJQK])");
        m=rev.matcher(s);
        while(m.find()) add(m.group(2), m.group(1).charAt(0), out);
    }
    private static void add(String rank, char suit, Set<PokerMath.Card> out) {
        int r=PokerMath.rankFromText(rank), s=PokerMath.suitFromChar(suit);
        if(r>=2&&s>=0) out.add(new PokerMath.Card(r,s));
    }
}
