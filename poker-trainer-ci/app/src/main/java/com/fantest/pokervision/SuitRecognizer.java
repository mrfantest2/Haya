package com.fantest.pokervision;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

final class SuitRecognizer {
    private final TemplateStore store;

    SuitRecognizer(TemplateStore store) { this.store = store; }

    SymbolPrediction<Integer> recognize(Mat indexCrop) {
        if (indexCrop == null || indexCrop.empty()) return null;
        int startY = Math.min(72, Math.max(0, indexCrop.rows() - 1));
        int h = Math.min(118, indexCrop.rows() - startY);
        int w = Math.min(VisionConstants.INDEX_WIDTH, indexCrop.cols());
        if (h < 20 || w < 20) return null;
        Mat region = new Mat(indexCrop, new Rect(0, startY, w, h));
        Mat query = TemplateStore.normalizeGlyph(region, 56, 56);
        region.release();
        if (Core.countNonZero(query) < 20) { query.release(); return null; }

        Integer bestValue = null;
        double best = -1;
        for (TemplateStore.Template<Integer> template : store.suitTemplates()) {
            double score = similarity(query, template.image);
            if (score > best) { best = score; bestValue = template.value; }
        }
        query.release();
        if (bestValue == null) return null;
        return new SymbolPrediction<>(bestValue, confidence(best));
    }

    private static double similarity(Mat a, Mat b) {
        double norm = Core.norm(a, b, Core.NORM_L2);
        double max = 255.0 * Math.sqrt(a.rows() * (double)a.cols());
        return max <= 0 ? 0 : Math.max(0, 1.0 - norm / max);
    }

    private static double confidence(double similarity) {
        return Math.max(0.0, Math.min(1.0, .34 + .66 * similarity));
    }
}
