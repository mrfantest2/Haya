package com.fantest.pokervision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ScanReviewState {
    private final ArrayList<ScanModels.Proposal> proposals = new ArrayList<>();

    void replaceProposals(List<ScanModels.Proposal> next) {
        proposals.clear();
        if (next != null) proposals.addAll(next);
    }

    void clear() { proposals.clear(); }

    List<ScanModels.Proposal> proposals() {
        return Collections.unmodifiableList(new ArrayList<>(proposals));
    }

    void correct(int area, int index, PokerMath.Card card) {
        if (card == null) throw new IllegalArgumentException("Card cannot be null");
        for (int i = 0; i < proposals.size(); i++) {
            ScanModels.Proposal p = proposals.get(i);
            if (p.area == area && p.index == index) {
                proposals.set(i, p.corrected(card));
                return;
            }
        }
        throw new IllegalArgumentException("Proposal not found");
    }

    boolean validateAgainst(PokerTableState table) {
        if (table == null || proposals.isEmpty()) return false;
        Set<Integer> slots = new HashSet<>();
        Set<PokerMath.Card> proposalCards = new HashSet<>();

        for (ScanModels.Proposal p : proposals) {
            if (p == null || p.card == null) return false;
            if (!(p.status == ScanModels.Status.STABLE || p.status == ScanModels.Status.MANUAL)) return false;
            int ordinal = ordinalOrMinusOne(p.area, p.index);
            if (ordinal < 0 || !slots.add(ordinal)) return false;
            if (!proposalCards.add(p.card)) return false;
        }

        for (ScanModels.Proposal p : proposals) {
            for (int i = 0; i < 2; i++) {
                PokerMath.Card existing = table.holeAt(i);
                if (existing == null) continue;
                if (p.area == PokerTableState.AREA_HOLE && p.index == i) continue;
                if (existing.equals(p.card)) return false;
            }
            for (int i = 0; i < 5; i++) {
                PokerMath.Card existing = table.boardAt(i);
                if (existing == null) continue;
                if (p.area == PokerTableState.AREA_BOARD && p.index == i) continue;
                if (existing.equals(p.card)) return false;
            }
        }
        return true;
    }

    List<PokerTableState.SlotUpdate> prepareCommit(PokerTableState table) {
        if (!validateAgainst(table)) throw new IllegalStateException("Scan review is not confirmable");
        ArrayList<PokerTableState.SlotUpdate> out = new ArrayList<>(proposals.size());
        for (ScanModels.Proposal p : proposals) {
            out.add(new PokerTableState.SlotUpdate(p.area, p.index, p.card));
        }
        return out;
    }

    private static int ordinalOrMinusOne(int area, int index) {
        if (area == PokerTableState.AREA_HOLE && index >= 0 && index < 2) return index;
        if (area == PokerTableState.AREA_BOARD && index >= 0 && index < 5) return 2 + index;
        return -1;
    }
}
