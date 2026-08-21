package com.fantest.pokervision;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CardAvailabilityTest {
    @Test
    public void cardUsedInAnotherSlotIsUnavailable() {
        PokerMath.Card aceSpades = new PokerMath.Card(14, 0);
        PokerMath.Card kingHearts = new PokerMath.Card(13, 1);
        List<PokerMath.Card> hole = Arrays.asList(aceSpades, kingHearts);

        assertTrue(CardAvailability.isUsedElsewhere(aceSpades, null, hole, Collections.emptyList()));
    }

    @Test
    public void cardInCurrentEditedSlotRemainsAvailable() {
        PokerMath.Card aceSpades = new PokerMath.Card(14, 0);
        PokerMath.Card kingHearts = new PokerMath.Card(13, 1);
        List<PokerMath.Card> hole = Arrays.asList(aceSpades, kingHearts);

        assertFalse(CardAvailability.isUsedElsewhere(aceSpades, aceSpades, hole, Collections.emptyList()));
    }

    @Test
    public void unusedCardRemainsAvailable() {
        PokerMath.Card aceSpades = new PokerMath.Card(14, 0);
        PokerMath.Card queenDiamonds = new PokerMath.Card(12, 2);
        List<PokerMath.Card> board = Collections.singletonList(queenDiamonds);

        assertFalse(CardAvailability.isUsedElsewhere(aceSpades, null, Collections.emptyList(), board));
    }
}
