package com.brouken.player;

final class LoadWatchdogPolicy {
    static final long MIN_PROGRESS_BYTES = 256L * 1024L;
    static final int MAX_SILENT_WINDOWS = 4;

    enum SourceKind { LOCAL, NETWORK, LIVE }
    enum Action { REARM, REPORT_INITIAL_TIMEOUT, REPORT_MIDSTREAM_STALL, IGNORE }

    private LoadWatchdogPolicy() {}

    static Action evaluate(boolean buffering, boolean everReady,
                           long startBytes, long endBytes, SourceKind sourceKind) {
        if (!buffering || sourceKind == null) return Action.IGNORE;
        long progress = Math.max(0L, endBytes - startBytes);
        if (progress >= MIN_PROGRESS_BYTES) {
            return Action.REARM;
        }
        return everReady ? Action.REPORT_MIDSTREAM_STALL : Action.REPORT_INITIAL_TIMEOUT;
    }

    static boolean shouldWaitForConnectedSource(boolean connected, int completedSilentWindows) {
        return connected && completedSilentWindows + 1 < MAX_SILENT_WINDOWS;
    }

    static boolean shouldHoldTvDecoder(boolean tvBox, boolean everReady) {
        return tvBox && everReady;
    }
}
