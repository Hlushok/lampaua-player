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
import java.util.regex.Pattern;

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

    private static Uri pendingUri;
    private static SubtitleTimeline pending;

    SubtitleTimeline(long[] startUs, long[] endUs, List<ImmutableList<Cue>> cues) {
        this.startUs = startUs;
        this.endUs = endUs;
        this.cues = cues;
    }

    private static synchronized SubtitleTimeline take(Uri uri) {
        if (pendingUri == null || !pendingUri.equals(uri)) return null;
        SubtitleTimeline timeline = pending;
        pendingUri = null;
        pending = null;
        return timeline;
    }

    private static synchronized void offer(Uri uri, SubtitleTimeline timeline) {
        pendingUri = uri;
        pending = timeline;
    }

    static SubtitleTimeline load(Context context, Uri uri, String mimeType) {
        SubtitleTimeline reused = take(uri);
        if (reused != null) return reused;
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

            List<CuesWithTiming> kept = withoutPromos(ordered);
            if (kept.isEmpty()) return null;

            int count = kept.size();
            long[] startUs = new long[count];
            long[] endUs = new long[count];
            List<ImmutableList<Cue>> cues = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                CuesWithTiming block = kept.get(i);
                startUs[i] = block.startTimeUs;
                endUs[i] = block.durationUs != C.TIME_UNSET && block.durationUs > 0
                        ? block.startTimeUs + block.durationUs
                        : (i + 1 < count ? kept.get(i + 1).startTimeUs
                        : block.startTimeUs + OPEN_ENDED_US);
                cues.add(block.cues);
            }
            SubtitleTimeline timeline = new SubtitleTimeline(startUs, endUs, cues);
            offer(uri, timeline);
            return timeline;
        } catch (Throwable error) {
            Utils.log("subtitles: timeline failed " + error.getClass().getSimpleName());
            return null;
        }
    }

    int size() {
        return startUs.length;
    }

    long startUs(int index) {
        return startUs[index];
    }

    long endUs(int index) {
        return endUs[index];
    }

    ImmutableList<Cue> cuesAt(int index) {
        return cues.get(index);
    }

    private static final Pattern PROMO_URL = Pattern.compile(
            "https?://|www\\.|[a-z0-9][a-z0-9-]*\\.(?:app|com|net|org|io|tv|me|info|link|ru|ua|pl)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROMO_WORDS = Pattern.compile(
            "subtitl|субтитр|переклад|перевод|translat|download|скача|завантаж|"
                    + "extension|browser|расширени|розширенн|\\bfree\\b|бесплатн|безкоштовн|"
                    + "\\bai\\b|нейросет|нейромереж|оцените|оцініть|watch any",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static List<CuesWithTiming> withoutPromos(List<CuesWithTiming> ordered) {
        int count = ordered.size();
        List<CuesWithTiming> kept = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            CuesWithTiming block = ordered.get(index);
            if ((index == 0 || index == count - 1) && isPromo(block)) {
                Utils.log("subtitles: dropped an ad at block " + (index + 1) + " of " + count);
            } else {
                kept.add(block);
            }
        }
        return kept;
    }

    private static boolean isPromo(CuesWithTiming block) {
        StringBuilder text = new StringBuilder();
        for (Cue cue : block.cues) {
            if (cue.text != null) text.append(cue.text).append('\n');
        }
        return PROMO_URL.matcher(text).find() && PROMO_WORDS.matcher(text).find();
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
