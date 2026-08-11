# UA Player Feature Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Selectively bring stable Just+ playback, TV-control, utility, track, updater, and skip improvements into UA Player while preserving UA branding and LAMPA/LampaUA integrations.

**Architecture:** Keep `PlayerActivity` as the Media3 host, but move new policies and state machines into focused Java classes that can be unit-tested without an Android device. Use upstream Just+ commits as behavior references, not as a wholesale merge; UI surfaces are adapted to UA navy/gold components, while playlist and skip data continue to flow through `LampaPlaylist` and the UA server API.

**Tech Stack:** Java 8 source compatibility, Android SDK 36, Media3 1.11.0-beta01, Gradle 9.5, Android Gradle Plugin 9.3, JUnit 4, GitHub Actions, JDK 21.

## Global Constraints

- Application id remains `com.lampaua.player`.
- UA Player's dark blue and gold interface, icons, names, and Ukrainian-first strings remain authoritative.
- Official LAMPA `video_list.*` metadata and legacy `lampaua.playlist_json` remain supported.
- Online skip lookup remains `https://kinohub.uk/lite/lampauaskip/segments`.
- Do not add Watch Together, manual skip offset, Sentry/crash telemetry, Just+ branding, or Just+'s direct multi-database skip finder.
- Do not change the public version, create a version tag, or publish a GitHub Release during implementation.
- All shared APKs must be built cleanly and must pass signature and resource verification.

---

### Task 1: Add test infrastructure and make resource ids safe

**Files:**
- Modify: `app/build.gradle`
- Modify: `gradle.properties`
- Create: `app/src/test/java/com/brouken/player/ResourceContractTest.java`
- Create: `scripts/verify_apk.py`
- Create: `scripts/build_test_apk.ps1`

**Interfaces:**
- Produces: `scripts/verify_apk.py --apk <path> --aapt2 <path> --apksigner <path> [--certificate <sha256>]`
- Produces: `scripts/build_test_apk.ps1`, which creates one clean, signed-or-debug-signed universal test APK and runs the verifier.

- [ ] **Step 1: Add JUnit and enable non-final resource ids**

Add to `app/build.gradle`:

```groovy
dependencies {
    testImplementation 'junit:junit:4.13.2'
}
```

Change `gradle.properties` to:

```properties
android.nonFinalResIds=true
```

This prevents `PlayerActivity` from permanently inlining a stale numeric id when Android resources move between incremental builds.

- [ ] **Step 2: Write the resource contract test**

Create a pure source-level regression test that reads the three authoritative string files and verifies the expected keys and Ukrainian values:

```java
@Test
public void skipLabelsStayUkrainian() throws Exception {
    Path path = Paths.get("src/main/res/values-uk/strings.xml");
    if (!Files.exists(path)) {
        path = Paths.get("app/src/main/res/values-uk/strings.xml");
    }
    String xml = new String(
        Files.readAllBytes(path),
        StandardCharsets.UTF_8);
    assertTrue(xml.contains("name=\"skip_action\">Пропустити</string>"));
    assertTrue(xml.contains("name=\"skip_available_in\">Пропуск через %1$d</string>"));
    assertFalse(xml.contains("SideSheetBehavior"));
}
```

- [ ] **Step 3: Run the test before changing the build path**

Run:

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ResourceContractTest --console=plain
```

Expected: PASS for source strings; this establishes that the defect is in packaged/inlined resources rather than translation XML.

- [ ] **Step 4: Implement APK resource and signature verification**

`verify_apk.py` must:

1. run `aapt2 dump resources <apk>`;
2. locate `string/skip_action` and require `Пропустити` in the default or `uk` configuration;
3. reject any `skip_action` entry resolving to `com.google.android.material.sidesheet.SideSheetBehavior`;
4. run `apksigner verify --verbose --print-certs <apk>`;
5. optionally compare the normalized SHA-256 certificate digest to the supplied digest;
6. return non-zero for any mismatch.

Use `subprocess.run(..., check=False, capture_output=True, text=True, encoding="utf-8", errors="replace")` and print one concise reason before exiting.

- [ ] **Step 5: Implement the clean local test-build script**

`build_test_apk.ps1` must call:

```powershell
& .\gradlew.bat clean :app:assembleLatestUniversalDebug --console=plain
```

It then locates the newest `latestUniversal/debug/*.apk`, discovers `aapt2` and `apksigner` from `%LOCALAPPDATA%\Android\Sdk\build-tools`, and invokes `verify_apk.py`. It must never copy an APK to `test-builds` unless verification succeeds.

- [ ] **Step 6: Verify the stale-resource fix**

Run the test-build script twice: once after `clean`, then once after adding and removing a temporary string resource. Both verified APKs must display `Пропустити` in `aapt2 dump resources`, and `javap`/DEX inspection must no longer show an inlined old skip id in `segmentButtonText`.

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle gradle.properties app/src/test/java/com/brouken/player/ResourceContractTest.java scripts/verify_apk.py scripts/build_test_apk.ps1
git commit -m "Make UA Player test builds deterministic"
```

---

### Task 2: Stabilize playlist identity, resume position, and launch metadata

**Files:**
- Modify: `app/src/main/java/com/brouken/player/LampaPlaylist.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/Prefs.java`
- Create: `app/src/main/java/com/brouken/player/PlaylistIdentity.java`
- Create: `app/src/test/java/com/brouken/player/PlaylistIdentityTest.java`

**Interfaces:**
- Produces: `PlaylistIdentity.key(String mediaUri, String imdbId, String tmdbId, int season, int episode): String`
- Produces: `LampaPlaylist.Item.resumeKey(): String`
- Preserves: official `video_list.*`, `lampaua.playlist_json`, per-item subtitles, quality variants, resume positions, posters, ids, and segments.

- [ ] **Step 1: Write identity tests**

Cover:

```java
@Test public void differentEpisodesNeverShareResumePosition() {
    String first = PlaylistIdentity.key("https://a/stream", "tt123", "456", 1, 1);
    String second = PlaylistIdentity.key("https://a/stream", "tt123", "456", 1, 2);
    assertNotEquals(first, second);
}

@Test public void sameEpisodeKeepsIdentityWhenResolverUrlChanges() {
    assertEquals(
        PlaylistIdentity.key("https://a/stream", "tt123", "456", 1, 2),
        PlaylistIdentity.key("https://b/stream", "tt123", "456", 1, 2));
}
```

The helper receives string URLs so its identity rules remain testable without Android framework stubs.

- [ ] **Step 2: Run tests and confirm the helper is absent**

Run the targeted unit test and expect compilation failure for missing `PlaylistIdentity`.

- [ ] **Step 3: Implement deterministic playlist identity**

Use IMDb/TMDB plus season and episode when ids are available. Fall back to a normalized media URL with transient resolver query keys removed. Never use the current list index alone.

- [ ] **Step 4: Bind positions and completion to the current item**

In `PlayerActivity`, update the save/restore paths so every player item transition reads and writes the current item's key. Mark playback completed only after `STATE_READY` was reached and the media genuinely ended; never report a failed launch as watched.

- [ ] **Step 5: Preserve per-episode metadata across resolver transitions**

When a deferred stream URL resolves, replace only the playable URI/headers. Do not discard the item's title, thumbnail, subtitles, quality variants, ids, segments, or resume position.

- [ ] **Step 6: Run playlist tests and compile**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.PlaylistIdentityTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/brouken/player/LampaPlaylist.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/java/com/brouken/player/Prefs.java app/src/main/java/com/brouken/player/PlaylistIdentity.java app/src/test/java/com/brouken/player/PlaylistIdentityTest.java
git commit -m "Keep playlist progress attached to each episode"
```

Reference behavior: Just+ PRs #21, #37, #54, #80, #83, and #84.

---

### Task 3: Add a bounded playback-recovery policy

**Files:**
- Create: `app/src/main/java/com/brouken/player/PlaybackRecoveryPolicy.java`
- Create: `app/src/test/java/com/brouken/player/PlaybackRecoveryPolicyTest.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/ResolverResponseDataSource.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`

**Interfaces:**
- Produces enum: `PlaybackRecoveryPolicy.Action { RETRY_SOURCE, RETRY_COMPATIBILITY, LOWER_QUALITY, FAIL }`
- Produces: `decide(FailureKind kind, boolean everReady, int sourceRetries, int compatibilityRetries, boolean lowerQualityAvailable): Action`
- PlayerActivity owns counters and resets them only after stable `STATE_READY` playback.

- [ ] **Step 1: Write the policy tests**

Required cases:

```java
assertEquals(RETRY_SOURCE, decide(NETWORK_READ, false, 0, 0, false));
assertEquals(RETRY_SOURCE, decide(NETWORK_READ, false, 2, 0, false));
assertEquals(RETRY_COMPATIBILITY, decide(DECODER, false, 3, 0, false));
assertEquals(LOWER_QUALITY, decide(DECODER, true, 3, 1, true));
assertEquals(FAIL, decide(DECODER, true, 3, 1, false));
assertEquals(FAIL, decide(TRUNCATED_LOCAL_FILE, false, 0, 0, true));
```

- [ ] **Step 2: Run tests and verify failure**

Expect compilation failure until `PlaybackRecoveryPolicy` exists.

- [ ] **Step 3: Implement bounded decisions**

Allow at most three transient source retries and three decoder re-prepares per media item. Allow one compatibility-mode rebuild and one lower-quality retry only when a distinct lower rendition exists. Allow two live-stream rejoins, restoring that budget only after one minute without a stall. Local truncated/corrupt files fail immediately with a plain-language message.

- [ ] **Step 4: Separate load and stall watchdogs**

In `PlayerActivity`, track:

```java
private long playbackWaitStartedAt;
private long lastPositionAdvanceAt;
private long lastObservedPosition;
private boolean playbackEverReady;
```

An initial load timeout fires after 30 seconds and retries the source within its budget. Rebuffering must persist for 1.5 seconds before an audio recovery is armed. Less than two seconds of actual progress counts as an at-start stall; later failures count as mid-film stalls. A mid-film stall first verifies that position has not advanced while the player is expected to play, then applies compatibility/lower-quality recovery. Cancel watchdog callbacks on pause, stop, media transition, and error completion.

- [ ] **Step 5: Normalize recoverable resolver and stream errors**

Map resolver-not-ready, HTTP/network read, decoder initialization, surface detach timeout, and truncated local input to distinct `FailureKind` values. Preserve UA Player's current deferred resolver retry and 4K lower-quality behavior.

- [ ] **Step 6: Verify with policy tests and a clean debug build**

Run all policy tests and `assembleLatestUniversalDebug`. Confirm logs show no retry loop after the allowed counters are exhausted.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/brouken/player/PlaybackRecoveryPolicy.java app/src/test/java/com/brouken/player/PlaybackRecoveryPolicyTest.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/java/com/brouken/player/ResolverResponseDataSource.java app/src/main/res/values*/strings.xml
git commit -m "Bound playback recovery and stall handling"
```

Reference behavior: Just+ PRs #29, #31, #47, #55, #59, #61, #69, and #82.

---

### Task 4: Rebuild passthrough audio safely and add 200% player volume

**Files:**
- Create: `app/src/main/java/com/brouken/player/BoostAudioProcessor.java`
- Create: `app/src/main/java/com/brouken/player/AudioRecoveryState.java`
- Create: `app/src/test/java/com/brouken/player/AudioRecoveryStateTest.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/Prefs.java`
- Modify: `app/src/main/java/com/brouken/player/SettingsActivity.java`
- Modify: `app/src/main/res/xml/root_preferences.xml`
- Modify: `app/src/main/res/values/arrays.xml`
- Modify: `app/src/main/res/values*/strings.xml`

**Interfaces:**
- Produces: `AudioRecoveryState.onPause()`, `onResume()`, `onSeek()`, `onAudioOutputChanged()`, `onWriteFailure(String mime)`.
- Produces: `boolean shouldRebuildSink()` and `Set<String> blockedPassthroughMimeTypes()`.
- Produces preferences: `systemVolume`, `volumeBoost`, `volumeGesturesEnabled`.

- [ ] **Step 1: Write audio recovery tests**

Verify that pause alone does not rebuild while playing, resume after pause does, seek schedules one rebuild, repeated callbacks coalesce, and a write failure blocks only the failing passthrough MIME type for the current session.

- [ ] **Step 2: Implement the pure recovery state**

Use one pending boolean and a set of blocked MIME types. `consumeRebuildRequest()` returns true once and clears the pending flag.

- [ ] **Step 3: Integrate Media3 audio-sink rebuilding**

Adapt Just+ PRs #27, #42, #46, #66, #67, #68, and #75. Re-prepare the player at safe lifecycle/seek boundaries instead of swapping an active audio sink. Retain playback position, play/pause state, playlist item, selected tracks, and subtitles.

- [ ] **Step 4: Add player-only volume and boost**

Integrate `BoostAudioProcessor` before the sink where supported. Map UI volume `0..200` to normal player gain through 100 and controlled boost above 100. If platform audio effects are unavailable, use the processor fallback from Just+ PR #96. Clamp all values and disable boost for formats/routes where it would break passthrough.

- [ ] **Step 5: Add settings and Ukrainian copy**

Expose player-only/system-volume mode, volume/brightness gesture toggles, and the maximum boost. Defaults: system volume on TV, existing phone behavior retained, boost capped at 100 until the user opts in.

- [ ] **Step 6: Verify**

Run unit tests and a clean build. On Xiaomi TV Box, test AC3/E-AC3 pause, resume, seek, and HDMI output reconnect; confirm that unsupported boost falls back without muting playback.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/brouken/player/BoostAudioProcessor.java app/src/main/java/com/brouken/player/AudioRecoveryState.java app/src/test/java/com/brouken/player/AudioRecoveryStateTest.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/java/com/brouken/player/Prefs.java app/src/main/java/com/brouken/player/SettingsActivity.java app/src/main/res
git commit -m "Harden TV audio and add optional volume boost"
```

---

### Task 5: Make TV navigation, focus, and seeking deterministic

**Files:**
- Modify: `app/src/main/java/com/brouken/player/CustomPlayerView.java`
- Modify: `app/src/main/java/com/brouken/player/CustomDefaultTimeBar.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/res/layout/activity_player.xml`
- Modify: `app/src/main/res/drawable/ua_dialog_item_background.xml`
- Create: `app/src/main/java/com/brouken/player/TvSeekController.java`
- Create: `app/src/test/java/com/brouken/player/TvSeekControllerTest.java`

**Interfaces:**
- Produces: `TvSeekController.press(direction, eventTimeMs)`, `hold(direction, eventTimeMs)`, `release(eventTimeMs)`.
- Produces: one final `long consumeTarget(long currentPosition, long duration)` per burst.

- [ ] **Step 1: Write accelerated-seek tests**

Test single press, rapid repeated presses, held key acceleration, clamping at zero/duration, and one committed seek on release.

- [ ] **Step 2: Implement `TvSeekController`**

Use increasing steps of 10, 30, 60, and 120 seconds based on press count/hold duration. Keep the preview target separate from the actual player position until commit.

- [ ] **Step 3: Integrate D-pad navigation**

Adapt Just+ PRs #48, #49, #50, #78, #79, and #91:

- Left/Right update the preview and commit once.
- Down from video focuses the time bar/current control row.
- Up dismisses open controls/panels.
- Back closes the topmost panel first, then controls, then the player.
- OK activates the visibly focused quality/episode row instead of toggling pause.

- [ ] **Step 4: Fix focus styling in every side panel**

Every selectable row must be `focusable`, `clickable`, have a UA gold focused stroke, and expose `nextFocus*` links where Android's default geometry is ambiguous. Current selection and current focus must be visually distinct.

- [ ] **Step 5: Add time-bar tap seeking**

Convert touch X into a clamped media position using the active progress-bar bounds. Ignore taps while duration is unknown or the stream is non-seekable.

- [ ] **Step 6: Verify on phone and Xiaomi TV Box**

Exercise episode, quality, audio, subtitle, speed, and settings panels with D-pad/OK; confirm Back behavior on Android versions below 13 and current Android TV.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/brouken/player/CustomPlayerView.java app/src/main/java/com/brouken/player/CustomDefaultTimeBar.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/java/com/brouken/player/TvSeekController.java app/src/test/java/com/brouken/player/TvSeekControllerTest.java app/src/main/res
git commit -m "Make player controls reliable with a TV remote"
```

---

### Task 6: Add scaling modes, hold-for-2x, and input locking

**Files:**
- Create: `app/src/main/java/com/brouken/player/VideoScaleMode.java`
- Create: `app/src/main/java/com/brouken/player/SwipeToUnlockView.java`
- Create: `app/src/test/java/com/brouken/player/VideoScaleModeTest.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/Prefs.java`
- Modify: `app/src/main/res/layout/activity_player.xml`
- Create/modify: `app/src/main/res/drawable/ic_lock_24dp.xml`
- Create/modify: `app/src/main/res/drawable/ic_unlock_24dp.xml`
- Modify: `app/src/main/res/values*/strings.xml`

**Interfaces:**
- Produces enum `VideoScaleMode` with FIT, CROP, FILL, RATIO_16_9, RATIO_4_3, RATIO_16_10, RATIO_2_1, RATIO_2_35_1, RATIO_2_39_1, RATIO_5_4.
- Produces: `apply(PlayerView, VideoScaleMode)` and `nextQuickMode(VideoScaleMode)`.
- Produces lock state that intercepts player input until a completed unlock swipe.

- [ ] **Step 1: Write scale-mode tests**

Verify stable preference keys and quick-cycle order FIT → CROP → FILL → RATIO_16_9 → RATIO_4_3 → FIT.

- [ ] **Step 2: Implement scale modes**

Map FIT/CROP/FILL to Media3 resize modes. For explicit ratios, apply a controlled aspect-ratio frame without changing decoder output or source resolution.

- [ ] **Step 3: Add quick and full selection UI**

A short press cycles the first five modes; a long press opens the full UA-styled picker with all ten modes. Persist selection.

- [ ] **Step 4: Add temporary 2× playback**

On a long uninterrupted press over video, store the current playback speed, switch to 2×, show a small `2×` overlay, and restore the exact previous speed on release/cancel/background transition. Do not trigger while a side panel or lock overlay is active.

- [ ] **Step 5: Add lock/unlock**

When locked, hide controls and consume touches, gestures, and D-pad media actions except Back and the explicit unlock swipe. Keep lock state across background/foreground but clear it on activity destruction or new external media intent.

- [ ] **Step 6: Verify and commit**

Run tests/build and manually verify touch and remote behavior, then commit:

```bash
git add app/src/main/java/com/brouken/player/VideoScaleMode.java app/src/main/java/com/brouken/player/SwipeToUnlockView.java app/src/test/java/com/brouken/player/VideoScaleModeTest.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/java/com/brouken/player/Prefs.java app/src/main/res
git commit -m "Add advanced scaling and protected controls"
```

Reference behavior: Just+ PRs #24, #73, and the commits for hold-to-speed, VLC scaling, and swipe unlock dated 2026-07-24.

---

### Task 7: Add sleep timer, transfer rate, and playback statistics

**Files:**
- Create: `app/src/main/java/com/brouken/player/DurationPanel.java`
- Create: `app/src/main/java/com/brouken/player/SleepTimerController.java`
- Create: `app/src/main/java/com/brouken/player/PlaybackStatistics.java`
- Create: `app/src/test/java/com/brouken/player/SleepTimerControllerTest.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/res/layout/activity_player.xml`
- Modify: `app/src/main/res/values*/strings.xml`

**Interfaces:**
- Produces: `SleepTimerController.armAt(long elapsedRealtimeMs)`, `armAfter(long durationMs)`, `armAtMediaEnd()`, `cancel()`, `tick(...)`.
- Produces immutable `PlaybackStatistics.Snapshot` for the statistics panel.

- [ ] **Step 1: Write timer tests with an injected clock**

Test every preset, custom time, end-of-file, cancellation, and linear fade from 100% to 0% during the final 30 seconds. No test may use wall-clock sleeps.

- [ ] **Step 2: Implement timer and duration panel**

Port the keypad/preset behavior from Just+ PRs #77 and #94, restyled for UA. Timer completion pauses playback and restores normal gain so the next launch is not muted.

- [ ] **Step 3: Add transfer-rate sampling**

Sample transferred bytes over elapsed real time only while loading. Reset the sample when media changes. Show a localized Mbps/Kbps value under the loading indicator without moving the center spinner.

- [ ] **Step 4: Add on-demand statistics**

Populate container, rendered resolution, codec, frame rate, bitrate, decoder name, audio codec/channels, buffer duration, current transfer rate, and dropped frames. Do not poll while the panel is closed.

- [ ] **Step 5: Verify and commit**

Run unit tests/build, confirm the timer survives background/foreground and the panel remains usable with TV focus, then commit:

```bash
git add app/src/main/java/com/brouken/player/DurationPanel.java app/src/main/java/com/brouken/player/SleepTimerController.java app/src/main/java/com/brouken/player/PlaybackStatistics.java app/src/test/java/com/brouken/player/SleepTimerControllerTest.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/res
git commit -m "Add sleep timer and playback diagnostics"
```

---

### Task 8: Improve track metadata, language priority, quality labels, and episode subtitles

**Files:**
- Create: `app/src/main/java/com/brouken/player/AudioLanguagePriority.java`
- Create: `app/src/main/java/com/brouken/player/AudioLanguagePriorityDialog.java`
- Create: `app/src/main/java/com/brouken/player/TrackMetadata.java`
- Create: `app/src/main/java/com/brouken/player/ContainerMetadataReader.java`
- Create: `app/src/main/java/com/brouken/player/MatroskaMetadataReader.java`
- Create: `app/src/main/java/com/brouken/player/Mp4MetadataReader.java`
- Create: `app/src/main/java/com/brouken/player/TrackNameParsingDataSource.java`
- Create: `app/src/test/java/com/brouken/player/AudioLanguagePriorityTest.java`
- Modify: `app/src/main/java/com/brouken/player/CustomDefaultTrackNameProvider.java`
- Modify: `app/src/main/java/com/brouken/player/LampaPlaylist.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/Prefs.java`
- Modify: `app/src/main/java/com/brouken/player/SettingsActivity.java`
- Modify: `app/src/main/res/values*/strings.xml`

**Interfaces:**
- Produces: `AudioLanguagePriority.parse(String): List<String>` and `serialize(List<String>): String`.
- Produces: `select(List<String> preferred, List<TrackMetadata> available): int`.
- Produces consistent `qualityLabel(Format)` used by header and quality panel.

- [ ] **Step 1: Write language-priority tests**

Test normalization (`uk`, `uk-UA`, legacy aliases), order preservation, duplicate removal, missing-language fallback, and selecting the first preferred language actually present.

- [ ] **Step 2: Implement priority storage and selection**

Store a comma-separated normalized language list in preferences. Existing single-language preference migrates to a one-item list. Selecting `Мова пристрою` expands device languages in order without duplicating explicit choices.

- [ ] **Step 3: Add the reorderable Ukrainian dialog**

Allow add, remove, move up/down, and promote-from-current-audio-panel. Use UA focus styling and persist only on confirmation.

- [ ] **Step 4: Port safe container metadata readers**

Adapt the Just+ readers with strict byte/atom/element limits. Metadata parsing must be best-effort: failure falls back to Media3 `Format` without failing playback.

- [ ] **Step 5: Unify track and quality names**

One formatter must drive the top header and selection rows. Display the long side first (`3840 × 2160`), codec/profile, fps, bitrate, HDR/Dolby Vision, and audio channel layout when known.

- [ ] **Step 6: Preserve per-episode subtitles**

Read official and legacy playlist subtitle arrays for every item, attach only the selected item's subtitles to its `MediaItem`, and preserve them when switching quality/resolving the stream.

- [ ] **Step 7: Verify and commit**

Run tests/build, open HLS with undeclared captions, MKV with named audio tracks, and a multi-episode playlist with per-episode subtitles. Commit:

```bash
git add app/src/main/java/com/brouken/player app/src/test/java/com/brouken/player app/src/main/res
git commit -m "Improve track selection and episode metadata"
```

Reference behavior: Just+ PRs #44, #60, #80, #85, #86, and #89.

---

### Task 9: Replace the minimal updater with a release-notes update experience

**Files:**
- Delete: `app/src/main/java/com/brouken/player/UAPlayerUpdater.java`
- Create: `app/src/main/java/com/brouken/player/update/MarkdownRenderer.java`
- Create: `app/src/main/java/com/brouken/player/update/UpdateInfo.java`
- Create: `app/src/main/java/com/brouken/player/update/Updater.java`
- Create: `app/src/main/java/com/brouken/player/update/UpdateUi.java`
- Create: `app/src/main/java/com/brouken/player/update/UpdatePolicy.java`
- Create: `app/src/test/java/com/brouken/player/update/UpdatePolicyTest.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/Prefs.java`
- Modify: `app/src/main/java/com/brouken/player/SettingsActivity.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/drawable/ic_update_24dp.xml`
- Modify: `app/src/main/res/values*/strings.xml`

**Interfaces:**
- Produces: `Updater.find(Callback)` querying `https://api.github.com/repos/Hlushok/lampaua-player/releases/latest`.
- Produces: `UpdatePolicy.shouldOffer(currentCode, release, skippedCode, debugBuild): boolean`.
- Produces: `UpdateUi.showAvailableDialog(Activity, UpdateInfo, Runnable skip, boolean manual)`.

- [ ] **Step 1: Write update-policy tests**

Cover newer stable release, same/older release, draft, prerelease, missing universal APK, skipped version, debug/test build, and malformed JSON. The selected asset must have an `.apk` name and match UA Player universal naming.

- [ ] **Step 2: Implement release discovery and persistence**

Parse version code/name, tag, body, APK URL, and size. Store pending info, last-check timestamp, and one skipped version code. Automatic checks run at most once per 24 hours; manual checks ignore the interval.

- [ ] **Step 3: Implement safe Markdown release notes**

Support headings, unordered lists, bold text, inline code, blank lines, and clickable `https://` links. Strip/escape unsupported HTML and non-HTTPS schemes. Empty notes render `Список змін не вказано.`.

- [ ] **Step 4: Implement the UA-styled dialog**

Show:

```text
Оновлення UA Player
Доступне оновлення: 1.6.0 → 1.7.0
[rendered release notes]
ПРОПУСТИТИ ЦЮ ВЕРСІЮ   ПІЗНІШЕ   ОНОВИТИ
```

Use a scrollable notes area, UA navy background, gold focus, and readable TV typography. `Пізніше` leaves the pending update icon visible; `Пропустити цю версію` clears it only for that version.

- [ ] **Step 5: Implement cancellable download and install**

Download into app cache with progress, cancel the thread/request cleanly, verify HTTP success and positive length, then open the package installer through the existing FileProvider. Delete partial files on cancellation/failure. Installer rejection returns to playback.

- [ ] **Step 6: Add pending-update control**

Show a gold update icon immediately before settings only when a newer unskipped stable release is pending. Selecting it reopens the release-notes dialog.

- [ ] **Step 7: Verify and commit**

Run unit tests and test against fixture JSON for stable, prerelease, missing asset, and multiline Markdown. Do not create a real release. Commit:

```bash
git add app/src/main/java/com/brouken/player/update app/src/test/java/com/brouken/player/update app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/java/com/brouken/player/Prefs.java app/src/main/java/com/brouken/player/SettingsActivity.java app/src/main/AndroidManifest.xml app/src/main/res
git rm app/src/main/java/com/brouken/player/UAPlayerUpdater.java
git commit -m "Show rich GitHub release updates in the player"
```

Reference behavior: Just+ PRs #22, #36, and #92, adapted to the UA repository and design.

---

### Task 10: Turn skip handling into a tested state machine

**Files:**
- Create: `app/src/main/java/com/brouken/player/skip/SkipSegment.java`
- Create: `app/src/main/java/com/brouken/player/skip/SkipPolicy.java`
- Create: `app/src/main/java/com/brouken/player/skip/SkipController.java`
- Create: `app/src/test/java/com/brouken/player/skip/SkipPolicyTest.java`
- Modify: `app/src/main/java/com/brouken/player/LampaPlaylist.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/Prefs.java`
- Modify: `app/src/main/res/values/arrays.xml`
- Modify: `app/src/main/res/values*/strings.xml`
- Modify: `app/src/main/res/xml/root_preferences.xml`

**Interfaces:**
- Produces enum: `SkipPolicy.Mode { BRIEF_BUTTON, FULL_BUTTON, AUTO }`.
- Produces states: `HIDDEN`, `COUNTDOWN`, `AVAILABLE`, `AUTO_PENDING`, `UNDO_AVAILABLE`.
- Produces actions: `NONE`, `SEEK_TO_END`, `PLAY_NEXT`, `RESTORE_POSITION`.
- Consumes only segments already present in `LampaPlaylist.Item`, including UA server results.

- [ ] **Step 1: Write segment-validation and state tests**

Cover negative/reversed/zero-length segments, segments beyond duration, an implausible segment spanning most of an episode, five-second brief mode, full mode, auto countdown/cancel, undo, credits-to-next-episode, and no-next-item behavior.

- [ ] **Step 2: Implement validation**

Reject a segment unless `0 <= start < end <= duration + 1.5 seconds`. Clamp only the 1.5-second end tolerance to the real duration. Reject any segment covering more than one third of the media duration unless explicitly marked as whole-content advertising. Sort and merge only overlapping segments of the same kind/source priority.

- [ ] **Step 3: Implement state transitions**

The controller receives current position and duration and returns a render/action model. It stores the pre-skip position for Undo. Automatic skip presents a cancellable countdown before seeking. Brief mode is visible for exactly five seconds after segment activation; full mode remains visible through the accepted segment.

- [ ] **Step 4: Integrate UA UI and remote focus**

Keep the skip pill near the lower-right timeline position. Display Ukrainian labels: `Пропустити`, `Скасувати`, `Повернутися`, `Наступна серія`. The progress line represents remaining countdown/segment time. Do not steal focus until the action is enabled; when enabled on TV, OK must execute it instead of pause.

- [ ] **Step 5: Keep server and LAMPA priority**

Use LAMPA/LampaUA supplied segments first. Query the existing UA endpoint only if allowed and the current item lacks useful segments. Do not add direct calls to SkipDB, IntroDB, TheIntroDB, AniSkip, or Just+ network sources.

- [ ] **Step 6: Verify and commit**

Run skip tests and verify intro, recap, ad, outro, end credits, next episode, blue timeline markers, cancel, and undo on both touch and TV remote. Commit:

```bash
git add app/src/main/java/com/brouken/player/skip app/src/test/java/com/brouken/player/skip app/src/main/java/com/brouken/player/LampaPlaylist.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/java/com/brouken/player/Prefs.java app/src/main/res
git commit -m "Make skip controls cancellable and reversible"
```

Reference behavior: Just+ PRs #26, #64, #71, #75, and #90. Do not port the manual offset panel from PR #63.

---

### Task 11: Modernize CI and perform full compatibility validation

**Files:**
- Modify: `.github/workflows/android.yml`
- Modify: `.github/workflows/android-build.yml`
- Modify: `README.md`
- Modify: `scripts/verify_apk.py`

**Interfaces:**
- CI on `main` and pull requests produces verified test artifacts without a release.
- Tag workflow remains the only path that may create a draft/prerelease, and is not triggered in this project phase.

- [ ] **Step 1: Update the CI workflow**

Use:

```yaml
- uses: actions/checkout@v6
- uses: actions/setup-java@v5
  with:
    distribution: temurin
    java-version: 21
    cache: gradle
```

Trigger branch CI on `main`. Run unit tests, lint, a clean universal debug build, APK resource verification, and artifact upload. `setup-java@v5` uses Node.js 24 internally; no Node dependency is added to the Android app.

- [ ] **Step 2: Correct the signed tag workflow without running it**

Keep the `v*` tag trigger, update action versions/JDK, make the multiline Gradle signing command valid YAML, build only required universal APKs, run `verify_apk.py`, then prepare the existing draft release action. Do not create or push a tag.

- [ ] **Step 3: Run local automated verification**

```powershell
.\gradlew.bat clean :app:testLatestUniversalDebugUnitTest :app:lintLatestUniversalDebug :app:assembleLatestUniversalDebug --console=plain
.\scripts\build_test_apk.ps1
```

Expected: all tests and lint complete, one verified APK is copied to `test-builds`, `skip_action` is Ukrainian, and the APK verifies cryptographically.

- [ ] **Step 4: Run device regression matrix**

On Xiaomi TV Box S (3rd Gen): quality/episode focus, OK, Back, D-pad seek, 4K lower-quality fallback, passthrough pause/resume/seek, skip modes, timer, lock, statistics, update dialog fixture, next episode.

On phone: touch panels, time-bar tap, 2× hold, gestures, boost, lock swipe, PiP/background, subtitles, update cancellation.

With LAMPA: official `video_list.*`, legacy `lampaua.playlist_json`, film without episode badge, series playlist posters/titles, per-episode subtitles/quality/segments.

- [ ] **Step 5: Update README without announcing a release**

Document current development features and integration fields. Mark Watch Together as not included. Do not add a version badge/change that implies a published version.

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/android.yml .github/workflows/android-build.yml README.md scripts/verify_apk.py
git commit -m "Verify UA Player builds on current GitHub Actions"
```

- [ ] **Step 7: Final review gate**

Run `git diff --check`, inspect the complete branch diff against `origin/main`, confirm no signing secrets or generated APKs are tracked, and request user approval before pushing, opening a PR, merging, changing the version, tagging, or publishing a release.
