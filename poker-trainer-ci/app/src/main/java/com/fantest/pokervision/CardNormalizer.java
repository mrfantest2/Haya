package com.fantest.pokervision;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

final class CardNormalizer {
    static final class Result {
        final Mat card;
        final Mat index;
        final ScanModels.Quad sourceQuad;

        Result(Mat card, Mat index, ScanModels.Quad sourceQuad) {
            this.card = card;
            this.index = index;
            this.sourceQuad = sourceQuad;
        }

        void release() {
            if (index != null) index.release();
            if (card != null) card.release();
        }
    }

    Result normalize(Mat frameGray, ScanModels.Quad q) {
        if (frameGray == null || frameGray.empty() || q == null) {
            throw new IllegalArgumentException("Frame and card quad are required");
        }

        Point[] ordered = toPoints(q);
        double top = distance(ordered[0], ordered[1]);
        double left = distance(ordered[0], ordered[3]);
        if (top > left) {
            ordered = new Point[]{ordered[1], ordered[2], ordered[3], ordered[0]};
        }

        MatOfPoint2f src = new MatOfPoint2f(ordered);
        MatOfPoint2f dst = new MatOfPoint2f(
                new Point(0, 0),
                new Point(VisionConstants.CARD_WIDTH - 1, 0),
                new Point(VisionConstants.CARD_WIDTH - 1, VisionConstants.CARD_HEIGHT - 1),
                new Point(0, VisionConstants.CARD_HEIGHT - 1));
        Mat transform = Imgproc.getPerspectiveTransform(src, dst);
        Mat warped = new Mat(VisionConstants.CARD_HEIGHT, VisionConstants.CARD_WIDTH, CvType.CV_8UC1);
        Imgproc.warpPerspective(frameGray, warped, transform,
                new Size(VisionConstants.CARD_WIDTH, VisionConstants.CARD_HEIGHT),
                Imgproc.INTER_LINEAR);

        Mat rotated = new Mat();
        Core.rotate(warped, rotated, Core.ROTATE_180);
        double normalScore = indexInkScore(warped);
        double rotatedScore = indexInkScore(rotated);
        Mat oriented;
        if (rotatedScore > normalScore * 1.08) {
            oriented = rotated;
            warped.release();
        } else {
            oriented = warped;
            rotated.release();
        }

        Rect indexRect = new Rect(0, 0,
                Math.min(VisionConstants.INDEX_WIDTH, oriented.cols()),
                Math.min(VisionConstants.INDEX_HEIGHT, oriented.rows()));
        Mat index = new Mat(oriented, indexRect).clone();

        src.release(); dst.release(); transform.release();
        return new Result(oriented, index, q);
    }

    private static double indexInkScore(Mat card) {
        if (card == null || card.empty()) return 0;
        Rect r = new Rect(0, 0,
                Math.min(VisionConstants.INDEX_WIDTH, card.cols()),
                Math.min(VisionConstants.INDEX_HEIGHT, card.rows()));
        Mat crop = new Mat(card, r);
        Mat binary = new Mat();
        Imgproc.threshold(crop, binary, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);
        double score = Core.countNonZero(binary);
        binary.release(); crop.release();
        return score;
    }

    private static Point[] toPoints(ScanModels.Quad q) {
        return new Point[]{
                new Point(q.p0.x, q.p0.y),
                new Point(q.p1.x, q.p1.y),
                new Point(q.p2.x, q.p2.y),
                new Point(q.p3.x, q.p3.y)
        };
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }
}
