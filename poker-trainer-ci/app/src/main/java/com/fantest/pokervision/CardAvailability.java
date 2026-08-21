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

        boolean currentOccurrenceSkipped = false;
        currentOccurrenceSkipped = scan(candidate, currentSlotCard, holeCards, currentOccurrenceSkipped);
        if (currentOccurrenceSkipped == USED_ELSEWHERE) return true;
        return scan(candidate, currentSlotCard, boardCards, currentOccurrenceSkipped) == USED_ELSEWHERE;
    }

    private static final boolean USED_ELSEWHERE = true;

    private static boolean scan(
            PokerMath.Card candidate,
            PokerMath.Card currentSlotCard,
            List<PokerMath.Card> cards,
            boolean currentOccurrenceSkipped) {
        if (cards == null) return currentOccurrenceSkipped;
        for (PokerMath.Card card : cards) {
            if (!same(candidate, card)) continue;
            if (!currentOccurrenceSkipped && same(candidate, currentSlotCard)) {
                currentOccurrenceSkipped = true;
                continue;
            }
            return USED_ELSEWHERE;
        }
        return currentOccurrenceSkipped;
    }

    private static boolean same(PokerMath.Card a, PokerMath.Card b) {
        return a != null && b != null && a.rank == b.rank && a.suit == b.suit;
    }
}
