# Subtitle Language and Appearance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give UA Player an ordered subtitle-language preference and complete in-app subtitle appearance controls that work on Android TV, Fire OS, and phones without depending on the system captioning screen.

**Architecture:** Generalize the existing audio-language normalization and UA-styled priority dialog for both audio and subtitles. Keep persistent values in `Prefs`, isolate color/scale decisions in a pure Java policy, apply the result through Media3's `DefaultTrackSelector` and `SubtitleView`, and deep-link a long press on the subtitle button to the correct native settings row.

**Tech Stack:** Java 8, Android SDK 36, Media3 1.11.0-beta01, AndroidX Preference, JUnit 4.

**Upstream reference:** Just+ branch `feature/subtitles` at `50785416692e92d4e79985cbc61f1362e8928120`, especially commits `05df283`, `cb3986a`, and `4c7bafc`.

**Spec:** `docs/superpowers/specs/2026-08-20-justplus-feature-sync-design.md`

**Execution order:** Run after `2026-08-20-playback-live-lock-stability.md` and `2026-08-20-watch-together.md`, because all three touch `PlayerActivity`, `SettingsActivity`, preferences, resources, and contracts.

## Global Constraints

- Application id remains `com.lampaua.player`; version remains `1.6.1 (19)`.
- Preserve UA navy/gold preference rows, Ukrainian-first resources, existing external/embedded subtitle loading, LAMPA subtitle payloads, and selected-track persistence.
- Do not replace the settings tree wholesale with Just+ resources or branding.
- The system caption language is inherited once only when `languageSubtitle` has never been stored. After that, the app-owned preference is authoritative.
- An empty subtitle-language list means no language preference; it must not force subtitles on.
- Keep embedded-style and bold switches, but make all style choices available without opening Android caption settings.

---

### Task 1: Share language normalization between audio and subtitles

**Files:**
- Rename: `app/src/main/java/com/brouken/player/AudioLanguagePriority.java` to `app/src/main/java/com/brouken/player/LanguagePriority.java`
- Rename: `app/src/test/java/com/brouken/player/AudioLanguagePriorityTest.java` to `app/src/test/java/com/brouken/player/LanguagePriorityTest.java`
- Modify: `app/src/main/java/com/brouken/player/Prefs.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/SettingsActivity.java`
- Modify: `app/src/main/java/com/brouken/player/Utils.java`

**Interfaces:**
- Produces: `LanguagePriority.normalize(String)`, `parse(String)`, `serialize(List<String>)`, `select(List<String>, List<TrackMetadata>)`, and `initialSubtitleValue(boolean, String, String)`.
- Preserves: ISO-639 legacy aliases, de-duplication, stored order, and the existing first-track fallback used by the helper test.

- [ ] **Step 1: Rename the test and make it cover subtitle tracks**

Use `git mv` for both production and test files, rename the classes, and add this regression alongside the existing normalization tests:

```java
@Test public void selectsFirstPreferredSubtitleLanguageThatExists() {
    List<TrackMetadata> tracks = Arrays.asList(
            new TrackMetadata(1, "English", "eng", TrackMetadata.Type.SUBTITLE),
            new TrackMetadata(2, "Українська", "uk-UA", TrackMetadata.Type.SUBTITLE));

    assertEquals(1, LanguagePriority.select(Arrays.asList("ukr", "eng"), tracks));
}

@Test public void captionLanguageSeedsOnlyAPreviouslyMissingPreference() {
    assertEquals("ukr", LanguagePriority.initialSubtitleValue(false, null, "uk-UA"));
    assertEquals("", LanguagePriority.initialSubtitleValue(true, "", "uk-UA"));
    assertEquals("eng", LanguagePriority.initialSubtitleValue(true, "en-US", "uk-UA"));
}
```

- [ ] **Step 2: Run the renamed test and capture unresolved references**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.LanguagePriorityTest --console=plain
```

Expected: compilation fails until all `AudioLanguagePriority` references are renamed.

- [ ] **Step 3: Rename all production references without changing behavior**

Replace `AudioLanguagePriority` with `LanguagePriority` in `Prefs`, `PlayerActivity`, `SettingsActivity`, and `Utils`. Keep the existing implementation and package visibility; do not add Android dependencies to this pure helper. `initialSubtitleValue` normalizes `storedValue` when `hasStoredValue` is true and otherwise normalizes `captionLanguage`; a null/invalid result serializes as the empty string.

- [ ] **Step 4: Run the focused test and Java compilation**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.LanguagePriorityTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 5: Commit the shared language helper**

```powershell
git add app/src/main/java/com/brouken/player/LanguagePriority.java app/src/test/java/com/brouken/player/LanguagePriorityTest.java app/src/main/java/com/brouken/player/Prefs.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/java/com/brouken/player/SettingsActivity.java app/src/main/java/com/brouken/player/Utils.java
git commit -m "Share media language priority logic"
```

---

### Task 2: Add subtitle preferences and a testable style policy

**Files:**
- Create: `app/src/main/java/com/brouken/player/SubtitleStylePolicy.java`
- Create: `app/src/test/java/com/brouken/player/SubtitleStylePolicyTest.java`
- Modify: `app/src/main/java/com/brouken/player/Prefs.java`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`

**Interfaces:**
- Produces preference fields: `languageSubtitle`, `subtitleScale`, `subtitleTextColor`, `subtitleBackgroundColor`, and `subtitleEdgeType`.
- Produces: `Prefs.getLanguageSubtitle(Context)` and `Prefs.setLanguageSubtitle(Context, String)`.
- Produces: `SubtitleStylePolicy.isReadablePair(int, int)`, `edgeColor(int)`, and `parseScale(String, float)`.
- Consumes `CaptioningManager.getLocale()` only inside first-run migration.

- [ ] **Step 1: Write failing pure style tests**

```java
package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubtitleStylePolicyTest {
    @Test public void opaqueMatchingTextAndBackgroundAreRejected() {
        assertFalse(SubtitleStylePolicy.isReadablePair(0xff000000, 0xff000000));
        assertTrue(SubtitleStylePolicy.isReadablePair(0xffffffff, 0xff000000));
        assertTrue(SubtitleStylePolicy.isReadablePair(0xff000000, 0x00000000));
    }

    @Test public void outlineContrastsWithBlackAndNonBlackText() {
        assertEquals(0xffffffff, SubtitleStylePolicy.edgeColor(0xff000000));
        assertEquals(0xff000000, SubtitleStylePolicy.edgeColor(0xffffffff));
        assertEquals(0xff000000, SubtitleStylePolicy.edgeColor(0xffffff00));
    }

    @Test public void corruptScaleFallsBackAndValidScaleIsBounded() {
        assertEquals(1f, SubtitleStylePolicy.parseScale("bad", 1f), 0f);
        assertEquals(0.25f, SubtitleStylePolicy.parseScale("0.1", 1f), 0f);
        assertEquals(2f, SubtitleStylePolicy.parseScale("9", 1f), 0f);
    }
}
```

- [ ] **Step 2: Add a failing preference migration contract**

Extend `ResourceContractTest` to require the exact preference keys and a contains-check before `CaptioningManager` migration:

```java
@Test public void subtitlePreferencesAreAppOwnedAndMigratedOnce() throws Exception {
    String prefs = readProjectFile("src/main/java/com/brouken/player/Prefs.java");
    assertTrue(prefs.contains("PREF_KEY_LANGUAGE_SUBTITLE = \"languageSubtitle\""));
    assertTrue(prefs.contains("PREF_KEY_SUBTITLE_SCALE = \"subtitleScale\""));
    assertTrue(prefs.contains("PREF_KEY_SUBTITLE_TEXT_COLOR = \"subtitleTextColor\""));
    assertTrue(prefs.contains("PREF_KEY_SUBTITLE_BACKGROUND = \"subtitleBackground\""));
    assertTrue(prefs.contains("PREF_KEY_SUBTITLE_EDGE = \"subtitleEdge\""));
    assertTrue(prefs.contains("contains(PREF_KEY_LANGUAGE_SUBTITLE)"));
}
```

- [ ] **Step 3: Run both tests and verify the red state**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.SubtitleStylePolicyTest --tests com.brouken.player.ResourceContractTest.subtitlePreferencesAreAppOwnedAndMigratedOnce --console=plain
```

- [ ] **Step 4: Implement the pure style policy**

Use integer ARGB operations so local JVM tests do not call mocked `android.graphics.Color` methods:

```java
static boolean isReadablePair(int textColor, int backgroundColor) {
    return (backgroundColor >>> 24) == 0 || textColor != backgroundColor;
}

static int edgeColor(int textColor) {
    return (textColor & 0x00ffffff) == 0 ? 0xffffffff : 0xff000000;
}

static float parseScale(String stored, float fallback) {
    try {
        return Math.max(0.25f, Math.min(2f, Float.parseFloat(stored)));
    } catch (RuntimeException error) {
        return fallback;
    }
}
```

- [ ] **Step 5: Add defaults, loading, and one-time language migration in `Prefs`**

Use these exact defaults:

```java
public String languageSubtitle = "";
public float subtitleScale = 1.0f;
public int subtitleTextColor = 0xffffffff;
public int subtitleBackgroundColor = 0x00000000;
public int subtitleEdgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE;
```

`getLanguageSubtitle(Context)` first checks `SharedPreferences.contains("languageSubtitle")`. If present, pass the stored value to `LanguagePriority.initialSubtitleValue(true, stored, null)`. If absent, read `CaptioningManager.getLocale()`, pass it to `initialSubtitleValue(false, null, captionLanguage)`, persist the result (including the empty string), and return it. Subsequent reads must never inspect `CaptioningManager`. Parse color strings with guarded fallbacks and parse scale through `SubtitleStylePolicy`.

- [ ] **Step 6: Run preference/style tests and compile**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.SubtitleStylePolicyTest --tests com.brouken.player.ResourceContractTest.subtitlePreferencesAreAppOwnedAndMigratedOnce :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 7: Commit subtitle preference storage**

```powershell
git add app/src/main/java/com/brouken/player/SubtitleStylePolicy.java app/src/test/java/com/brouken/player/SubtitleStylePolicyTest.java app/src/main/java/com/brouken/player/Prefs.java app/src/test/java/com/brouken/player/ResourceContractTest.java
git commit -m "Add app-owned subtitle preferences"
```

---

### Task 3: Build the UA-styled language and appearance settings

**Files:**
- Rename: `app/src/main/java/com/brouken/player/AudioLanguagePriorityDialog.java` to `app/src/main/java/com/brouken/player/LanguagePriorityDialog.java`
- Modify: `app/src/main/java/com/brouken/player/SettingsActivity.java`
- Modify: `app/src/main/res/values/arrays.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/main/res/xml/root_preferences.xml`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`

**Interfaces:**
- Produces a shared `LanguagePriorityDialog.show(Context, String, int, List<String>, LinkedHashMap<String, String>, List<String>, Listener)`.
- Produces root preference `languageSubtitle` and nested screen `subtitleAppearance`.
- Produces list preferences `subtitleScale`, `subtitleTextColor`, `subtitleBackground`, and `subtitleEdge`.
- Preserves remote focus after add, move, and remove operations.

- [ ] **Step 1: Add failing settings/resource contracts**

```java
@Test public void subtitleSettingsHaveLanguageAppearanceAndUaText() throws Exception {
    String xml = readProjectFile("src/main/res/xml/root_preferences.xml");
    String arrays = readProjectFile("src/main/res/values/arrays.xml");
    String ukrainian = readProjectFile("src/main/res/values-uk/strings.xml");
    assertTrue(xml.contains("app:key=\"languageSubtitle\""));
    assertTrue(xml.contains("app:key=\"subtitleAppearance\""));
    assertTrue(xml.contains("app:key=\"subtitleScale\""));
    assertTrue(xml.contains("app:key=\"subtitleTextColor\""));
    assertTrue(xml.contains("app:key=\"subtitleBackground\""));
    assertTrue(xml.contains("app:key=\"subtitleEdge\""));
    assertTrue(arrays.contains("name=\"subtitle_scale_values\""));
    assertTrue(arrays.contains("name=\"subtitle_text_color_values\""));
    assertTrue(ukrainian.contains("name=\"pref_language_subtitle\">Мова субтитрів</string>"));
    assertFalse(xml.contains("android.settings.CAPTIONING_SETTINGS"));
}
```

- [ ] **Step 2: Run the resource contract and verify it fails**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ResourceContractTest.subtitleSettingsHaveLanguageAppearanceAndUaText --console=plain
```

- [ ] **Step 3: Generalize the existing UA dialog**

Rename the class and add title/empty-resource parameters. Retain `ua_preference_item_background`, the existing up/down/remove controls, and the pinned-language order. After rebuilding a row, restore focus to the surviving button or the Add row; do not replace the dialog with the upstream unstyled implementation.

- [ ] **Step 4: Add the arrays and preference tree**

Add the five scale values `0.25`, `0.5`, `1.0`, `1.5`, `2.0`; readable text colors white, soft white, yellow, cyan, green, black; backgrounds transparent, translucent black, black, white, blue; and edge values outline `1`, shadow `2`, none `0`.

Under the subtitle category, place `languageSubtitle` first and a nested `PreferenceScreen` keyed `subtitleAppearance` containing scale, text color, background, edge, embedded styles, and bold. Remove the system captioning intent. Keep all existing preference keys stable.

- [ ] **Step 5: Wire settings behavior and nested screens**

Make `SettingsActivity` implement `PreferenceFragmentCompat.OnPreferenceStartScreenCallback`. Opening `subtitleAppearance` replaces the fragment with a root-key fragment and adds the transaction to the back stack. Configure both audio and subtitle rows through `LanguagePriorityDialog`; use `pref_language_subtitle_none` for an empty subtitle list.

Decorate color entries with visible chips, and use `SubtitleStylePolicy.isReadablePair` in both change listeners. Reject an opaque same-color text/background choice with the localized `pref_subtitle_color_clash` toast. Preserve the existing TV focus background and the Task 4 focus-search fix from the playback plan.

- [ ] **Step 6: Run contracts and compile**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ResourceContractTest.subtitleSettingsHaveLanguageAppearanceAndUaText --tests com.brouken.player.LanguagePriorityTest --tests com.brouken.player.SubtitleStylePolicyTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 7: Commit the settings UI**

```powershell
git add app/src/main/java/com/brouken/player/LanguagePriorityDialog.java app/src/main/java/com/brouken/player/SettingsActivity.java app/src/main/res/values/arrays.xml app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml app/src/main/res/values-ru/strings.xml app/src/main/res/xml/root_preferences.xml app/src/test/java/com/brouken/player/ResourceContractTest.java
git commit -m "Add subtitle language and appearance settings"
```

---

### Task 4: Apply subtitle language and style in the player

**Files:**
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/SettingsActivity.java`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`
- Modify: `app/src/test/java/com/brouken/player/LampaIntentContractTest.java`

**Interfaces:**
- Consumes `Prefs.languageSubtitle` when constructing `DefaultTrackSelector` parameters.
- Consumes all subtitle style fields in `updateSubtitleStyle(Context)`.
- Produces `SettingsActivity.EXTRA_SCROLL_TO = "scrollTo"` and `PlayerActivity.openSettings(String)`.

- [ ] **Step 1: Add failing player/deep-link contracts**

Extend source contracts to require preferred text languages, app-owned style values, and the long-press deep link while retaining official LAMPA subtitle parsing:

```java
assertTrue(activity.contains("setPreferredTextLanguages"));
assertTrue(activity.contains("mPrefs.languageSubtitle"));
assertTrue(activity.contains("mPrefs.subtitleTextColor"));
assertTrue(activity.contains("SubtitleStylePolicy.edgeColor"));
assertTrue(activity.contains("openSettings(\"languageSubtitle\")"));
assertTrue(settings.contains("EXTRA_SCROLL_TO"));
assertFalse(activity.contains("getUserStyle()"));
```

- [ ] **Step 2: Run the two contract suites and verify the red state**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ResourceContractTest --tests com.brouken.player.LampaIntentContractTest --console=plain
```

- [ ] **Step 3: Apply ordered subtitle languages**

Parse `mPrefs.languageSubtitle` with `LanguagePriority`. When non-empty, call `setPreferredTextLanguages` on the track selector builder in stored order. When empty, do not set a preferred text language and do not enable the text renderer. Keep explicit user track selection and `#none` overrides authoritative over language preference.

- [ ] **Step 4: Apply the app-owned caption style**

Replace runtime reads of `CaptioningManager.getFontScale()` and `getUserStyle()` with:

```java
subtitlesScale = SubtitleUtils.normalizeFontScale(
        mPrefs.subtitleScale, isTvBox || Utils.isTablet(context));
CaptionStyleCompat style = new CaptionStyleCompat(
        mPrefs.subtitleTextColor,
        mPrefs.subtitleBackgroundColor,
        0x00000000,
        mPrefs.subtitleEdgeType,
        SubtitleStylePolicy.edgeColor(mPrefs.subtitleTextColor),
        Typeface.create(Typeface.DEFAULT,
                mPrefs.subtitleStyleBold ? Typeface.BOLD : Typeface.NORMAL));
```

Set that style, embedded-style flag, bottom padding, and fractional text size on the existing `SubtitleView`. Reapply after settings return and after player/view rebuilds.

- [ ] **Step 5: Add precise settings navigation**

`PlayerActivity.openSettings(String key)` snapshots current preferences, sends current media languages, adds `EXTRA_SCROLL_TO`, and starts `SettingsActivity` for result through the existing request code. A subtitle-button long press passes `languageSubtitle`; the More-button long press calls the no-argument overload.

Remove `REQUEST_SYSTEM_CAPTIONS` and its activity-result branch after the system captioning intent is gone.

On the first root settings fragment only, `SettingsActivity` resolves the adapter position for that key, scrolls with the preceding category header visible, and requests focus after layout. Returning from `subtitleAppearance` must not repeat the deep link.

- [ ] **Step 6: Run focused tests and compile**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.LanguagePriorityTest --tests com.brouken.player.SubtitleStylePolicyTest --tests com.brouken.player.ResourceContractTest --tests com.brouken.player.LampaIntentContractTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 7: Commit player integration**

```powershell
git add app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/java/com/brouken/player/SettingsActivity.java app/src/test/java/com/brouken/player/ResourceContractTest.java app/src/test/java/com/brouken/player/LampaIntentContractTest.java
git commit -m "Apply subtitle preferences in playback"
```

---

### Task 5: Verify subtitle behavior without publishing

**Files:**
- Modify only through the failed task that owns any discovered defect.

**Interfaces:**
- Verifies audio-language compatibility, subtitle selection, style persistence, and TV navigation.

- [ ] **Step 1: Run all unit tests**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --console=plain
```

- [ ] **Step 2: Run lint and debug build**

```powershell
.\gradlew.bat :app:lintLatestUniversalDebug :app:assembleLatestUniversalDebug --console=plain
```

- [ ] **Step 3: Run source and integrity checks**

```powershell
rg -n "CAPTIONING_SETTINGS|getUserStyle\(\)" app/src/main
git diff --check
```

Expected: no system-caption settings link or live system-style read remains; diff check exits 0.

- [ ] **Step 4: Complete device checks before merge**

On Android TV and phone, verify ordered Ukrainian/English subtitle fallback, empty-list behavior, explicit None, sidecar subtitles, embedded PGS/ASS/SRT, scale extremes, each color/background/edge choice, clash rejection, bold/embedded style, long-press focus on the language row, nested-screen Back navigation, and persistence after activity/player rebuild.

- [ ] **Step 5: Route any finding back to its owning task**

Add the regression test first, correct only files listed in Task 1-4, rerun that task's focused command, and commit with its explicit file list. Do not create a catch-all verification commit.
