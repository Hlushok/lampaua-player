package com.brouken.player.together;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure room synchronization logic, adapted from Just+ Player by Oleksandr Zhyzhchenko.
 * It samples player state and returns commands without owning Media3 or Android lifecycle objects.
 */
public final class SyncEngine {
    public static final long TICK_MS = 250L;
    public static final long BUFFER_WAIT_MAX_MS = 15_000L;
    public static final long SETTLE_MS = 3_000L;

    private static final long HEARTBEAT_MS = 3_000L;
    private static final long LOCAL_ACTION_MS = 700L;
    private static final long DRIFT_DEADBAND_MS = 300L;
    private static final long DRIFT_HARD_SEEK_MS = 1_500L;
    private static final long TARGET_STALE_MS = 15_000L;
    private static final long HARD_SEEK_COOLDOWN_MS = 3_000L;
    private static final long LOCAL_COMMAND_MS = 2_000L;
    private static final long MEDIA_CONFIRM_MS = 10_000L;
    private static final long END_SLACK_MS = 1_000L;
    private static final float CORRECTION_GAIN = 0.1f;
    private static final float CORRECTION_MAX = 0.1f;
    private static final float SPEED_EPSILON = 0.001f;

    public static final class Action {
        public final List<JSONObject> out = new ArrayList<>(2);
        public long seekToMs = -1L;
        public Boolean play;
        public Float speed;
        public String waitingFor;
    }

    private static final class Stall {
        final String nick;
        final long at;

        Stall(final String nick, final long at) {
            this.nick = nick;
            this.at = at;
        }
    }

    private final Map<String, Stall> stalls = new LinkedHashMap<>();

    private String pid = "";
    private String nick = "";
    private String ownerPid = "";
    private String appliedPid = "";
    private long seq;
    private long appliedSeq = -1L;

    private boolean primed;
    private long lastPosMs;
    private boolean lastPlay;
    private float lastSpeed = 1f;
    private long lastAt;

    private boolean targetKnown;
    private long lastFrameAt;
    private long targetPosMs;
    private boolean targetPlay;
    private float targetSpeed = 1f;
    private long targetAt;

    private float userSpeed = 1f;
    private long lastSentAt;
    private long settleUntil;
    private boolean intentPlay;
    private boolean buffering;
    private boolean localBuffering;
    private boolean ended;
    private boolean arriving;
    private long durationMs;
    private long unconfirmedUntil;
    private long lastHardSeekAt = -HARD_SEEK_COOLDOWN_MS;
    private long localCommandAt = -LOCAL_COMMAND_MS;
    private long landedOnMs = Long.MIN_VALUE;

    public void bind(final String pid, final String nick) {
        this.pid = pid == null ? "" : pid;
        this.nick = nick == null ? "" : nick;
    }

    public void setOwner(final String ownerPid) {
        this.ownerPid = ownerPid == null ? "" : ownerPid;
    }

    public void setBuffering(final boolean value) {
        buffering = value;
    }

    public void setEnded(final boolean value) {
        ended = value;
    }

    public void setDuration(final long value) {
        durationMs = Math.max(0L, value);
    }

    public void mediaChanged(final boolean confirmed, final long now) {
        targetKnown = false;
        arriving = true;
        landedOnMs = Long.MIN_VALUE;
        appliedPid = "";
        unconfirmedUntil = confirmed ? 0L : now + MEDIA_CONFIRM_MS;
        lastHardSeekAt = now - HARD_SEEK_COOLDOWN_MS;
        reprime();
    }

    public void mediaConfirmed() {
        unconfirmedUntil = 0L;
    }

    public float userSpeed() {
        return userSpeed;
    }

    public boolean trimmed() {
        return Math.abs(lastSpeed - userSpeed) > SPEED_EPSILON;
    }

    public long position() {
        return lastPosMs;
    }

    public long roomPositionMs(final long now) {
        return targetKnown ? Math.max(0L, target(now)) : -1L;
    }

    public void settle(final long until) {
        settleUntil = Math.max(settleUntil, until);
    }

    public void seed(final long posMs, final boolean playing, final long now) {
        lastFrameAt = now;
        intentPlay = playing;
        setTarget(posMs, playing, userSpeed, now);
    }

    public void reprime() {
        primed = false;
    }

    public Action tick(final long now, final long posMs, final boolean play, final float speed) {
        final Action action = new Action();
        if (!primed) {
            primed = true;
            if (Math.abs(speed - lastSpeed) > SPEED_EPSILON) {
                userSpeed = speed;
            }
            intentPlay = play;
            setLast(posMs, play, speed, now);
            return action;
        }

        final long expected = lastPosMs + (lastPlay ? Math.round((now - lastAt) * lastSpeed) : 0L);
        final boolean speedChanged = Math.abs(speed - lastSpeed) > SPEED_EPSILON;
        final boolean movedByUser = Math.abs(posMs - expected) > LOCAL_ACTION_MS;
        final boolean userAction = !ended && (speedChanged || play != lastPlay || movedByUser);
        if (speedChanged) {
            userSpeed = speed;
        }

        final boolean settling = now < settleUntil;
        final String waiting = waitingFor(now);
        if (userAction && !settling) {
            seq++;
            appliedSeq = seq;
            appliedPid = pid;
            intentPlay = play;
            localCommandAt = now;
            setTarget(posMs, play, userSpeed, now);
            final String verb = play != lastPlay
                    ? (play ? LpartyCodec.V_RESUMED : LpartyCodec.V_PAUSED)
                    : (movedByUser ? LpartyCodec.V_SEEKED : null);
            add(action, LpartyCodec.transport(
                    LpartyCodec.T_ACT, pid, seq, posMs, play, userSpeed, verb, nick));
            lastSentAt = now;
        } else {
            if (targetKnown) {
                intentPlay = targetPlay;
            }
            if (!settling && !ended && waiting == null && now - lastSentAt >= HEARTBEAT_MS) {
                add(action, LpartyCodec.transport(
                        LpartyCodec.T_SYNC, pid, seq, posMs, intentPlay, userSpeed, null, nick));
                lastSentAt = now;
            }
        }

        if (targetKnown && now - lastFrameAt > TARGET_STALE_MS) {
            targetKnown = false;
        }
        if (!buffering) {
            arriving = false;
        }

        action.waitingFor = waiting;
        final boolean wantPlay = intentPlay && waiting == null;
        float wantSpeed = userSpeed;
        if (targetKnown && wantPlay) {
            final long drift = posMs - target(now);
            if (Math.abs(drift) > DRIFT_HARD_SEEK_MS
                    && (!ended || drift > 0)
                    && (arriving || (!buffering && !localBuffering))
                    && jumpAllowed(now)) {
                action.seekToMs = Math.max(0L, target(now));
                lastHardSeekAt = now;
                arriving = false;
            } else if (Math.abs(drift) > DRIFT_DEADBAND_MS) {
                float trim = (drift / 1000f) * CORRECTION_GAIN;
                trim = Math.max(-CORRECTION_MAX, Math.min(CORRECTION_MAX, trim));
                wantSpeed = userSpeed - trim;
            }
        } else if (targetKnown && !targetPlay && !buffering && !localBuffering
                && landedOnMs != targetPosMs
                && Math.abs(posMs - targetPosMs) > DRIFT_HARD_SEEK_MS
                && jumpAllowed(now)) {
            action.seekToMs = Math.max(0L, targetPosMs);
            landedOnMs = targetPosMs;
            lastHardSeekAt = now;
            settle(now + SETTLE_MS);
        }

        if (wantPlay != play) {
            action.play = wantPlay;
        }
        if (Math.abs(wantSpeed - speed) > SPEED_EPSILON) {
            action.speed = wantSpeed;
        }
        if (settling && targetKnown && Math.abs(posMs - target(now)) <= DRIFT_DEADBAND_MS) {
            settleUntil = 0L;
        }
        setLast(action.seekToMs >= 0L ? action.seekToMs : posMs, wantPlay, wantSpeed, now);
        return action;
    }

    public RoomAction onFrame(final JSONObject frame, final long now) {
        final String type = LpartyCodec.type(frame);
        final String framePid = LpartyCodec.pid(frame);
        if (framePid.isEmpty() || framePid.equals(pid)) {
            return null;
        }
        if (LpartyCodec.T_BUFFERING.equals(type)) {
            if (LpartyCodec.bufferingValue(frame)) {
                stalls.put(framePid, new Stall(LpartyCodec.nick(frame), now));
            } else {
                stalls.remove(framePid);
            }
            return null;
        }

        final boolean isAction = LpartyCodec.T_ACT.equals(type);
        if (!isAction && !LpartyCodec.T_SYNC.equals(type)) {
            return null;
        }

        final long frameSeq = LpartyCodec.seq(frame);
        if (frameSeq >= 0L) {
            seq = Math.max(seq, frameSeq);
        }
        if (now - localCommandAt < LOCAL_COMMAND_MS) {
            return null;
        }
        if (isAction) {
            if (frameSeq >= 0L) {
                if (frameSeq < appliedSeq
                        || (frameSeq == appliedSeq && framePid.compareTo(appliedPid) <= 0)) {
                    return null;
                }
                appliedSeq = frameSeq;
            }
            appliedPid = framePid;
        } else if (pid.equals(ownerPid)) {
            return null;
        } else if (appliedPid.isEmpty()) {
            appliedPid = framePid;
        } else if (!appliedPid.equals(framePid)) {
            return null;
        }

        final long framePos = LpartyCodec.positionMs(frame);
        if (durationMs > 0L && framePos >= durationMs - END_SLACK_MS) {
            return null;
        }
        if (unconfirmedUntil != 0L) {
            if (now < unconfirmedUntil) {
                return null;
            }
            unconfirmedUntil = 0L;
        }

        final boolean jumped = targetKnown && Math.abs(framePos - target(now)) > 5_000L;
        lastFrameAt = now;
        final float receivedSpeed = LpartyCodec.hasSpeed(frame)
                ? LpartyCodec.speed(frame) : userSpeed;
        setTarget(framePos, LpartyCodec.playing(frame), receivedSpeed, now);
        userSpeed = targetSpeed;
        final RoomAction act = isAction ? LpartyCodec.act(frame) : null;
        return act != null ? act : (jumped ? RoomAction.SEEKED : null);
    }

    public void forget(final String memberPid) {
        if (memberPid == null) {
            return;
        }
        stalls.remove(memberPid);
        if (memberPid.equals(appliedPid)) {
            appliedPid = "";
        }
    }

    public JSONObject localBuffering(final boolean value, final long now) {
        if (localBuffering == value) {
            return null;
        }
        localBuffering = value;
        return LpartyCodec.buffering(pid, nick, value);
    }

    public boolean playing() {
        return intentPlay;
    }

    private String waitingFor(final long now) {
        if (localBuffering) {
            return null;
        }
        final Iterator<Map.Entry<String, Stall>> iterator = stalls.entrySet().iterator();
        while (iterator.hasNext()) {
            final Stall stall = iterator.next().getValue();
            if (now - stall.at > BUFFER_WAIT_MAX_MS) {
                iterator.remove();
                continue;
            }
            return stall.nick;
        }
        return null;
    }

    private void setLast(final long posMs, final boolean play, final float speed, final long now) {
        lastPosMs = posMs;
        lastPlay = play;
        lastSpeed = speed;
        lastAt = now;
    }

    private void setTarget(final long posMs, final boolean play, final float speed, final long now) {
        targetPosMs = posMs;
        targetPlay = play;
        targetSpeed = speed > 0f ? speed : 1f;
        targetAt = now;
        targetKnown = true;
    }

    private long target(final long now) {
        return targetPosMs + (targetPlay ? Math.round((now - targetAt) * targetSpeed) : 0L);
    }

    private boolean jumpAllowed(final long now) {
        return now - lastHardSeekAt >= HARD_SEEK_COOLDOWN_MS;
    }

    private static void add(final Action action, final JSONObject frame) {
        if (frame != null) {
            action.out.add(frame);
        }
    }
}
