package com.fantest.pokervision;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class CardPickerModelTest {
    @Test public void startsOnSuitStep() {
        CardPickerModel m = new CardPickerModel(Collections.emptySet(), null);
        assertEquals(CardPickerModel.STEP_SUIT, m.step());
    }

    @Test public void choosingSuitEnablesValueStep() {
        CardPickerModel m = new CardPickerModel(Collections.emptySet(), null);
        m.chooseSuit(1);
        assertEquals(CardPickerModel.STEP_RANK, m.step());
        assertEquals(1, m.selectedSuit());
    }

    @Test public void blockedExactCardDisablesOnlyThatRankForChosenSuit() {
        Set<PokerMath.Card> blocked = new HashSet<>();
        blocked.add(new PokerMath.Card(12, 1));
        CardPickerModel m = new CardPickerModel(blocked, null);
        m.chooseSuit(1);
        assertFalse(m.isRankEnabled(12));
        assertTrue(m.isRankEnabled(11));
    }

    @Test public void currentCardRemainsSelectableWhileEditing() {
        PokerMath.Card current = new PokerMath.Card(12, 1);
        Set<PokerMath.Card> blocked = new HashSet<>();
        blocked.add(current);
        CardPickerModel m = new CardPickerModel(blocked, current);
        m.chooseSuit(1);
        assertTrue(m.isRankEnabled(12));
    }

    @Test public void choosingRankReturnsCompletedCardImmediately() {
        CardPickerModel m = new CardPickerModel(Collections.emptySet(), null);
        m.chooseSuit(2);
        assertEquals(new PokerMath.Card(14, 2), m.chooseRank(14));
    }

    @Test(expected = IllegalStateException.class)
    public void choosingRankBeforeSuitIsRejected() {
        new CardPickerModel(Collections.emptySet(), null).chooseRank(14);
    }
}
