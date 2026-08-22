package com.fantest.pokervision;

import androidx.camera.core.ImageProxy;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class VisionPipeline {
    interface Callback {
        void onResult(List<ScanModels.TrackedDetection> detections, TableZoneMapper.Result zones);
        void onError(String message);
    }

    private final CardDetector detector = new CardDetector();
    private final CardNormalizer normalizer = new CardNormalizer();
    private final TemplateStore templates = new TemplateStore();
    private final RankRecognizer rankRecognizer = new RankRecognizer(templates);
    private final SuitRecognizer suitRecognizer = new SuitRecognizer(templates);
    private final CardRecognitionFusion fusion = new CardRecognitionFusion();
    private final RecognitionVoteTracker tracker = new RecognitionVoteTracker();
    private final TableZoneMapper mapper = new TableZoneMapper();
    private long lastProcessMs = Long.MIN_VALUE;

    void process(ImageProxy proxy, long nowMs, Callback callback) {
        if (proxy == null) return;
        if (lastProcessMs != Long.MIN_VALUE && nowMs - lastProcessMs < VisionConstants.RECOGNITION_INTERVAL_MS) {
            proxy.close();
            return;
        }
        lastProcessMs = nowMs;

        Mat gray = null;
        try {
            gray = grayFromProxy(proxy);
            if (gray == null || gray.empty()) {
                callback.onResult(Collections.emptyList(), mapper.map(Collections.emptyList()));
                return;
            }

            List<ScanModels.Quad> quads = detector.detect(gray);
            ArrayList<ScanModels.Observation> observations = new ArrayList<>(quads.size());
            for (ScanModels.Quad pixelQuad : quads) {
                CardNormalizer.Result normalized = null;
                try {
                    normalized = normalizer.normalize(gray, pixelQuad);
                    SymbolPrediction<Integer> rank = rankRecognizer.recognize(normalized.index);
                    SymbolPrediction<Integer> suit = suitRecognizer.recognize(normalized.index);
                    ScanModels.CardIdentity identity = fusion.fuse(rank, suit, null);
                    observations.add(new ScanModels.Observation(
                            normalizeQuad(pixelQuad, gray.cols(), gray.rows()), identity.card, identity.confidence));
                } catch (RuntimeException ignored) {
                    observations.add(new ScanModels.Observation(
                            normalizeQuad(pixelQuad, gray.cols(), gray.rows()), null, 0.0));
                } finally {
                    if (normalized != null) normalized.release();
                }
            }

            List<ScanModels.TrackedDetection> tracked = tracker.update(nowMs, observations);
            callback.onResult(tracked, mapper.map(tracked));
        } catch (Throwable t) {
            callback.onError(t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        } finally {
            if (gray != null) gray.release();
            proxy.close();
        }
    }

    void clear() {
        tracker.clear();
        lastProcessMs = Long.MIN_VALUE;
    }

    List<ScanModels.Proposal> proposals(TableZoneMapper.Result zones) {
        return mapper.proposals(zones);
    }

    void release() {
        tracker.clear();
        templates.release();
    }

    private static Mat grayFromProxy(ImageProxy proxy) {
        if (proxy.getPlanes().length == 0) return new Mat();
        ImageProxy.PlaneProxy plane = proxy.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();
        int width = proxy.getWidth();
        int height = proxy.getHeight();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        Mat gray;

        if (pixelStride == 1) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Mat padded = new Mat(height, rowStride, CvType.CV_8UC1);
            padded.put(0, 0, bytes);
            gray = padded.colRange(0, width).clone();
            padded.release();
        } else {
            byte[] source = new byte[buffer.remaining()];
            buffer.get(source);
            byte[] packed = new byte[width * height];
            for (int y = 0; y < height; y++) {
                int row = y * rowStride;
                for (int x = 0; x < width; x++) {
                    int src = row + x * pixelStride;
                    if (src < source.length) packed[y * width + x] = source[src];
                }
            }
            gray = new Mat(height, width, CvType.CV_8UC1);
            gray.put(0, 0, packed);
        }

        int rotation = proxy.getImageInfo().getRotationDegrees();
        if (rotation == 90 || rotation == 180 || rotation == 270) {
            Mat rotated = new Mat();
            if (rotation == 90) Core.rotate(gray, rotated, Core.ROTATE_90_CLOCKWISE);
            else if (rotation == 180) Core.rotate(gray, rotated, Core.ROTATE_180);
            else Core.rotate(gray, rotated, Core.ROTATE_90_COUNTERCLOCKWISE);
            gray.release();
            gray = rotated;
        }
        return gray;
    }

    private static ScanModels.Quad normalizeQuad(ScanModels.Quad q, int width, int height) {
        double w = Math.max(1, width);
        double h = Math.max(1, height);
        return new ScanModels.Quad(
                new ScanModels.Point(q.p0.x / w, q.p0.y / h),
                new ScanModels.Point(q.p1.x / w, q.p1.y / h),
                new ScanModels.Point(q.p2.x / w, q.p2.y / h),
                new ScanModels.Point(q.p3.x / w, q.p3.y / h));
    }
}
