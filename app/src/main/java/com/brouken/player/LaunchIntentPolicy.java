package com.brouken.player;

final class LaunchIntentPolicy {

    private static final String ACTION_MAIN = "android.intent.action.MAIN";

    private LaunchIntentPolicy() {}

    static boolean shouldSuppressResume(String action, boolean hasData, boolean hasRoomInvite) {
        return ACTION_MAIN.equals(action) && !hasData && !hasRoomInvite;
    }

    static boolean shouldInheritLiveSession(String action, boolean hasData,
                                            boolean hasRoomInvite, boolean liveHasMedia) {
        return liveHasMedia && shouldSuppressResume(action, hasData, hasRoomInvite);
    }
}
