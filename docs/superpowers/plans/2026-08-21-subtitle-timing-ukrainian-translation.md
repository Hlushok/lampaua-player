# Subtitle Timing and Ukrainian Auto-Translation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add session subtitle timing, interruption-free online subtitle attachment, and fail-closed automatic translation of missing Ukrainian SubRip subtitles.

**Architecture:** Adapt the pinned Just+ text-renderer wrapper and subtitle timeline, but integrate them into UA Player's existing renderer, playlist, menu, and recovery lifecycles. Keep translation in focused pure-Java document/policy classes behind an OkHttp transport, then let `PlayerActivity` coordinate direct Ukrainian search, foreign fallback, atomic cache output, and timeline painting.

**Tech Stack:** Java 8, Android API 23+, Media3 1.11.0-beta01, OkHttp 5.3.2, `org.json`, JUnit 4, Gradle Android plugin.

**Spec:** `docs/superpowers/specs/2026-08-21-ukrainian-subtitle-translation-design.md`

**Execution mode:** Inline in the current session. Do not dispatch subagents.

## Global Constraints

- Keep application id `com.lampaua.player`, version name `1.6.1`, version code `19`, and certificate SHA-256 `749d118bc8a16a7c0464b8dd0498c53da8a86a668d8f09f551e60cf7d88ee15e`.
- Keep UA branding, LAMPA input/result contracts, GitHub updater, playlist state, server skip API, and `skipSilence` behavior.
- Keep manual skip offset excluded; `OffsetPanel` is used for subtitle timing only.
- Keep translation target fixed to `uk`; never send media ids, URLs, titles, timestamps, formatting tokens, diagnostics, or credentials to translation.
- Do not add runtime dependencies, telemetry, Sentry, remote diagnostics, Just+ branding, or direct skip databases.
- Do not re-prepare playback when a parseable external subtitle arrives.
- Do not change the public version, merge PR #11, create a tag, or publish a release.

---

### Task 1: Subtitle Timeline and Offset Primitives

**Files:**
- Create: `app/src/main/java/com/brouken/player/SubtitleOffsetPolicy.java`
- Create: `app/src/main/java/com/brouken/player/SubtitleTimeline.java`
- Create: `app/src/main/java/com/brouken/player/SubtitleOffset.java`
- Create: `app/src/main/java/com/brouken/player/OffsetPanel.java`
- Create: `app/src/main/res/drawable/ic_subtitle_offset_24dp.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Create: `app/src/test/java/com/brouken/player/SubtitleOffsetPolicyTest.java`
- Create: `app/src/test/java/com/brouken/player/SubtitleTimelineTest.java`
- Create: `app/src/test/java/com/brouken/player/OffsetPanelTest.java`

**Interfaces:**
- Produces: `SubtitleOffsetPolicy.rendererPositionUs(long, double, boolean)`, `SubtitleOffsetPolicy.timelinePositionUs(long, double)`, and `SubtitleOffsetPolicy.cueDueMs(long, double)`.
- Produces: `SubtitleTimeline.load(Context, Uri, String)`, `visibleAt(long)`, and `cuesOf(int[])`.
- Produces: `SubtitleOffset.wrap(Renderer)`, `setTimeline(SubtitleTimeline)`, `setOffsetSec(double)`, `wake()`, and `clear()`.
- Produces: `OffsetPanel.create(...)` and `OffsetPanel.format(double)` for Task 2.

- [ ] **Step 1: Write failing timing-policy tests**

```java
@Test public void negativeOffsetAdvancesEmbeddedRendererClock() {
    assertEquals(12_500_000L,
            SubtitleOffsetPolicy.rendererPositionUs(10_000_000L, -2.5, false));
}

@Test public void positiveOffsetLooksBackInExternalTimeline() {
    assertEquals(7_500_000L,
            SubtitleOffsetPolicy.timelinePositionUs(10_000L, 2.5));
}

@Test public void positiveOffsetDelaysCue() {
    assertEquals(12_500L, SubtitleOffsetPolicy.cueDueMs(10_000_000L, 2.5));
}
```

- [ ] **Step 2: Write failing timeline and format tests**

Construct a package-visible `SubtitleTimeline` from arrays and `ImmutableList<Cue>` blocks. Assert that `visibleAt` returns overlapping cue indexes, returns none outside intervals, and that `OffsetPanel.format` yields `0 s`, `+2.5 s`, and `-10 s`.

- [ ] **Step 3: Run the focused tests and capture the red state**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests '*SubtitleOffsetPolicyTest' --tests '*SubtitleTimelineTest' --tests '*OffsetPanelTest' --console=plain
```

Expected: test compilation fails because the four production classes do not exist.

- [ ] **Step 4: Implement the timing primitives**

Adapt `SubtitleOffset`, `SubtitleTimeline`, `OffsetPanel`, and the icon from Just+ commits `d55277b2a4e7663ae46015ce8d593a3b0620f803` and `774b873acce77d1ec314772b76cf8c232e36f396`. Add a source comment naming Just+ Player and Oleksandr Zhyzhchenko. Keep the panel's reset label subtitle-specific rather than importing any skip-offset UI.

Use this pure policy from `SubtitleOffset`:

```java
final class SubtitleOffsetPolicy {
    static long rendererPositionUs(long positionUs, double offsetSec, boolean timeline) {
        return !timeline && offsetSec < 0
                ? positionUs - (long) (offsetSec * C.MICROS_PER_SECOND) : positionUs;
    }

    static long timelinePositionUs(long positionMs, double offsetSec) {
        return positionMs * 1000L - (long) (offsetSec * C.MICROS_PER_SECOND);
    }

    static long cueDueMs(long presentationTimeUs, double offsetSec) {
        return presentationTimeUs / 1000L + (long) (offsetSec * 1000L);
    }
}
```

- [ ] **Step 5: Run focused tests and Java compilation**

Run the Step 3 command, then:

```powershell
.\gradlew.bat :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

Expected: all focused tests pass and compilation exits `0`.

- [ ] **Step 6: Commit the primitive layer**

```powershell
git add app/src/main/java/com/brouken/player/SubtitleOffsetPolicy.java app/src/main/java/com/brouken/player/SubtitleTimeline.java app/src/main/java/com/brouken/player/SubtitleOffset.java app/src/main/java/com/brouken/player/OffsetPanel.java app/src/main/res/drawable/ic_subtitle_offset_24dp.xml app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml app/src/main/res/values-ru/strings.xml app/src/test/java/com/brouken/player/SubtitleOffsetPolicyTest.java app/src/test/java/com/brouken/player/SubtitleTimelineTest.java app/src/test/java/com/brouken/player/OffsetPanelTest.java
git commit -m "Add subtitle timing primitives"
```

---

### Task 2: Renderer Integration and Interruption-Free Subtitle Attach

**Files:**
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`

**Interfaces:**
- Consumes: all Task 1 interfaces.
- Produces: `addSubtitleTrack(Uri)`, `paintSubtitle(Uri)`, `attachSubtitleTrack(Uri)`, `updateSubtitleTimeline(Tracks)`, `clearSubtitleTimeline()`, and `applySubtitleOffset(double)`.

- [ ] **Step 1: Add failing source-contract tests**

Extend `ResourceContractTest` to require:

```java
assertTrue(activity.contains("buildTextRenderers(Context context, TextOutput output"));
assertTrue(activity.contains("new SubtitleOffset(output, outputLooper"));
assertTrue(activity.contains("paintSubtitle(subtitleUri)"));
assertTrue(activity.contains("SubtitleTimeline.load"));
assertTrue(activity.contains("subtitleOffset.setTimeline"));
assertTrue(activity.contains("R.drawable.ic_subtitle_offset_24dp"));
assertFalse(activity.contains("button_skip_offset"));
```

- [ ] **Step 2: Run the contract test and verify failure**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests '*ResourceContractTest.subtitleOffsetAndHotAttachStayIntegrated' --console=plain
```

Expected: FAIL because `PlayerActivity` does not yet wrap text renderers or paint found subtitles.

- [ ] **Step 3: Integrate the text renderer**

Add session fields for offset value, renderer wrapper, parsed timeline, timeline URI, painted URI, and dialog. In the existing anonymous `DefaultRenderersFactory`, override the Media3 signature exactly:

```java
@Override
protected void buildTextRenderers(Context context, TextOutput output, Looper outputLooper,
                                  int extensionRendererMode, ArrayList<Renderer> out) {
    SubtitleOffset offset = new SubtitleOffset(output, outputLooper, new SubtitleOffset.Position() {
        @Override public long currentMs() {
            return player == null ? C.TIME_UNSET : player.getCurrentPosition();
        }
        @Override public boolean playing() {
            return player != null && player.isPlaying();
        }
    });
    offset.setOffsetSec(subtitleOffsetSec);
    offset.setTimeline(subtitleTimeline);
    subtitleOffset = offset;
    int first = out.size();
    super.buildTextRenderers(context, offset, outputLooper, extensionRendererMode, out);
    for (int i = first; i < out.size(); i++) out.set(i, offset.wrap(out.get(i)));
}
```

- [ ] **Step 4: Integrate timeline selection and hot attach**

Adapt the pinned upstream flow without replacing UA playlist or recovery code:

- `addSubtitleTrack` paints a new parseable file and returns without touching media items.
- `paintSubtitle` parses on a daemon worker and checks the URI before applying.
- `attachSubtitleTrack` retains the current `setMediaItems(...); prepare()` implementation only for parser failure.
- subtitle picker includes a painted file row and correctly handles Off/another track.
- track changes call `updateSubtitleTimeline(tracks)` before online search.
- media transitions, seek discontinuities, release, and playback-state changes clear/wake the offset at the same lifecycle points as the pinned implementation.
- `savePlayer` does not persist `#none` while a painted subtitle is active.

- [ ] **Step 5: Add the subtitle-offset menu action**

Show the new menu row only when `player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_TEXT)` or `paintedSubtitleUri != null`. Bind `OffsetPanel` to -30 through +30 seconds with the pinned 0.5-second step. Reset to zero when the media item changes. Do not add any skip-offset field, dialog, button, resource, or behavior.

- [ ] **Step 6: Run focused tests and compilation**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests '*ResourceContractTest' --tests '*SubtitleOffsetPolicyTest' --tests '*SubtitleTimelineTest' --tests '*OffsetPanelTest' --console=plain
.\gradlew.bat :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

Expected: both commands exit `0`.

- [ ] **Step 7: Commit renderer integration**

```powershell
git add app/src/main/java/com/brouken/player/PlayerActivity.java app/src/test/java/com/brouken/player/ResourceContractTest.java
git commit -m "Attach subtitles without restarting playback"
```

---

### Task 3: Ukrainian Translation Search Policy

**Files:**
- Create: `app/src/main/java/com/brouken/player/UkrainianSubtitlePolicy.java`
- Create: `app/src/test/java/com/brouken/player/UkrainianSubtitlePolicyTest.java`

**Interfaces:**
- Produces: `enabled(boolean, List<String>)`, `directLanguages(List<String>)`, `fallbackLanguages(List<String>)`, `translatedCacheName(String, String)`, and `isDirectUkrainianCache(File)`.

- [ ] **Step 1: Write failing policy tests**

```java
@Test public void directSearchIsUkrainianOnlyWhenEnabled() {
    assertEquals(Collections.singletonList("ukr"),
            UkrainianSubtitlePolicy.directLanguages(Arrays.asList("ukr", "eng")));
}

@Test public void fallbackUsesUserOrderThenDefaultsWithoutDuplicates() {
    assertEquals(Arrays.asList("deu", "eng", "rus", "pol"),
            UkrainianSubtitlePolicy.fallbackLanguages(Arrays.asList("ukr", "deu", "eng")));
}

@Test public void translationRequiresUkrainianPreference() {
    assertFalse(UkrainianSubtitlePolicy.enabled(true, Arrays.asList("eng", "rus")));
}
```

Also test target constancy, path-safe language normalization, direct-cache precedence, and `auto-ukr` generated naming.

- [ ] **Step 2: Run tests and verify the missing-class failure**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests '*UkrainianSubtitlePolicyTest' --console=plain
```

- [ ] **Step 3: Implement the pure policy**

Use `ukr` as the search code and `uk` as the transport target. Preserve user-ordered non-Ukrainian preferences, then append `eng`, `rus`, and `pol` through a `LinkedHashSet`. Generate names with `cachePrefix + ".auto-ukr." + sourceIso3 + ".srt"` after accepting only `[a-z]{3}` source codes.

- [ ] **Step 4: Run tests and commit**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests '*UkrainianSubtitlePolicyTest' --console=plain
git add app/src/main/java/com/brouken/player/UkrainianSubtitlePolicy.java app/src/test/java/com/brouken/player/UkrainianSubtitlePolicyTest.java
git commit -m "Define Ukrainian subtitle fallback policy"
```

---

### Task 4: Integrity-Preserving SubRip Document Translation

**Files:**
- Create: `app/src/main/java/com/brouken/player/SubRipDocument.java`
- Create: `app/src/test/java/com/brouken/player/SubRipDocumentTest.java`

**Interfaces:**
- Produces: `SubRipDocument.parse(byte[])`, `cueCount()`, `batch(int, int)`, `acceptTranslation(Batch, String)`, and `renderUtf8()`.
- Produces nested immutable `Batch` with `fromCue`, `toCue`, `payload`, and ordered marker ids.

- [ ] **Step 1: Write parser and renderer tests**

Use fixtures containing CRLF, multiline text, overlapping timings, `<i>`/`<b>`/`<font>` tags, ASS override tags, and no final newline. Assert byte-for-byte preservation of identifiers and timing lines while translated visible text changes.

```java
SubRipDocument doc = SubRipDocument.parse(SAMPLE.getBytes(StandardCharsets.UTF_8));
SubRipDocument.Batch batch = doc.batch(0, doc.cueCount());
assertFalse(batch.payload.contains("00:00:01,000 --> 00:00:03,000"));
assertFalse(batch.payload.contains("<i>"));
assertTrue(doc.acceptTranslation(batch,
        "Привіт\n[[[UA_PLAYER_000001]]]\nСвіт"));
```

- [ ] **Step 2: Write failure tests**

Reject malformed timing lines, binary NUL input, input over 2 MiB, more than 10,000 cues, missing/duplicated/out-of-order markers, changed formatting placeholders, and a translation applied to the wrong batch.

- [ ] **Step 3: Run tests and capture the red state**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests '*SubRipDocumentTest' --console=plain
```

- [ ] **Step 4: Implement bounded parsing, batching, and rendering**

Parse blocks separated by blank lines, require a numeric identifier and a strict SubRip timing line, and keep each original structural line. Replace formatting spans with deterministic local placeholders before batching. Use boundary markers `[[[UA_PLAYER_%06d]]]`, cap batches at 40 cues and 3,500 UTF-8 bytes, and require every marker exactly once in order before accepting output.

- [ ] **Step 5: Run focused tests and commit**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests '*SubRipDocumentTest' --console=plain
git add app/src/main/java/com/brouken/player/SubRipDocument.java app/src/test/java/com/brouken/player/SubRipDocumentTest.java
git commit -m "Preserve SubRip structure during translation"
```

---

### Task 5: Google Transport and Atomic Translation Cache

**Files:**
- Create: `app/src/main/java/com/brouken/player/SubtitleTranslationTransport.java`
- Create: `app/src/main/java/com/brouken/player/GoogleSubtitleTranslationTransport.java`
- Create: `app/src/main/java/com/brouken/player/UkrainianSubtitleTranslator.java`
- Create: `app/src/test/java/com/brouken/player/GoogleSubtitleTranslationTransportTest.java`
- Create: `app/src/test/java/com/brouken/player/UkrainianSubtitleTranslatorTest.java`

**Interfaces:**
- Produces: `SubtitleTranslationTransport.translate(String sourceIso2, String targetIso2, String payload)`.
- Produces: `GoogleSubtitleTranslationTransport.parseResponse(String)` for deterministic unit testing.
- Produces: `UkrainianSubtitleTranslator.translate(File source, File target, String sourceIso3, SubtitleTranslationTransport transport)` returning `boolean`.

- [ ] **Step 1: Write failing response and request-contract tests**

Assert that Google JSON fragments are concatenated, malformed JSON fails, target is exactly `uk`, and request construction uses HTTPS `POST` form parameters `client=gtx`, `sl`, `tl`, `dt=t`, and `q`. Assert no request logger or payload logging exists.

- [ ] **Step 2: Write failing translator tests with a fake transport**

The fake returns translated batches, corrupts one marker, throws once then succeeds, or stays corrupt for a single cue. Assert one transient retry, recursive batch splitting, complete-file failure for an irrecoverable cue, temporary-file cleanup, and atomic target creation only after complete success.

- [ ] **Step 3: Run tests and verify failure**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests '*GoogleSubtitleTranslationTransportTest' --tests '*UkrainianSubtitleTranslatorTest' --console=plain
```

- [ ] **Step 4: Implement the transport**

Build a dedicated client from `SubtitleSearch.CLIENT` with existing bounded timeouts. Send form data through `FormBody`; parse the first top-level array and concatenate translated fragment index `0`. Throw `IOException` for non-2xx responses, empty bodies, malformed arrays, or empty translation.

- [ ] **Step 5: Implement translation orchestration**

Read at most 2 MiB, parse `SubRipDocument`, translate sequential batches, retry a transport exception once, recursively halve only marker-damaged batches, render UTF-8, write `target.getName() + ".tmp"`, flush and close, then replace the target with `File.renameTo` in the same cache directory. Delete the temporary file on every failure and interruption.

- [ ] **Step 6: Run tests, perform one explicit live probe, and commit**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests '*GoogleSubtitleTranslationTransportTest' --tests '*UkrainianSubtitleTranslatorTest' --console=plain
curl.exe -sS -X POST 'https://translate.googleapis.com/translate_a/single' -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode 'client=gtx' --data-urlencode 'sl=en' --data-urlencode 'tl=uk' --data-urlencode 'dt=t' --data-urlencode 'q=Hello.'
git add app/src/main/java/com/brouken/player/SubtitleTranslationTransport.java app/src/main/java/com/brouken/player/GoogleSubtitleTranslationTransport.java app/src/main/java/com/brouken/player/UkrainianSubtitleTranslator.java app/src/test/java/com/brouken/player/GoogleSubtitleTranslationTransportTest.java app/src/test/java/com/brouken/player/UkrainianSubtitleTranslatorTest.java
git commit -m "Translate SubRip subtitles to Ukrainian"
```

Expected live response contains `Привіт.` and reports detected source `en`.

---

### Task 6: Preference, Two-Phase Search, and Player UI

**Files:**
- Modify: `app/src/main/java/com/brouken/player/Prefs.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/res/xml/root_preferences.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`
- Modify: `README.md`

**Interfaces:**
- Consumes: Tasks 2 through 5.
- Produces preference field `Prefs.subtitleAutoTranslateUkrainian`, default `true`.
- Produces the two-phase search flow inside `maybeSearchSubtitlesOnline(Tracks)`.

- [ ] **Step 1: Add failing preference and privacy contracts**

Require `subtitleAutoTranslateUkrainian` in preferences, default `true`, target `uk`, endpoint HTTPS, `FormBody`, separate `auto-ukr` cache names, no title/id in transport, and Ukrainian progress/success/failure strings. Keep `subtitleSearch` opt-in unchanged.

- [ ] **Step 2: Run contract and policy tests and verify failure**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests '*ResourceContractTest.onlineSubtitleTranslationStaysUkrainianAndPrivate' --tests '*UkrainianSubtitlePolicyTest' --console=plain
```

- [ ] **Step 3: Add the preference and localized UI**

Add a `SwitchPreferenceCompat` under subtitle search/sources with default `true`. Its summary states that only subtitle cue text is sent to an external Google service and the output language is Ukrainian. Load the value in `Prefs.loadUserPreferences()` without changing the existing search opt-in.

- [ ] **Step 4: Implement direct-cache and translated-cache precedence**

In `maybeSearchSubtitlesOnline`, retain the existing direct cache lookup first. When auto-translation is enabled and Ukrainian is wanted, check valid `auto-ukr` cache files in fallback order before starting network work. Attach a cache hit through `attachSearchedSubtitle` and timeline painting.

- [ ] **Step 5: Implement two-phase search in one generation-scoped worker**

Use Phase 1 `SubtitleSearch.find(id, Collections.singletonList("ukr"), ...)`. If it downloads a direct file, attach and stop. If at least one source answered but no direct file was delivered, run Phase 2 with `UkrainianSubtitlePolicy.fallbackLanguages(preferred)`, download the foreign file under a source cache name, show the progress notice, translate atomically to the generated cache name, then post the Ukrainian result only after checking generation and media identity.

If Phase 1 had no source response, do not start translation and do not record a definitive miss. If translation fails, show the non-blocking failure notice and do not attach the foreign file.

- [ ] **Step 6: Update documentation and run focused tests**

Document subtitle offset, hot attach, and optional external Ukrainian auto-translation in README. Run:

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests '*Subtitle*Test' --tests '*ResourceContractTest' --console=plain
.\gradlew.bat :app:compileLatestUniversalDebugJavaWithJavac --console=plain
git diff --check
```

- [ ] **Step 7: Commit the integrated feature**

```powershell
git add app/src/main/java/com/brouken/player/Prefs.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/res/xml/root_preferences.xml app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml app/src/main/res/values-ru/strings.xml app/src/test/java/com/brouken/player/ResourceContractTest.java README.md
git commit -m "Auto-translate missing subtitles to Ukrainian"
```

---

### Task 7: Full Verification and PR Update

**Files:**
- Modify: `docs/superpowers/specs/2026-08-21-ukrainian-subtitle-translation-design.md` (record the verified upstream merge SHA and subtitle-only offset boundary)
- Add: `docs/superpowers/plans/2026-08-21-subtitle-timing-ukrainian-translation.md`
- Generated only, never tracked: `app/build/outputs/apk/**`

**Interfaces:**
- Consumes: the complete feature.
- Produces: verified branch and PR #11; no merge or release.

- [ ] **Step 1: Run full tests, lint, and all publication variants with JDK 21**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --console=plain
.\gradlew.bat :app:lintLatestUniversalDebug --console=plain
.\gradlew.bat :app:assembleLatestUniversalDebug :app:assembleLegacyUniversalDebug :app:assembleLatestUniversalRelease :app:assembleLegacyUniversalRelease --console=plain
```

Expected: every command exits `0`.

- [ ] **Step 2: Run privacy, dependency, and whitespace checks**

```powershell
rg -n "io\.sentry|termbin|qrserver|HttpLoggingInterceptor|request\.body|translate.*title|translate.*imdb|translate.*tmdb" app/src/main app/build.gradle
.\gradlew.bat :app:dependencies --configuration latestUniversalDebugRuntimeClasspath --console=plain
git diff --check
git status --short --branch
```

Expected: no telemetry/payload logging match; dependency graph has one intended Media3 line and no new runtime translation library; worktree contains only planned commits.

- [ ] **Step 3: Sign and verify a local preflight APK**

Sign the fresh latest release APK to a uniquely named ignored preflight file with `C:\Users\stpuh\.android\debug.keystore`, alias `androiddebugkey`, using Android build-tools `37.0.0`. Then run `scripts/verify_apk.py` with `aapt2`, `apksigner`, and `apkanalyzer`, and independently run `aapt2 dump badging`, `apksigner verify --verbose --print-certs`, and `Get-FileHash -Algorithm SHA256`.

Expected package/version/certificate:

```text
package: com.lampaua.player
versionCode: 19
versionName: 1.6.1
certificate SHA-256: 749d118bc8a16a7c0464b8dd0498c53da8a86a668d8f09f551e60cf7d88ee15e
```

- [ ] **Step 4: Push and wait for PR CI**

```powershell
git push origin codex/sync-justplus-2026-08
gh pr checks 11 --repo Hlushok/lampaua-player
```

If checks are pending, select and watch the newest branch run:

```powershell
$run = gh run list --repo Hlushok/lampaua-player --branch codex/sync-justplus-2026-08 --limit 1 --json databaseId --jq '.[0].databaseId'
gh run watch $run --repo Hlushok/lampaua-player --exit-status
```

Stop when CI is green. Do not merge and do not create a release.

- [ ] **Step 5: Report evidence and manual-test boundary**

Report the commits, tests/lint/build exit status, signed preflight path and SHA-256, package/version/certificate, upstream pin, PR URL, and CI state. Explicitly state that torrent interruption, TV focus, long-file translation, endpoint throttling, and renderer timing still require device-level verification.
