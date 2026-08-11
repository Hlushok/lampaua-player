package com.brouken.player;

import androidx.media3.common.C;
import androidx.media3.common.Format;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class MediaFormatLabel {
    private MediaFormatLabel() {}

    static String qualityLabel(Format format) {
        int longSide = Math.max(format.width, format.height);
        return longSide > 0 ? longSide + "p" : "?";
    }

    static String videoDetails(Format format) {
        List<String> details = videoParts(format);
        return String.join(" \u00B7 ", details);
    }

    static List<String> videoParts(Format format) {
        List<String> details = new ArrayList<>();
        int longSide = Math.max(format.width, format.height);
        int shortSide = Math.min(format.width, format.height);
        if (longSide > 0) details.add(shortSide > 0 ? longSide + " \u00D7 " + shortSide : String.valueOf(longSide));
        String codec = CustomDefaultTrackNameProvider.formatNameFromMime(format.sampleMimeType);
        if (codec != null) details.add(codec);
        if (format.colorInfo != null) {
            if (format.colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084) details.add("HDR10");
            else if (format.colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG) details.add("HLG");
        }
        if (format.frameRate > 0) details.add(Math.round(format.frameRate) + " fps");
        int bitrate = format.averageBitrate > 0 ? format.averageBitrate : format.peakBitrate;
        if (bitrate > 0) details.add(String.format(Locale.US, "%.1f Mbps", bitrate / 1_000_000f));
        return details;
    }
}
