package com.brouken.player.together;

import org.json.JSONObject;

/** Wire-level compatibility with LAMPA's lparty.js room protocol. */
final class LpartyCodec {
    static final String T_HELLO = "hello";
    static final String T_ME = "me";
    static final String T_STATE = "state";
    static final String T_SYNC = "sync";
    static final String T_ACT = "act";
    static final String T_URL = "url";
    static final String T_HOST = "host";
    static final String T_BYE = "bye";
    static final String T_HOLD = "hold";
    static final String T_READY = "ready";
    static final String T_GO = "go";
    static final String T_SESSION = "jsess";
    static final String T_BUFFERING = "buf";

    static final String V_RESUMED = "resumed";
    static final String V_PAUSED = "paused";
    static final String V_SEEKED = "seeked";

    private static final String PID = "u";
    private static final String NICK = "n";
    private static final String STATE = "s";
    private static final String POSITION = "p";
    private static final String VERB = "v";
    private static final String SEQ = "sq";
    private static final String SPEED = "sp";
    private static final String PLAYING = "playing";
    private static final String PAUSED = "paused";

    private LpartyCodec() {
    }

    static String type(final JSONObject frame) {
        return frame == null ? "" : frame.optString("t");
    }

    static String pid(final JSONObject frame) {
        return frame == null ? "" : frame.optString(PID);
    }

    static String nick(final JSONObject frame) {
        return frame == null ? "" : frame.optString(NICK);
    }

    static RoomAction act(final JSONObject frame) {
        if (frame == null) {
            return null;
        }
        switch (frame.optString(VERB)) {
            case V_PAUSED:
                return RoomAction.PAUSED;
            case V_RESUMED:
                return RoomAction.RESUMED;
            case V_SEEKED:
                return RoomAction.SEEKED;
            default:
                return null;
        }
    }

    static long positionMs(final JSONObject frame) {
        return frame == null ? 0 : Math.max(0, Math.round(frame.optDouble(POSITION, 0) * 1000));
    }

    static boolean playing(final JSONObject frame) {
        return frame != null && PLAYING.equals(frame.optString(STATE));
    }

    static boolean hasSpeed(final JSONObject frame) {
        return frame != null && frame.has(SPEED);
    }

    static float speed(final JSONObject frame) {
        final double value = frame == null ? 1 : frame.optDouble(SPEED, 1);
        return value > 0 ? (float) value : 1f;
    }

    static long seq(final JSONObject frame) {
        return frame == null ? -1 : frame.optLong(SEQ, -1);
    }

    static JSONObject transport(final String type, final String pid, final long seq,
                                final long posMs, final boolean playing, final float speed,
                                final String verb, final String nick) {
        final JSONObject frame = base(type, pid);
        try {
            frame.put(STATE, playing ? PLAYING : PAUSED)
                    .put(POSITION, posMs / 1000.0)
                    .put(SEQ, seq)
                    .put(SPEED, speed);
            if (verb != null) {
                frame.put(VERB, verb).put(NICK, nick == null ? "" : nick);
            }
            return frame;
        } catch (Exception e) {
            return null;
        }
    }

    static JSONObject state(final String pid, final String roomName, final String owner,
                            final String url, final String title, final String poster,
                            final int tmdbId, final String source, final String mediaType,
                            final boolean playing, final long posMs) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            return base(T_STATE, pid)
                    .put("rn", value(roomName))
                    .put("own", value(owner))
                    .put("url", url)
                    .put("ti", value(title))
                    .put("po", value(poster))
                    .put("tm", tmdbId)
                    .put("src", value(source))
                    .put("ty", mediaType == null ? "movie" : mediaType)
                    .put(STATE, playing ? PLAYING : PAUSED)
                    .put(POSITION, posMs / 1000.0);
        } catch (Exception e) {
            return null;
        }
    }

    static String stateUrl(final JSONObject frame) {
        final String value = frame == null ? "" : frame.optString("url", "");
        return value.isEmpty() ? null : value;
    }

    static String stateOwner(final JSONObject frame) {
        final String value = frame == null ? "" : frame.optString("own", "");
        return value.isEmpty() ? null : value;
    }

    static String stateRoomName(final JSONObject frame) {
        return frame == null ? "" : frame.optString("rn", "");
    }

    static String stateTitle(final JSONObject frame) {
        return frame == null ? "" : frame.optString("ti", "");
    }

    static String statePoster(final JSONObject frame) {
        return frame == null ? "" : frame.optString("po", "");
    }

    static JSONObject hello(final String pid, final String nick) {
        return named(T_HELLO, pid, nick);
    }

    static JSONObject me(final String pid, final String nick) {
        return named(T_ME, pid, nick);
    }

    static JSONObject host(final String pid, final String nick) {
        return named(T_HOST, pid, nick);
    }

    static JSONObject bye(final String pid, final String nick) {
        return named(T_BYE, pid, nick);
    }

    static JSONObject hold(final String pid, final long posMs) {
        return at(T_HOLD, pid, posMs);
    }

    static JSONObject ready(final String pid) {
        return base(T_READY, pid);
    }

    static JSONObject go(final String pid, final long posMs, final boolean playing) {
        final JSONObject frame = at(T_GO, pid, posMs);
        try {
            return frame.put(STATE, playing ? PLAYING : PAUSED);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean goPlaying(final JSONObject frame) {
        return frame != null && (!frame.has(STATE) || playing(frame));
    }

    static JSONObject url(final String pid, final String url, final String title) {
        try {
            return base(T_URL, pid).put("url", url).put("ti", value(title));
        } catch (Exception e) {
            return null;
        }
    }

    static JSONObject session(final String pid, final JSONObject session) {
        if (session == null) {
            return null;
        }
        try {
            return base(T_SESSION, pid).put("ses", session);
        } catch (Exception e) {
            return null;
        }
    }

    static JSONObject sessionOf(final JSONObject frame) {
        return frame == null ? null : frame.optJSONObject("ses");
    }

    static JSONObject buffering(final String pid, final String nick, final boolean buffering) {
        try {
            return named(T_BUFFERING, pid, nick).put(VERB, buffering);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean bufferingValue(final JSONObject frame) {
        return frame != null && frame.optBoolean(VERB);
    }

    private static JSONObject named(final String type, final String pid, final String nick) {
        try {
            return base(type, pid).put(NICK, value(nick));
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject at(final String type, final String pid, final long posMs) {
        try {
            return base(type, pid).put(POSITION, posMs / 1000.0);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject base(final String type, final String pid) {
        try {
            return new JSONObject().put("t", type).put(PID, value(pid));
        } catch (Exception e) {
            return null;
        }
    }

    private static String value(final String text) {
        return text == null ? "" : text;
    }
}
