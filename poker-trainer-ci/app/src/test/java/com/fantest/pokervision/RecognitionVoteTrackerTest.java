package com.fantest.pokervision;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class RecognitionVoteTrackerTest {
    private static final PokerMath.Card AS = new PokerMath.Card(14, 0);
    private static final PokerMath.Card KH = new PokerMath.Card(13, 1);

    private static ScanModels.Observation o(PokerMath.Card card, double confidence, double x) {
        return new ScanModels.Observation(
                ScanModels.Quad.fromBounds(x - .04, .20, x + .04, .32), card, confidence);
    }

    @Test public void fourOfFiveHighConfidenceVotesBecomeStable() {
        RecognitionVoteTracker tracker = new RecognitionVoteTracker();
        List<ScanModels.TrackedDetection> out = Collections.emptyList();
        out = tracker.update(0, Arrays.asList(o(AS, .90, .30)));
        out = tracker.update(100, Arrays.asList(o(AS, .91, .30)));
        out = tracker.update(200, Arrays.asList(o(KH, .92, .30)));
        out = tracker.update(300, Arrays.asList(o(AS, .89, .30)));
        out = tracker.update(400, Arrays.asList(o(AS, .93, .30)));
        assertEquals(1, out.size());
        assertEquals(ScanModels.Status.STABLE, out.get(0).status);
        assertEquals(AS, out.get(0).card);
    }

    @Test public void oneConflictingFrameDoesNotReplaceStableCard() {
        RecognitionVoteTracker tracker = new RecognitionVoteTracker();
        for (int i = 0; i < 4; i++) tracker.update(i * 100L, Arrays.asList(o(AS, .9, .3)));
        List<ScanModels.TrackedDetection> out = tracker.update(450, Arrays.asList(o(KH, .95, .3)));
        assertEquals(ScanModels.Status.STABLE, out.get(0).status);
        assertEquals(AS, out.get(0).card);
    }

    @Test public void newIdentityNeedsFreshFourOfFiveBeforeReplacingStableCard() {
        RecognitionVoteTracker tracker = new RecognitionVoteTracker();
        for (int i = 0; i < 4; i++) tracker.update(i * 100L, Arrays.asList(o(AS, .9, .3)));
        for (int i = 0; i < 3; i++) {
            List<ScanModels.TrackedDetection> out = tracker.update(500 + i * 100L, Arrays.asList(o(KH, .95, .3)));
            assertEquals(AS, out.get(0).card);
        }
        List<ScanModels.TrackedDetection> out = tracker.update(800, Arrays.asList(o(KH, .95, .3)));
        assertEquals(KH, out.get(0).card);
        assertEquals(ScanModels.Status.STABLE, out.get(0).status);
    }

    @Test public void unmatchedTrackExpiresAfter1200ms() {
        RecognitionVoteTracker tracker = new RecognitionVoteTracker();
        tracker.update(0, Arrays.asList(o(AS, .9, .3)));
        assertEquals(1, tracker.update(1200, Collections.emptyList()).size());
        assertTrue(tracker.update(1201, Collections.emptyList()).isEmpty());
    }

    @Test public void closeCenterCanAssociateAfterLowIouMotion() {
        RecognitionVoteTracker tracker = new RecognitionVoteTracker();
        List<ScanModels.TrackedDetection> first = tracker.update(0, Arrays.asList(
                new ScanModels.Observation(ScanModels.Quad.fromBounds(.20, .20, .30, .40), AS, .9)));
        long trackId = first.get(0).trackId;
        List<ScanModels.TrackedDetection> second = tracker.update(100, Arrays.asList(
                new ScanModels.Observation(ScanModels.Quad.fromBounds(.215, .20, .315, .40), AS, .9)));
        assertEquals(1, second.size());
        assertEquals(trackId, second.get(0).trackId);
    }
}
