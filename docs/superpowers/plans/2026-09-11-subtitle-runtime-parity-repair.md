# Subtitle Runtime Parity Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In this task the primary agent executes the plan inline because the user explicitly requested no subagents.

**Goal:** Restore Just+ v1.4.7 subtitle runtime/search parity, preserve the LAMPA series title, keep the finish time visible, and publish the verified repair as UA Player v2.0.2.

**Architecture:** Treat Just+ tag `v1.4.7` (`f26a71e8e931ed1859a162bd3829d66419aba2b9`) as authoritative for subtitle renderer state, track callback ordering, layout, and search navigation. Adapt only the presentation and launch metadata seams required by UA Player. Preserve root series identity separately from per-episode playlist labels and use a compact localized finish-time value.

**Tech Stack:** Android Java, Media3 1.11.0, Gradle 9.7.1, Android Gradle Plugin 9.4.0, JUnit 4, Android SDK 37, GitHub Actions, `aapt2`, `apkanalyzer`, `zipalign`, and `apksigner`.

**Spec:** `docs/superpowers/specs/2026-09-11-subtitle-runtime-parity-repair-design.md`

## Global Constraints

- Work on `main` and preserve unrelated user work.
- Keep package `com.lampaua.player`, UA Player branding, LAMPA/LampaUA integration, Watch Together, playlists, skips, recovery, and Ukrainian-only automatic-translation target.
- Use donor v1.4.7 behavior for the changed subtitle core; do not re-create an approximate parallel implementation.
- Retain the signing certificate SHA-256 `749d118bc8a16a7c0464b8dd0498c53da8a86a668d8f09f551e60cf7d88ee15e`.
- Publish only after local tests, APK checks, and CI pass. The authorized target is a new public stable `v2.0.2`, never an overwrite of `v2.0.1`.
- Do not modify Lampac, LampaUASkip, Windows UA Player, or LampaUa Desktop.

---

### Task 1: Add failing regression contracts

**Files:**
- Create: `app/src/test/java/com/brouken/player/SubtitleRuntimeParityContractTest.java`
- Modify: `app/src/test/java/com/brouken/player/LampaPlaylistSessionTest.java`

**Interfaces:**
- Guards `PlayerActivity` renderer state and callback ordering against the exact donor invariants.
- Guards the root series-title parse/session round-trip independently of episode titles.
- Guards the compact finish-label resource and single-line layout.

- [ ] **Step 1: Add source contracts for subtitle runtime and finish label**

Assert that `disableSubtitles()` clears the track-type-wide flag and disables
only the primary text renderer; `applySubtitle()` re-enables that renderer;
`paintSubtitle()` restores `mainLineOff`; `onTracksChanged()` runs donor
selection in the required order; and the finish label uses a single-line compact
resource.

- [ ] **Step 2: Add a playlist round-trip test**

Parse a root object whose title is `Назва серіалу` and item title is `Серія 5`,
then verify `getTitle()` and the serialized/restored snapshot retain the series
title while the item title remains unchanged.

- [ ] **Step 3: Run the focused tests and confirm they fail for the expected missing contracts**

Run:

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.SubtitleRuntimeParityContractTest --tests com.brouken.player.LampaPlaylistSessionTest
```

Expected: failures point to the type-wide subtitle disable, missing root title,
old callback order, and wrapping finish-time layout.

---

### Task 2: Restore the donor subtitle renderer and layout state machine

**Files:**
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`

**Interfaces:**
- `disableSubtitles()` disables renderer `primaryTextRendererIndex` only.
- `applySubtitle()` and `paintSubtitle()` restore the main subtitle line.
- `onTracksChanged()` follows the donor v1.4.7 selection sequence.
- `updateSubtitleLayout()` uses the donor consolidated primary/secondary resting bands and control-aware slide behavior.

- [ ] **Step 1: Port the donor renderer enable/disable helpers verbatim where UA hooks do not differ**

- [ ] **Step 2: Port the donor new-track callback ordering and name restoration**

- [ ] **Step 3: Port the donor subtitle layout calculations, adapting only existing UA inset and PiP names**

- [ ] **Step 4: Run the focused subtitle contract and existing subtitle unit tests**

Run:

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests "com.brouken.player.*Subtitle*Test" --tests com.brouken.player.ResourceContractTest
```

Expected: all selected tests pass.

---

### Task 3: Preserve series identity and adopt the donor search flow

**Files:**
- Modify: `app/src/main/java/com/brouken/player/LampaPlaylist.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/TitleSearch.java` only if the donor comparison shows a required delta
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`

**Interfaces:**
- `LampaPlaylist.getTitle()` exposes a root series title and `toSessionJson()` preserves it.
- The official LAMPA playlist bridge emits that root title.
- Manual search prefers series title, keeps current season/episode, supports donor live title results and season/episode catalogue, and falls back to explicit numeric entry.

- [ ] **Step 1: Add root title parsing, serialization, and access**

- [ ] **Step 2: Keep launch/series title separate when applying an episode**

- [ ] **Step 3: Port the donor v1.4.7 manual title and episode selection flow into UA-styled dialogs**

- [ ] **Step 4: Add the complete localized search strings and rerun focused tests**

Run:

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.SubtitleRuntimeParityContractTest --tests com.brouken.player.LampaPlaylistSessionTest --tests com.brouken.player.SubtitleSearchV13Test --tests com.brouken.player.ResourceContractTest
```

Expected: all selected tests pass and resource compilation succeeds.

---

### Task 4: Guarantee finish-time visibility

**Files:**
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`

**Interfaces:**
- `playback_finishes_at_compact` has one `%1$s` time value.
- `lampaFinishTime` is single-line and its measured width preserves `до 18:04` on portrait phone, landscape phone, and TV header layouts.

- [ ] **Step 1: Add compact localized resources and switch runtime formatting**

- [ ] **Step 2: Replace the wrapping max-lines contract with a single-line value-first layout**

- [ ] **Step 3: Run resource and UI policy tests**

Run:

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.SubtitleRuntimeParityContractTest --tests com.brouken.player.ResourceContractTest --tests com.brouken.player.TopPanelPolicyTest
```

Expected: all selected tests pass.

---

### Task 4A: Restore Google TV control classification

**Files:**
- Modify: `app/src/main/java/com/brouken/player/Utils.java`
- Create: `app/src/test/java/com/brouken/player/GoogleTvDetectionTest.java`

- [ ] **Step 1: Recognize TV mode, Leanback, television, and Fire TV signals**

- [ ] **Step 2: Verify Google TV selects the remote-focus control row and does
  not expose the touch rotation tile**

---

### Task 5: Verify, package, and publish UA Player v2.0.2

**Files:**
- Modify: `app/build.gradle`
- Modify: `README.md`
- Create: `docs/releases/v2.0.2.md`
- Create: `test-builds/UA-Player-2.0.2.apk`

**Interfaces:**
- Android package version is `2.0.2 (22)`.
- APK keeps the established public-release ARM ABIs (`arm64-v8a` and
  `armeabi-v7a`) and signing certificate.
- Git tag and public GitHub release are exactly `v2.0.2`, `draft=false`, `prerelease=false`.

- [ ] **Step 1: Bump version and write user-facing release notes**

- [ ] **Step 2: Run full local verification**

Run:

```powershell
.\gradlew.bat clean :app:testLatestUniversalDebugUnitTest :app:lintLatestUniversalRelease :app:assembleLatestUniversalDebug :app:assembleLatestUniversalRelease
git diff --check
```

Expected: unit tests, lint, debug build, and release build succeed with no
fatal lint errors.

- [ ] **Step 3: Sign and validate the release artifact**

Sign with the existing local compatible keystore without printing credentials,
then run `zipalign -c -P 16`, `apksigner verify --verbose --print-certs`,
`aapt2 dump badging`, `apkanalyzer apk summary`, and
`scripts/verify_apk.py` with the required certificate fingerprint. Confirm
package, `2.0.2 (22)`, non-debuggable release status, the established
`arm64-v8a` and `armeabi-v7a` release ABIs, v1/v2/v3 signatures, certificate,
size, and SHA-256.

- [ ] **Step 4: Check ADB availability and record the runtime-QA boundary**

Run `adb devices`; perform a smoke test if a device is connected, otherwise
state clearly that phone/Google TV validation still needs the user's hardware.

- [ ] **Step 5: Commit, push, tag, and wait for both GitHub workflows**

Push `main`, create and push annotated tag `v2.0.2`, then use `gh run watch`
until CI and signed-release workflows finish successfully.

- [ ] **Step 6: Verify the public release independently**

Use `gh release view v2.0.2` and a fresh asset download. Confirm
`draft=false`, `prerelease=false`, public URL, asset name, certificate, and
downloaded SHA-256 equal the local verified artifact before reporting success.
