package com.fantest.pokervision;

final class VisionConstants {
    private VisionConstants() {}

    static final int DISCOVERY_LONG_EDGE = 960;
    static final long RECOGNITION_INTERVAL_MS = 250L;
    static final int CARD_WIDTH = 350;
    static final int CARD_HEIGHT = 500;
    static final int INDEX_WIDTH = 105;
    static final int INDEX_HEIGHT = 190;

    static final double CARD_RATIO_MIN = 0.58;
    static final double CARD_RATIO_MAX = 0.78;
    static final double CARD_AREA_MIN = 0.015;
    static final double CARD_AREA_MAX = 0.30;
    static final double DUPLICATE_IOU = 0.70;
    static final double TRACK_IOU = 0.35;
    static final double TRACK_CENTER_DISTANCE_FACTOR = 0.12;
    static final long TRACK_EXPIRY_MS = 1200L;

    static final double HIGH_CONFIDENCE = 0.78;
    static final double AMBER_CONFIDENCE = 0.55;
    static final double OCR_ONLY_CAP = 0.49;
    static final int STABLE_VOTES = 4;
    static final int VOTE_WINDOW = 5;

    static final double BOARD_X_MIN = 0.05;
    static final double BOARD_X_MAX = 0.95;
    static final double BOARD_Y_MIN = 0.08;
    static final double BOARD_Y_MAX = 0.48;
    static final double HOLE_X_MIN = 0.18;
    static final double HOLE_X_MAX = 0.82;
    static final double HOLE_Y_MIN = 0.58;
    static final double HOLE_Y_MAX = 0.92;
}
