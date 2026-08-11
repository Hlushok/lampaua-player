# UA Player TV Branding, Focus and Direct Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Republish UA Player 1.6.1 with UA branding on Android TV, an unmistakable bottom-control focus state, predictable two-press Back behavior, verified LAMPA metadata/results and a browserless update path.

**Architecture:** Replace only the Android TV launcher banner while retaining the existing UA launcher icons. Style the dynamically assembled bottom row after all buttons have been attached, using a dedicated state-list drawable and state-list animator. Route modern and legacy Back delivery through one activity method backed by a small testable exit guard. Keep the existing official LAMPA/MX-compatible intent contract and OkHttp/FileProvider updater, locking both with regression tests.

**Tech Stack:** Android Java, Media3 UI resources, Android state-list drawables/animators, JUnit 4, Pillow for deterministic asset generation, Gradle/JDK 21, GitHub CLI.

## Global Constraints

- Keep `versionName 1.6.1` and `versionCode 19`.
- Keep application ID `com.lampaua.player`.
- Do not add silent installation or remove Android's package-installer confirmation.
- Use signer certificate SHA-256 `749d118bc8a16a7c0464b8dd0498c53da8a86a668d8f09f551e60cf7d88ee15e`.
- Replace the existing release asset under tag `v1.6.1`; users already on version code 19 must reinstall manually.

---

### Task 1: Lock the branding and updater resource contracts

**Files:**
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`
- Test: `app/src/test/java/com/brouken/player/ResourceContractTest.java`

**Interfaces:**
- Consumes: repository files relative to `app/`.
- Produces: regression tests for `banner.png`, TV focus resources and `Updater.java`'s internal download/install path.

- [ ] **Step 1: Add failing resource-contract tests**

Add project-path and image helpers plus tests equivalent to:

```java
@Test public void tvBannerUsesUaPalette() throws Exception {
    BufferedImage image = ImageIO.read(projectPath("src/main/res/mipmap-xhdpi/banner.png").toFile());
    assertEquals(320, image.getWidth());
    assertEquals(180, image.getHeight());
    assertTrue(countUaGold(image) > 300);
    assertTrue(countUaBlue(image) > 300);
    assertTrue(countDark(image) > image.getWidth() * image.getHeight() / 2);
}

@Test public void bottomControlsHaveStrongTvFocusResources() throws Exception {
    String background = readProjectFile("src/main/res/drawable/ua_tv_control_background.xml");
    String animator = readProjectFile("src/main/res/animator/ua_tv_control_focus.xml");
    String activity = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");
    assertTrue(background.contains("state_focused=\"true\""));
    assertTrue(background.contains("3dp"));
    assertTrue(animator.contains("1.10"));
    assertTrue(activity.contains("styleTvBottomControls(controls)"));
}

@Test public void updaterDownloadsInsidePlayerAndUsesFileProvider() throws Exception {
    String updater = readProjectFile("src/main/java/com/brouken/player/update/Updater.java");
    assertTrue(updater.contains("CLIENT.newCall(request).execute()"));
    assertTrue(updater.contains("context.getCacheDir()"));
    assertTrue(updater.contains("FileProvider.getUriForFile"));
    assertTrue(updater.contains("FLAG_GRANT_READ_URI_PERMISSION"));
    assertFalse(updater.contains("Intent.createChooser"));
}
```

- [ ] **Step 2: Run the focused test and confirm failure**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ResourceContractTest --console=plain
```

Expected: FAIL because the current TV banner is green Just Player branding and the UA TV focus resources/call do not exist.

- [ ] **Step 3: Commit the red contract**

```powershell
git add app/src/test/java/com/brouken/player/ResourceContractTest.java
git commit -m "Test TV branding and update contracts"
```

### Task 2: Replace the Android TV banner

**Files:**
- Modify: `app/src/main/res/mipmap-xhdpi/banner.png`
- Verify: `app/src/main/res/drawable-nodpi/ua_player_icon.png`
- Verify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: the approved UA logo in `drawable-nodpi/ua_player_icon.png` and manifest `android:banner="@mipmap/banner"`.
- Produces: a 320 x 180 UA Player television banner with a dark background, round UA logo and `UA Player` label.

- [ ] **Step 1: Generate the banner asset**

Use Pillow to create a 320 x 180 RGB image with:

```text
background #07152A to #020812
logo crop 128 x 128 at x=18, y=26
gold divider #F0B726 at x=154
white "UA Player" title at x=170
blue accent #0877E8 and gold accent #FFD400
```

Save it directly to `app/src/main/res/mipmap-xhdpi/banner.png` and do not alter the source UA logo.

- [ ] **Step 2: Run the branding contract**

Run the focused `ResourceContractTest`; expected: banner assertions PASS while focus assertions remain FAIL.

- [ ] **Step 3: Visually inspect the banner**

Open `app/src/main/res/mipmap-xhdpi/banner.png` and verify the logo is not cropped, the title is centered vertically and the banner contains no Just Player green branding.

- [ ] **Step 4: Commit the banner**

```powershell
git add app/src/main/res/mipmap-xhdpi/banner.png
git commit -m "Brand the Android TV launcher as UA Player"
```

### Task 3: Make bottom-row focus unmistakable

**Files:**
- Create: `app/src/main/res/drawable/ua_tv_control_background.xml`
- Create: `app/src/main/res/animator/ua_tv_control_focus.xml`
- Modify: `app/src/main/res/layout/controls.xml`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Test: `app/src/test/java/com/brouken/player/ResourceContractTest.java`

**Interfaces:**
- Consumes: `LinearLayout controls` after every bottom button is attached.
- Produces: `private void styleTvBottomControls(ViewGroup controls)` and resource-driven focus/press states.

- [ ] **Step 1: Add the background selector**

Create a selector whose focused state uses `@color/ua_blue`, a 3 dp `@color/ua_gold` stroke and 8 dp corners; pressed uses a brighter blue with the same stroke; default is transparent.

- [ ] **Step 2: Add the scale animator**

Create a state-list animator with `scaleX` and `scaleY` at `1.10` for focused state and `1.0` otherwise, using 100 ms transitions.

- [ ] **Step 3: Apply resources to every bottom button**

Add:

```java
private void styleTvBottomControls(ViewGroup controls) {
    if (!isTvBox) return;
    controls.setClipChildren(false);
    controls.setClipToPadding(false);
    for (int i = 0; i < controls.getChildCount(); i++) {
        View child = controls.getChildAt(i);
        if (!(child instanceof ImageButton)) continue;
        child.setBackgroundResource(R.drawable.ua_tv_control_background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            child.setStateListAnimator(AnimatorInflater.loadStateListAnimator(
                    this, R.animator.ua_tv_control_focus));
        }
    }
}
```

Call `styleTvBottomControls(controls);` immediately after the final `controls.addView(...)`. Disable clipping on the scroll container in `controls.xml` so the focused scale is visible.

- [ ] **Step 4: Run the focused contract test**

Expected: all branding/focus/updater contract tests PASS.

- [ ] **Step 5: Commit the focus change**

```powershell
git add app/src/main/res/drawable/ua_tv_control_background.xml app/src/main/res/animator/ua_tv_control_focus.xml app/src/main/res/layout/controls.xml app/src/main/java/com/brouken/player/PlayerActivity.java
git commit -m "Strengthen Android TV control focus"
```

### Task 4: Clarify and verify browserless updating

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Test: `app/src/test/java/com/brouken/player/ResourceContractTest.java`

**Interfaces:**
- Consumes: existing `UpdateUi.startDownload(Activity, UpdateInfo)` and `Updater.installApk(Context, File)`.
- Produces: user-facing wording that explicitly starts download/install without changing the direct updater architecture.

- [ ] **Step 1: Change the positive action copy**

Set `update_now` to:

```xml
<string name="update_now">Download and install</string>
<string name="update_now">Завантажити й установити</string>
<string name="update_now">Скачать и установить</string>
```

in the default, Ukrainian and Russian resources respectively.

- [ ] **Step 2: Run update and resource tests**

Run:

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ResourceContractTest --tests com.brouken.player.update.UpdatePolicyTest --console=plain
```

Expected: PASS and no browser intent appears in the updater contract.

- [ ] **Step 3: Commit updater clarification**

```powershell
git add app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml app/src/main/res/values-ru/strings.xml
git commit -m "Clarify direct in-player updates"
```

### Task 5: Restore TV Back behavior and lock the LAMPA contract

**Files:**
- Create: `app/src/main/java/com/brouken/player/BackExitGuard.java`
- Create: `app/src/test/java/com/brouken/player/BackExitGuardTest.java`
- Create: `app/src/test/java/com/brouken/player/LampaIntentContractTest.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`

- [ ] **Step 1: Add failing exit-guard and LAMPA contract tests**

Test that the first Back arms a two-second guard, a second press inside the window exits, an expired window rearms, and reset clears it. The LAMPA source contract must cover official `video_list.*` metadata, current/per-episode quality, subtitles, segments, ids, MX result action, `end_by`, `position`, `duration`, active URI and `RESULT_OK`.

- [ ] **Step 2: Implement one Back path for all Android versions**

Register `OnBackInvokedDispatcher` on Android 13+. In `onBackPressed()`, cancel an armed TV seek or hide visible controls before arming the exit guard. Exclude Back from the hidden-controller TV key interceptor so Android versions below 13 retain framework key tracking.

- [ ] **Step 3: Add localized copy**

Use `Press Back again to exit`, `Натисніть «Назад» ще раз для виходу`, and `Нажмите «Назад» ещё раз для выхода`.

- [ ] **Step 4: Run the focused compatibility tests and commit**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.BackExitGuardTest --tests com.brouken.player.LampaIntentContractTest --console=plain
```

Commit as `Make TV exit and LAMPA handoff predictable`.

### Task 6: Verify and republish UA Player 1.6.1

**Files:**
- Verify: `app/build.gradle`
- Produce: `app/build/outputs/apk/latestUniversal/release/LampaUA.Player.v1.6.1.apk`
- Publish: GitHub PR, `main`, tag `v1.6.1`, release asset `UA-Player-1.6.1.apk`

**Interfaces:**
- Consumes: completed branding, focus and update commits.
- Produces: a signed non-debug APK under the existing public version.

- [ ] **Step 1: Run the full verification suite on JDK 21**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testLatestUniversalDebugUnitTest :app:lintLatestUniversalDebug :app:assembleLatestUniversalDebug --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Build the signed non-debug release APK locally**

Use `%USERPROFILE%\.android\debug.keystore`, alias `androiddebugkey`, and the existing local passwords through Gradle's injected signing properties. Do not upload the keystore to GitHub.

- [ ] **Step 3: Verify the APK**

Run `scripts/verify_apk.py`, `apkanalyzer manifest debuggable`, `version-name`, `version-code`, and `apksigner --print-certs`.

Expected:

```text
debuggable=false
versionName=1.6.1
versionCode=19
certificate SHA-256=749d118bc8a16a7c0464b8dd0498c53da8a86a668d8f09f551e60cf7d88ee15e
```

- [ ] **Step 4: Push, create PR and wait for CI**

Push `agent/tv-brand-focus-direct-update`, create a PR to `main`, and merge only after the required Android CI succeeds.

- [ ] **Step 5: Move the existing version tag and replace the release asset**

After merge, move annotated tag `v1.6.1` to the new `origin/main` merge commit, force-push only that exact tag, delete only the existing `UA-Player-1.6.1.apk` asset, and upload the newly verified APK under the same name. Keep the release published, stable and marked latest.

- [ ] **Step 6: Verify public state**

Confirm the release is not draft/prerelease, `/releases/latest` returns `v1.6.1`, the remote asset digest equals the local SHA-256, tag commit equals `origin/main`, and the local worktree is clean.
