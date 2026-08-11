package com.brouken.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PlaybackStatistics {
    static final class Labels {
        final String container;
        final String video;
        final String fps;
        final String bitrate;
        final String buffer;
        final String network;
        final String decoder;
        final String audio;
        final String droppedFrames;

        Labels(String container, String video, String fps, String bitrate, String buffer,
               String network, String decoder, String audio, String droppedFrames) {
            this.container = container;
            this.video = video;
            this.fps = fps;
            this.bitrate = bitrate;
            this.buffer = buffer;
            this.network = network;
            this.decoder = decoder;
            this.audio = audio;
            this.droppedFrames = droppedFrames;
        }
    }

    static final class Snapshot {
        final String container;
        final int width;
        final int height;
        final String videoCodec;
        final float frameRate;
        final long bitrate;
        final long bufferedMs;
        final String videoDecoder;
        final String audio;
        final long transferBitrate;
        final int droppedFrames;

        Snapshot(String container, int width, int height, String videoCodec, float frameRate,
                 long bitrate, long bufferedMs, String videoDecoder, String audio,
                 long transferBitrate, int droppedFrames) {
            this.container = container;
            this.width = width;
            this.height = height;
            this.videoCodec = videoCodec;
            this.frameRate = frameRate;
            this.bitrate = bitrate;
            this.bufferedMs = bufferedMs;
            this.videoDecoder = videoDecoder;
            this.audio = audio;
            this.transferBitrate = transferBitrate;
            this.droppedFrames = droppedFrames;
        }

        String render(Labels labels) {
            List<String> rows = new ArrayList<>();
            if (container != null) rows.add(labels.container + ": " + container);
            if (width > 0 && height > 0) rows.add(labels.video + ": " + width + " \u00D7 " + height
                    + (videoCodec == null ? "" : " \u00B7 " + videoCodec));
            if (frameRate > 0) rows.add(String.format(Locale.US, "%s: %.2f", labels.fps, frameRate));
            if (bitrate > 0) rows.add(String.format(Locale.US, "%s: %.2f Mbps", labels.bitrate, bitrate / 1_000_000f));
            rows.add(String.format(Locale.US, "%s: %.1f s", labels.buffer, bufferedMs / 1000f));
            if (transferBitrate > 0) rows.add(String.format(Locale.US, "%s: %.2f Mbps", labels.network, transferBitrate / 1_000_000f));
            if (videoDecoder != null) rows.add(labels.decoder + ": " + videoDecoder);
            if (audio != null) rows.add(labels.audio + ": " + audio);
            rows.add(labels.droppedFrames + ": " + droppedFrames);
            return String.join("\n", rows);
        }
    }

    private PlaybackStatistics() {}
}
