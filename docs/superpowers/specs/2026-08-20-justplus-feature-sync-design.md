# UA Player Just+ Feature Sync Design

## Goal

Bring the technically applicable playback fixes and user-facing features from the current Just+ Player into UA Player while preserving UA Player's identity, LAMPA integration, signing compatibility, update source, and server-backed skip contract.

The sync is pinned to these reviewed source snapshots:

- UA Player base: `origin/main` at `f49c2fc6a945e052a581a21d1d81709eb80fd331`.
- Just+ stable base: `just-plus-player/master` at `d37c8b7634bf45afebe5d8107f2cda37fd2c61d8` (`v1.2.0`).
- Just+ subtitle feature: `just-plus-player/feature/subtitles` at `50785416692e92d4e79985cbc61f1362e8928120`.

Later upstream work is not silently added. It requires a fresh review against this design.

## Project Invariants

- Keep application id `com.lampaua.player` and preserve compatibility with the existing signing certificate.
- Keep UA Player names, icons, Android TV banner, navy/blue/gold visual language, and Ukrainian-first text.
- Preserve official LAMPA `video_list.*` extras, per-episode metadata, return-result behavior, and legacy `lampaua.playlist_json` support.
- Preserve the UA server skip endpoint and launcher-provided segments. Do not add direct client calls to Just+ skip databases or a manual skip offset.
- Keep the UA GitHub updater pointed only at `Hlushok/lampaua-player` stable releases.
- Do not add Just+ branding, Sentry, automatic crash telemetry, or an automatic log-upload service.
- Do not change the public version, create a release tag, or publish an APK release as part of this work.
- Preserve existing tests, build verification scripts, and release-signature checks.

## Integration Strategy

UA Player and Just+ share a Moneytoo Player ancestor but implement their newer behavior in independent commits. A merge, rebase, or wholesale file replacement would remove UA-specific playlist, resolver, update, styling, recovery, and test code.

The implementation therefore ports behavior in four isolated batches. Small upstream classes that already have a clean boundary may be adapted directly with attribution. Changes embedded in Just+'s large `PlayerActivity` are reimplemented against UA Player's existing helper classes and state machines.

## Batch 1: Playback Stability, Live Streams, and Lock UI

### Torrent and slow-source watchdog

The current UA watchdog treats 30 seconds in `STATE_BUFFERING` as failure regardless of whether bytes continue to arrive. On a torrent-backed source, a large range can remain buffering while the backend downloads pieces or seeks to a distant container index. UA then spends its source-retry budget rebuilding a stream that is still alive.

Add a pure Java `LoadWatchdogPolicy` with these inputs:

- whether the player is still buffering;
- whether media has ever reached `STATE_READY`;
- bytes received at the start and end of the 30-second window;
- whether the item is local, ordinary network media, or live media.

The policy returns `REARM`, `REPORT_INITIAL_TIMEOUT`, `REPORT_MIDSTREAM_STALL`, or `IGNORE`. A network load is considered alive when at least 256 KiB arrived in the window. An alive load rearms the watchdog without releasing or preparing the player. A silent source is stopped and left retryable from the existing Play action; it is not repeatedly rebuilt by the watchdog.

`TrackNameParsingDataSource.bytesRead` remains the byte counter. The existing 15-second connect timeout and 30-second HTTP read timeout remain. Source-retry counters reset after stable `STATE_READY` playback so unrelated interruptions across a long film do not accumulate into a later terminal failure.

### Live/IPTV behavior

Just+ does not contain an M3U/EPG browser. Its IPTV behavior is HLS/live playback resilience, which matches UA Player's role as an external player.

For `player.isCurrentMediaItemLive()` failures:

- rejoin the current live window with `seekToDefaultPosition()` and `prepare()` before rebuilding the whole player;
- keep a separate budget of two live rejoins;
- restore that budget after 60 seconds without a live stall;
- distinguish an initial load, a midstream network wait, a frozen playback clock, and an Android surface-detach timeout;
- never revoke a device-wide audio mime merely because a live source stopped sending bytes.

Existing extensionless HLS detection, resolver control responses, lower-quality fallback, and LAMPA headers remain authoritative.

### Lock overlay

Keep the UA-styled `SwipeToUnlockView`, but add a dedicated 3,000 ms visibility timeout. Showing or tapping the locked screen restarts the timeout. Beginning a swipe cancels it, and releasing an incomplete swipe rearms it. Unlocking, entering PiP, changing media, ending playback, or clearing the lock removes all pending callbacks.

### Related stable fixes

This batch also ports applicable fixes for:

- backward swipe seeking committing once per landed gesture;
- TV settings focus crossing non-focusable preference rows;
- play/pause retaining TV focus after a media start;
- the time-bar scrubber rendering above skip segments;
- recents cleanup when a finished episode closes the player;
- auto frame-rate startup continuing when Android never reports a display change;
- using known Media3/container frame rate before opening a second extractor.

## Batch 2: Watch Together

### Boundary

Add `com.brouken.player.together` as an isolated subsystem. Its pure protocol and synchronization classes do not import Android UI or Media3. `TogetherManager` is the only bridge between the subsystem and `PlayerActivity`.

The port includes:

- room creation and joining by code or invite;
- optional passwords and public room listing;
- the rule that a publicly listed room must have a password;
- play, pause, seek, rate, buffering, join, and leave synchronization;
- drift correction by small temporary speed changes, with bounded hard seeks;
- pause-for-newcomer buffering behavior;
- room continuity across playlist episode boundaries;
- invite sharing on phones and locally generated QR codes on TV;
- configurable nickname, relay URL, invite page, password, and listing preference;
- a room badge and activity messages that follow the player chrome visibility.

The default protocol remains compatible with LAMPA `lparty`:

- relay: `wss://itty.ws/c/`;
- invite page: `https://siaivo.isroot.in/lparty/`.

The settings UI states that room state crosses the configured public relay. No room is listed publicly by default. Room codes, passwords, and playback state are never sent to telemetry because no telemetry is added.

OkHttp already supplies WebSocket support. ZXing Core `3.5.3` is the only new runtime dependency and generates QR codes locally.

### Lifecycle

The room lives for the activity session rather than an individual ExoPlayer instance. Player rebuilds for quality, audio, resolver, or decoder recovery retain the room. Backgrounding suspends local sampling without broadcasting an artificial pause. Finishing the activity or explicitly leaving closes room and lobby sockets.

## Batch 3: Subtitle Preferences and TV Settings

Replace the audio-only language dialog with a shared `LanguagePriorityDialog`. Audio behavior remains unchanged; subtitles gain their own ordered fallback list.

A fresh installation seeds the subtitle language once from Android captioning preferences. After that, the UA Player preference is authoritative. Existing users retain their current effective language on first migration.

Add in-app subtitle appearance controls for:

- scale;
- text color;
- background color;
- edge type;
- bold text;
- whether embedded ASS/SSA styling is applied.

Apply these settings through Media3 `SubtitleView`. Prevent choosing identical opaque text and background colors. Keep external subtitles, LAMPA per-episode subtitle bundles, track labels, and automatic track selection intact.

A long press on the subtitle button opens UA Player settings directly at the subtitle section. Deep-linked settings request focus on the opened row and remain navigable with a TV remote. The system captioning shortcut is hidden when unavailable and is no longer the only configuration path.

## Batch 4: Applicable Post-Sync Improvements

Port these remaining behaviors where they do not conflict with UA invariants:

- reliable MKV/AVI frame-rate and bitrate extraction;
- buffered container-header parsing keyed by media URI, avoiding parser teardown races;
- time-remaining display in the bottom bar;
- held-finger speed steering and restoration of the user's selected speed;
- Dolby Vision profile 7 to single-layer profile 8.1 conversion when the device and native converter support it, with the existing HEVC/HDR fallback for every unsupported or failed case;
- a local full-screen playback diagnostic view with sanitized URLs, on-screen report, Copy, and Android Share;
- fading the statistics and room badge with player controls.

Remote diagnostic upload is excluded. The upstream implementation uses a raw socket to a public paste service and an external QR endpoint; UA Player will not send logs, device details, media URLs, tokens, or room data to those services. Query strings and credentials are stripped from displayed and shared diagnostics.

Changes tied only to Just+'s direct Aniskip/multi-database finder, manual skip offset, Sentry, branding, release automation, or updater are excluded. UA's `skipSilence` preference is retained unless a UA-specific reproducible defect is found.

## Error Handling

- Every recovery path has an item-scoped budget and a stable-playback reset condition.
- A resolver `not ready` response stays distinct from a dead socket, decoder failure, and live-window stall.
- Local corrupt/truncated files fail without network retries.
- Watch Together connection loss retries with bounded backoff and leaves ordinary playback untouched.
- Invalid relay or invite URLs fall back to documented defaults.
- Subtitle preference parsing falls back to safe defaults rather than failing playback or Settings.
- DV7 conversion is opt-in through the existing compatibility setting and always preserves the existing HEVC fallback.

## Testing

Add pure JUnit tests before implementation for:

- load-watchdog byte-progress decisions and retry-budget resets;
- live recovery budget and action selection;
- room code/invite encoding, relay URL validation, lparty frame encoding, and synchronization drift decisions;
- room continuity across an episode identity change;
- subtitle-language migration, ordered selection, and color-pair validation;
- frame-rate matching calculations for 23.976, 24, 25, 29.97, 50, 59.94, and 60 Hz;
- resource and manifest contracts for UA branding, package id, updater source, lock timeout, Watch Together, and subtitle settings.

After each batch run targeted unit tests and Java compilation. Before a PR run the full unit-test suite, Android lint, debug build, release build without publishing, `git diff --check`, and the repository APK verifier. If a signed APK is produced, verify package, version, certificate SHA-256, and APK SHA-256 independently.

Device-level verification remains necessary for torrent-backed buffering, HLS live-window recovery, TV D-pad focus, display-mode changes, QR display, subtitle appearance, and two-device Watch Together synchronization. Automated tests prove state transitions but cannot prove vendor decoder, panel, remote, or relay behavior.

## Attribution

The implementation records Just+ Player and Oleksandr Zhyzhchenko as the source of the adapted functionality in the project documentation and relevant source comments. The repositories use the Unlicense. The adapted alias word list retains its LocalSend Apache-2.0 attribution, and ZXing remains covered by its own dependency license.

## Delivery

Work is performed on `codex/sync-justplus-2026-08` in reviewable commits, one independently testable batch at a time. A pull request is opened only after local verification. CI must be green before merge. No release, tag, or public APK is created without a separate direct user request.
