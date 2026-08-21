package com.brouken.player.together;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** WebSocket transport for the public itty.ws relay used by LAMPA lparty.js. */
public final class Relay {
    public interface Listener {
        void onFrame(JSONObject frame);

        default void onFrame(final JSONObject frame, final String senderUid) {
            onFrame(frame);
        }

        default void onLeave(final String uid, final String alias, final int total) {
            onPeers(total);
        }

        void onReady(int total);

        void onPeers(int total);

        void onConnected(boolean connected);
    }

    public static final String DEFAULT_BASE = "wss://itty.ws/c/";

    private static final String OPTIONS = "?announce=true&list=true";
    private static final long[] BACKOFF_MS = {1_000L, 2_000L, 5_000L};
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();
    private static final ScheduledExecutorService RETRY =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                final Thread thread = new Thread(runnable, "TogetherRetry");
                thread.setDaemon(true);
                return thread;
            });

    private static volatile String base = DEFAULT_BASE;

    private final String channel;
    private final String query;
    private final String endpoint;
    private final Listener listener;
    private final Set<String> members = new LinkedHashSet<>();

    private volatile WebSocket socket;
    private volatile boolean closed;
    private volatile int attempt;
    private volatile int generation;
    private volatile String uid = "";

    public Relay(final String channel, final Listener listener) {
        this(channel, null, listener);
    }

    public Relay(final String channel, final String alias, final Listener listener) {
        this.channel = channel;
        this.listener = listener;
        this.endpoint = base;
        this.query = alias == null || alias.isEmpty()
                ? OPTIONS : OPTIONS + "&as=" + encode(alias);
    }

    public static void setBase(final String url) {
        final String value = url == null ? "" : url.trim();
        final String lower = value.toLowerCase(Locale.US);
        if (value.isEmpty() || !(lower.startsWith("ws://") || lower.startsWith("wss://"))) {
            base = DEFAULT_BASE;
            return;
        }
        base = value.endsWith("/") ? value : value + '/';
    }

    public static String base() {
        return base;
    }

    public String uid() {
        return uid;
    }

    public List<String> memberUids() {
        final List<String> copy;
        synchronized (members) {
            copy = new ArrayList<>(members);
        }
        Collections.sort(copy);
        return copy;
    }

    public void open() {
        if (closed || socket != null) {
            return;
        }
        final int socketGeneration = ++generation;
        final WebSocket opened = CLIENT.newWebSocket(
                new Request.Builder().url(endpoint + channel + query).build(),
                new Callbacks(socketGeneration));
        socket = opened;
        if (closed) {
            socket = null;
            opened.close(1000, null);
        }
    }

    public void close() {
        closed = true;
        final WebSocket open = socket;
        socket = null;
        if (open != null) {
            open.close(1000, null);
        }
        uid = "";
        synchronized (members) {
            members.clear();
        }
    }

    public void send(final JSONObject frame) {
        final WebSocket open = socket;
        if (open != null && frame != null) {
            open.send(frame.toString());
        }
    }

    private void retry() {
        if (closed) {
            return;
        }
        socket = null;
        final long delay = BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)];
        attempt++;
        RETRY.schedule(this::open, delay, TimeUnit.MILLISECONDS);
    }

    private void disconnected(final int callbackGeneration) {
        if (closed || callbackGeneration != generation) {
            return;
        }
        uid = "";
        synchronized (members) {
            members.clear();
        }
        listener.onConnected(false);
        retry();
    }

    private static String encode(final String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private final class Callbacks extends WebSocketListener {
        private final int callbackGeneration;

        Callbacks(final int callbackGeneration) {
            this.callbackGeneration = callbackGeneration;
        }

        private boolean spent() {
            return closed || callbackGeneration != generation;
        }

        @Override
        public void onOpen(@NonNull final WebSocket webSocket, @NonNull final Response response) {
            if (!spent()) {
                attempt = 0;
                listener.onConnected(true);
            }
        }

        @Override
        public void onMessage(@NonNull final WebSocket webSocket, @NonNull final String text) {
            if (spent()) {
                return;
            }
            try {
                final JSONObject envelope = new JSONObject(text);
                final String type = envelope.optString("type");
                if ("join".equals(type)) {
                    handleJoin(envelope);
                    return;
                }
                if ("leave".equals(type)) {
                    final String who = envelope.optString("uid");
                    synchronized (members) {
                        members.remove(who);
                    }
                    listener.onLeave(who, envelope.optString("alias"),
                            envelope.optInt("total", 1));
                    return;
                }
                final JSONObject body = envelope.optJSONObject("message");
                if (body != null && body.has("t")) {
                    listener.onFrame(body, envelope.optString("uid"));
                }
            } catch (Exception ignored) {
                // An invalid peer frame cannot be allowed to tear down playback.
            }
        }

        private void handleJoin(final JSONObject envelope) {
            final String who = envelope.optString("uid");
            if (envelope.optBoolean("self")) {
                uid = who;
                final JSONArray users = envelope.optJSONArray("users");
                synchronized (members) {
                    members.clear();
                    if (!who.isEmpty()) {
                        members.add(who);
                    }
                    for (int i = 0; users != null && i < users.length(); i++) {
                        final JSONObject user = users.optJSONObject(i);
                        final String member = user == null ? "" : user.optString("uid");
                        if (!member.isEmpty()) {
                            members.add(member);
                        }
                    }
                }
                listener.onReady(envelope.optInt("total", 1));
                return;
            }
            synchronized (members) {
                if (!who.isEmpty()) {
                    members.add(who);
                }
            }
            listener.onPeers(envelope.optInt("total", 1));
        }

        @Override
        public void onClosed(@NonNull final WebSocket webSocket, final int code,
                             @NonNull final String reason) {
            disconnected(callbackGeneration);
        }

        @Override
        public void onFailure(@NonNull final WebSocket webSocket, @NonNull final Throwable error,
                              final Response response) {
            disconnected(callbackGeneration);
        }
    }
}
