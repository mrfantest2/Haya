package com.fantest.pokervision;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CardDetector {
    List<ScanModels.Quad> detect(Mat inputGray) {
        ArrayList<ScanModels.Quad> empty = new ArrayList<>();
        if (inputGray == null || inputGray.empty()) return empty;

        int originalW = inputGray.cols();
        int originalH = inputGray.rows();
        double scale = 1.0;
        int longEdge = Math.max(originalW, originalH);
        if (longEdge > VisionConstants.DISCOVERY_LONG_EDGE) {
            scale = VisionConstants.DISCOVERY_LONG_EDGE / (double) longEdge;
        }

        Mat work = new Mat();
        if (scale < 1.0) {
            Imgproc.resize(inputGray, work, new Size(Math.round(originalW * scale), Math.round(originalH * scale)), 0, 0, Imgproc.INTER_AREA);
        } else {
            inputGray.copyTo(work);
        }

        Mat blurred = new Mat();
        Mat edges = new Mat();
        Mat hierarchy = new Mat();
        Imgproc.GaussianBlur(work, blurred, new Size(5, 5), 0);
        Imgproc.Canny(blurred, edges, 45, 140);
        Imgproc.dilate(edges, edges, new Mat(), new Point(-1, -1), 1);

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        double frameArea = work.cols() * (double) work.rows();
        ArrayList<Candidate> candidates = new ArrayList<>();

        for (MatOfPoint contour : contours) {
            double area = Math.abs(Imgproc.contourArea(contour));
            double areaFraction = frameArea <= 0 ? 0 : area / frameArea;
            if (areaFraction < VisionConstants.CARD_AREA_MIN || areaFraction > VisionConstants.CARD_AREA_MAX) {
                contour.release();
                continue;
            }

            MatOfPoint2f curve = new MatOfPoint2f(contour.toArray());
            MatOfPoint2f approx = new MatOfPoint2f();
            double perimeter = Imgproc.arcLength(curve, true);
            Imgproc.approxPolyDP(curve, approx, .025 * perimeter, true);
            Point[] pts = approx.toArray();
            if (pts.length != 4) {
                curve.release(); approx.release(); contour.release();
                continue;
            }
            MatOfPoint polygon = new MatOfPoint(pts);
            if (!Imgproc.isContourConvex(polygon)) {
                polygon.release(); curve.release(); approx.release(); contour.release();
                continue;
            }

            Point[] ordered = order(pts);
            double top = distance(ordered[0], ordered[1]);
            double right = distance(ordered[1], ordered[2]);
            double bottom = distance(ordered[2], ordered[3]);
            double left = distance(ordered[3], ordered[0]);
            double width = (top + bottom) * .5;
            double height = (left + right) * .5;
            double shortEdge = Math.min(width, height);
            double longSide = Math.max(width, height);
            double ratio = longSide <= 0 ? 0 : shortEdge / longSide;
            if (ratio < VisionConstants.CARD_RATIO_MIN || ratio > VisionConstants.CARD_RATIO_MAX) {
                polygon.release(); curve.release(); approx.release(); contour.release();
                continue;
            }

            double inv = 1.0 / scale;
            ScanModels.Quad q = new ScanModels.Quad(
                    new ScanModels.Point(ordered[0].x * inv, ordered[0].y * inv),
                    new ScanModels.Point(ordered[1].x * inv, ordered[1].y * inv),
                    new ScanModels.Point(ordered[2].x * inv, ordered[2].y * inv),
                    new ScanModels.Point(ordered[3].x * inv, ordered[3].y * inv));
            candidates.add(new Candidate(q, area));
            polygon.release(); curve.release(); approx.release(); contour.release();
        }

        candidates.sort(Comparator.comparingDouble((Candidate c) -> c.area).reversed());
        ArrayList<ScanModels.Quad> accepted = new ArrayList<>();
        for (Candidate c : candidates) {
            boolean duplicate = false;
            for (ScanModels.Quad prior : accepted) {
                if (prior.iou(c.quad) >= VisionConstants.DUPLICATE_IOU) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) accepted.add(c.quad);
        }

        work.release(); blurred.release(); edges.release(); hierarchy.release();
        return accepted;
    }

    private static final class Candidate {
        final ScanModels.Quad quad;
        final double area;
        Candidate(ScanModels.Quad quad, double area) { this.quad = quad; this.area = area; }
    }

    private static Point[] order(Point[] pts) {
        Point tl = pts[0], tr = pts[0], br = pts[0], bl = pts[0];
        double minSum = Double.MAX_VALUE, maxSum = -Double.MAX_VALUE;
        double minDiff = Double.MAX_VALUE, maxDiff = -Double.MAX_VALUE;
        for (Point p : pts) {
            double sum = p.x + p.y;
            double diff = p.x - p.y;
            if (sum < minSum) { minSum = sum; tl = p; }
            if (sum > maxSum) { maxSum = sum; br = p; }
            if (diff > maxDiff) { maxDiff = diff; tr = p; }
            if (diff < minDiff) { minDiff = diff; bl = p; }
        }
        return new Point[]{tl, tr, br, bl};
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }
}
