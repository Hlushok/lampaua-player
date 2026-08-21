# Watch Together Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add LAMPA-compatible shared viewing with rooms, passwords, public discovery, drift correction, episode continuity, invite sharing, and TV QR codes without coupling the room session to ExoPlayer rebuilds.

**Architecture:** Port the pure protocol and synchronization code into `com.brouken.player.together`, keep WebSocket ownership in `TogetherManager`, and expose Media3 only through `TogetherManager.Host`. Adapt upstream Just+ commits `918b96b`, `de3aff1`, `8bf62bb`, `f4d03a1`, `34a1334`, and `9870eea`; preserve UA styling and LAMPA playlist/session serialization.

**Tech Stack:** Java 8, Android SDK 36, Media3 1.11.0-beta01, OkHttp 5.3.2 WebSocket, ZXing Core 3.5.3, org.json, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-20-justplus-feature-sync-design.md`

**Execution order:** Run after `2026-08-20-playback-live-lock-stability.md`; it relies on the stabilized lifecycle/focus paths and touches the same player and settings files.

## Global Constraints

- Application id remains `com.lampaua.player`; version remains `1.6.1 (19)`.
- Default relay is `wss://itty.ws/c/`; default invite page is `https://siaivo.isroot.in/lparty/`.
- Public room listing is off by default, and a listed room must have a non-empty password.
- Do not add Sentry, automatic telemetry, remote log upload, Just+ branding, or a release.
- Ordinary playback, resolver recovery, LAMPA metadata, API result return, UA skip service, and updater behavior must work with Watch Together inactive.
- Preserve source attribution to Oleksandr Zhyzhchenko/Just+ Player and the LocalSend Apache-2.0 attribution in `AliasGenerator`.

---

### Task 1: Add the room protocol and synchronization engine with tests

**Files:**
- Create: `app/src/main/java/com/brouken/player/together/AliasGenerator.java`
- Create: `app/src/main/java/com/brouken/player/together/LpartyCodec.java`
- Create: `app/src/main/java/com/brouken/player/together/Room.java`
- Create: `app/src/main/java/com/brouken/player/together/SyncEngine.java`
- Create: `app/src/test/java/com/brouken/player/together/RoomTest.java`
- Create: `app/src/test/java/com/brouken/player/together/LpartyCodecTest.java`
- Create: `app/src/test/java/com/brouken/player/together/SyncEngineTest.java`
- Modify: `app/build.gradle`

**Interfaces:**
- Produces: `Room.newCode()`, `Room.channel()`, `Room.invite()`, `Room.inviteFrom(String)`, `Room.isCode(String)`, `Room.setInvitePage(String)`.
- Produces: `LpartyCodec` frame builders/readers for `hello`, `state`, `sync`, `act`, `url`, `hold`, `ready`, `go`, `jsess`, and `buf`.
- Produces: `SyncEngine.tick(long now, long positionMs, boolean play, float speed): SyncEngine.Action`.
- Produces: `SyncEngine.mediaChanged(boolean confirmed, long now)` and `mediaConfirmed()` for episode transitions.

- [ ] **Step 1: Add the test-only JSON implementation**

Keep Android's `org.json` at runtime and add only this local JVM dependency:

```groovy
testImplementation 'org.json:json:20240303'
```

- [ ] **Step 2: Write failing room tests**

```java
package com.brouken.player.together;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class RoomTest {
    @Test public void lampaLetterAndUaNumericCodesAreAccepted() {
        assertTrue(Room.isCode("ABC234"));
        assertTrue(Room.isCode("123456"));
        assertFalse(Room.isCode("12 456"));
        assertFalse(Room.isCode("12345"));
    }

    @Test public void passwordChangesThePrivateRelayChannel() {
        Room open = new Room("ABC234", "");
        Room locked = new Room("ABC234", "secret");
        assertNotEquals(open.channel(), locked.channel());
        assertTrue(open.channel().startsWith("lparty-r-"));
        assertEquals(24, open.channel().substring("lparty-r-".length()).length());
    }

    @Test public void invalidInvitePageFallsBackToUaDefault() {
        Room.setInvitePage("javascript:alert(1)");
        assertEquals(Room.DEFAULT_INVITE_PAGE, Room.invitePage());
        Room.setInvitePage("https://example.test/lparty/?old=1");
        assertEquals("https://example.test/lparty/", Room.invitePage());
    }
}
```

- [ ] **Step 3: Write failing codec and sync tests**

```java
@Test public void actionFrameRoundTripsSeek() throws Exception {
    JSONObject frame = LpartyCodec.transport(LpartyCodec.T_ACT, "p1", 7,
            42_000L, true, 1f, LpartyCodec.V_SEEKED, "TV");
    assertEquals("act", LpartyCodec.type(frame));
    assertEquals(42_000L, LpartyCodec.positionMs(frame));
    assertEquals(TogetherManager.Act.SEEKED, LpartyCodec.act(frame));
}

@Test public void smallDriftUsesSpeedAndLargeDriftUsesSeek() {
    SyncEngine engine = new SyncEngine();
    engine.bind("me", "UA TV");
    engine.seed(10_000L, true, 1_000L);
    engine.onFrame(LpartyCodec.transport(LpartyCodec.T_SYNC, "peer", 1,
            11_000L, true, 1f, null, null), 1_000L);
    SyncEngine.Action small = engine.tick(1_250L, 10_250L, true, 1f);
    assertTrue(small.seekToMs < 0L);
    assertNotNull(small.speed);

    engine.onFrame(LpartyCodec.transport(LpartyCodec.T_SYNC, "peer", 2,
            30_000L, true, 1f, null, null), 5_000L);
    SyncEngine.Action large = engine.tick(5_250L, 14_250L, true, 1f);
    assertTrue(large.seekToMs >= 0L);
}
```

- [ ] **Step 4: Run the focused tests and verify missing-class failures**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests "com.brouken.player.together.*" --console=plain
```

- [ ] **Step 5: Adapt the four pure upstream classes**

Copy behavior from `just-plus-player/master` at `d37c8b7`, retaining class boundaries and comments. Keep these constants unchanged because they are protocol behavior:

```java
public static final long TICK_MS = 250L;
public static final long BUFFER_WAIT_MAX_MS = 15_000L;
public static final long SETTLE_MS = 3_000L;
public static final String DEFAULT_INVITE_PAGE = "https://siaivo.isroot.in/lparty/";
```

`AliasGenerator` must retain its LocalSend attribution comment. `Room.setInvitePage` accepts only HTTP(S), strips an existing query, and falls back to the default. `SyncEngine` must keep bounded hard seeks, temporary speed trim, newcomer hold, stale member removal, end-of-episode suppression, and `mediaChanged` state reset.

- [ ] **Step 6: Run room/codec/sync tests**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests "com.brouken.player.together.*" --console=plain
```

- [ ] **Step 7: Commit the pure room core**

```powershell
git add app/build.gradle app/src/main/java/com/brouken/player/together/AliasGenerator.java app/src/main/java/com/brouken/player/together/LpartyCodec.java app/src/main/java/com/brouken/player/together/Room.java app/src/main/java/com/brouken/player/together/SyncEngine.java app/src/test/java/com/brouken/player/together
git commit -m "Add LAMPA watch-together protocol"
```

---

### Task 2: Add relay, lobby, session codec, and manager lifecycle

**Files:**
- Create: `app/src/main/java/com/brouken/player/together/Relay.java`
- Create: `app/src/main/java/com/brouken/player/together/Lobby.java`
- Create: `app/src/main/java/com/brouken/player/together/SessionCodec.java`
- Create: `app/src/main/java/com/brouken/player/together/TogetherManager.java`
- Create: `app/src/test/java/com/brouken/player/together/RelayPolicyTest.java`
- Create: `app/src/test/java/com/brouken/player/together/SessionCodecContractTest.java`

**Interfaces:**
- Produces: `Relay.DEFAULT_BASE`, `Relay.setBase(String)`, `Relay.base()`, `open()`, `close()`, and `send(JSONObject)`.
- Produces: `TogetherManager.Host` with player state getters, apply methods, session description, room card, and room callbacks.
- Produces: `TogetherManager.create`, `join`, `leave`, `suspend`, `resume`, `changeMedia`, `mediaStepped`, `invite`, and room-state getters.
- Consumes: the Task 1 protocol classes and OkHttp WebSocket.

- [ ] **Step 1: Write failing relay URL tests**

```java
@Test public void relayNormalizesAndRejectsUnsafeSchemes() {
    Relay.setBase("wss://relay.example/channel");
    assertEquals("wss://relay.example/channel/", Relay.base());
    Relay.setBase("https://not-a-websocket.example/");
    assertEquals(Relay.DEFAULT_BASE, Relay.base());
    Relay.setBase("");
    assertEquals("wss://itty.ws/c/", Relay.base());
}
```

- [ ] **Step 2: Write a session contract test**

The test reads `SessionCodec.java` and `LampaPlaylist.java` as UTF-8 text and requires all launcher-owned fields to remain represented: `video_list`, `headers`, `subs`, `segments`, `season`, `episode`, `imdb_id`, `id`, `quality_levels`, and per-episode `quality_urls`.

- [ ] **Step 3: Run the focused tests and capture the red state**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.together.RelayPolicyTest --tests com.brouken.player.together.SessionCodecContractTest --console=plain
```

- [ ] **Step 4: Adapt the network/session classes**

Port from the pinned upstream source with these required boundaries:

```java
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
    void onRoomAction(String nick, Act act);
    JSONObject sessionDescription();
    JSONObject roomCard();
    void openSession(JSONObject session);
    void onJoinFailed();
    void onHoldLifted();
}
```

Relay callbacks must post to the main looper before touching `SyncEngine` or Host. `suspend()` stops sampling without sending a pause; `resume()` reprimes local state. Closing the manager cancels ticker, reconnect, join-timeout, lobby, and hold callbacks.

- [ ] **Step 5: Run tests and Java compilation**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests "com.brouken.player.together.*" :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 6: Commit the room transport**

```powershell
git add app/src/main/java/com/brouken/player/together/Relay.java app/src/main/java/com/brouken/player/together/Lobby.java app/src/main/java/com/brouken/player/together/SessionCodec.java app/src/main/java/com/brouken/player/together/TogetherManager.java app/src/test/java/com/brouken/player/together
git commit -m "Add watch-together room transport"
```

---

### Task 3: Add UA-styled room controls, settings, sharing, and TV QR

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/CustomPlayerView.java`
- Modify: `app/src/main/java/com/brouken/player/Prefs.java`
- Modify: `app/src/main/java/com/brouken/player/SettingsActivity.java`
- Modify: `app/src/main/res/layout/activity_player.xml`
- Create: `app/src/main/res/drawable/ic_together_24dp.xml`
- Create: `app/src/main/res/drawable/ic_search_24dp.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/main/res/xml/root_preferences.xml`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`

**Interfaces:**
- Produces preferences: `togetherNick`, `togetherPassword`, `togetherPublic`, `togetherRelay`, and `togetherInvitePage`.
- Produces player actions: create, join, find, share, show QR, leave.
- Consumes: `TogetherManager.Host` and ZXing `QRCodeWriter`.

- [ ] **Step 1: Add failing resource and privacy contracts**

Require the Ukrainian strings for room creation/join/share/leave, password-required public listing, relay disclosure, and QR hint. Require the manifest `ACTION_SEND` query, local ZXing dependency, and absence of Sentry/termbin/qrserver references.

```java
assertTrue(ukrainian.contains("name=\"together_title\">Дивитися разом</string>"));
assertTrue(ukrainian.contains("name=\"together_public_needs_password\""));
assertTrue(build.contains("com.google.zxing:core:3.5.3"));
assertFalse(build.contains("io.sentry"));
assertFalse(activity.contains("termbin.com"));
assertFalse(activity.contains("api.qrserver.com"));
```

- [ ] **Step 2: Run the contract and verify the red state**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ResourceContractTest --console=plain
```

- [ ] **Step 3: Add the runtime dependency and manifest query**

```groovy
implementation 'com.google.zxing:core:3.5.3'
```

Add a package-visibility query for `ACTION_SEND` with `text/plain`; do not add new permissions beyond existing Internet access.

- [ ] **Step 4: Add preferences and settings behavior**

Seed `togetherNick` once with `AliasGenerator.random()`. Validate relay/invite values through `Relay.setBase` and `Room.setInvitePage`, show the effective default in summaries, and keep public listing false by default. Place Watch Together behind its own preference screen using UA dialog/list backgrounds.

- [ ] **Step 5: Integrate `TogetherManager.Host` in `PlayerActivity`**

Create the manager lazily. Host getters read the current player; Host apply methods change only Media3 state and never call launcher result APIs. `sessionDescription()` serializes the active LAMPA playlist/item and headers; `openSession()` restores that payload through the existing intent/session parsing path rather than constructing a second playlist implementation.

Add a room badge below the player header and a gear-menu entry. Room dialogs must use Ukrainian-first text and UA navy/gold styling. Public create dialog keeps OK disabled while listing is checked and password is empty.

- [ ] **Step 6: Generate QR codes locally**

Use `QRCodeWriter.encode(invite, BarcodeFormat.QR_CODE, size, size)` and convert the `BitMatrix` to an in-memory `Bitmap`. Do not call any QR web service. Phones use Android Share; TV displays the local bitmap and room code.

- [ ] **Step 7: Run resources, contracts, and compile**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ResourceContractTest --tests "com.brouken.player.together.*" :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 8: Commit the room UI**

```powershell
git add app/build.gradle app/src/main/AndroidManifest.xml app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/java/com/brouken/player/CustomPlayerView.java app/src/main/java/com/brouken/player/Prefs.java app/src/main/java/com/brouken/player/SettingsActivity.java app/src/main/res/layout/activity_player.xml app/src/main/res/drawable/ic_together_24dp.xml app/src/main/res/drawable/ic_search_24dp.xml app/src/main/res/values app/src/main/res/xml/root_preferences.xml app/src/test/java/com/brouken/player/ResourceContractTest.java
git commit -m "Add UA watch-together controls"
```

---

### Task 4: Preserve rooms across rebuilds and episode boundaries

**Files:**
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/together/SyncEngine.java`
- Modify: `app/src/main/java/com/brouken/player/together/TogetherManager.java`
- Modify: `app/src/test/java/com/brouken/player/together/SyncEngineTest.java`
- Modify: `app/src/test/java/com/brouken/player/LampaIntentContractTest.java`

**Interfaces:**
- Consumes: existing `playPlaylistIndex`, quality switch, resolver rebuild, settings rebuild, and activity lifecycle paths.
- Produces: `TogetherManager.changeMedia(JSONObject)` for owner-driven media changes and `mediaStepped(JSONObject)` for a local playlist step that awaits room confirmation.

- [ ] **Step 1: Add episode-boundary regression tests**

Test that `SyncEngine.mediaChanged(false, now)` clears old target position, suppresses old-episode frames until confirmation or 10 seconds, and that an end-of-media frame at duration is not applied as a seek in the next episode. Extend `LampaIntentContractTest` to require Watch Together session serialization to retain per-episode subtitles, quality, ids, and segments.

- [ ] **Step 2: Run the targeted tests and record the failure**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.together.SyncEngineTest --tests com.brouken.player.LampaIntentContractTest --console=plain
```

- [ ] **Step 3: Wire lifecycle and episode transitions**

Call `together.suspend()` before background-induced pause/release and `resume()` after foreground restoration. Do not close the room during quality, decoder, resolver, subtitle, audio, or settings rebuilds. Call `mediaStepped()` when the local viewer changes episode and `changeMedia()` when the room owner announces a new session. Explicit Leave, finishing activity, or terminal empty-state exit closes the room.

- [ ] **Step 4: Make room chrome follow player chrome**

Fade badge and activity message with the controller using the same duration and cancellation behavior as the statistics panel. PiP hides both. A hidden/locked controller must not expose room action buttons.

- [ ] **Step 5: Run targeted tests and compilation**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests "com.brouken.player.together.*" --tests com.brouken.player.LampaIntentContractTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 6: Commit lifecycle fixes**

```powershell
git add app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/java/com/brouken/player/together/SyncEngine.java app/src/main/java/com/brouken/player/together/TogetherManager.java app/src/test/java/com/brouken/player/together/SyncEngineTest.java app/src/test/java/com/brouken/player/LampaIntentContractTest.java
git commit -m "Keep shared rooms across episodes"
```

---

### Task 5: Verify Watch Together without publishing

**Files:**
- Modify only through the failed task that owns any discovered defect.

**Interfaces:**
- Verifies inactive-mode isolation and active room behavior.

- [ ] **Step 1: Run all unit tests**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --console=plain
```

- [ ] **Step 2: Run lint and debug build**

```powershell
.\gradlew.bat :app:lintLatestUniversalDebug :app:assembleLatestUniversalDebug --console=plain
```

- [ ] **Step 3: Run dependency and source checks**

```powershell
.\gradlew.bat :app:dependencies --configuration latestUniversalDebugRuntimeClasspath --console=plain
rg -n "sentry|termbin|qrserver" app/src/main app/build.gradle
git diff --check
```

Expected: ZXing is present; Sentry and remote diagnostic endpoints are absent; diff check exits 0.

- [ ] **Step 4: Complete two-device checks before merge**

Verify UA-to-UA and UA-to-LAMPA rooms: create private room, wrong password, public room password guard, lobby discovery, phone Share, TV QR, play/pause/seek, small drift correction, large seek, one viewer buffering, background/foreground, relay reconnect, episode auto-advance, quality switch, resolver retry, and explicit leave. Ordinary playback with no room must be unchanged.
