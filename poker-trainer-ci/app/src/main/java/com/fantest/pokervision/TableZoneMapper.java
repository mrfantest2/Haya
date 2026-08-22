package com.fantest.pokervision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class TableZoneMapper {
    static final class Result {
        final List<ScanModels.TrackedDetection> hole;
        final List<ScanModels.TrackedDetection> board;
        final List<ScanModels.TrackedDetection> unassigned;
        final boolean capacityError;

        Result(List<ScanModels.TrackedDetection> hole,
               List<ScanModels.TrackedDetection> board,
               List<ScanModels.TrackedDetection> unassigned,
               boolean capacityError) {
            this.hole = Collections.unmodifiableList(hole);
            this.board = Collections.unmodifiableList(board);
            this.unassigned = Collections.unmodifiableList(unassigned);
            this.capacityError = capacityError;
        }
    }

    Result map(List<ScanModels.TrackedDetection> detections) {
        ArrayList<ScanModels.TrackedDetection> hole = new ArrayList<>();
        ArrayList<ScanModels.TrackedDetection> board = new ArrayList<>();
        ArrayList<ScanModels.TrackedDetection> unassigned = new ArrayList<>();

        if (detections != null) {
            for (ScanModels.TrackedDetection detection : detections) {
                if (detection == null || detection.quad == null) continue;
                double x = detection.quad.centerX();
                double y = detection.quad.centerY();
                if (inside(x, y,
                        VisionConstants.BOARD_X_MIN, VisionConstants.BOARD_X_MAX,
                        VisionConstants.BOARD_Y_MIN, VisionConstants.BOARD_Y_MAX)) {
                    board.add(detection);
                } else if (inside(x, y,
                        VisionConstants.HOLE_X_MIN, VisionConstants.HOLE_X_MAX,
                        VisionConstants.HOLE_Y_MIN, VisionConstants.HOLE_Y_MAX)) {
                    hole.add(detection);
                } else {
                    unassigned.add(detection);
                }
            }
        }

        Comparator<ScanModels.TrackedDetection> leftToRight = Comparator.comparingDouble(d -> d.quad.centerX());
        hole.sort(leftToRight);
        board.sort(leftToRight);
        return new Result(hole, board, unassigned, hole.size() > 2 || board.size() > 5);
    }

    List<ScanModels.Proposal> proposals(Result result) {
        if (result == null || result.capacityError) return Collections.emptyList();
        ArrayList<ScanModels.Proposal> out = new ArrayList<>();
        for (int i = 0; i < result.hole.size(); i++) {
            ScanModels.TrackedDetection d = result.hole.get(i);
            out.add(new ScanModels.Proposal(PokerTableState.AREA_HOLE, i, d.card, d.confidence, d.quad, d.status, d.status == ScanModels.Status.MANUAL));
        }
        for (int i = 0; i < result.board.size(); i++) {
            ScanModels.TrackedDetection d = result.board.get(i);
            out.add(new ScanModels.Proposal(PokerTableState.AREA_BOARD, i, d.card, d.confidence, d.quad, d.status, d.status == ScanModels.Status.MANUAL));
        }
        return out;
    }

    private static boolean inside(double x, double y, double minX, double maxX, double minY, double maxY) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }
}
