package com.brouken.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.text.Cue;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.SubtitleView;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;

import java.util.Collections;

public final class SubtitlePreviewPreference extends Preference {
    private static final float BASE_PREVIEW_TEXT_SP = 26f;

    public SubtitlePreviewPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public SubtitlePreviewPreference(@NonNull Context context, @Nullable AttributeSet attrs,
                                     int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    public SubtitlePreviewPreference(@NonNull Context context, @Nullable AttributeSet attrs,
                                     int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize();
    }

    private void initialize() {
        setLayoutResource(R.layout.preference_subtitle_preview);
        setSelectable(false);
        setPersistent(false);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        SubtitleView preview = (SubtitleView) holder.findViewById(R.id.subtitle_preview);
        if (preview == null) return;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean embedded = preferences.getBoolean("subtitleStyleEmbedded", true);
        boolean bold = preferences.getBoolean("subtitleStyleBold", false);
        float storedScale = readFloat(preferences, "subtitleScale", 1f);
        int textColor = readColor(preferences, "subtitleTextColor", Color.WHITE);
        int backgroundColor = readColor(preferences, "subtitleBackground", Color.TRANSPARENT);
        int edgeType = readInt(preferences, "subtitleEdge",
                CaptionStyleCompat.EDGE_TYPE_OUTLINE);

        CaptionStyleCompat style = new CaptionStyleCompat(
                textColor,
                backgroundColor,
                Color.TRANSPARENT,
                edgeType,
                textColor == Color.BLACK ? Color.WHITE : Color.BLACK,
                Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        preview.setStyle(style);
        preview.setApplyEmbeddedStyles(embedded);
        preview.setBottomPaddingFraction(0.12f);
        preview.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP,
                BASE_PREVIEW_TEXT_SP * SubtitleUtils.normalizeFontScale(storedScale,
                        Utils.isTvBox(getContext()) || Utils.isTablet(getContext())));

        SpannableString sample = new SpannableString(
                getContext().getString(R.string.subtitle_preview_sample));
        sample.setSpan(new StyleSpan(Typeface.ITALIC), 0, sample.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        preview.setCues(Collections.singletonList(new Cue.Builder().setText(sample).build()));
        preview.setContentDescription(sample.toString());
    }

    public void refresh() {
        notifyChanged();
    }

    private static float readFloat(SharedPreferences preferences, String key, float fallback) {
        try {
            return Float.parseFloat(preferences.getString(key, String.valueOf(fallback)));
        } catch (ClassCastException | NumberFormatException ignored) {
            try {
                return preferences.getFloat(key, fallback);
            } catch (ClassCastException ignoredAgain) {
                return fallback;
            }
        }
    }

    private static int readColor(SharedPreferences preferences, String key, int fallback) {
        try {
            return Color.parseColor(preferences.getString(key,
                    String.format("#%08X", fallback)));
        } catch (IllegalArgumentException | ClassCastException ignored) {
            return fallback;
        }
    }

    private static int readInt(SharedPreferences preferences, String key, int fallback) {
        try {
            return Integer.parseInt(preferences.getString(key, String.valueOf(fallback)));
        } catch (ClassCastException | NumberFormatException ignored) {
            try {
                return preferences.getInt(key, fallback);
            } catch (ClassCastException ignoredAgain) {
                return fallback;
            }
        }
    }
}
