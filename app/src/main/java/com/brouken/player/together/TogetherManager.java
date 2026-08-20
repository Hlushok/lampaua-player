package com.brouken.player.together;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns one shared-viewing room for the activity lifecycle, independently of Media3 rebuilds.
 * Behavior is adapted from Just+ Player by Oleksandr Zhyzhchenko.
 */
public final class TogetherManager implements Relay.Listener {
    public interface Host {
        boolean ready();

        boolean scrubbing();

        long positionMs();

        boolean playWhenReady();

        float speed();

        boolean buffering();

        boolean ended();

        long durationMs();

        String playingUri();

        long bufferedAheadMs();

        void applyPlay(boolean play);

        void applySeek(long positionMs);

        void applySpeed(float speed);

        void onRoomChanged();

        void onRoomAction(String nick, RoomAction action);

        JSONObject sessionDescription();

        JSONObject roomCard();

        void openSession(JSONObject session);

        void onJoinFailed();

        void onHoldLifted();
    }

    public interface Rooms {
        void onRooms(List<JSONObject> rooms);
    }

    private static final long BUFFER_ARM_MS = 1_000L;
    private static final long JOIN_TIMEOUT_MS = 6_000L;
    private static final long OWNER_ELECTION_MS = 500L;
    private static final long HOLD_MAX_MS = 15_000L;
    private static final long HOLD_BUFFER_MS = 6_000L;
    private static final long HOLD_MIN_BUFFER_MS = 2_000L;
    private static final long HOLD_STALL_MS = 3_000L;
    private static final long RECONNECT_HELLO_MS = 60_000L;
    private static final String PID = newPid();

    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, String> nicks = new HashMap<>();
    private final Map<String, String> pidByUid = new HashMap<>();
    private final Map<String, Long> helloSeenAt = new HashMap<>();
    private final Set<String> holdWaiting = new HashSet<>();
    private final Lobby.Publisher publisher = new Lobby.Publisher(this::advertisement);

    private SyncEngine engine = new SyncEngine();
    private Room room;
    private Relay relay;
    private String nick = "";
    private String roomName = "";
    private JSONObject session;
    private String owner;
    private boolean joining;
    private boolean mediaApplied;
    private boolean running;
    private boolean connected;
    private boolean everConnected;
    private int peers = 1;
    private String waitingFor;
    private long bufferingSince;

    private boolean holdActive;
    private boolean holdOurs;
    private boolean holdWasPlaying;
    private long holdPositionMs;
    private boolean holdReadySent;
    private long holdBufferSeenMs;
    private long holdBufferAt;

    public TogetherManager(final Host host) {
        this.host = host;
    }

    public void create(final String code, final String password, final boolean listed,
                       final String roomName, final JSONObject session, final String nick) {
        enter(code, password, nick);
        this.roomName = roomName == null ? "" : roomName;
        this.session = session;
        assignOwner(PID);
        mediaApplied = true;
        if (listed && room.hasPassword()) {
            publisher.start();
        }
        host.onRoomChanged();
    }

    public void join(final String code, final String password, final String nick) {
        enter(code, password, nick);
        joining = true;
        host.onRoomChanged();
    }

    private void enter(final String code, final String password, final String nick) {
        leave();
        room = new Room(code, password);
        this.nick = nick == null ? "" : nick;
        roomName = "";
        session = null;
        joining = false;
        mediaApplied = false;
        connected = false;
        everConnected = false;
        peers = 1;
        waitingFor = null;
        bufferingSince = 0L;
        engine = new SyncEngine();
        engine.bind(PID, this.nick);
        assignOwner(null);
        relay = new Relay(room.channel(), this.nick, this);
        relay.open();
        running = true;
        handler.postDelayed(ticker, SyncEngine.TICK_MS);
    }

    public void leave() {
        if (running && engine.trimmed()) {
            host.applySpeed(engine.userSpeed());
        }
        running = false;
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(joinTimeout);
        handler.removeCallbacks(ownerElection);
        handler.removeCallbacks(holdExpiry);
        publisher.stop();
        if (relay != null) {
            relay.send(LpartyCodec.bye(PID, nick));
            relay.close();
            relay = null;
        }
        room = null;
        session = null;
        owner = null;
        joining = false;
        connected = false;
        waitingFor = null;
        nicks.clear();
        pidByUid.clear();
        helloSeenAt.clear();
        clearHold();
        host.onRoomChanged();
    }

    public void suspend() {
        running = false;
        handler.removeCallbacks(ticker);
    }

    public void resume() {
        if (room == null || running) {
            return;
        }
        engine.reprime();
        engine.settle(SystemClock.elapsedRealtime() + SyncEngine.SETTLE_MS);
        running = true;
        handler.postDelayed(ticker, SyncEngine.TICK_MS);
    }

    public boolean isActive() {
        return room != null;
    }

    public String code() {
        return room == null ? null : room.code();
    }

    public String roomName() {
        return roomName == null ? "" : roomName;
    }

    public String url() {
        return session == null ? null : session.optString("uri", null);
    }

    public String invite() {
        return room == null ? null : room.invite();
    }

    public int peers() {
        return peers;
    }

    public boolean isOwner() {
        return PID.equals(owner);
    }

    public float userSpeed() {
        return engine.userSpeed();
    }

    public boolean connected() {
        return connected;
    }

    public boolean everConnected() {
        return everConnected;
    }

    public String waitingFor() {
        return waitingFor;
    }

    public long roomPositionMs() {
        return engine.roomPositionMs(SystemClock.elapsedRealtime());
    }

    public boolean holding() {
        return holdActive;
    }

    public void changeMedia(final JSONObject session) {
        if (relay == null || !isOwner() || session == null) {
            return;
        }
        this.session = session;
        relay.send(LpartyCodec.session(PID, session));
        relay.send(LpartyCodec.url(PID, session.optString("uri", null), cardTitle()));
        stepped(true);
    }

    public void mediaStepped(final JSONObject session) {
        if (session != null) {
            this.session = session;
        }
        stepped(false);
    }

    public static void discover(final Rooms callback) {
        Lobby.discover(new Handler(Looper.getMainLooper()), callback::onRooms);
    }

    private void stepped(final boolean confirmed) {
        final long now = SystemClock.elapsedRealtime();
        engine.mediaChanged(confirmed, now);
        engine.settle(now + SyncEngine.SETTLE_MS);
    }

    private void assignOwner(final String ownerPid) {
        owner = ownerPid;
        engine.setOwner(ownerPid);
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            tick();
            handler.postDelayed(this, SyncEngine.TICK_MS);
        }
    };

    private final Runnable joinTimeout = this::joinTimedOut;

    private void joinTimedOut() {
        if (!joining) {
            return;
        }
        joining = false;
        leave();
        host.onJoinFailed();
    }

    private final Runnable ownerElection = this::electOwner;
    private final Runnable holdExpiry = this::holdTimedOut;

    private void tick() {
        final long now = SystemClock.elapsedRealtime();
        if (relay == null || joining || !host.ready()) {
            engine.reprime();
            engine.settle(now + SyncEngine.SETTLE_MS);
            updateWaiting(null);
            return;
        }
        if (holdActive) {
            engine.reprime();
            engine.settle(now + SyncEngine.SETTLE_MS);
            updateWaiting(null);
            holdTick();
            return;
        }
        if (host.scrubbing()) {
            return;
        }

        updateBuffering(now);
        engine.setBuffering(host.buffering());
        engine.setEnded(host.ended());
        engine.setDuration(host.durationMs());
        final SyncEngine.Action action = engine.tick(
                now, host.positionMs(), host.playWhenReady(), host.speed());
        for (JSONObject frame : action.out) {
            relay.send(frame);
        }
        if (action.seekToMs >= 0L) {
            host.applySeek(action.seekToMs);
        }
        if (action.play != null) {
            host.applyPlay(action.play);
        }
        if (action.speed != null) {
            host.applySpeed(action.speed);
        }
        updateWaiting(action.waitingFor);
    }

    private void updateWaiting(final String value) {
        if (same(waitingFor, value)) {
            return;
        }
        waitingFor = value;
        host.onRoomChanged();
    }

    private void updateBuffering(final long now) {
        if (host.buffering()) {
            if (bufferingSince == 0L) {
                bufferingSince = now;
            } else if (now - bufferingSince > BUFFER_ARM_MS) {
                send(engine.localBuffering(true, now));
            }
            return;
        }
        bufferingSince = 0L;
        send(engine.localBuffering(false, now));
    }

    private void answerNewcomer() {
        if (!isOwner()) {
            return;
        }
        send(LpartyCodec.session(PID, freshSession()));
        final JSONObject card = host.roomCard();
        if (card == null) {
            return;
        }
        send(LpartyCodec.state(
                PID,
                roomName(),
                owner,
                card.optString("url", null),
                card.optString("title", ""),
                card.optString("poster", ""),
                card.optInt("tmdb"),
                card.optString("source", ""),
                card.optString("type", "movie"),
                engine.playing(),
                host.ready() ? host.positionMs() : engine.position()
        ));
    }

    private JSONObject freshSession() {
        if (session == null) {
            return null;
        }
        final JSONObject fresh = host.sessionDescription();
        if (fresh != null && session.optString("uri", "")
                .equals(fresh.optString("uri", null))) {
            session = fresh;
        }
        return session;
    }

    private void applyMedia(final JSONObject candidate) {
        if (candidate == null) {
            return;
        }
        session = candidate;
        if (joining) {
            joining = false;
            handler.removeCallbacks(joinTimeout);
        }
        if (!mediaApplied) {
            mediaApplied = true;
            engine.settle(SystemClock.elapsedRealtime() + SyncEngine.SETTLE_MS);
            host.openSession(candidate);
        }
        host.onRoomChanged();
    }

    private static JSONObject thinSession(final String url, final String title,
                                          final String poster) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            final JSONObject value = new JSONObject().put("uri", url);
            if (title != null && !title.isEmpty()) {
                value.put("title", title);
            }
            if (poster != null && !poster.isEmpty()) {
                value.put("poster", poster);
            }
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject advertisement() {
        final JSONObject card = room == null || !isOwner() ? null : host.roomCard();
        if (card == null || card.optString("url", "").isEmpty()) {
            return null;
        }
        try {
            return new JSONObject()
                    .put("id", room.code())
                    .put("name", roomName().isEmpty() ? room.code() : roomName())
                    .put("title", card.optString("title", ""))
                    .put("poster", card.optString("poster", ""))
                    .put("owner", nick)
                    .put("members", peers)
                    .put("pwd", room.hasPassword() ? 1 : 0)
                    .put("tmdb", card.optInt("tmdb"))
                    .put("type", card.optString("type", "movie"));
        } catch (Exception e) {
            return null;
        }
    }

    private String cardTitle() {
        final JSONObject card = host.roomCard();
        return card == null ? "" : card.optString("title", "");
    }

    private void startHold(final String newcomer) {
        if (relay == null || !isOwner() || !host.ready()) {
            return;
        }
        if (!holdActive) {
            holdActive = true;
            holdOurs = true;
            holdWasPlaying = host.playWhenReady();
            holdPositionMs = host.positionMs();
            holdWaiting.clear();
            holdReadySent = false;
            resetHoldProgress();
            host.applyPlay(false);
            host.onRoomChanged();
        }
        if (newcomer != null && !newcomer.isEmpty() && !PID.equals(newcomer)) {
            holdWaiting.add(newcomer);
        }
        send(LpartyCodec.hold(PID, holdPositionMs));
        handler.removeCallbacks(holdExpiry);
        handler.postDelayed(holdExpiry, HOLD_MAX_MS);
    }

    private void applyHold(final long positionMs) {
        if (!holdActive) {
            holdActive = true;
            holdOurs = false;
            holdWasPlaying = host.playWhenReady();
        }
        holdPositionMs = positionMs;
        holdReadySent = false;
        resetHoldProgress();
        host.applyPlay(false);
        if (Math.abs(host.positionMs() - positionMs) > 1_000L) {
            host.applySeek(positionMs);
        }
        host.onRoomChanged();
        handler.removeCallbacks(holdExpiry);
        handler.postDelayed(holdExpiry, HOLD_MAX_MS + OWNER_ELECTION_MS * 4L);
    }

    private void markReady(final String memberPid) {
        if (!holdActive || !holdOurs) {
            return;
        }
        holdWaiting.remove(memberPid);
        checkHoldDone();
    }

    private void checkHoldDone() {
        if (holdActive && holdOurs && holdWaiting.isEmpty() && bufferReady()) {
            send(LpartyCodec.go(PID, holdPositionMs, holdWasPlaying));
            releaseHold(holdPositionMs, holdWasPlaying);
        }
    }

    private void holdTimedOut() {
        if (!holdActive) {
            return;
        }
        if (holdOurs) {
            send(LpartyCodec.go(PID, holdPositionMs, holdWasPlaying));
        }
        releaseHold(holdPositionMs, holdWasPlaying);
    }

    private void releaseHold(final long positionMs, final boolean play) {
        if (!holdActive) {
            return;
        }
        final boolean ours = holdOurs;
        clearHold();
        if (Math.abs(host.positionMs() - positionMs) > 500L) {
            host.applySeek(positionMs);
        }
        host.applyPlay(play);
        if (!ours) {
            engine.seed(positionMs, play, SystemClock.elapsedRealtime());
        }
        engine.reprime();
        engine.settle(SystemClock.elapsedRealtime() + SyncEngine.SETTLE_MS);
        host.onHoldLifted();
        host.onRoomChanged();
    }

    private void holdTick() {
        if (holdOurs) {
            checkHoldDone();
        } else if (!holdReadySent && bufferReady()) {
            holdReadySent = true;
            send(LpartyCodec.ready(PID));
        }
    }

    private boolean bufferReady() {
        final long ahead = host.bufferedAheadMs();
        final long now = SystemClock.elapsedRealtime();
        if (ahead > holdBufferSeenMs + 250L) {
            holdBufferSeenMs = ahead;
            holdBufferAt = now;
        }
        return ahead >= HOLD_BUFFER_MS
                || (ahead >= HOLD_MIN_BUFFER_MS && now - holdBufferAt > HOLD_STALL_MS);
    }

    private void resetHoldProgress() {
        holdBufferSeenMs = -1L;
        holdBufferAt = SystemClock.elapsedRealtime();
    }

    private void clearHold() {
        holdActive = false;
        holdOurs = false;
        holdWasPlaying = false;
        holdWaiting.clear();
        holdReadySent = false;
        handler.removeCallbacks(holdExpiry);
    }

    private void send(final JSONObject frame) {
        if (frame != null && relay != null) {
            relay.send(frame);
        }
    }

    @Override
    public void onFrame(final JSONObject frame) {
        onFrame(frame, "");
    }

    @Override
    public void onFrame(final JSONObject frame, final String senderUid) {
        handler.post(() -> handleFrame(frame, senderUid));
    }

    private void handleFrame(final JSONObject frame, final String senderUid) {
        if (relay == null) {
            return;
        }
        final String type = LpartyCodec.type(frame);
        final String framePid = LpartyCodec.pid(frame);
        final String frameNick = LpartyCodec.nick(frame);
        if (!frameNick.isEmpty()) {
            nicks.put(framePid, frameNick);
        }
        if (!senderUid.isEmpty() && !framePid.isEmpty()) {
            pidByUid.put(senderUid, framePid);
        }

        if (LpartyCodec.T_HELLO.equals(type)) {
            relay.send(LpartyCodec.me(PID, nick));
            answerNewcomer();
            final long now = SystemClock.elapsedRealtime();
            final Long seenAt = helloSeenAt.put(framePid, now);
            if (seenAt == null || now - seenAt >= RECONNECT_HELLO_MS) {
                startHold(framePid);
            }
            return;
        }
        if (LpartyCodec.T_HOLD.equals(type)) {
            if (owner == null || framePid.equals(owner)) {
                applyHold(LpartyCodec.positionMs(frame));
            }
            return;
        }
        if (LpartyCodec.T_READY.equals(type)) {
            markReady(framePid);
            return;
        }
        if (LpartyCodec.T_GO.equals(type)) {
            if (owner == null || framePid.equals(owner)) {
                releaseHold(LpartyCodec.positionMs(frame), LpartyCodec.goPlaying(frame));
            }
            return;
        }
        if (LpartyCodec.T_SESSION.equals(type)) {
            applyRichSession(framePid, LpartyCodec.sessionOf(frame));
            return;
        }
        if (LpartyCodec.T_STATE.equals(type)) {
            applyState(frame);
            return;
        }
        if (LpartyCodec.T_URL.equals(type)) {
            applyUrl(frame, framePid);
            return;
        }
        if (LpartyCodec.T_HOST.equals(type)) {
            assignOwner(framePid);
            host.onRoomChanged();
            return;
        }
        if (LpartyCodec.T_BYE.equals(type) || LpartyCodec.T_ME.equals(type)) {
            return;
        }

        final RoomAction action = engine.onFrame(frame, SystemClock.elapsedRealtime());
        if (action != null) {
            host.onRoomAction(frameNick, action);
        }
    }

    private void applyRichSession(final String framePid, final JSONObject candidate) {
        if (candidate == null || (owner != null && !owner.equals(framePid))) {
            return;
        }
        if (!joining && mediaApplied) {
            if (candidate.optString("uri", "").equals(host.playingUri())) {
                engine.mediaConfirmed();
            } else {
                stepped(true);
                mediaApplied = false;
            }
        }
        applyMedia(candidate);
    }

    private void applyState(final JSONObject frame) {
        if (owner == null) {
            assignOwner(LpartyCodec.stateOwner(frame));
        }
        if (roomName.isEmpty()) {
            roomName = LpartyCodec.stateRoomName(frame);
        }
        if (joining) {
            engine.seed(LpartyCodec.positionMs(frame), LpartyCodec.playing(frame),
                    SystemClock.elapsedRealtime());
        }
        if (!mediaApplied) {
            applyMedia(thinSession(
                    LpartyCodec.stateUrl(frame),
                    LpartyCodec.stateTitle(frame),
                    LpartyCodec.statePoster(frame)
            ));
        }
    }

    private void applyUrl(final JSONObject frame, final String framePid) {
        if (framePid == null || !framePid.equals(owner)) {
            return;
        }
        final JSONObject candidate = thinSession(
                LpartyCodec.stateUrl(frame), LpartyCodec.stateTitle(frame), null);
        if (candidate == null) {
            return;
        }
        if (candidate.optString("uri", "").equals(host.playingUri())) {
            engine.mediaConfirmed();
            host.onRoomChanged();
            return;
        }
        stepped(true);
        mediaApplied = false;
        applyMedia(candidate);
    }

    @Override
    public void onReady(final int total) {
        handler.post(() -> {
            if (relay == null) {
                return;
            }
            peers = total;
            connected = true;
            everConnected = true;
            if (joining) {
                if (total <= 1) {
                    joining = false;
                    leave();
                    host.onJoinFailed();
                    return;
                }
                relay.send(LpartyCodec.hello(PID, nick));
                handler.removeCallbacks(joinTimeout);
                handler.postDelayed(joinTimeout, JOIN_TIMEOUT_MS);
            } else {
                relay.send(LpartyCodec.hello(PID, nick));
                answerNewcomer();
            }
            host.onRoomChanged();
        });
    }

    @Override
    public void onPeers(final int total) {
        handler.post(() -> {
            if (relay != null) {
                peers = total;
                host.onRoomChanged();
            }
        });
    }

    @Override
    public void onLeave(final String uid, final String alias, final int total) {
        handler.post(() -> handleLeave(uid, alias, total));
    }

    private void handleLeave(final String uid, final String alias, final int total) {
        if (relay == null) {
            return;
        }
        peers = total;
        final String memberPid = pidByUid.remove(uid);
        engine.forget(memberPid);
        if (memberPid != null) {
            helloSeenAt.remove(memberPid);
            markReady(memberPid);
        }
        final String remembered = memberPid == null ? null : nicks.remove(memberPid);
        final String who = alias != null && !alias.isEmpty() ? alias : remembered;
        host.onRoomAction(who, RoomAction.LEFT);
        host.onRoomChanged();
        if (memberPid != null && memberPid.equals(owner)) {
            handler.removeCallbacks(ownerElection);
            handler.postDelayed(ownerElection, OWNER_ELECTION_MS);
        }
    }

    private void electOwner() {
        if (relay == null || room == null || joining || ownerPresent()) {
            return;
        }
        final List<String> members = relay.memberUids();
        if (members.isEmpty() || !members.get(0).equals(relay.uid())) {
            return;
        }
        assignOwner(PID);
        relay.send(LpartyCodec.host(PID, nick));
        host.onRoomChanged();
    }

    private boolean ownerPresent() {
        if (owner == null) {
            return false;
        }
        if (isOwner()) {
            return true;
        }
        for (String uid : relay.memberUids()) {
            if (owner.equals(pidByUid.get(uid))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onConnected(final boolean up) {
        handler.post(() -> {
            if (relay == null) {
                return;
            }
            connected = up;
            everConnected |= up;
            if (!up) {
                pidByUid.clear();
            }
            host.onRoomChanged();
        });
    }

    private static String newPid() {
        final byte[] bytes = new byte[4];
        new SecureRandom().nextBytes(bytes);
        final StringBuilder value = new StringBuilder(8);
        for (byte item : bytes) {
            value.append(Character.forDigit((item >> 4) & 0x0f, 16));
            value.append(Character.forDigit(item & 0x0f, 16));
        }
        return value.toString();
    }

    private static boolean same(final String left, final String right) {
        return left == null ? right == null : left.equals(right);
    }
}
