package com.fantest.pokervision;

import java.util.Objects;

final class ScanModels {
    private ScanModels() {}

    enum Status { NEUTRAL, AMBER, STABLE, INVALID, MANUAL }

    static final class Point {
        final double x;
        final double y;
        Point(double x, double y) { this.x = x; this.y = y; }
    }

    static final class Quad {
        final Point p0, p1, p2, p3;

        Quad(Point p0, Point p1, Point p2, Point p3) {
            this.p0 = p0; this.p1 = p1; this.p2 = p2; this.p3 = p3;
        }

        static Quad fromBounds(double left, double top, double right, double bottom) {
            return new Quad(new Point(left, top), new Point(right, top), new Point(right, bottom), new Point(left, bottom));
        }

        double left() { return Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x)); }
        double top() { return Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y)); }
        double right() { return Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x)); }
        double bottom() { return Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y)); }
        double centerX() { return (left() + right()) * .5; }
        double centerY() { return (top() + bottom()) * .5; }
        double width() { return Math.max(0.0, right() - left()); }
        double height() { return Math.max(0.0, bottom() - top()); }
        double diagonal() { return Math.hypot(width(), height()); }

        double iou(Quad other) {
            double l = Math.max(left(), other.left());
            double t = Math.max(top(), other.top());
            double r = Math.min(right(), other.right());
            double b = Math.min(bottom(), other.bottom());
            double intersection = Math.max(0.0, r - l) * Math.max(0.0, b - t);
            double a = width() * height();
            double c = other.width() * other.height();
            double union = a + c - intersection;
            return union <= 0 ? 0 : intersection / union;
        }

        double centerDistance(Quad other) {
            return Math.hypot(centerX() - other.centerX(), centerY() - other.centerY());
        }
    }

    static final class CardIdentity {
        final PokerMath.Card card;
        final double confidence;
        CardIdentity(PokerMath.Card card, double confidence) {
            this.card = card; this.confidence = confidence;
        }
    }

    static final class Observation {
        final Quad quad;
        final PokerMath.Card card;
        final double confidence;
        Observation(Quad quad, PokerMath.Card card, double confidence) {
            this.quad = Objects.requireNonNull(quad);
            this.card = card;
            this.confidence = confidence;
        }
    }

    static final class TrackedDetection {
        final long trackId;
        final Quad quad;
        final PokerMath.Card card;
        final double confidence;
        final Status status;
        final long lastSeenMs;

        TrackedDetection(long trackId, Quad quad, PokerMath.Card card, double confidence, Status status, long lastSeenMs) {
            this.trackId = trackId; this.quad = quad; this.card = card; this.confidence = confidence; this.status = status; this.lastSeenMs = lastSeenMs;
        }
    }

    static final class Proposal {
        final int area;
        final int index;
        final PokerMath.Card card;
        final double confidence;
        final Quad quad;
        final Status status;
        final boolean manual;

        Proposal(int area, int index, PokerMath.Card card, double confidence, Quad quad, Status status, boolean manual) {
            this.area = area; this.index = index; this.card = card; this.confidence = confidence; this.quad = quad;
            this.status = status; this.manual = manual;
        }

        Proposal corrected(PokerMath.Card replacement) {
            return new Proposal(area, index, replacement, 1.0, quad, Status.MANUAL, true);
        }

        Proposal withStatus(Status replacementStatus) {
            return new Proposal(area, index, card, confidence, quad, replacementStatus, manual);
        }
    }
}
