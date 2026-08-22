package com.fantest.pokervision;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TemplateStore {
    static final class Template<T> {
        final T value;
        final Mat image;
        Template(T value, Mat image) { this.value = value; this.image = image; }
    }

    private final List<Template<Integer>> ranks = new ArrayList<>();
    private final List<Template<Integer>> suits = new ArrayList<>();

    TemplateStore() {
        int[] rankValues = {14,13,12,11,10,9,8,7,6,5,4,3,2};
        int[] fonts = {Imgproc.FONT_HERSHEY_SIMPLEX, Imgproc.FONT_HERSHEY_DUPLEX};
        for (int rank : rankValues) {
            String label = PokerMath.rankText(rank);
            for (int font : fonts) ranks.add(new Template<>(rank, renderRank(label, font)));
        }
        for (int suit = 0; suit < 4; suit++) suits.add(new Template<>(suit, renderSuit(suit)));
    }

    List<Template<Integer>> rankTemplates() { return Collections.unmodifiableList(ranks); }
    List<Template<Integer>> suitTemplates() { return Collections.unmodifiableList(suits); }

    Mat syntheticCard(int rank, int suit) {
        Mat card = new Mat(VisionConstants.CARD_HEIGHT, VisionConstants.CARD_WIDTH, CvType.CV_8UC1, new Scalar(255));
        String label = PokerMath.rankText(rank);
        Imgproc.putText(card, label, new Point(7, 65), Imgproc.FONT_HERSHEY_DUPLEX,
                label.equals("10") ? 1.25 : 1.55, new Scalar(0), 3, Imgproc.LINE_AA, false);

        Mat suitGlyph = renderSuit(suit);
        Mat inverted = new Mat();
        Core.bitwise_not(suitGlyph, inverted);
        Imgproc.resize(inverted, inverted, new Size(58, 58), 0, 0, Imgproc.INTER_NEAREST);
        Mat roi = card.submat(new Rect(10, 82, 58, 58));
        Core.min(roi, inverted, roi);
        roi.release(); inverted.release(); suitGlyph.release();
        return card;
    }

    void release() {
        for (Template<Integer> t : ranks) t.image.release();
        for (Template<Integer> t : suits) t.image.release();
        ranks.clear(); suits.clear();
    }

    static Mat normalizeGlyph(Mat source, int targetW, int targetH) {
        Mat binary = new Mat();
        if (source.channels() == 1) source.copyTo(binary);
        else Imgproc.cvtColor(source, binary, Imgproc.COLOR_BGR2GRAY);

        double mean = Core.mean(binary).val[0];
        if (mean > 127) Imgproc.threshold(binary, binary, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);
        else Imgproc.threshold(binary, binary, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binary.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        Rect union = null;
        for (MatOfPoint contour : contours) {
            if (Imgproc.contourArea(contour) < 12) { contour.release(); continue; }
            Rect r = Imgproc.boundingRect(contour);
            if (union == null) union = r;
            else {
                int left = Math.min(union.x, r.x);
                int top = Math.min(union.y, r.y);
                int right = Math.max(union.x + union.width, r.x + r.width);
                int bottom = Math.max(union.y + union.height, r.y + r.height);
                union = new Rect(left, top, right - left, bottom - top);
            }
            contour.release();
        }
        hierarchy.release();

        Mat out = Mat.zeros(targetH, targetW, CvType.CV_8UC1);
        if (union == null || union.width <= 0 || union.height <= 0) {
            binary.release();
            return out;
        }

        Mat glyph = new Mat(binary, union);
        double scale = Math.min((targetW * .80) / union.width, (targetH * .80) / union.height);
        int w = Math.max(1, Math.min(targetW, (int)Math.round(union.width * scale)));
        int h = Math.max(1, Math.min(targetH, (int)Math.round(union.height * scale)));
        Mat resized = new Mat();
        Imgproc.resize(glyph, resized, new Size(w, h), 0, 0, Imgproc.INTER_NEAREST);
        int x = (targetW - w) / 2;
        int y = (targetH - h) / 2;
        Mat target = out.submat(new Rect(x, y, w, h));
        resized.copyTo(target);

        target.release(); resized.release(); glyph.release(); binary.release();
        return out;
    }

    private static Mat renderRank(String label, int font) {
        Mat canvas = Mat.zeros(96, 76, CvType.CV_8UC1);
        int[] baseline = {0};
        double fontScale = label.equals("10") ? 1.15 : 1.55;
        Size size = Imgproc.getTextSize(label, font, fontScale, 3, baseline);
        Point origin = new Point(Math.max(1, (76 - size.width) / 2.0), Math.max(size.height + 2, (96 + size.height) / 2.0));
        Imgproc.putText(canvas, label, origin, font, fontScale, new Scalar(255), 3, Imgproc.LINE_AA, false);
        Mat normalized = normalizeGlyph(canvas, 64, 80);
        canvas.release();
        return normalized;
    }

    private static Mat renderSuit(int suit) {
        Mat m = Mat.zeros(72, 72, CvType.CV_8UC1);
        Scalar white = new Scalar(255);
        if (suit == 2) { // diamond
            Imgproc.fillConvexPoly(m, new MatOfPoint(
                    new Point(36, 7), new Point(61, 36), new Point(36, 65), new Point(11, 36)), white);
        } else if (suit == 1) { // heart
            Imgproc.circle(m, new Point(25, 26), 15, white, -1);
            Imgproc.circle(m, new Point(47, 26), 15, white, -1);
            Imgproc.fillConvexPoly(m, new MatOfPoint(
                    new Point(11, 29), new Point(61, 29), new Point(36, 66)), white);
        } else if (suit == 0) { // spade
            Imgproc.circle(m, new Point(25, 40), 15, white, -1);
            Imgproc.circle(m, new Point(47, 40), 15, white, -1);
            Imgproc.fillConvexPoly(m, new MatOfPoint(
                    new Point(11, 38), new Point(61, 38), new Point(36, 7)), white);
            Imgproc.rectangle(m, new Point(31, 43), new Point(41, 65), white, -1);
        } else { // club
            Imgproc.circle(m, new Point(36, 20), 14, white, -1);
            Imgproc.circle(m, new Point(22, 39), 14, white, -1);
            Imgproc.circle(m, new Point(50, 39), 14, white, -1);
            Imgproc.rectangle(m, new Point(31, 39), new Point(41, 65), white, -1);
        }
        Mat normalized = normalizeGlyph(m, 56, 56);
        m.release();
        return normalized;
    }
}
