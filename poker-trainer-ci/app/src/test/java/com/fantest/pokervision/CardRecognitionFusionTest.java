package com.fantest.pokervision;

import org.junit.Test;
import static org.junit.Assert.*;

public class CardRecognitionFusionTest {
    @Test public void agreeingOcrBoostsImagePrediction() {
        CardRecognitionFusion f = new CardRecognitionFusion();
        assertEquals(.90, f.adjust(.80, true, false), .0001);
    }

    @Test public void disagreeingOcrPenalizesImagePrediction() {
        CardRecognitionFusion f = new CardRecognitionFusion();
        assertEquals(.65, f.adjust(.80, false, true), .0001);
    }

    @Test public void ocrOnlyCannotBecomeConfirmable() {
        CardRecognitionFusion f = new CardRecognitionFusion();
        assertEquals(VisionConstants.OCR_ONLY_CAP, f.ocrOnlyConfidence(), .0001);
        assertTrue(f.ocrOnlyConfidence() < VisionConstants.AMBER_CONFIDENCE);
    }

    @Test public void overallConfidenceUsesWeakerHalf() {
        CardRecognitionFusion f = new CardRecognitionFusion();
        SymbolPrediction<Integer> rank = new SymbolPrediction<>(14, .91);
        SymbolPrediction<Integer> suit = new SymbolPrediction<>(0, .79);
        ScanModels.CardIdentity id = f.fuse(rank, suit, null);
        assertEquals(new PokerMath.Card(14, 0), id.card);
        assertEquals(.79, id.confidence, .0001);
    }
}
