package com.fantest.pokervision;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RecognitionVoteTracker {
    private static final class Vote {
        final PokerMath.Card card;
        final double confidence;
        Vote(PokerMath.Card card, double confidence) { this.card = card; this.confidence = confidence; }
    }

    private static final class Track {
        final long id;
        ScanModels.Quad quad;
        long lastSeenMs;
        final Deque<Vote> votes = new ArrayDeque<>();
        PokerMath.Card stableCard;
        double stableConfidence;
        PokerMath.Card latestCard;
        double latestConfidence;

        Track(long id, ScanModels.Observation observation, long nowMs) {
            this.id = id;
            observe(observation, nowMs);
        }

        void observe(ScanModels.Observation observation, long nowMs) {
            quad = observation.quad;
            lastSeenMs = nowMs;
            latestCard = observation.card;
            latestConfidence = observation.confidence;
            if (observation.card != null) {
                votes.addLast(new Vote(observation.card, observation.confidence));
                while (votes.size() > VisionConstants.VOTE_WINDOW) votes.removeFirst();
            }
            updateStable();
        }

        private void updateStable() {
            Map<PokerMath.Card, Integer> counts = new HashMap<>();
            Map<PokerMath.Card, Double> sums = new HashMap<>();
            for (Vote vote : votes) {
                if (vote.card == null || vote.confidence < VisionConstants.HIGH_CONFIDENCE) continue;
                counts.put(vote.card, counts.getOrDefault(vote.card, 0) + 1);
                sums.put(vote.card, sums.getOrDefault(vote.card, 0.0) + vote.confidence);
            }
            PokerMath.Card winner = null;
            int best = 0;
            for (Map.Entry<PokerMath.Card, Integer> entry : counts.entrySet()) {
                if (entry.getValue() >= VisionConstants.STABLE_VOTES && entry.getValue() > best) {
                    winner = entry.getKey();
                    best = entry.getValue();
                }
            }
            if (winner != null) {
                stableCard = winner;
                stableConfidence = sums.get(winner) / counts.get(winner);
            }
        }

        ScanModels.TrackedDetection snapshot() {
            if (stableCard != null) {
                return new ScanModels.TrackedDetection(id, quad, stableCard, stableConfidence,
                        ScanModels.Status.STABLE, lastSeenMs);
            }
            ScanModels.Status status = latestCard != null && latestConfidence >= VisionConstants.AMBER_CONFIDENCE
                    ? ScanModels.Status.AMBER : ScanModels.Status.NEUTRAL;
            return new ScanModels.TrackedDetection(id, quad, latestCard, latestConfidence, status, lastSeenMs);
        }
    }

    private final ArrayList<Track> tracks = new ArrayList<>();
    private long nextTrackId = 1L;

    List<ScanModels.TrackedDetection> update(long nowMs, List<ScanModels.Observation> observations) {
        expire(nowMs);
        List<ScanModels.Observation> incoming = observations == null ? Collections.emptyList() : observations;
        Set<Long> matched = new HashSet<>();

        for (ScanModels.Observation observation : incoming) {
            if (observation == null || observation.quad == null) continue;
            Track best = null;
            double bestIou = -1.0;
            for (Track track : tracks) {
                if (matched.contains(track.id)) continue;
                double iou = track.quad.iou(observation.quad);
                if (iou >= VisionConstants.TRACK_IOU && iou > bestIou) {
                    best = track;
                    bestIou = iou;
                }
            }
            if (best == null) {
                double bestDistance = Double.MAX_VALUE;
                for (Track track : tracks) {
                    if (matched.contains(track.id)) continue;
                    double distance = track.quad.centerDistance(observation.quad);
                    double allowed = VisionConstants.TRACK_CENTER_DISTANCE_FACTOR
                            * Math.max(track.quad.diagonal(), observation.quad.diagonal());
                    if (distance <= allowed && distance < bestDistance) {
                        best = track;
                        bestDistance = distance;
                    }
                }
            }
            if (best == null) {
                best = new Track(nextTrackId++, observation, nowMs);
                tracks.add(best);
            } else {
                best.observe(observation, nowMs);
            }
            matched.add(best.id);
        }

        expire(nowMs);
        ArrayList<ScanModels.TrackedDetection> out = new ArrayList<>(tracks.size());
        for (Track track : tracks) out.add(track.snapshot());
        out.sort(Comparator.comparingLong(d -> d.trackId));
        return Collections.unmodifiableList(out);
    }

    void clear() {
        tracks.clear();
        nextTrackId = 1L;
    }

    private void expire(long nowMs) {
        tracks.removeIf(track -> nowMs - track.lastSeenMs > VisionConstants.TRACK_EXPIRY_MS);
    }
}
