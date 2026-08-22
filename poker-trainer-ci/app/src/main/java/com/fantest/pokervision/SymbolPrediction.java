package com.fantest.pokervision;

final class SymbolPrediction<T> {
    final T value;
    final double imageScore;

    SymbolPrediction(T value, double imageScore) {
        this.value = value;
        this.imageScore = clamp(imageScore);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
