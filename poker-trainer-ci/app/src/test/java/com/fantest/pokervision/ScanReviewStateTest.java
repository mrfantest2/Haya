package com.fantest.pokervision;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ScanReviewStateTest {
    private static ScanModels.Quad quad(double x, double y) {
        return ScanModels.Quad.fromBounds(x - .03, y - .04, x + .03, y + .04);
    }

    private static ScanModels.Proposal p(int area, int index, PokerMath.Card card, ScanModels.Status status) {
        return new ScanModels.Proposal(area, index, card, .90, quad(.3 + index * .1, area == PokerTableState.AREA_HOLE ? .7 : .3), status, status == ScanModels.Status.MANUAL);
    }

    @Test public void stableUniqueProposalsPrepareAtomicCommit() {
        ScanReviewState review = new ScanReviewState();
        review.replaceProposals(Arrays.asList(
                p(PokerTableState.AREA_HOLE, 0, new PokerMath.Card(14, 0), ScanModels.Status.STABLE),
                p(PokerTableState.AREA_HOLE, 1, new PokerMath.Card(13, 0), ScanModels.Status.STABLE),
                p(PokerTableState.AREA_BOARD, 0, new PokerMath.Card(12, 1), ScanModels.Status.STABLE)
        ));
        PokerTableState table = new PokerTableState();
        assertTrue(review.validateAgainst(table));
        List<PokerTableState.SlotUpdate> updates = review.prepareCommit(table);
        assertEquals(3, updates.size());
        assertTrue(table.applyBatch(updates));
        assertEquals(new PokerMath.Card(12, 1), table.boardAt(0));
    }

    @Test public void duplicateAgainstConfirmedTableIsInvalid() {
        PokerTableState table = new PokerTableState();
        table.selectHole(0);
        table.setSelected(new PokerMath.Card(14, 0));

        ScanReviewState review = new ScanReviewState();
        review.replaceProposals(Arrays.asList(
                p(PokerTableState.AREA_BOARD, 0, new PokerMath.Card(14, 0), ScanModels.Status.STABLE)
        ));
        assertFalse(review.validateAgainst(table));
    }

    @Test public void duplicateWithinProposalsIsInvalid() {
        PokerMath.Card qh = new PokerMath.Card(12, 1);
        ScanReviewState review = new ScanReviewState();
        review.replaceProposals(Arrays.asList(
                p(PokerTableState.AREA_HOLE, 0, qh, ScanModels.Status.STABLE),
                p(PokerTableState.AREA_BOARD, 0, qh, ScanModels.Status.STABLE)
        ));
        assertFalse(review.validateAgainst(new PokerTableState()));
    }

    @Test public void manualCorrectionBecomesConfirmable() {
        ScanReviewState review = new ScanReviewState();
        review.replaceProposals(Arrays.asList(
                p(PokerTableState.AREA_BOARD, 0, new PokerMath.Card(10, 2), ScanModels.Status.NEUTRAL)
        ));
        assertFalse(review.validateAgainst(new PokerTableState()));
        review.correct(PokerTableState.AREA_BOARD, 0, new PokerMath.Card(9, 2));
        assertTrue(review.validateAgainst(new PokerTableState()));
        assertEquals(ScanModels.Status.MANUAL, review.proposals().get(0).status);
    }

    @Test(expected = IllegalStateException.class)
    public void prepareCommitRejectsUnconfirmableProposal() {
        ScanReviewState review = new ScanReviewState();
        review.replaceProposals(Arrays.asList(
                p(PokerTableState.AREA_BOARD, 0, new PokerMath.Card(10, 2), ScanModels.Status.AMBER)
        ));
        review.prepareCommit(new PokerTableState());
    }
}
