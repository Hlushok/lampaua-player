package com.brouken.player;

final class PlaybackRecoveryPolicy {

    static final int MAX_SOURCE_RETRIES = 3;
    static final int MAX_COMPATIBILITY_RETRIES = 1;

    enum Action {
        REPREPARE_SOURCE,
        RETRY_SOURCE,
        RETRY_COMPATIBILITY,
        LOWER_QUALITY,
        FAIL
    }

    enum FailureKind {
        NETWORK_READ,
        NETWORK_RESPONSE,
        SOURCE_CONFIGURATION,
        RESOLVER_NOT_READY,
        PLAYLIST_STUCK,
        DECODER,
        STALL_AT_START,
        STALL_MIDSTREAM,
        LIVE_STALL,
        TRUNCATED_LOCAL_FILE,
        UNKNOWN
    }

    private PlaybackRecoveryPolicy() {
    }

    static Action decide(FailureKind kind, boolean everReady, int sourceRetries,
                         int compatibilityRetries, boolean lowerQualityAvailable) {
        if (kind == null || kind == FailureKind.NETWORK_RESPONSE
                || kind == FailureKind.SOURCE_CONFIGURATION
                || kind == FailureKind.TRUNCATED_LOCAL_FILE
                || kind == FailureKind.LIVE_STALL || kind == FailureKind.UNKNOWN) {
            return Action.FAIL;
        }

        if (kind == FailureKind.NETWORK_READ) {
            return sourceRetries < MAX_SOURCE_RETRIES ? Action.REPREPARE_SOURCE : Action.FAIL;
        }
        if (kind == FailureKind.RESOLVER_NOT_READY) {
            return sourceRetries < MAX_SOURCE_RETRIES ? Action.RETRY_SOURCE : Action.FAIL;
        }
        if (kind == FailureKind.PLAYLIST_STUCK) {
            return sourceRetries < MAX_SOURCE_RETRIES ? Action.RETRY_SOURCE : Action.FAIL;
        }

        if (!everReady && sourceRetries < MAX_SOURCE_RETRIES) {
            return Action.RETRY_SOURCE;
        }
        if (compatibilityRetries < MAX_COMPATIBILITY_RETRIES) {
            return Action.RETRY_COMPATIBILITY;
        }
        if (lowerQualityAvailable) {
            return Action.LOWER_QUALITY;
        }
        return Action.FAIL;
    }

    static long recoveryPosition(long currentPosition, long lastObservedPosition,
                                 boolean everReady) {
        if (currentPosition > 0L) return currentPosition;
        return everReady && lastObservedPosition > 0L ? lastObservedPosition : 0L;
    }
}
