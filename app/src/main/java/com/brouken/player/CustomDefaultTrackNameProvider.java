package com.brouken.player;

import android.content.res.Resources;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.ui.DefaultTrackNameProvider;

import java.util.Locale;
import java.util.Map;

class CustomDefaultTrackNameProvider extends DefaultTrackNameProvider {
    private Map<String, String> trackNames;

    CustomDefaultTrackNameProvider(Resources resources) {
        super(resources);
    }

    void setTrackNames(Map<String, String> names) {
        trackNames = names;
    }

    @Override public String getTrackName(Format format) {
        boolean hasLanguage = format.language != null && !format.language.isEmpty()
                && !"und".equals(format.language);
        String title = format.label;
        if ((title == null || title.isEmpty()) && trackNames != null && format.id != null) {
            title = trackNames.get(format.id);
        }
        if ((title == null || title.isEmpty()) && hasLanguage) {
            title = new Locale(format.language).getDisplayLanguage();
            if (!title.isEmpty()) title = title.substring(0, 1).toUpperCase() + title.substring(1);
        }
        String tech = techInfo(format);
        StringBuilder result = new StringBuilder();
        if (title != null) result.append(title);
        if (!tech.isEmpty()) {
            if (result.length() > 0) result.append(' ');
            result.append('[').append(tech).append(']');
        }
        if (hasLanguage) {
            if (result.length() > 0) result.append(' ');
            result.append('(').append(format.language).append(')');
        }
        return result.length() == 0 ? super.getTrackName(format) : result.toString();
    }

    static String techInfo(Format format) {
        String codec = formatNameFromMime(format.sampleMimeType);
        if (codec == null) codec = formatNameFromMime(format.codecs);
        if (!MimeTypes.isAudio(format.sampleMimeType)) return codec == null ? "" : codec;
        StringBuilder value = new StringBuilder(codec == null ? "" : codec);
        if (format.channelCount > 0) {
            if (value.length() > 0) value.append(' ');
            value.append(formatChannels(format.channelCount));
        }
        int bitrate = format.averageBitrate > 0 ? format.averageBitrate : format.peakBitrate;
        if (bitrate > 0) {
            if (value.length() > 0) value.append(' ');
            value.append(bitrate / 1000).append('k');
        }
        return value.toString();
    }

    private static String formatChannels(int channels) {
        if (channels == 1) return "1.0";
        if (channels == 2) return "2.0";
        if (channels == 6) return "5.1";
        if (channels == 8) return "7.1";
        return channels + " ch";
    }

    static String formatNameFromMime(String mimeType) {
        if (mimeType == null) return null;
        switch (mimeType) {
            case MimeTypes.VIDEO_H264: return "H.264";
            case MimeTypes.VIDEO_H265: return "H.265";
            case MimeTypes.VIDEO_AV1: return "AV1";
            case MimeTypes.VIDEO_VP9: return "VP9";
            case MimeTypes.VIDEO_VP8: return "VP8";
            case MimeTypes.VIDEO_MPEG2: return "MPEG-2";
            case MimeTypes.VIDEO_MP4V: return "MPEG-4";
            case MimeTypes.VIDEO_H263: return "H.263";
            case MimeTypes.VIDEO_DOLBY_VISION: return "DOLBY VISION";
            case MimeTypes.AUDIO_DTS: return "DTS";
            case MimeTypes.AUDIO_DTS_HD: return "DTS-HD";
            case MimeTypes.AUDIO_DTS_EXPRESS: return "DTS Express";
            case MimeTypes.AUDIO_TRUEHD: return "TrueHD";
            case MimeTypes.AUDIO_AC3: return "AC-3";
            case MimeTypes.AUDIO_E_AC3: return "E-AC-3";
            case MimeTypes.AUDIO_E_AC3_JOC: return "E-AC-3-JOC";
            case MimeTypes.AUDIO_AC4: return "AC-4";
            case MimeTypes.AUDIO_AAC: return "AAC";
            case MimeTypes.AUDIO_MPEG: return "MP3";
            case MimeTypes.AUDIO_MPEG_L2: return "MP2";
            case MimeTypes.AUDIO_VORBIS: return "Vorbis";
            case MimeTypes.AUDIO_OPUS: return "Opus";
            case MimeTypes.AUDIO_FLAC: return "FLAC";
            case MimeTypes.AUDIO_ALAC: return "ALAC";
            case MimeTypes.AUDIO_WAV: return "WAV";
            case MimeTypes.AUDIO_AMR: return "AMR";
            case MimeTypes.AUDIO_AMR_NB: return "AMR-NB";
            case MimeTypes.AUDIO_AMR_WB: return "AMR-WB";
            case MimeTypes.AUDIO_IAMF: return "IAMF";
            case MimeTypes.AUDIO_MPEGH_MHA1:
            case MimeTypes.AUDIO_MPEGH_MHM1: return "MPEG-H";
            case MimeTypes.APPLICATION_PGS: return "PGS";
            case MimeTypes.APPLICATION_SUBRIP: return "SRT";
            case MimeTypes.TEXT_SSA: return "SSA";
            case MimeTypes.TEXT_VTT: return "VTT";
            case MimeTypes.APPLICATION_TTML: return "TTML";
            case MimeTypes.APPLICATION_TX3G: return "TX3G";
            case MimeTypes.APPLICATION_DVBSUBS: return "DVB";
            default: return null;
        }
    }
}
