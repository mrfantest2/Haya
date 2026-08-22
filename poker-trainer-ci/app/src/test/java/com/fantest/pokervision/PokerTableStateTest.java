package com.fantest.pokervision;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class PokerTableStateTest {
    @Test public void exactSlotCanBeSelectedAndFilled() {
        PokerTableState s = new PokerTableState();
        PokerMath.Card aceSpades = new PokerMath.Card(14, 0);
        s.selectBoard(3);
        assertTrue(s.setSelected(aceSpades));
        assertEquals(aceSpades, s.boardAt(3));
        assertNull(s.boardAt(0));
    }

    @Test public void selectedSlotCanBeReplaced() {
        PokerTableState s = new PokerTableState();
        s.selectHole(0);
        assertTrue(s.setSelected(new PokerMath.Card(14, 0)));
        assertTrue(s.setSelected(new PokerMath.Card(13, 1)));
        assertEquals(new PokerMath.Card(13, 1), s.holeAt(0));
    }

    @Test public void duplicateCardIsRejectedAcrossSlots() {
        PokerTableState s = new PokerTableState();
        PokerMath.Card queenHearts = new PokerMath.Card(12, 1);
        s.selectHole(0);
        assertTrue(s.setSelected(queenHearts));
        s.selectBoard(2);
        assertFalse(s.setSelected(queenHearts));
        assertNull(s.boardAt(2));
    }

    @Test public void autoAdvanceFollowsNaturalTableOrder() {
        PokerTableState s = new PokerTableState();
        s.selectHole(0);
        assertTrue(s.setSelected(new PokerMath.Card(14, 0)));
        assertTrue(s.selectNextEmpty());
        assertEquals(PokerTableState.AREA_HOLE, s.selectedArea());
        assertEquals(1, s.selectedIndex());
        assertTrue(s.setSelected(new PokerMath.Card(13, 0)));
        assertTrue(s.selectNextEmpty());
        assertEquals(PokerTableState.AREA_BOARD, s.selectedArea());
        assertEquals(0, s.selectedIndex());
    }

    @Test public void listsContainOnlyFilledSlots() {
        PokerTableState s = new PokerTableState();
        PokerMath.Card a = new PokerMath.Card(14, 0);
        PokerMath.Card k = new PokerMath.Card(13, 1);
        PokerMath.Card q = new PokerMath.Card(12, 2);
        s.selectHole(0); s.setSelected(a);
        s.selectHole(1); s.setSelected(k);
        s.selectBoard(4); s.setSelected(q);
        assertEquals(Arrays.asList(a, k), s.holeCards());
        assertEquals(Arrays.asList(q), s.boardCards());
    }

    @Test public void invalidBatchDoesNotPartiallyMutateTable() {
        PokerTableState t = new PokerTableState();
        t.selectHole(0);
        assertTrue(t.setSelected(new PokerMath.Card(14, 0)));
        int areaBefore = t.selectedArea();
        int indexBefore = t.selectedIndex();
        List<PokerTableState.SlotUpdate> updates = Arrays.asList(
                new PokerTableState.SlotUpdate(PokerTableState.AREA_BOARD, 0, new PokerMath.Card(13, 1)),
                new PokerTableState.SlotUpdate(PokerTableState.AREA_BOARD, 1, new PokerMath.Card(14, 0))
        );
        assertFalse(t.applyBatch(updates));
        assertNull(t.boardAt(0));
        assertNull(t.boardAt(1));
        assertEquals(areaBefore, t.selectedArea());
        assertEquals(indexBefore, t.selectedIndex());
    }

    @Test public void validBatchCommitsEverySlotTogether() {
        PokerTableState t = new PokerTableState();
        List<PokerTableState.SlotUpdate> updates = Arrays.asList(
                new PokerTableState.SlotUpdate(PokerTableState.AREA_HOLE, 0, new PokerMath.Card(14, 0)),
                new PokerTableState.SlotUpdate(PokerTableState.AREA_HOLE, 1, new PokerMath.Card(13, 0)),
                new PokerTableState.SlotUpdate(PokerTableState.AREA_BOARD, 0, new PokerMath.Card(12, 1))
        );
        assertTrue(t.applyBatch(updates));
        assertEquals(new PokerMath.Card(14, 0), t.holeAt(0));
        assertEquals(new PokerMath.Card(13, 0), t.holeAt(1));
        assertEquals(new PokerMath.Card(12, 1), t.boardAt(0));
    }
}
