package com.fantest.pokervision;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java state model for the seven visible Texas Hold'em card slots.
 * Keeps UI targeting, replacement and duplicate-card checks out of the Activity.
 */
public final class PokerTableState {
    public static final int AREA_HOLE = 0;
    public static final int AREA_BOARD = 1;

    private final PokerMath.Card[] hole = new PokerMath.Card[2];
    private final PokerMath.Card[] board = new PokerMath.Card[5];
    private int selectedArea = AREA_HOLE;
    private int selectedIndex = 0;

    public int selectedArea() { return selectedArea; }
    public int selectedIndex() { return selectedIndex; }

    public void selectHole(int index) {
        if (index < 0 || index >= hole.length) throw new IllegalArgumentException("Hole slot out of range");
        selectedArea = AREA_HOLE;
        selectedIndex = index;
    }

    public void selectBoard(int index) {
        if (index < 0 || index >= board.length) throw new IllegalArgumentException("Board slot out of range");
        selectedArea = AREA_BOARD;
        selectedIndex = index;
    }

    public PokerMath.Card selectedCard() {
        return selectedArea == AREA_HOLE ? hole[selectedIndex] : board[selectedIndex];
    }

    public PokerMath.Card holeAt(int index) { return hole[index]; }
    public PokerMath.Card boardAt(int index) { return board[index]; }

    public boolean setSelected(PokerMath.Card card) {
        if (card == null) throw new IllegalArgumentException("Card cannot be null");
        if (existsElsewhere(card, selectedArea, selectedIndex)) return false;
        if (selectedArea == AREA_HOLE) hole[selectedIndex] = card;
        else board[selectedIndex] = card;
        return true;
    }

    public void clearSelected() {
        if (selectedArea == AREA_HOLE) hole[selectedIndex] = null;
        else board[selectedIndex] = null;
    }

    public void clearHole() {
        for (int i = 0; i < hole.length; i++) hole[i] = null;
        selectHole(0);
    }

    public void clearBoard() {
        for (int i = 0; i < board.length; i++) board[i] = null;
        selectBoard(0);
    }

    public void clearAll() {
        for (int i = 0; i < hole.length; i++) hole[i] = null;
        for (int i = 0; i < board.length; i++) board[i] = null;
        selectHole(0);
    }

    public int holeCount() {
        int n = 0;
        for (PokerMath.Card c : hole) if (c != null) n++;
        return n;
    }

    public int boardCount() {
        int n = 0;
        for (PokerMath.Card c : board) if (c != null) n++;
        return n;
    }

    public boolean holeComplete() { return holeCount() == 2; }
    public boolean boardComplete() { return boardCount() == 5; }

    public List<PokerMath.Card> holeCards() {
        ArrayList<PokerMath.Card> out = new ArrayList<>(2);
        for (PokerMath.Card c : hole) if (c != null) out.add(c);
        return out;
    }

    public List<PokerMath.Card> boardCards() {
        ArrayList<PokerMath.Card> out = new ArrayList<>(5);
        for (PokerMath.Card c : board) if (c != null) out.add(c);
        return out;
    }

    public boolean contains(PokerMath.Card card) {
        if (card == null) return false;
        for (PokerMath.Card c : hole) if (card.equals(c)) return true;
        for (PokerMath.Card c : board) if (card.equals(c)) return true;
        return false;
    }

    /** Select the next empty slot in natural play order: hole 1, hole 2, board 1..5. */
    public boolean selectNextEmpty() {
        int currentOrdinal = ordinal(selectedArea, selectedIndex);
        for (int step = 1; step <= 7; step++) {
            int ord = (currentOrdinal + step) % 7;
            if (cardAtOrdinal(ord) == null) {
                selectOrdinal(ord);
                return true;
            }
        }
        return false;
    }

    public boolean selectFirstEmpty() {
        for (int ord = 0; ord < 7; ord++) {
            if (cardAtOrdinal(ord) == null) {
                selectOrdinal(ord);
                return true;
            }
        }
        return false;
    }

    private boolean existsElsewhere(PokerMath.Card card, int area, int index) {
        for (int i = 0; i < hole.length; i++) {
            if (area == AREA_HOLE && index == i) continue;
            if (card.equals(hole[i])) return true;
        }
        for (int i = 0; i < board.length; i++) {
            if (area == AREA_BOARD && index == i) continue;
            if (card.equals(board[i])) return true;
        }
        return false;
    }

    private int ordinal(int area, int index) {
        return area == AREA_HOLE ? index : 2 + index;
    }

    private PokerMath.Card cardAtOrdinal(int ordinal) {
        return ordinal < 2 ? hole[ordinal] : board[ordinal - 2];
    }

    private void selectOrdinal(int ordinal) {
        if (ordinal < 2) selectHole(ordinal);
        else selectBoard(ordinal - 2);
    }
}
