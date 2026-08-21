package com.fantest.pokervision;

import java.util.List;

final class CardAvailability {
    private CardAvailability() {}

    static boolean isUsedElsewhere(
            PokerMath.Card candidate,
            PokerMath.Card currentSlotCard,
            List<PokerMath.Card> holeCards,
            List<PokerMath.Card> boardCards) {
        if (candidate == null) return false;

        int matches = countMatches(candidate, holeCards) + countMatches(candidate, boardCards);
        if (same(candidate, currentSlotCard) && matches > 0) matches--;
        return matches > 0;
    }

    private static int countMatches(PokerMath.Card candidate, List<PokerMath.Card> cards) {
        if (cards == null) return 0;
        int count = 0;
        for (PokerMath.Card card : cards) {
            if (same(candidate, card)) count++;
        }
        return count;
    }

    private static boolean same(PokerMath.Card a, PokerMath.Card b) {
        return a != null && b != null && a.rank == b.rank && a.suit == b.suit;
    }
}
