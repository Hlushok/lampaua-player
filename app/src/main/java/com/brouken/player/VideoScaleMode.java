package com.brouken.player;

import androidx.media3.ui.AspectRatioFrameLayout;

enum VideoScaleMode {
    FIT("fit", AspectRatioFrameLayout.RESIZE_MODE_FIT, 0f),
    CROP("crop", AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 0f),
    FILL("fill", AspectRatioFrameLayout.RESIZE_MODE_FILL, 0f),
    RATIO_16_9("16_9", AspectRatioFrameLayout.RESIZE_MODE_FIT, 16f / 9f),
    RATIO_4_3("4_3", AspectRatioFrameLayout.RESIZE_MODE_FIT, 4f / 3f),
    RATIO_16_10("16_10", AspectRatioFrameLayout.RESIZE_MODE_FIT, 16f / 10f),
    RATIO_2_1("2_1", AspectRatioFrameLayout.RESIZE_MODE_FIT, 2f),
    RATIO_2_35_1("2_35_1", AspectRatioFrameLayout.RESIZE_MODE_FIT, 2.35f),
    RATIO_2_39_1("2_39_1", AspectRatioFrameLayout.RESIZE_MODE_FIT, 2.39f),
    RATIO_5_4("5_4", AspectRatioFrameLayout.RESIZE_MODE_FIT, 5f / 4f);

    final String key;
    final int resizeMode;
    final float ratio;

    VideoScaleMode(String key, int resizeMode, float ratio) {
        this.key = key;
        this.resizeMode = resizeMode;
        this.ratio = ratio;
    }

    static VideoScaleMode nextQuickMode(VideoScaleMode current) {
        if (current == FIT) return CROP;
        if (current == CROP) return FILL;
        if (current == FILL) return RATIO_16_9;
        if (current == RATIO_16_9) return RATIO_4_3;
        return FIT;
    }

    static VideoScaleMode from(int resizeMode, float ratio) {
        if (ratio > 0) {
            for (VideoScaleMode mode : values()) {
                if (mode.ratio > 0 && Math.abs(mode.ratio - ratio) < 0.001f) return mode;
            }
        }
        for (VideoScaleMode mode : values()) {
            if (mode.ratio == 0 && mode.resizeMode == resizeMode) return mode;
        }
        return FIT;
    }

    void apply(CustomPlayerView view) {
        view.applyAspectMode(resizeMode, ratio);
    }
}
