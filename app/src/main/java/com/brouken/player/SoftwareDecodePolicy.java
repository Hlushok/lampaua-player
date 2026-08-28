package com.brouken.player;

import java.util.Locale;
import java.util.Map;

/** Evidence gate for large streams that a software video decoder cannot deliver in real time. */
final class SoftwareDecodePolicy {
    private static final long MIN_ACTIVE_MS = 5_000L;
    private static final long MIN_BUFFERED_MS = 5_000L;
    private static final int MIN_DROPPED_FRAMES = 24;
    private static final int MAX_FALLBACK_HEIGHT = 1080;

    static final class Variant {
        final String label;
        final String url;
        final int height;

        Variant(String label, String url, int height) {
            this.label = label;
            this.url = url;
            this.height = height;
        }
    }

    static final class Evidence {
        final boolean softwareDecoder;
        final int width;
        final int height;
        final float frameRate;
        final boolean playbackActive;
        final boolean outputStalled;
        final long activeMs;
        final long bufferedMs;
        final long transferBitrate;
        final long videoBitrate;
        final int droppedFrames;
        final Map<String, String> sourceVariants;
        final String currentUrl;

        Evidence(boolean softwareDecoder, int width, int height, float frameRate,
                 boolean playbackActive, boolean outputStalled, long activeMs,
                 long bufferedMs, long transferBitrate, long videoBitrate,
                 int droppedFrames, Map<String, String> sourceVariants, String currentUrl) {
            this.softwareDecoder = softwareDecoder;
            this.width = width;
            this.height = height;
            this.frameRate = frameRate;
            this.playbackActive = playbackActive;
            this.outputStalled = outputStalled;
            this.activeMs = activeMs;
            this.bufferedMs = bufferedMs;
            this.transferBitrate = transferBitrate;
            this.videoBitrate = videoBitrate;
            this.droppedFrames = droppedFrames;
            this.sourceVariants = sourceVariants;
            this.currentUrl = currentUrl;
        }
    }

    static final class Decision {
        final boolean software;
        final boolean slow;
        final String reason;
        final Variant fallback;

        Decision(boolean software, boolean slow, String reason, Variant fallback) {
            this.software = software;
            this.slow = slow;
            this.reason = reason;
            this.fallback = fallback;
        }
    }

    private SoftwareDecodePolicy() {}

    static Decision evaluate(Evidence evidence) {
        if (evidence == null || !evidence.softwareDecoder) {
            return new Decision(false, false, "hardware_or_unknown", null);
        }
        if (!isAbove1080p(evidence.width, evidence.height)) {
            return new Decision(true, false, "resolution_supported", null);
        }
        if (!evidence.playbackActive || evidence.activeMs < MIN_ACTIVE_MS) {
            return new Decision(true, false, "warming_up", null);
        }
        int droppedThreshold = evidence.frameRate > 0f
                ? Math.max(MIN_DROPPED_FRAMES, Math.round(evidence.frameRate))
                : MIN_DROPPED_FRAMES;
        if (!evidence.outputStalled && evidence.droppedFrames < droppedThreshold) {
            return new Decision(true, false, "output_progress", null);
        }
        boolean transferCanFeed = evidence.videoBitrate > 0L
                && evidence.transferBitrate >= evidence.videoBitrate;
        if (evidence.bufferedMs < MIN_BUFFERED_MS && !transferCanFeed) {
            return new Decision(true, false, "network_starved", null);
        }
        return new Decision(true, true, "software_decode_too_slow",
                chooseFallback(evidence.sourceVariants, evidence.currentUrl));
    }

    static Decision forDecoderInit(boolean softwareDecoder, int width, int height,
                                   Map<String, String> sourceVariants, String currentUrl) {
        if (!softwareDecoder) {
            return new Decision(false, false, "hardware_or_unknown", null);
        }
        if (!isAbove1080p(width, height)) {
            return new Decision(true, false, "resolution_supported", null);
        }
        return new Decision(true, true, "software_decoder_init_failed",
                chooseFallback(sourceVariants, currentUrl));
    }

    static boolean isSoftwareName(String decoderName) {
        if (decoderName == null || decoderName.trim().isEmpty()) return false;
        String name = decoderName.trim().toLowerCase(Locale.US);
        return !name.contains(".") || name.startsWith("c2.android.")
                || name.startsWith("c2.google.") || name.startsWith("omx.google.")
                || name.contains("ffmpeg") || name.contains("libdav1d")
                || name.contains("software") || name.contains(".sw.");
    }

    static boolean isAbove1080p(int width, int height) {
        if (width <= 0 || height <= 0) return false;
        return Math.max(width, height) >= 2560 || Math.min(width, height) >= 1440;
    }

    private static Variant chooseFallback(Map<String, String> variants, String currentUrl) {
        if (variants == null || variants.isEmpty()) return null;
        Variant best = null;
        for (Map.Entry<String, String> entry : variants.entrySet()) {
            String url = entry.getValue();
            int height = qualityHeight(entry.getKey());
            if (url == null || url.trim().isEmpty() || url.equals(currentUrl)
                    || height <= 0 || height > MAX_FALLBACK_HEIGHT) {
                continue;
            }
            if (best == null || height > best.height) {
                best = new Variant(entry.getKey(), url, height);
            }
        }
        return best;
    }

    static int qualityHeight(String label) {
        if (label == null) return 0;
        String normalized = label.toLowerCase(Locale.US);
        if (normalized.contains("4k") || normalized.contains("uhd")) return 2160;
        if (normalized.contains("fhd")) return 1080;
        if (normalized.contains("qhd")) return 1440;
        if (normalized.contains("hd") && !normalized.matches(".*\\d.*")) return 720;
        String digits = normalized.replaceAll("[^0-9]", "");
        try {
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
