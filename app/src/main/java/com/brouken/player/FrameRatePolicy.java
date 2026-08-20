package com.brouken.player;

final class FrameRatePolicy {
    private FrameRatePolicy() {}

    static boolean isWholeMultiple(float displayRate, float contentRate) {
        if (displayRate <= 0f || contentRate <= 0f) return false;
        float ratio = displayRate / contentRate;
        int multiple = Math.round(ratio);
        return multiple >= 1 && Math.abs(ratio - multiple) < multiple * 0.0002f;
    }

    static float bestRate(float activeRate, float contentRate, float[] supportedRates) {
        float fallback = Math.max(0f, activeRate);
        float bestMultiple = 0f;
        if (supportedRates == null) return fallback;
        for (float rate : supportedRates) {
            if (rate <= 0f) continue;
            fallback = Math.max(fallback, rate);
            if (rate + 0.001f < contentRate) continue;
            if (isWholeMultiple(rate, contentRate) && rate > bestMultiple) {
                bestMultiple = rate;
            }
        }
        return bestMultiple > 0f ? bestMultiple : fallback;
    }
}
