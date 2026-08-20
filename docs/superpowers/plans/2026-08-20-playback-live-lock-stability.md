# Playback, Live Stream, and Lock Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop active torrent loads from being misclassified as failures, recover HLS/live playback without rebuilding the whole activity, auto-hide the lock affordance after three seconds, and port the related TV and frame-rate regressions.

**Architecture:** Put timing and retry decisions in pure Java policies, leaving `PlayerActivity` responsible only for Media3 actions and UI. Reuse `TrackNameParsingDataSource.bytesRead`, the existing recovery state, and UA Player's TV helpers; adapt the reviewed behavior from Just+ commits `7f8a24e`, `2d815ba`, `461dcc7`, `62a6fcb`, `a143634`, `c904633`, `51545c6`, and `c415e66` rather than replacing whole files.

**Tech Stack:** Java 8, Android SDK 36, Media3 1.11.0-beta01, JUnit 4, Gradle 9.5, Android Gradle Plugin 9.3.

**Spec:** `docs/superpowers/specs/2026-08-20-justplus-feature-sync-design.md`

**Execution order:** Run this plan first; the later Watch Together, subtitle, and metadata plans build on these lifecycle, focus, recovery, and frame-rate changes.

## Global Constraints

- Application id remains `com.lampaua.player`; version remains `1.6.1 (19)`.
- Preserve UA Player branding, Ukrainian-first resources, LAMPA intent contracts, legacy `lampaua.playlist_json`, updater source, and server skip API.
- Do not add Just+ branding, Sentry, direct multi-database skip lookup, manual skip offset, a release tag, or a public APK release.
- Keep the existing 15-second connect timeout and 30-second HTTP read timeout.
- Keep `test-builds/` and unrelated worktree content untouched.

---

### Task 1: Make the load watchdog progress-aware

**Files:**
- Create: `app/src/main/java/com/brouken/player/LoadWatchdogPolicy.java`
- Create: `app/src/test/java/com/brouken/player/LoadWatchdogPolicyTest.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java:203`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java:313`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java:4068`

**Interfaces:**
- Produces: `LoadWatchdogPolicy.SourceKind { LOCAL, NETWORK, LIVE }`.
- Produces: `LoadWatchdogPolicy.Action { REARM, REPORT_INITIAL_TIMEOUT, REPORT_MIDSTREAM_STALL, IGNORE }`.
- Produces: `LoadWatchdogPolicy.evaluate(boolean buffering, boolean everReady, long startBytes, long endBytes, SourceKind sourceKind): Action`.
- Consumes: `TrackNameParsingDataSource.bytesRead` and `Player.STATE_BUFFERING`.

- [ ] **Step 1: Write the failing policy tests**

```java
package com.brouken.player;

import org.junit.Test;

import static com.brouken.player.LoadWatchdogPolicy.Action.IGNORE;
import static com.brouken.player.LoadWatchdogPolicy.Action.REARM;
import static com.brouken.player.LoadWatchdogPolicy.Action.REPORT_INITIAL_TIMEOUT;
import static com.brouken.player.LoadWatchdogPolicy.Action.REPORT_MIDSTREAM_STALL;
import static com.brouken.player.LoadWatchdogPolicy.SourceKind.LOCAL;
import static com.brouken.player.LoadWatchdogPolicy.SourceKind.NETWORK;
import static org.junit.Assert.assertEquals;

public class LoadWatchdogPolicyTest {
    @Test public void activeTorrentLoadGetsAnotherWindow() {
        assertEquals(REARM, LoadWatchdogPolicy.evaluate(
                true, false, 1_000_000L, 1_262_144L, NETWORK));
    }

    @Test public void keepaliveDribbleIsNotPlayableProgress() {
        assertEquals(REPORT_INITIAL_TIMEOUT, LoadWatchdogPolicy.evaluate(
                true, false, 1_000_000L, 1_008_191L, NETWORK));
    }

    @Test public void midFilmSilenceIsReportedAsStall() {
        assertEquals(REPORT_MIDSTREAM_STALL, LoadWatchdogPolicy.evaluate(
                true, true, 5_000_000L, 5_000_000L, NETWORK));
    }

    @Test public void localFileCannotClaimNetworkProgress() {
        assertEquals(REPORT_INITIAL_TIMEOUT, LoadWatchdogPolicy.evaluate(
                true, false, 0L, 500_000L, LOCAL));
    }

    @Test public void callbackAfterReadyIsIgnored() {
        assertEquals(IGNORE, LoadWatchdogPolicy.evaluate(
                false, true, 0L, 0L, NETWORK));
    }
}
```

- [ ] **Step 2: Run the focused test and verify the red state**

Run:

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.LoadWatchdogPolicyTest --console=plain
```

Expected: Java compilation fails because `LoadWatchdogPolicy` does not exist.

- [ ] **Step 3: Implement the pure policy**

```java
package com.brouken.player;

final class LoadWatchdogPolicy {
    static final long MIN_PROGRESS_BYTES = 256L * 1024L;

    enum SourceKind { LOCAL, NETWORK, LIVE }
    enum Action { REARM, REPORT_INITIAL_TIMEOUT, REPORT_MIDSTREAM_STALL, IGNORE }

    private LoadWatchdogPolicy() {}

    static Action evaluate(boolean buffering, boolean everReady,
                           long startBytes, long endBytes, SourceKind sourceKind) {
        if (!buffering || sourceKind == null) return Action.IGNORE;
        long progress = Math.max(0L, endBytes - startBytes);
        if (sourceKind != SourceKind.LOCAL && progress >= MIN_PROGRESS_BYTES) {
            return Action.REARM;
        }
        return everReady ? Action.REPORT_MIDSTREAM_STALL : Action.REPORT_INITIAL_TIMEOUT;
    }
}
```

- [ ] **Step 4: Replace the wall-clock-only runnable with arm/report methods**

Add `loadWatchdogBytes`, `armLoadWatchdog()`, `cancelLoadWatchdog()`, and `reportLoadWatchdog()` to `PlayerActivity`. The report method must call the policy with the current byte count, rearm on `REARM`, ignore stale callbacks, and call `player.stop()` plus the correct localized message on a silent source. It must not call `releasePlayer()` or `initializePlayer()` for a watchdog timeout.

```java
private long loadWatchdogBytes;
private final Runnable loadTimeoutRunnable = this::reportLoadWatchdog;

private void armLoadWatchdog() {
    cancelLoadWatchdog();
    loadWatchdogBytes = TrackNameParsingDataSource.bytesRead.get();
    playerView.postDelayed(loadTimeoutRunnable, VIDEO_LOAD_TIMEOUT_MS);
}

private void cancelLoadWatchdog() {
    if (playerView != null) playerView.removeCallbacks(loadTimeoutRunnable);
}
```

Use `armLoadWatchdog()` on entry to `STATE_BUFFERING`, `cancelLoadWatchdog()` on `STATE_READY`, `STATE_ENDED`, player release, and media replacement. Restore episode navigation after a terminal watchdog report.

- [ ] **Step 5: Run policy tests and Java compilation**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.LoadWatchdogPolicyTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

Expected: focused tests pass and Java compilation exits 0.

- [ ] **Step 6: Commit the watchdog change**

```powershell
git add app/src/main/java/com/brouken/player/LoadWatchdogPolicy.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/test/java/com/brouken/player/LoadWatchdogPolicyTest.java
git commit -m "Keep active torrent loads alive"
```

---

### Task 2: Rejoin live streams with a separate bounded budget

**Files:**
- Create: `app/src/main/java/com/brouken/player/LiveRecoveryPolicy.java`
- Create: `app/src/test/java/com/brouken/player/LiveRecoveryPolicyTest.java`
- Modify: `app/src/main/java/com/brouken/player/PlaybackRecoveryPolicy.java`
- Modify: `app/src/test/java/com/brouken/player/PlaybackRecoveryPolicyTest.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java:4237`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java:4417`

**Interfaces:**
- Produces: `LiveRecoveryPolicy.MAX_ATTEMPTS = 2` and `RESET_AFTER_MS = 60_000L`.
- Produces: `LiveRecoveryPolicy.effectiveAttempts(int attempts, long nowMs, long lastAttemptAtMs): int`.
- Produces: `LiveRecoveryPolicy.canRejoin(int attempts, long nowMs, long lastAttemptAtMs): boolean`.
- Produces: `PlayerActivity.rejoinLiveWindow(): boolean`.

- [ ] **Step 1: Write failing live-budget tests**

```java
package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveRecoveryPolicyTest {
    @Test public void twoImmediateRejoinsAreAllowed() {
        assertTrue(LiveRecoveryPolicy.canRejoin(0, 10_000L, 0L));
        assertTrue(LiveRecoveryPolicy.canRejoin(1, 20_000L, 10_000L));
        assertFalse(LiveRecoveryPolicy.canRejoin(2, 30_000L, 20_000L));
    }

    @Test public void quietMinuteRestoresBudget() {
        assertEquals(0, LiveRecoveryPolicy.effectiveAttempts(2, 80_001L, 20_000L));
        assertTrue(LiveRecoveryPolicy.canRejoin(2, 80_001L, 20_000L));
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails for the missing class**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.LiveRecoveryPolicyTest --console=plain
```

- [ ] **Step 3: Implement the live policy**

```java
package com.brouken.player;

final class LiveRecoveryPolicy {
    static final int MAX_ATTEMPTS = 2;
    static final long RESET_AFTER_MS = 60_000L;

    private LiveRecoveryPolicy() {}

    static int effectiveAttempts(int attempts, long nowMs, long lastAttemptAtMs) {
        return lastAttemptAtMs > 0L && nowMs - lastAttemptAtMs >= RESET_AFTER_MS ? 0 : attempts;
    }

    static boolean canRejoin(int attempts, long nowMs, long lastAttemptAtMs) {
        return effectiveAttempts(attempts, nowMs, lastAttemptAtMs) < MAX_ATTEMPTS;
    }
}
```

- [ ] **Step 4: Route `LIVE_STALL` to a live-window rejoin**

`rejoinLiveWindow()` must return false unless the current item is live and the budget allows another attempt. Preserve `playWhenReady`, increment only the live budget, call `seekToDefaultPosition()`, `prepare()`, restore play intent, show the existing recovery message, and arm the load watchdog. Do not release the player or increment ordinary source retries.

```java
private boolean rejoinLiveWindow() {
    if (player == null || !player.isCurrentMediaItemLive()) return false;
    long now = SystemClock.elapsedRealtime();
    liveRecoveryAttempts = LiveRecoveryPolicy.effectiveAttempts(
            liveRecoveryAttempts, now, lastLiveRecoveryAt);
    if (!LiveRecoveryPolicy.canRejoin(liveRecoveryAttempts, now, lastLiveRecoveryAt)) return false;
    boolean resume = player.getPlayWhenReady();
    liveRecoveryAttempts++;
    lastLiveRecoveryAt = now;
    player.seekToDefaultPosition();
    player.prepare();
    player.setPlayWhenReady(resume);
    armLoadWatchdog();
    return true;
}
```

Call this before compatibility/audio fallbacks for a live `ERROR_CODE_TIMEOUT` or `LIVE_STALL`. Keep resolver-not-ready and extensionless-HLS handling unchanged.

- [ ] **Step 5: Run live and existing recovery tests**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.LiveRecoveryPolicyTest --tests com.brouken.player.PlaybackRecoveryPolicyTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 6: Commit the live recovery change**

```powershell
git add app/src/main/java/com/brouken/player/LiveRecoveryPolicy.java app/src/main/java/com/brouken/player/PlaybackRecoveryPolicy.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/test/java/com/brouken/player/LiveRecoveryPolicyTest.java app/src/test/java/com/brouken/player/PlaybackRecoveryPolicyTest.java
git commit -m "Rejoin stalled live streams"
```

---

### Task 3: Auto-hide the lock affordance after three seconds

**Files:**
- Modify: `app/src/main/java/com/brouken/player/SwipeToUnlockView.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java:748`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java:5350`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`

**Interfaces:**
- Produces: `SwipeToUnlockView.setOnStartTouchingListener(Runnable)`.
- Produces: `SwipeToUnlockView.setOnStopTouchingListener(Runnable)`.
- Produces: `PlayerActivity.SWIPE_UNLOCK_TIMEOUT_MS = 3_000L`.

- [ ] **Step 1: Add a failing source/resource contract**

Extend `ResourceContractTest` with:

```java
@Test public void lockHintAutoHidesAfterThreeSeconds() throws Exception {
    String activity = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");
    String swipe = readProjectFile("src/main/java/com/brouken/player/SwipeToUnlockView.java");
    assertTrue(activity.contains("SWIPE_UNLOCK_TIMEOUT_MS = 3_000L"));
    assertTrue(activity.contains("postDelayed(swipeHider, SWIPE_UNLOCK_TIMEOUT_MS)"));
    assertTrue(swipe.contains("setOnStartTouchingListener"));
    assertTrue(swipe.contains("setOnStopTouchingListener"));
}
```

- [ ] **Step 2: Run the contract and verify the red state**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ResourceContractTest.lockHintAutoHidesAfterThreeSeconds --console=plain
```

- [ ] **Step 3: Add touch lifecycle callbacks to `SwipeToUnlockView`**

Store start/stop listeners. Invoke start on `ACTION_DOWN`; invoke stop after animating back on `ACTION_UP` or `ACTION_CANCEL`. Do not invoke stop after a successful unlock.

```java
void setOnStartTouchingListener(Runnable listener) { onStartTouching = listener; }
void setOnStopTouchingListener(Runnable listener) { onStopTouching = listener; }
```

- [ ] **Step 4: Add the dedicated hider in `PlayerActivity`**

```java
private static final long SWIPE_UNLOCK_TIMEOUT_MS = 3_000L;
private final Runnable swipeHider = this::hideSwipeToUnlock;

void showSwipeToUnlock() {
    if (swipeToUnlock == null || !locked || inPip) return;
    swipeToUnlock.setVisibility(View.VISIBLE);
    rescheduleSwipeHide();
}

private void rescheduleSwipeHide() {
    playerView.removeCallbacks(swipeHider);
    playerView.postDelayed(swipeHider, SWIPE_UNLOCK_TIMEOUT_MS);
}
```

Wire touch start to `removeCallbacks(swipeHider)` and touch stop to `rescheduleSwipeHide()`. `hideSwipeToUnlock()` must cancel the callback. Call it when lock state clears, media changes, playback ends, PiP opens, and the activity is destroyed.

- [ ] **Step 5: Run the contract and compile**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ResourceContractTest.lockHintAutoHidesAfterThreeSeconds :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 6: Commit the lock behavior**

```powershell
git add app/src/main/java/com/brouken/player/SwipeToUnlockView.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/test/java/com/brouken/player/ResourceContractTest.java
git commit -m "Auto-hide the lock hint"
```

---

### Task 4: Port TV input, focus, scrubber, and recents fixes

**Files:**
- Modify: `app/src/main/java/com/brouken/player/CustomPlayerView.java`
- Modify: `app/src/main/java/com/brouken/player/CustomDefaultTimeBar.java`
- Modify: `app/src/main/java/com/brouken/player/SettingsActivity.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`
- Modify: `app/src/test/java/com/brouken/player/TvFocusPolicyTest.java`
- Modify: `app/src/test/java/com/brouken/player/TvSeekControllerTest.java`

**Interfaces:**
- Preserves: `TvSeekController` as the D-pad seek state owner.
- Produces: one committed seek on swipe release rather than a seek for every move event.
- Produces: `android:autoRemoveFromRecents="true"` on `PlayerActivity`, without changing its launch mode or intent filters.

- [ ] **Step 1: Add regression contracts for the exact behaviors**

Add source assertions that `CustomPlayerView` gates in-flight swipe seeks and lands on the promised target at gesture end, `CustomDefaultTimeBar` cuts the segment band around the scrubber, `SettingsActivity` lays out enough rows for D-pad focus to cross disabled groups, and the manifest removes a genuinely finished player task while retaining package/provider contracts.

```java
assertTrue(customView.contains("seekGesture(final long position)"));
assertTrue(customView.contains("player.seekTo(seekStart + seekChange)"));
assertTrue(timeBar.contains("drawBand(Canvas canvas, int left, int right)"));
assertTrue(settings.contains("calculateExtraLayoutSpace"));
assertTrue(manifest.contains("android:autoRemoveFromRecents=\"true\""));
```

- [ ] **Step 2: Run resource, TV focus, and TV seek tests to capture the red state**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ResourceContractTest --tests com.brouken.player.TvFocusPolicyTest --tests com.brouken.player.TvSeekControllerTest --console=plain
```

- [ ] **Step 3: Adapt the reviewed upstream fixes without replacing UA helpers**

Apply behavior from:

```text
2d815ba  CustomPlayerView / PlayerActivity: commit one landed swipe seek
461dcc7  SettingsActivity: skip non-focusable preference rows with D-pad
62a6fcb  PlayerActivity: return TV focus to Play/Pause after start
a143634  CustomDefaultTimeBar: render scrubber above segment markers
c904633  AndroidManifest: auto-remove a task when its last PlayerActivity finishes
```

Keep UA Player's blue/gold focus resources, `TvSeekController`, double-Back exit guard, Android 13 back dispatcher, skip controls, and API result handling.

- [ ] **Step 4: Run the focused suite and debug compilation**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ResourceContractTest --tests com.brouken.player.TvFocusPolicyTest --tests com.brouken.player.TvSeekControllerTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 5: Commit the TV regressions**

```powershell
git add app/src/main/java/com/brouken/player/CustomPlayerView.java app/src/main/java/com/brouken/player/CustomDefaultTimeBar.java app/src/main/java/com/brouken/player/SettingsActivity.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/AndroidManifest.xml app/src/test/java/com/brouken/player/ResourceContractTest.java app/src/test/java/com/brouken/player/TvFocusPolicyTest.java app/src/test/java/com/brouken/player/TvSeekControllerTest.java
git commit -m "Port upstream TV interaction fixes"
```

---

### Task 5: Make frame-rate switching bounded and mathematically correct

**Files:**
- Create: `app/src/main/java/com/brouken/player/FrameRatePolicy.java`
- Create: `app/src/test/java/com/brouken/player/FrameRatePolicyTest.java`
- Modify: `app/src/main/java/com/brouken/player/Utils.java:514`
- Modify: `app/src/main/java/com/brouken/player/Utils.java:847`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java:4177`

**Interfaces:**
- Produces: `FrameRatePolicy.isWholeMultiple(float displayRate, float contentRate): boolean`.
- Produces: `FrameRatePolicy.bestRate(float activeRate, float contentRate, float[] supportedRates): float`.
- Produces: a 1,500 ms fallback that starts playback if no display-change callback arrives.

- [ ] **Step 1: Write failing NTSC and integer-rate tests**

```java
package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FrameRatePolicyTest {
    @Test public void exactNtscMultiplesBeatNearbyIntegerModes() {
        assertTrue(FrameRatePolicy.isWholeMultiple(119.88f, 23.976f));
        assertFalse(FrameRatePolicy.isWholeMultiple(120f, 23.976f));
        assertEquals(119.88f, FrameRatePolicy.bestRate(
                60f, 23.976f, new float[]{24f, 60f, 119.88f, 120f, 144f}), 0.001f);
    }

    @Test public void ordinaryRatesKeepExpectedMultiples() {
        assertTrue(FrameRatePolicy.isWholeMultiple(48f, 24f));
        assertTrue(FrameRatePolicy.isWholeMultiple(59.94f, 29.97f));
        assertTrue(FrameRatePolicy.isWholeMultiple(100f, 50f));
        assertTrue(FrameRatePolicy.isWholeMultiple(120f, 60f));
        assertFalse(FrameRatePolicy.isWholeMultiple(60f, 50f));
        assertEquals(50f, FrameRatePolicy.bestRate(60f, 25f,
                new float[]{24f, 50f, 60f}), 0.001f);
        assertEquals(60f, FrameRatePolicy.bestRate(60f, 30f,
                new float[]{30f, 60f}), 0.001f);
        assertTrue(FrameRatePolicy.isWholeMultiple(119.88f, 59.94f));
    }
}
```

- [ ] **Step 2: Run the test and verify it fails for the missing policy**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.FrameRatePolicyTest --console=plain
```

- [ ] **Step 3: Implement relative-error matching**

```java
static boolean isWholeMultiple(float displayRate, float contentRate) {
    if (displayRate <= 0f || contentRate <= 0f) return false;
    float ratio = displayRate / contentRate;
    int multiple = Math.round(ratio);
    return multiple >= 1 && Math.abs(ratio - multiple) < multiple * 0.0002f;
}
```

`bestRate` filters rates below the content rate, selects the highest whole multiple, and falls back to the highest supported rate at the active resolution.

- [ ] **Step 4: Integrate bounded display switching**

Use the current Media3/container frame rate when positive; only fall back to `Utils.getFrameRate()` when no known rate exists. A requested mode change registers the display listener and posts a 1,500 ms `playIfCan` fallback. A real display callback cancels the fallback. Every no-switch, detached-display, rejected-mode, and exception path calls the same `playIfCan` method exactly once.

- [ ] **Step 5: Run policy tests and compile**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.FrameRatePolicyTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 6: Commit frame-rate stability**

```powershell
git add app/src/main/java/com/brouken/player/FrameRatePolicy.java app/src/main/java/com/brouken/player/Utils.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/test/java/com/brouken/player/FrameRatePolicyTest.java
git commit -m "Bound frame-rate switching"
```

---

### Task 6: Verify Batch 1 end to end

**Files:**
- Modify only if verification exposes a defect in files already listed by Tasks 1-5.

**Interfaces:**
- Verifies all Batch 1 contracts without creating a release.

- [ ] **Step 1: Run all unit tests**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --console=plain
```

Expected: all tests pass with zero failures.

- [ ] **Step 2: Run lint and debug build**

```powershell
.\gradlew.bat :app:lintLatestUniversalDebug :app:assembleLatestUniversalDebug --console=plain
```

Expected: both tasks exit 0.

- [ ] **Step 3: Run repository integrity checks**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only intended tracked changes or generated ignored build output.

- [ ] **Step 4: Record device checks that remain manual**

Verify on hardware before merge: a large torrent-backed file buffers beyond 30 seconds without restarting; a dead torrent stops with a retryable message; an HLS live channel rejoins after a drop; the lock hint disappears after 3 seconds but stays while dragged; D-pad focus and seeking remain correct; frame-rate matching never leaves playback paused at the first frame.

- [ ] **Step 5: Route any verification finding back to its owning task**

If verification exposes a defect, return to Task 1-5, add a failing regression test beside that task's tests, make the smallest correction in that task's listed files, rerun its focused command, and commit with that task's explicit `git add` list. Do not create a catch-all verification commit.
