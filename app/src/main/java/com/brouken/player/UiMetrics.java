package com.brouken.player;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

/** Single source of truth for adaptive UI sizing across phone, tablet and TV. */
final class UiMetrics {

    enum DeviceClass { PHONE, TABLET_MEDIUM, TABLET_LARGE, TV }

    final DeviceClass deviceClass;
    private final float density;
    private final float scale;
    private final int screenWidthDp;
    private final int orientation;

    private UiMetrics(Context context, boolean television) {
        Configuration configuration = context.getResources().getConfiguration();
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        density = displayMetrics.density;
        screenWidthDp = configuration.screenWidthDp;
        orientation = configuration.orientation;
        deviceClass = classify(configuration.smallestScreenWidthDp, television);
        scale = scaleFor(deviceClass);
    }

    private UiMetrics(float density, int screenWidthDp, int smallestWidthDp,
                      int orientation, boolean television) {
        this.density = Math.max(0f, density);
        this.screenWidthDp = Math.max(0, screenWidthDp);
        this.orientation = orientation;
        deviceClass = classify(Math.max(0, smallestWidthDp), television);
        scale = scaleFor(deviceClass);
    }

    static UiMetrics of(Context context, boolean television) {
        return new UiMetrics(context, television);
    }

    static UiMetrics forTest(float density, int screenWidthDp, int smallestWidthDp,
                             int orientation, boolean television) {
        return new UiMetrics(density, screenWidthDp, smallestWidthDp, orientation, television);
    }

    private static DeviceClass classify(int smallestWidthDp, boolean television) {
        if (television) return DeviceClass.TV;
        if (smallestWidthDp >= 720) return DeviceClass.TABLET_LARGE;
        if (smallestWidthDp >= 600) return DeviceClass.TABLET_MEDIUM;
        return DeviceClass.PHONE;
    }

    private static float scaleFor(DeviceClass deviceClass) {
        switch (deviceClass) {
            case TV:
                return 1.30f;
            case TABLET_LARGE:
                return 1.25f;
            case TABLET_MEDIUM:
                return 1.15f;
            default:
                return 1.0f;
        }
    }

    private boolean tv() {
        return deviceClass == DeviceClass.TV;
    }

    int dp(float dp) {
        return Math.round(dp * density);
    }

    int dpS(float dp) {
        return Math.round(dp * scale * density);
    }

    float sp(float sp) {
        return sp * scale;
    }

    int gridH() { return dpS(14); }
    int pillCorner() { return dpS(8); }
    int pillPadH() { return dpS(4); }
    int clusterBox() { return dpS(40); }
    int clusterPad() { return dpS(8); }
    int heroBox() { return dpS(90); }
    int heroInset() { return dpS(10); }
    int episodeDisc() { return dpS(46); }
    int episodeDiscPad() { return dpS(10); }
    int episodeDiscMargin() { return dpS(6); }
    int spinnerSize() { return dpS(60); }
    int listPad() { return dpS(10); }
    int lockMarginEnd() { return dpS(8); }

    int posterHeight() {
        switch (deviceClass) {
            case TV:
                return dp(96);
            case TABLET_LARGE:
                return dp(88);
            case TABLET_MEDIUM:
                return dp(80);
            default:
                return dp(74);
        }
    }

    int rowMinHeight() {
        switch (deviceClass) {
            case TV:
                return dp(56);
            case TABLET_LARGE:
            case TABLET_MEDIUM:
                return dp(52);
            default:
                return dp(48);
        }
    }

    int overscanH() {
        return tv() ? dp(24) : 0;
    }

    int overscanV() {
        return tv() ? dp(16) : 0;
    }

    int pickerTopPadLand() {
        return Math.max(dp(16), overscanV());
    }

    int pickerWidthPx(Configuration configuration) {
        return pickerWidthPx(configuration.screenWidthDp, configuration.orientation);
    }

    int pickerWidthPx(int windowWidthDp, int windowOrientation) {
        int preferred;
        switch (deviceClass) {
            case TV:
                preferred = 420;
                break;
            case TABLET_LARGE:
                preferred = 440;
                break;
            case TABLET_MEDIUM:
                preferred = 400;
                break;
            default:
                preferred = 360;
                break;
        }
        int portraitCap = Math.max(0, windowWidthDp - 56);
        int cap = windowOrientation == Configuration.ORIENTATION_LANDSCAPE
                ? Math.min(Math.round(windowWidthDp * 0.60f), portraitCap)
                : portraitCap;
        return dp(Math.max(0, Math.min(preferred, cap)));
    }

    private float text(float phone, float mediumTablet, float largeTablet, float television) {
        switch (deviceClass) {
            case TV:
                return television;
            case TABLET_LARGE:
                return largeTablet;
            case TABLET_MEDIUM:
                return mediumTablet;
            default:
                return phone;
        }
    }

    float textTitle() { return text(18, 20, 21, 22); }
    float textBody() { return text(16, 17, 18, 20); }
    float textCaption() { return text(13, 14, 15, 16); }
    float textList() { return text(15, 16, 17, 18); }
    float textInfo() { return text(12, 13, 13, 14); }
    float textHeaderTitle() { return text(22, 24, 25, 26); }
    float textClock() { return text(20, 21, 22, 22); }
    float textEndsAt() { return text(16, 17, 18, 18); }
    float textSkip() { return text(13, 14, 14, 15); }
    float textBadge() { return text(11, 11, 12, 13); }
    float textValue() { return text(40, 44, 46, 48); }
    float textAction() { return text(15, 16, 16, 18); }
    float textPlaceholder() { return text(20, 21, 22, 22); }
    float textListNumber() { return text(18, 19, 20, 20); }

    boolean sameClassAndWidth(UiMetrics other) {
        return other != null
                && deviceClass == other.deviceClass
                && screenWidthDp == other.screenWidthDp
                && orientation == other.orientation;
    }
}
