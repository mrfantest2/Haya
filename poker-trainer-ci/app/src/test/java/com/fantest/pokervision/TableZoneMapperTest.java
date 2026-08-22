package com.fantest.pokervision;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class TableZoneMapperTest {
    private static ScanModels.TrackedDetection d(long id, double x, double y) {
        return new ScanModels.TrackedDetection(
                id,
                ScanModels.Quad.fromBounds(x - .025, y - .04, x + .025, y + .04),
                new PokerMath.Card(14 - (int) id, (int) (id % 4)),
                .90,
                ScanModels.Status.STABLE,
                1000L);
    }

    @Test public void boardCardsSortLeftToRight() {
        TableZoneMapper.Result result = new TableZoneMapper().map(Arrays.asList(
                d(1, .70, .30), d(2, .20, .30), d(3, .45, .30)));
        assertFalse(result.capacityError);
        assertEquals(3, result.board.size());
        assertEquals(.20, result.board.get(0).quad.centerX(), .0001);
        assertEquals(.45, result.board.get(1).quad.centerX(), .0001);
        assertEquals(.70, result.board.get(2).quad.centerX(), .0001);
    }

    @Test public void holeZoneCapsAtTwo() {
        TableZoneMapper.Result result = new TableZoneMapper().map(Arrays.asList(
                d(1, .30, .70), d(2, .50, .70), d(3, .70, .70)));
        assertTrue(result.capacityError);
        assertEquals(3, result.hole.size());
    }

    @Test public void outsideBothZonesIsUnassigned() {
        TableZoneMapper.Result result = new TableZoneMapper().map(Arrays.asList(d(1, .50, .53)));
        assertEquals(1, result.unassigned.size());
        assertTrue(result.board.isEmpty());
        assertTrue(result.hole.isEmpty());
    }

    @Test public void zoneEdgesAreInclusive() {
        TableZoneMapper.Result result = new TableZoneMapper().map(Arrays.asList(
                d(1, VisionConstants.BOARD_X_MIN, VisionConstants.BOARD_Y_MIN),
                d(2, VisionConstants.HOLE_X_MAX, VisionConstants.HOLE_Y_MAX)));
        assertEquals(1, result.board.size());
        assertEquals(1, result.hole.size());
    }
}
