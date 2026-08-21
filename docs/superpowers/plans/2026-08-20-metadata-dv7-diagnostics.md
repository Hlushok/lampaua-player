# Metadata, Dolby Vision 7, Controls, and Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the technically applicable Just+ convergence work: reliable MKV/AVI metadata and frame rate, Dolby Vision profile 7 conversion with safe fallback, time-remaining and held-speed controls, and a local-only playback report screen whose chrome follows the player controls.

**Architecture:** Replace the racing pipe parser with a bounded header buffer, key parsed metadata by media URI, and feed the known frame rate into the Batch 1 frame-rate policy. Wrap the existing extractor factory with the reviewed DV7 converter only when conversion is enabled, preserving the current HEVC recovery rung. Keep gesture math and diagnostic sanitization in pure Java policies, while Android classes own views, Media3 calls, clipboard, and share intents.

**Tech Stack:** Java 8, Android SDK 36, Media3 1.11.0-beta01, local ExoPlayer AAR, ExoplayerHdrUtils 0.3.0, JUnit 4.

**Upstream reference:** Just+ `master` at `d37c8b7634bf45afebe5d8107f2cda37fd2c61d8`, especially commits `632128d`, `402f2ab`, `e8520dc`, `51545c6`, `c415e66`, `2a98d66`, `7b72e7a`, `02c8003`, `e387774`, `30aa98c`, and `da81f41`.

**Spec:** `docs/superpowers/specs/2026-08-20-justplus-feature-sync-design.md`

**Execution order:** Run last, after the playback, Watch Together, and subtitle plans. It consumes `FrameRatePolicy`, `LanguagePriorityTest`, `togetherBadge`, the final settings tree, and the shared `PlayerActivity` lifecycle.

## Global Constraints

- Application id remains `com.lampaua.player`; version remains `1.6.1 (19)`.
- Preserve the existing local `lib-exoplayer-release.aar`, Media3 version, LAMPA contracts, UA skip behavior, passthrough/boost recovery, resolver handling, and profile-7-to-HEVC user fallback.
- Do not add Sentry, a global crash handler, telemetry, termbin, qrserver, remote diagnostics, Just+ branding, or a release.
- Parsing metadata must never fail playback, block a Media3 load thread on a helper thread, or allocate from an untrusted declared size without a fixed ceiling.
- DV7 conversion must not commit rewritten codec metadata or sample bytes until conversion has succeeded. Every unsupported or failed path replays the original sample and leaves the existing HEVC fallback available.
- Keep Watch Together ownership separate; this plan only makes its badge fade with established player chrome.

---

### Task 1: Parse bounded MKV and AVI frame-rate metadata

**Files:**
- Create: `app/src/main/java/com/brouken/player/AviMetadataReader.java`
- Create: `app/src/test/java/com/brouken/player/AviMetadataReaderTest.java`
- Create: `app/src/test/java/com/brouken/player/ContainerMetadataReaderTest.java`
- Create: `app/src/test/java/com/brouken/player/MatroskaMetadataReaderTest.java`
- Modify: `app/src/main/java/com/brouken/player/TrackMetadata.java`
- Modify: `app/src/main/java/com/brouken/player/ContainerMetadataReader.java`
- Modify: `app/src/main/java/com/brouken/player/MatroskaMetadataReader.java`
- Modify: `app/src/main/java/com/brouken/player/Mp4MetadataReader.java`
- Modify: `app/src/test/java/com/brouken/player/LanguagePriorityTest.java`

**Interfaces:**
- Produces `TrackMetadata.frameRate`, where `0f` means unknown.
- Produces `ContainerMetadataReader.SIGNATURE_BYTES = 12` and `headerBudget(byte[])`.
- Produces budgets: Matroska 256 KiB, AVI 64 KiB, MP4 512 KiB, unknown 0.
- Produces `AviMetadataReader.parse(InputStream)` using `avih/dwMicroSecPerFrame`.

- [ ] **Step 1: Add failing signature-budget tests**

```java
package com.brouken.player;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class ContainerMetadataReaderTest {
    @Test public void knownContainersGetBoundedHeaderBudgets() {
        byte[] mkv = new byte[12];
        mkv[0] = 0x1a; mkv[1] = 0x45; mkv[2] = (byte) 0xdf; mkv[3] = (byte) 0xa3;

        byte[] mp4 = new byte[12];
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, mp4, 4, 4);

        byte[] avi = new byte[12];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, avi, 0, 4);
        System.arraycopy("AVI ".getBytes(StandardCharsets.US_ASCII), 0, avi, 8, 4);

        assertEquals(256 * 1024, ContainerMetadataReader.headerBudget(mkv));
        assertEquals(512 * 1024, ContainerMetadataReader.headerBudget(mp4));
        assertEquals(64 * 1024, ContainerMetadataReader.headerBudget(avi));
        assertEquals(0, ContainerMetadataReader.headerBudget(new byte[12]));
    }
}
```

- [ ] **Step 2: Add a failing AVI rate test**

Build a little-endian `RIFF/AVI /LIST/hdrl/avih` header and assert 40,000 microseconds per frame becomes 25 fps:

```java
@Test public void mainHeaderMicrosecondsBecomeFrameRate() {
    ByteBuffer bytes = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
    bytes.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(28);
    bytes.put("AVI ".getBytes(StandardCharsets.US_ASCII));
    bytes.put("LIST".getBytes(StandardCharsets.US_ASCII)).putInt(16);
    bytes.put("hdrl".getBytes(StandardCharsets.US_ASCII));
    bytes.put("avih".getBytes(StandardCharsets.US_ASCII)).putInt(4).putInt(40_000);

    List<TrackMetadata> tracks = AviMetadataReader.parse(
            new ByteArrayInputStream(bytes.array()));
    assertEquals(1, tracks.size());
    assertEquals(TrackMetadata.Type.VIDEO, tracks.get(0).type);
    assertEquals(25f, tracks.get(0).frameRate, 0.001f);
}
```

- [ ] **Step 3: Add a failing Matroska marker-bit/rate fixture**

Use this minimal EBML sequence, whose element IDs retain their marker bits and whose `DefaultDuration` is 40,000,000 ns:

```java
private static final byte[] MKV_VIDEO_TRACK = new byte[] {
        0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, (byte) 0x80,
        0x18, 0x53, (byte) 0x80, 0x67, (byte) 0xff,
        0x16, 0x54, (byte) 0xae, 0x6b, (byte) 0x9c,
        (byte) 0xae, (byte) 0x9a,
        (byte) 0xd7, (byte) 0x81, 0x01,
        (byte) 0x83, (byte) 0x81, 0x01,
        0x23, (byte) 0xe3, (byte) 0x83, (byte) 0x84, 0x02, 0x62, 0x5a, 0x00,
        0x53, 0x6e, (byte) 0x82, 0x56, 0x31,
        0x22, (byte) 0xb5, (byte) 0x9c, (byte) 0x83, 0x75, 0x6b, 0x72
};

@Test public void markerBitsAndDefaultDurationAreRead() {
    List<TrackMetadata> tracks = MatroskaMetadataReader.parse(
            new ByteArrayInputStream(MKV_VIDEO_TRACK));
    assertEquals(1, tracks.size());
    assertEquals(1, tracks.get(0).trackId);
    assertEquals("V1", tracks.get(0).name);
    assertEquals("ukr", tracks.get(0).language);
    assertEquals(25f, tracks.get(0).frameRate, 0.001f);
}
```

- [ ] **Step 4: Run parser tests and capture the red state**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ContainerMetadataReaderTest --tests com.brouken.player.AviMetadataReaderTest --tests com.brouken.player.MatroskaMetadataReaderTest --console=plain
```

- [ ] **Step 5: Add frame rate to metadata and implement the parsers**

Change the constructor to:

```java
TrackMetadata(int trackId, String name, String language, Type type, float frameRate)
```

Update every constructor call in MP4, Matroska, and `LanguagePriorityTest`; MP4 records `0f`. Adapt `ContainerMetadataReader` and `AviMetadataReader` from the pinned upstream source. In `MatroskaMetadataReader`, keep marker bits in `readId()`, parse `DefaultDuration` id `0x23E383`, reject truncated integers/strings, and cap strings at 64 KiB.

- [ ] **Step 6: Run parser/language tests and compile**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ContainerMetadataReaderTest --tests com.brouken.player.AviMetadataReaderTest --tests com.brouken.player.MatroskaMetadataReaderTest --tests com.brouken.player.LanguagePriorityTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 7: Commit container parsing**

```powershell
git add app/src/main/java/com/brouken/player/AviMetadataReader.java app/src/main/java/com/brouken/player/TrackMetadata.java app/src/main/java/com/brouken/player/ContainerMetadataReader.java app/src/main/java/com/brouken/player/MatroskaMetadataReader.java app/src/main/java/com/brouken/player/Mp4MetadataReader.java app/src/test/java/com/brouken/player/AviMetadataReaderTest.java app/src/test/java/com/brouken/player/ContainerMetadataReaderTest.java app/src/test/java/com/brouken/player/MatroskaMetadataReaderTest.java app/src/test/java/com/brouken/player/LanguagePriorityTest.java
git commit -m "Read MKV and AVI frame rates"
```

---

### Task 2: Replace the pipe parser with a bounded header buffer

**Files:**
- Create: `app/src/main/java/com/brouken/player/ContainerHeaderBuffer.java`
- Create: `app/src/test/java/com/brouken/player/ContainerHeaderBufferTest.java`
- Modify: `app/src/main/java/com/brouken/player/TrackNameParsingDataSource.java`

**Interfaces:**
- Produces `ContainerHeaderBuffer.append(byte[], int, int)`, `finish()`, and `isDone()`.
- Produces URI-keyed listener methods `onMetadataParsed(Uri, List<TrackMetadata>)`, `isMetadataParsed(Uri)`, and `onContentLength(Uri, long)`.
- Preserves `TrackNameParsingDataSource.bytesRead`, manifest sniffing, and resolver-JSON rejection.

- [ ] **Step 1: Write failing bounded-buffer tests**

```java
@Test public void unknownSignatureStopsWithoutPayloadAllocation() {
    ContainerHeaderBuffer buffer = new ContainerHeaderBuffer();
    byte[] manifest = "#EXTM3U\n".getBytes(StandardCharsets.US_ASCII);
    buffer.append(manifest, 0, manifest.length);
    buffer.append(new byte[64 * 1024], 0, 64 * 1024);
    assertTrue(buffer.isDone());
    assertNull(buffer.finish());
}

@Test public void partialMkvHeaderIsReturnedOnClose() {
    ContainerHeaderBuffer buffer = new ContainerHeaderBuffer();
    buffer.append(MKV_VIDEO_TRACK, 0, MKV_VIDEO_TRACK.length);
    assertArrayEquals(MKV_VIDEO_TRACK, buffer.finish());
    assertTrue(buffer.isDone());
}

@Test public void containerBudgetCapsCollectedBytes() {
    ContainerHeaderBuffer buffer = new ContainerHeaderBuffer();
    byte[] input = new byte[300 * 1024];
    input[0] = 0x1a; input[1] = 0x45; input[2] = (byte) 0xdf; input[3] = (byte) 0xa3;
    buffer.append(input, 0, input.length);
    assertEquals(256 * 1024, buffer.finish().length);
}
```

Declare a matching `MKV_VIDEO_TRACK` fixture in this test class; do not couple tests across classes.

- [ ] **Step 2: Run the buffer test and verify the missing class failure**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ContainerHeaderBufferTest --console=plain
```

- [ ] **Step 3: Implement the pure bounded collector**

Collect only the first 12 signature bytes until `ContainerMetadataReader.headerBudget` returns a budget. Unknown signatures transition directly to done and retain no payload. Known containers allocate at most their budget, stop accepting after that limit, and return at most one byte array from `finish()`. Catch allocation/runtime failures in the data-source sink and discard metadata rather than propagating them into playback.

- [ ] **Step 4: Replace `PipeSink` with a buffer-backed `HeaderSink`**

Remove `PipedInputStream`, `PipedOutputStream`, parser thread, the 128 MiB cap, and load-thread backpressure. Tee only offset-zero opens whose exact URI is not already parsed. Parse collected bytes synchronously when the budget is reached or the source closes, then invoke `onMetadataParsed(originalUri, tracks)`.

After `upstream.open`, report whole-item length when `dataSpec.position == 0`, `dataSpec.length == C.LENGTH_UNSET`, and returned length is positive. Keep all listener calls best-effort and keyed by the URI requested by Media3, not the redirected URI.

- [ ] **Step 5: Run buffer/parser tests and compile**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ContainerHeaderBufferTest --tests com.brouken.player.ContainerMetadataReaderTest --tests com.brouken.player.AviMetadataReaderTest --tests com.brouken.player.MatroskaMetadataReaderTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 6: Commit buffered parsing**

```powershell
git add app/src/main/java/com/brouken/player/ContainerHeaderBuffer.java app/src/main/java/com/brouken/player/TrackNameParsingDataSource.java app/src/test/java/com/brouken/player/ContainerHeaderBufferTest.java
git commit -m "Buffer container headers without parser races"
```

---

### Task 3: Use URI-keyed metadata for stats and frame-rate matching

**Files:**
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/PlaybackStatistics.java`
- Create: `app/src/test/java/com/brouken/player/PlaybackStatisticsTest.java`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`
- Modify: `app/src/test/java/com/brouken/player/FrameRatePolicyTest.java`

**Interfaces:**
- Produces `currentContainerTracks()`, `containerFrameRate()`, and `videoFrameRate()`.
- Produces URI-keyed content lengths and average bitrate fallback for MKV/AVI.
- Produces `PlaybackStatistics.averageBitrate(long contentLength, long durationMs)` without integer overflow.
- Consumes `FrameRatePolicy` and bounded switch completion from the Batch 1 plan.

- [ ] **Step 1: Add failing source/statistics contracts**

Require the listener to key metadata and lengths by `originalUri`, the stats panel to use a container fallback, and frame-rate matching to prefer a known rate:

```java
assertTrue(activity.contains("currentContainerTracks()"));
assertTrue(activity.contains("containerFrameRate()"));
assertTrue(activity.contains("videoFrameRate()"));
assertTrue(activity.contains("contentLengths"));
assertTrue(activity.contains("onContentLength(Uri originalUri, long length)"));
assertTrue(activity.contains("Utils.handleFrameRate(PlayerActivity.this, rate)"));
```

Extend `FrameRatePolicyTest` with a 25 fps container rate choosing 50 Hz over 60 Hz.

Add pure bitrate tests:

```java
@Test public void contentLengthAndDurationBecomeAverageBitsPerSecond() {
    assertEquals(8_000_000L, PlaybackStatistics.averageBitrate(1_000_000L, 1_000L));
    assertEquals(8_000L,
            PlaybackStatistics.averageBitrate(Long.MAX_VALUE, Long.MAX_VALUE));
    assertEquals(0L, PlaybackStatistics.averageBitrate(0L, 1_000L));
    assertEquals(0L, PlaybackStatistics.averageBitrate(1_000L, 0L));
}
```

- [ ] **Step 2: Run focused tests and capture the red state**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ResourceContractTest --tests com.brouken.player.FrameRatePolicyTest --tests com.brouken.player.PlaybackStatisticsTest --console=plain
```

- [ ] **Step 3: Key metadata and content length by URI**

Store parsed tracks under the original media URI. Apply them to track labels, stats, and frame-rate switching only when that key equals `currentMediaUri().toString()`. A preloaded next episode must never overwrite current-episode labels or rate. Store positive content length in a `ConcurrentHashMap<String, Long>` and clear only the active-item view state on item changes, not the whole map.

- [ ] **Step 4: Add rate and bitrate fallbacks**

`videoFrameRate()` returns `Format.frameRate` when positive and otherwise the first current video `TrackMetadata.frameRate`. The statistics panel displays that value. For a current item with no format bitrate and known positive duration/length, call `PlaybackStatistics.averageBitrate`. Implement it with double precision and clamp to `Long.MAX_VALUE` before casting, so the intermediate `contentLength * 8_000` cannot overflow.

At `STATE_READY`, pass `videoFrameRate()` to `Utils.handleFrameRate`; call URI probing only when it is zero. Keep the Batch 1 1,500 ms display-switch timeout and relative-error matching.

- [ ] **Step 5: Run metadata, frame-rate, and resource tests**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.ContainerHeaderBufferTest --tests com.brouken.player.ContainerMetadataReaderTest --tests com.brouken.player.AviMetadataReaderTest --tests com.brouken.player.MatroskaMetadataReaderTest --tests com.brouken.player.FrameRatePolicyTest --tests com.brouken.player.PlaybackStatisticsTest --tests com.brouken.player.ResourceContractTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 6: Commit metadata integration**

```powershell
git add app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/java/com/brouken/player/PlaybackStatistics.java app/src/test/java/com/brouken/player/PlaybackStatisticsTest.java app/src/test/java/com/brouken/player/ResourceContractTest.java app/src/test/java/com/brouken/player/FrameRatePolicyTest.java
git commit -m "Use container metadata in playback stats"
```

---

### Task 4: Convert Dolby Vision profile 7 with fail-open playback

**Files:**
- Create: `app/src/main/java/com/brouken/player/Dv7Converter.java`
- Create: `app/src/test/java/com/brouken/player/Dv7ConverterTest.java`
- Modify: `app/build.gradle`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`

**Interfaces:**
- Produces `Dv7Converter(ExtractorsFactory, SubtitleParser.Factory)`, `status()`, `copyWithoutEnhancementLayer`, and `findRpu`.
- Consumes ExoplayerHdrUtils 0.3.0 while excluding duplicate Media3 ExoPlayer and `core-ktx`.
- Preserves `mapDV7ToHevc` and decoder-failure `forceHevcForDolbyVision` recovery.

- [ ] **Step 1: Write failing NAL filtering tests**

```java
@Test public void dropsEnhancementLayerButKeepsBaseAndRpu() {
    byte[] sample = new byte[] {
            0, 0, 0, 1, 64, 1, 0x11,
            0, 0, 0, 1, 126, 1, 0x22,
            0, 0, 0, 1, 124, 1, 0x33
    };
    ByteBuffer output = ByteBuffer.allocate(32);
    int written = Dv7Converter.copyWithoutEnhancementLayer(sample, sample.length, output);
    assertEquals(14, written);
    assertArrayEquals(new byte[] {
            0, 0, 0, 1, 64, 1, 0x11,
            0, 0, 0, 1, 124, 1, 0x33
    }, Arrays.copyOf(output.array(), written));
}

@Test public void findsLengthPrefixedRpuOnlyWhenBlockTilesExactly() {
    byte[] block = new byte[] {0, 3, 124, 1, 0x55};
    int[] range = new int[2];
    assertTrue(Dv7Converter.findRpu(block, block.length, 2, range));
    assertArrayEquals(new int[] {2, 3}, range);
    assertFalse(Dv7Converter.findRpu(new byte[] {0, 4, 124, 1}, 4, 2, range));
}
```

- [ ] **Step 2: Add a failing dependency/fallback contract**

```java
assertTrue(build.contains("com.suyashbelekar:exoplayerhdrutils:0.3.0"));
assertTrue(build.contains("exclude group: \"androidx.media3\", module: \"media3-exoplayer\""));
assertTrue(build.contains("exclude group: \"androidx.core\", module: \"core-ktx\""));
assertTrue(activity.contains("!mPrefs.mapDV7ToHevc && !forceHevcForDolbyVision"));
assertTrue(activity.contains("new Dv7Converter"));
assertFalse(build.contains("io.sentry"));
```

- [ ] **Step 3: Run tests and verify the red state**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.Dv7ConverterTest --tests com.brouken.player.ResourceContractTest --console=plain
```

- [ ] **Step 4: Add the dependency with exclusions**

```groovy
implementation("com.suyashbelekar:exoplayerhdrutils:0.3.0") {
    exclude group: "androidx.media3", module: "media3-exoplayer"
    exclude group: "androidx.core", module: "core-ktx"
}
```

Do not change Media3, compileSdk, the local AAR, or existing native decoder dependencies.

- [ ] **Step 5: Adapt the reviewed converter**

Port `Dv7Converter.java` from upstream commits `2a98d66` and `7b72e7a`, retaining bounded 8 MiB block-additional and 32 MiB frame limits. Preserve the Matroska `BlockAdditional` hook, in-band enhancement-layer removal, delayed codec-string rewrite, original-sample replay, subtitle parser factory, device profile-8 check, and status descriptions. Remove no fallback paths and add no reporting calls.

- [ ] **Step 6: Integrate it around the existing extractor factory**

Create one `DefaultSubtitleParserFactory` and pass it to both `DefaultExtractorsFactory` and `Dv7Converter`. Enable conversion only when neither the user nor recovery requested HEVC:

```java
boolean convertDv7 = !mPrefs.mapDV7ToHevc && !forceHevcForDolbyVision;
dv7Converter = convertDv7 ? new Dv7Converter(extractorsFactory, subtitleParserFactory) : null;
ExtractorsFactory activeFactory = convertDv7 ? dv7Converter : extractorsFactory;
```

Use `activeFactory` in `DefaultMediaSourceFactory`. A decoder timeout/decoding failure still rebuilds with `forceHevcForDolbyVision = true`, bypasses the converter, preserves position/play intent, and routes the DV stream to HEVC exactly once.

- [ ] **Step 7: Run tests, compile, and inspect dependencies**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.Dv7ConverterTest --tests com.brouken.player.ResourceContractTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
.\gradlew.bat :app:dependencies --configuration latestUniversalDebugRuntimeClasspath --console=plain
```

Expected: one Media3 ExoPlayer implementation remains, ExoplayerHdrUtils 0.3.0 resolves, and no Sentry artifact appears.

- [ ] **Step 8: Commit DV7 conversion**

```powershell
git add app/build.gradle app/src/main/java/com/brouken/player/Dv7Converter.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/test/java/com/brouken/player/Dv7ConverterTest.java app/src/test/java/com/brouken/player/ResourceContractTest.java
git commit -m "Convert Dolby Vision profile 7 safely"
```

---

### Task 5: Add time remaining and held speed steering

**Files:**
- Create: `app/src/main/java/com/brouken/player/HoldSpeedPolicy.java`
- Create: `app/src/test/java/com/brouken/player/HoldSpeedPolicyTest.java`
- Modify: `app/src/main/java/com/brouken/player/CustomPlayerView.java`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/java/com/brouken/player/Prefs.java`
- Modify: `app/src/main/java/com/brouken/player/SettingsActivity.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/main/res/xml/root_preferences.xml`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`

**Interfaces:**
- Produces preferences `timeRemaining` default false and `holdSpeed` default true.
- Produces `HoldSpeedPolicy.State { direction, speed }` from horizontal drag distance and prior direction.
- Produces a 100 ms rewind ticker using the existing seek-in-flight gate.

- [ ] **Step 1: Write failing gesture-policy tests**

```java
@Test public void holdStartsAtTwoAndRightDragRaisesToFour() {
    assertEquals(2f, HoldSpeedPolicy.evaluate(0f, false).speed, 0f);
    assertEquals(4f, HoldSpeedPolicy.evaluate(80f, false).speed, 0f);
    assertEquals(HoldSpeedPolicy.Direction.FORWARD,
            HoldSpeedPolicy.evaluate(80f, false).direction);
}

@Test public void leftDragCrossesIntoBoundedRewind() {
    HoldSpeedPolicy.State state = HoldSpeedPolicy.evaluate(-80f, false);
    assertEquals(HoldSpeedPolicy.Direction.REWIND, state.direction);
    assertEquals(3f, state.speed, 0f);
    assertEquals(4f, HoldSpeedPolicy.evaluate(-400f, true).speed, 0f);
}

@Test public void rewindHysteresisPreventsBoundaryThrashing() {
    assertEquals(HoldSpeedPolicy.Direction.REWIND,
            HoldSpeedPolicy.evaluate(-38f, true).direction);
    assertEquals(HoldSpeedPolicy.Direction.FORWARD,
            HoldSpeedPolicy.evaluate(-35f, true).direction);
}
```

Use the upstream axis: anchor is 2x, every 40 dp changes one step, forward and rewind cap at 4x, and an active rewind exits only above the 1.1x boundary.

- [ ] **Step 2: Add failing preference/UI contracts**

Require `timeRemaining`, `holdSpeed`, progress listener, rewind ticker, and Ukrainian labels. Require `SettingsActivity` to hide `holdSpeed` on TV.

```java
assertTrue(activity.contains("setProgressUpdateListener"));
assertTrue(activity.contains("mPrefs.timeRemaining"));
assertTrue(customView.contains("REWIND_TICK_MS = 100"));
assertTrue(customView.contains("HoldSpeedPolicy.evaluate"));
assertTrue(settings.contains("findPreference(\"holdSpeed\")"));
assertTrue(ukrainian.contains("name=\"pref_time_remaining\""));
assertTrue(ukrainian.contains("name=\"pref_hold_speed\""));
```

- [ ] **Step 3: Run tests and capture the red state**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.HoldSpeedPolicyTest --tests com.brouken.player.ResourceContractTest --console=plain
```

- [ ] **Step 4: Implement the pure gesture mapping**

Return rates rounded to one decimal. Forward uses `max(1, 2 + deltaDp / 40)`; crossing below 1 enters rewind at `2 + (1 - value)`. An active rewind remains rewind while `value < 1.1`. Clamp both directions to 4.

- [ ] **Step 5: Add time-remaining rendering**

Load the two preferences in `Prefs` and add localized settings rows. Attach one `PlayerControlView.setProgressUpdateListener`; use Media3 `Util.getStringForTime` to show total duration or `-remaining`. Leave live/unknown duration untouched. Reapply after timeline changes through the same progress callback.

- [ ] **Step 6: Add held speed and rewind behavior**

Adapt commit `e387774` to call `HoldSpeedPolicy`. Long press while playing stores the prior speed and starts at 2x. Horizontal moves update a top-center UA-styled rate/direction pill. Rewind pauses playback, sets `SeekParameters.PREVIOUS_SYNC`, and moves a promised position every 100 ms through the existing one-seek-in-flight gate. Release/cancel removes all callbacks, lands on the promised position, restores prior speed and play state, resets seek parameters, and hides the pill.

Disable the gesture when `holdSpeed` is false, while locked, during PiP, or on TV. Hide only the preference row on TV; keep the stored value unchanged.

- [ ] **Step 7: Run tests and compile**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.HoldSpeedPolicyTest --tests com.brouken.player.ResourceContractTest --tests com.brouken.player.TvSeekControllerTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
```

- [ ] **Step 8: Commit playback controls**

```powershell
git add app/src/main/java/com/brouken/player/HoldSpeedPolicy.java app/src/test/java/com/brouken/player/HoldSpeedPolicyTest.java app/src/main/java/com/brouken/player/CustomPlayerView.java app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/java/com/brouken/player/Prefs.java app/src/main/java/com/brouken/player/SettingsActivity.java app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml app/src/main/res/values-ru/strings.xml app/src/main/res/xml/root_preferences.xml app/src/test/java/com/brouken/player/ResourceContractTest.java
git commit -m "Add time remaining and held speed steering"
```

---

### Task 6: Add a local-only playback report screen and synchronized chrome fading

**Files:**
- Create: `app/src/main/java/com/brouken/player/DiagnosticReport.java`
- Create: `app/src/main/java/com/brouken/player/PlaybackReportActivity.java`
- Create: `app/src/test/java/com/brouken/player/DiagnosticReportTest.java`
- Create: `app/src/main/res/layout/activity_playback_report.xml`
- Create: `app/src/main/res/layout-land/activity_playback_report.xml`
- Create: `app/src/main/res/drawable/ua_report_panel_background.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/brouken/player/PlayerActivity.java`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/test/java/com/brouken/player/ResourceContractTest.java`
- Modify: `README.md`

**Interfaces:**
- Produces `DiagnosticReport.sanitizeText`, `sanitizeNetworkUri`, `rootMessage`, and `stackTrace`.
- Produces `PlaybackReportActivity.show(Context, String, String, String)` with Copy, Share, and Close only.
- Produces one auxiliary-chrome fade path for the statistics panel and `togetherBadge`.

- [ ] **Step 1: Write failing sanitizer tests**

```java
@Test public void removesCredentialsQueryAndFragmentFromNetworkUrls() {
    assertEquals("Load https://example.test/video.m3u8 failed",
            DiagnosticReport.sanitizeText(
                    "Load https://user:secret@example.test/video.m3u8?token=abc#part failed"));
}

@Test public void localMediaDescriptionKeepsOnlyScheme() {
    assertEquals("content (local)",
            DiagnosticReport.sanitizeNetworkUri("content://media/external/video/42"));
    assertEquals("file (local)",
            DiagnosticReport.sanitizeNetworkUri("file:///storage/private/movie.mkv"));
}

@Test public void deepestNonEmptyCauseMessageWins() {
    Throwable error = new RuntimeException("outer", new IOException("socket closed"));
    assertEquals("socket closed", DiagnosticReport.rootMessage(error));
}
```

- [ ] **Step 2: Add failing privacy/resource contracts**

```java
assertTrue(manifest.contains(".PlaybackReportActivity"));
assertTrue(activity.contains("PlaybackReportActivity.show"));
assertTrue(reportActivity.contains("Intent.ACTION_SEND"));
assertTrue(reportActivity.contains("ClipboardManager"));
assertFalse(reportActivity.contains("Socket"));
assertFalse(reportActivity.contains("HttpURLConnection"));
assertFalse(reportActivity.contains("termbin"));
assertFalse(reportActivity.contains("qrserver"));
assertFalse(build.contains("io.sentry"));
```

- [ ] **Step 3: Run tests and capture the red state**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.DiagnosticReportTest --tests com.brouken.player.ResourceContractTest --console=plain
```

- [ ] **Step 4: Implement pure report sanitization**

Sanitize every HTTP(S) token embedded in summary, exception text, and stack/cause text by rebuilding it without user info, query, or fragment. Report local media only as its scheme name followed by ` (local)` and never include local path/file name. Return unchanged ordinary text. Handle malformed URLs by removing their query/fragment tail rather than throwing.

- [ ] **Step 5: Build the UA report activity**

Adapt the on-screen report concept from `ErrorActivity` at commit `30aa98c`, but create `PlaybackReportActivity` without `App`, crash handler, upload button/progress/result, sockets, HTTP, or QR. Show a short sanitized summary and a scrollable full report containing app version/flavor, Android/device/ABI, timestamp, selected video/audio formats, playback position/duration/state, decoder names, current recovery flags, DV7 converter status, and sanitized media description.

Copy writes the local report to the clipboard. Share uses `ACTION_SEND` with `text/plain`; Close finishes. Use UA navy/gold resources, focusable 48 dp actions, portrait and landscape layouts, `Theme.Player.Settings`, `exported=false`, and `excludeFromRecents=true`. Add the existing `ACTION_SEND` package query once in the manifest.

- [ ] **Step 6: Route errors and statistics to the screen**

Replace transient/copy-only playback diagnostics with `PlaybackReportActivity.show`. A fatal playback error opens the report after bounded recovery is exhausted; the statistics/report action opens the same activity without claiming a crash. Keep launcher result reporting and episode navigation behavior intact.

- [ ] **Step 7: Fade all auxiliary chrome with controls**

Create one method that cancels in-flight animations and applies the controller's target visibility/duration to the statistics panel and Watch Together `togetherBadge`. On controller show, set visible before alpha animation; on hide, set `GONE` only in the matching animation-end callback. PiP and lock force immediate hidden state. The skip pill, held-speed indicator, and three-second swipe-to-unlock affordance retain their independent visibility lifecycles.

- [ ] **Step 8: Update attribution**

In `README.md`, credit [Just+ Player](https://github.com/just-plus-player/just-plus-player) and Oleksandr Zhyzhchenko for reviewed upstream behavior adapted into UA Player. Keep the existing moneytoo/Player credit and Unlicense statement. The Watch Together plan separately retains the LocalSend Apache-2.0 comment in `AliasGenerator`.

- [ ] **Step 9: Run tests, compile, and privacy scan**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --tests com.brouken.player.DiagnosticReportTest --tests com.brouken.player.ResourceContractTest :app:compileLatestUniversalDebugJavaWithJavac --console=plain
rg -n "io\.sentry|termbin|qrserver|uploadTo|HttpURLConnection|new Socket" app/src/main app/build.gradle
```

Expected: tests/compile pass and the privacy scan returns no matches.

- [ ] **Step 10: Commit local diagnostics and chrome behavior**

```powershell
git add app/src/main/java/com/brouken/player/DiagnosticReport.java app/src/main/java/com/brouken/player/PlaybackReportActivity.java app/src/test/java/com/brouken/player/DiagnosticReportTest.java app/src/main/res/layout/activity_playback_report.xml app/src/main/res/layout-land/activity_playback_report.xml app/src/main/res/drawable/ua_report_panel_background.xml app/src/main/AndroidManifest.xml app/src/main/java/com/brouken/player/PlayerActivity.java app/src/main/res/values/themes.xml app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml app/src/main/res/values-ru/strings.xml app/src/test/java/com/brouken/player/ResourceContractTest.java README.md
git commit -m "Add local playback reports"
```

---

### Task 7: Verify the complete convergence branch without publishing

**Files:**
- Modify only through the failed task that owns any discovered defect.

**Interfaces:**
- Verifies parser safety, frame-rate behavior, DV7 fallback, controls, diagnostics privacy, and inactive Watch Together compatibility.

- [ ] **Step 1: Run all unit tests**

```powershell
.\gradlew.bat :app:testLatestUniversalDebugUnitTest --console=plain
```

- [ ] **Step 2: Run lint plus debug and unsigned release builds**

```powershell
.\gradlew.bat :app:lintLatestUniversalDebug :app:assembleLatestUniversalDebug :app:assembleLegacyUniversalDebug :app:assembleLatestUniversalRelease :app:assembleLegacyUniversalRelease --console=plain
```

- [ ] **Step 3: Check dependency uniqueness and privacy**

```powershell
.\gradlew.bat :app:dependencies --configuration latestUniversalDebugRuntimeClasspath --console=plain
rg -n "io\.sentry|termbin|qrserver|uploadTo|HttpURLConnection|new Socket" app/src/main app/build.gradle
git diff --check
```

Expected: ExoplayerHdrUtils resolves, Media3 ExoPlayer is not duplicated, the privacy scan has no matches, and diff check exits 0.

- [ ] **Step 4: Verify the packaged debug APK without copying it to `test-builds/`**

```powershell
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdkRoot "build-tools") -Directory | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$apk = Get-ChildItem -LiteralPath "app\build\outputs\apk\latestUniversal\debug" -Filter *.apk | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$aapt2 = Join-Path $buildTools.FullName "aapt2.exe"
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
$apkanalyzer = Join-Path $sdkRoot "cmdline-tools\latest\bin\apkanalyzer.bat"
$python = Get-Command py -ErrorAction SilentlyContinue
if ($python) {
    & $python.Source -3 scripts\verify_apk.py --apk $apk.FullName --aapt2 $aapt2 --apksigner $apksigner --apkanalyzer $apkanalyzer
} else {
    & (Get-Command python -ErrorAction Stop).Source scripts\verify_apk.py --apk $apk.FullName --aapt2 $aapt2 --apksigner $apksigner --apkanalyzer $apkanalyzer
}
if ((& $apkanalyzer manifest application-id $apk.FullName).Trim() -ne "com.lampaua.player") { throw "Unexpected application id" }
if ((& $apkanalyzer manifest version-name $apk.FullName).Trim() -ne "1.6.1") { throw "Unexpected version name" }
if ((& $apkanalyzer manifest version-code $apk.FullName).Trim() -ne "19") { throw "Unexpected version code" }
Get-FileHash -Algorithm SHA256 -LiteralPath $apk.FullName
```

This verifies installability, package, version, resources, bytecode contract, debug signature, and records the test artifact SHA-256. It does not claim release-certificate compatibility: no official signed APK is created in this branch task. Before any user-requested APK or release, sign with the existing key and require certificate SHA-256 `749d118bc8a16a7c0464b8dd0498c53da8a86a668d8f09f551e60cf7d88ee15e`.

- [ ] **Step 5: Complete hardware/media checks before merge**

Verify MKV and AVI FPS/bitrate display; 23.976/25/29.97 frame-rate matching; a large Matroska header; profile 7 with block-additional and in-band RPU; unsupported ABI/no-RPU/encrypted DV7 fail-open playback; forced HEVC recovery; embedded Matroska subtitles after converter activation; time remaining; forward/rewind hold steering and release cleanup; PiP/lock transitions; local report Copy/Share with redacted token and credentials; stats/room fading plus independent skip/speed visibility; and ordinary playback with Watch Together inactive.

- [ ] **Step 6: Route any finding back to its owning task**

Add a failing regression first, correct only files listed in Task 1-6, rerun that task's focused command, and commit with its explicit file list. Do not create a catch-all verification commit.
