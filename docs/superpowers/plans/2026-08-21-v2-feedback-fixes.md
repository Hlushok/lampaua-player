# UA Player 2.0 Feedback Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Execute this plan task-by-task in the current session. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the reported MP4/HLS and TorrServe HLS failures while simplifying subtitle settings and making the player controls configurable.

**Architecture:** Keep Android actions in `PlayerActivity`, but move format-fallback decisions into a pure policy class covered by JVM tests. Reuse `PlaybackRecoveryPolicy` for bounded source rebuilds, centralize subtitle preview rendering in a custom `Preference`, and keep dynamic control visibility at the methods that already own each button's availability.

**Tech Stack:** Java, AndroidX Media3 1.11.0-beta01, AndroidX Preference, Gradle/JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-21-v2-feedback-fixes-design.md`

## Global Constraints

- Keep package `com.lampaua.player`, version `2.0.0`, and versionCode `20` unchanged.
- Preserve the existing signing identity; do not generate or replace signing material.
- Do not merge, tag, publish an APK, or create/update a GitHub Release.
- Keep Play/Pause and `More` visible.
- Keep online subtitle search opt-in and Ukrainian auto-translation Ukrainian-only.

---

### Task 1: Stream fallback and stuck-playlist recovery

**Files:**
- Create: `app/src/main/java/com/brouken/player/StreamTypeFallbackPolicy.java`
- Create: `app/src/test/java/com/brouken/player/StreamTypeFallbackPolicyTest.java`
- Modify: `app/src/main/java/com/brouken/player/PlaybackRecoveryPolicy.java`
- Modify: `app/src/test/java/com/brouken/player/PlaybackRecoveryPolicyTest.java`
- Modify: `app/src/main/java/com/brouken/player/LiveRecoveryPolicy.java`
- Modify: `app/src/test/java/com/brouken/player/LiveRecoveryPolicyTest.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`

**Interfaces:**
- Produces: `StreamTypeFallbackPolicy.canTryHls(boolean playbackEverReady, int errorCode, String currentMimeType, String uri)`.
- Produces: `PlaybackRecoveryPolicy.FailureKind.PLAYLIST_STUCK`, mapped to `RETRY_SOURCE` while the source budget remains.
- Produces: `LiveRecoveryPolicy.hasPlaybackProgress(...)`, which accepts a backwards live-window rollover without accepting backwards VOD movement.

- [x] **Step 1: Write failing policy tests**

```java
assertFalse(StreamTypeFallbackPolicy.canTryHls(true,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null, mp4Uri));
assertTrue(StreamTypeFallbackPolicy.canTryHls(false,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, null, ambiguousUri));
assertEquals(RETRY_SOURCE, PlaybackRecoveryPolicy.decide(
        PLAYLIST_STUCK, true, 0, 0, false));
assertTrue(LiveRecoveryPolicy.hasPlaybackProgress(5_800L, 900L, true));
```

- [x] **Step 2: Run the focused tests and confirm failure**

Run: `./gradlew.bat testLatestUniversalDebugUnitTest --tests '*StreamTypeFallbackPolicyTest' --tests '*PlaybackRecoveryPolicyTest'`

- [x] **Step 3: Implement the policy and PlayerActivity integration**

Gate both LAMPA playlist and IPTV type toggles through the startup parsing policy. Detect `HlsPlaylistTracker.PlaylistStuckException` in the cause chain and route it to a full source rebuild that saves the current position. Treat a backwards position jump as progress only for sliding live windows, and clear recovery loading UI whenever playback returns to `STATE_READY`.

- [x] **Step 4: Run the focused tests and confirm success**

Run: `./gradlew.bat testLatestUniversalDebugUnitTest --tests '*StreamTypeFallbackPolicyTest' --tests '*PlaybackRecoveryPolicyTest'`

### Task 2: Diagnostic URL privacy

**Files:**
- Modify: `app/src/main/java/com/brouken/player/DiagnosticReport.java`
- Modify: `app/src/test/java/com/brouken/player/DiagnosticReportTest.java`

**Interfaces:**
- Produces: `sanitizeNetworkUri(String)` output limited to scheme, host, port, and optional `/<redacted>` marker.

- [x] **Step 1: Change tests to reject path disclosure**

```java
assertEquals("https://media.example.test:8443/[redacted]",
        DiagnosticReport.sanitizeNetworkUri(signedUrl));
assertFalse(sanitized.contains("movie.m3u8"));
```

- [x] **Step 2: Run `DiagnosticReportTest` and confirm failure**

- [x] **Step 3: Redact URI paths in both parsed and fallback branches**

- [x] **Step 4: Run `DiagnosticReportTest` and confirm success**

### Task 3: Automatic subtitle sources and live style preview

**Files:**
- Create: `app/src/main/java/com/brouken/player/SubtitlePreviewPreference.java`
- Create: `app/src/main/res/layout/preference_subtitle_preview.xml`
- Create: `app/src/main/res/drawable/ua_subtitle_preview_background.xml`
- Modify: `app/src/main/java/com/brouken/player/SettingsActivity.java`
- Modify: `app/src/main/java/com/brouken/player/SubtitleSearch.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/Prefs.java`
- Modify: `app/src/main/res/xml/root_preferences.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`

**Interfaces:**
- Produces: `SubtitlePreviewPreference.refresh()` for preference-change updates.
- Subtitle search always tries the four integrated sources when global search is enabled.

- [x] **Step 1: Update resource contracts to require no source screen and one preview**

- [x] **Step 2: Run `ResourceContractTest` and confirm failure**

- [x] **Step 3: Remove source switches and per-source preference reads**

- [x] **Step 4: Add a Media3 `SubtitleView` preview preference and observe appearance keys**

- [x] **Step 5: Run resource and subtitle-search tests**

### Task 4: Subtitle timing guidance and optional controls

**Files:**
- Modify: `app/src/main/java/com/brouken/player/OffsetPanel.java`
- Modify: `app/src/main/java/com/brouken/player/Prefs.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/res/xml/root_preferences.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`

**Interfaces:**
- `OffsetPanel.create(...)` renders earlier/later guidance without changing its signed offset callback.
- `PlayerActivity` applies `showButton*` preferences while preserving dynamic availability.

- [x] **Step 1: Add failing resource/preference assertions**

- [x] **Step 2: Add the `Player buttons` screen and preference loading**

- [x] **Step 3: Apply visibility to static and dynamic controls**

- [x] **Step 4: Add localized earlier/later labels to `OffsetPanel`**

- [x] **Step 5: Run focused unit tests**

### Task 5: Full verification and pull request

**Files:**
- Modify only files required by failed checks.

**Interfaces:**
- Produces: a reviewable remote branch and pull request; no release artifact.

- [x] **Step 1: Run all unit tests**

Run: `./gradlew.bat test`

- [x] **Step 2: Run lint**

Run with JDK 21: `./gradlew.bat lintLatestUniversalRelease`

- [x] **Step 3: Build debug and release APK variants**

Run: `./gradlew.bat assembleLatestUniversalDebug assembleLatestUniversalRelease`

- [x] **Step 4: Inspect `git diff --check`, status, version, and release-related diff**

- [ ] **Step 5: Commit, push, create a PR, and wait for green CI**

Do not merge the PR and do not modify `v2.0.0` or any GitHub Release.
