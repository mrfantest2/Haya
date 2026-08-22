package com.fantest.pokervision;

final class CardRecognitionFusion {
    double adjust(double imageScore, boolean ocrAgrees, boolean ocrDisagrees) {
        double score = clamp(imageScore);
        if (ocrAgrees) score = Math.min(1.0, score + .10);
        else if (ocrDisagrees) score = Math.max(0.0, score - .15);
        return score;
    }

    double ocrOnlyConfidence() { return VisionConstants.OCR_ONLY_CAP; }

    ScanModels.CardIdentity fuse(SymbolPrediction<Integer> rank,
                                 SymbolPrediction<Integer> suit,
                                 CardRecognizer.Hints hints) {
        Integer hintedRank = hints == null ? null : hints.rank;
        Integer hintedSuit = hints == null ? null : hints.suit;

        if (rank == null || rank.value == null || suit == null || suit.value == null) {
            if (hintedRank != null && hintedSuit != null) {
                return new ScanModels.CardIdentity(
                        new PokerMath.Card(hintedRank, hintedSuit), VisionConstants.OCR_ONLY_CAP);
            }
            return new ScanModels.CardIdentity(null, 0.0);
        }

        boolean rankAgree = hintedRank != null && hintedRank.equals(rank.value);
        boolean rankDisagree = hintedRank != null && !hintedRank.equals(rank.value);
        boolean suitAgree = hintedSuit != null && hintedSuit.equals(suit.value);
        boolean suitDisagree = hintedSuit != null && !hintedSuit.equals(suit.value);

        double rankConfidence = adjust(rank.imageScore, rankAgree, rankDisagree);
        double suitConfidence = adjust(suit.imageScore, suitAgree, suitDisagree);
        return new ScanModels.CardIdentity(
                new PokerMath.Card(rank.value, suit.value),
                Math.min(rankConfidence, suitConfidence));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
