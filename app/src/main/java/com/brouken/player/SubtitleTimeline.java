package com.brouken.player;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.text.Cue;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceUtil;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An external subtitle held in memory and addressable by media time.
 *
 * <p>Adapted from Just+ Player PR #130 by Oleksandr Zhyzhchenko (Unlicense).
 */
final class SubtitleTimeline {
    private static final int[] NONE = new int[0];
    private static final long OPEN_ENDED_US = 5 * C.MICROS_PER_SECOND;

    private final long[] startUs;
    private final long[] endUs;
    private final List<ImmutableList<Cue>> cues;

    SubtitleTimeline(long[] startUs, long[] endUs, List<ImmutableList<Cue>> cues) {
        this.startUs = startUs;
        this.endUs = endUs;
        this.cues = cues;
    }

    static SubtitleTimeline load(Context context, Uri uri, String mimeType) {
        try {
            Format format = new Format.Builder().setSampleMimeType(mimeType).build();
            SubtitleParser.Factory factory = new DefaultSubtitleParserFactory();
            if (!factory.supportsFormat(format)) return null;

            DataSource source = new DefaultDataSource.Factory(context).createDataSource();
            byte[] data;
            try {
                source.open(new DataSpec(uri));
                data = DataSourceUtil.readToEnd(source);
            } finally {
                DataSourceUtil.closeQuietly(source);
            }

            List<CuesWithTiming> blocks = new ArrayList<>();
            factory.create(format).parse(data, SubtitleParser.OutputOptions.allCues(), blocks::add);
            List<CuesWithTiming> ordered = new ArrayList<>(blocks.size());
            for (CuesWithTiming block : blocks) {
                if (block.startTimeUs != C.TIME_UNSET && !block.cues.isEmpty()) {
                    ordered.add(block);
                }
            }
            if (ordered.isEmpty()) return null;
            Collections.sort(ordered, (left, right) ->
                    Long.compare(left.startTimeUs, right.startTimeUs));

            int count = ordered.size();
            long[] startUs = new long[count];
            long[] endUs = new long[count];
            List<ImmutableList<Cue>> cues = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                CuesWithTiming block = ordered.get(i);
                startUs[i] = block.startTimeUs;
                endUs[i] = block.durationUs != C.TIME_UNSET && block.durationUs > 0
                        ? block.startTimeUs + block.durationUs
                        : (i + 1 < count ? ordered.get(i + 1).startTimeUs
                        : block.startTimeUs + OPEN_ENDED_US);
                cues.add(block.cues);
            }
            return new SubtitleTimeline(startUs, endUs, cues);
        } catch (Throwable error) {
            Utils.log("subtitles: timeline failed " + error.getClass().getSimpleName());
            return null;
        }
    }

    int[] visibleAt(long timeUs) {
        int count = 0;
        int[] found = null;
        for (int i = 0; i < startUs.length && startUs[i] <= timeUs; i++) {
            if (timeUs < endUs[i]) {
                if (found == null) {
                    found = new int[4];
                } else if (count == found.length) {
                    found = Arrays.copyOf(found, count * 2);
                }
                found[count++] = i;
            }
        }
        if (count == 0) return NONE;
        return count == found.length ? found : Arrays.copyOf(found, count);
    }

    ImmutableList<Cue> cuesOf(int[] indices) {
        if (indices.length == 0) return ImmutableList.of();
        if (indices.length == 1) return cues.get(indices[0]);
        ImmutableList.Builder<Cue> all = ImmutableList.builder();
        for (int index : indices) all.addAll(cues.get(index));
        return all.build();
    }
}
