package com.fantest.pokervision;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PokerMathTest {
    @Test public void straightFlushIsRecognized() {
        List<PokerMath.Card> cards = Arrays.asList(
                new PokerMath.Card(14,0), new PokerMath.Card(13,0),
                new PokerMath.Card(12,0), new PokerMath.Card(11,0),
                new PokerMath.Card(10,0));
        assertEquals("Straight Flush", PokerMath.handNameFor(cards));
    }

    @Test public void fullHouseBeatsFlushCategory() {
        List<PokerMath.Card> cards = Arrays.asList(
                new PokerMath.Card(13,0), new PokerMath.Card(13,1),
                new PokerMath.Card(13,2), new PokerMath.Card(6,0),
                new PokerMath.Card(6,1));
        assertEquals("Full House", PokerMath.handNameFor(cards));
    }

    @Test public void monteCarloPercentagesAddToHundred() {
        List<PokerMath.Card> hole = Arrays.asList(new PokerMath.Card(14,0), new PokerMath.Card(13,0));
        List<PokerMath.Card> board = Arrays.asList(new PokerMath.Card(12,0), new PokerMath.Card(11,0), new PokerMath.Card(5,2));
        PokerMath.Result r = PokerMath.calculate(hole, board, 1, 600, 12345L);
        assertEquals(100.0, r.win + r.tie + r.lose, 0.01);
        assertTrue(r.equity >= 0.0 && r.equity <= 100.0);
        assertEquals("High Card", r.currentHand);
    }
}
