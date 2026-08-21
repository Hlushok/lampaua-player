package com.brouken.player.together;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ephemeral LAMPA-compatible room discovery over a shared relay channel. */
final class Lobby {
    interface Found {
        void onRooms(List<JSONObject> rooms);
    }

    interface AdSource {
        JSONObject ad();
    }

    private static final String CHANNEL = "lparty-lobby-v1";
    private static final long COLLECT_MS = 1_500L;
    private static final int REPLY_MIN_MS = 50;
    private static final int REPLY_SPREAD_MS = 350;

    private Lobby() {
    }

    static void discover(final Handler handler, final Found callback) {
        final Map<String, JSONObject> found = new LinkedHashMap<>();
        final Relay[] relay = new Relay[1];
        relay[0] = new Relay(CHANNEL, new SimpleRelayListener() {
            @Override
            public void onFrame(final JSONObject frame) {
                if (!"ad".equals(frame.optString("t"))) {
                    return;
                }
                final JSONObject ad = frame.optJSONObject("r");
                if (ad != null && !ad.optString("id").isEmpty()) {
                    synchronized (found) {
                        found.put(ad.optString("id"), ad);
                    }
                }
            }

            @Override
            public void onReady(final int total) {
                relay[0].send(frame("who", null));
            }
        });
        relay[0].open();

        handler.postDelayed(() -> {
            relay[0].close();
            final List<JSONObject> rooms;
            synchronized (found) {
                rooms = new ArrayList<>(found.values());
            }
            Collections.sort(rooms,
                    (a, b) -> Integer.compare(b.optInt("members"), a.optInt("members")));
            callback.onRooms(rooms);
        }, COLLECT_MS);
    }

    static final class Publisher {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final SecureRandom random = new SecureRandom();
        private final AdSource source;
        private Relay relay;

        Publisher(final AdSource source) {
            this.source = source;
        }

        void start() {
            if (relay != null) {
                return;
            }
            relay = new Relay(CHANNEL, new SimpleRelayListener() {
                @Override
                public void onFrame(final JSONObject message) {
                    if (!"who".equals(message.optString("t"))) {
                        return;
                    }
                    handler.postDelayed(() -> {
                        final JSONObject ad = source.ad();
                        if (relay != null && ad != null) {
                            relay.send(frame("ad", ad));
                        }
                    }, REPLY_MIN_MS + random.nextInt(REPLY_SPREAD_MS));
                }
            });
            relay.open();
        }

        void stop() {
            if (relay != null) {
                relay.close();
                relay = null;
            }
        }
    }

    private abstract static class SimpleRelayListener implements Relay.Listener {
        @Override
        public void onFrame(final JSONObject frame) {
        }

        @Override
        public void onReady(final int total) {
        }

        @Override
        public void onPeers(final int total) {
        }

        @Override
        public void onConnected(final boolean connected) {
        }
    }

    private static JSONObject frame(final String type, final JSONObject room) {
        try {
            final JSONObject frame = new JSONObject().put("t", type);
            return room == null ? frame : frame.put("r", room);
        } catch (Exception e) {
            return null;
        }
    }
}
