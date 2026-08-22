package com.fantest.pokervision;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class CardPickerModel {
    static final int STEP_SUIT = 0;
    static final int STEP_RANK = 1;

    private final Set<PokerMath.Card> blocked;
    private int step = STEP_SUIT;
    private int selectedSuit = -1;

    CardPickerModel(Set<PokerMath.Card> blockedCards, PokerMath.Card currentCard) {
        HashSet<PokerMath.Card> copy = new HashSet<>();
        if (blockedCards != null) copy.addAll(blockedCards);
        if (currentCard != null) copy.remove(currentCard);
        blocked = Collections.unmodifiableSet(copy);
    }

    int step() { return step; }
    int selectedSuit() { return selectedSuit; }

    void chooseSuit(int suit) {
        if (suit < 0 || suit > 3) throw new IllegalArgumentException("Suit out of range");
        selectedSuit = suit;
        step = STEP_RANK;
    }

    void backToSuit() {
        selectedSuit = -1;
        step = STEP_SUIT;
    }

    boolean isRankEnabled(int rank) {
        if (rank < 2 || rank > 14) return false;
        if (selectedSuit < 0) return false;
        return !blocked.contains(new PokerMath.Card(rank, selectedSuit));
    }

    PokerMath.Card chooseRank(int rank) {
        if (selectedSuit < 0 || step != STEP_RANK) {
            throw new IllegalStateException("Choose suit before rank");
        }
        if (rank < 2 || rank > 14) throw new IllegalArgumentException("Rank out of range");
        PokerMath.Card card = new PokerMath.Card(rank, selectedSuit);
        if (blocked.contains(card)) throw new IllegalArgumentException("Card is already used");
        return card;
    }
}
