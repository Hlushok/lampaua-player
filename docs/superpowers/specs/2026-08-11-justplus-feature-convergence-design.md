# UA Player — selective Just+ feature convergence

## Goal

Bring the stable playback, TV-control, skip, track, quality, and utility improvements developed in Just+ Player into UA Player without replacing UA Player's visual identity, LAMPA/LampaUA integration, GitHub update source, or server-backed skip API.

The work must also eliminate the stale-resource test-build failure that displayed `com.google.android.material.sidesheet.SideSheetBehavior` instead of `Пропустити`.

## Constraints

- Keep the Android application id `com.lampaua.player`.
- Keep UA Player's dark blue and gold interface, icons, names, and Ukrainian-first strings.
- Keep official LAMPA playlist metadata and `lampaua.playlist_json` compatibility.
- Keep `https://kinohub.uk/lite/lampauaskip/segments` as the online skip lookup used by UA Player.
- Do not add Just+ branding, Sentry/crash telemetry, Just+'s multi-database online skip finder, or manual skip-offset controls.
- Do not add Watch Together in this project phase. It will be designed and implemented separately.
- Do not publish a GitHub Release while implementing or testing this scope.
- Do not increase the public version until all batches pass validation and the user explicitly requests a release.

## Integration strategy

The Just+ branch is 231 commits ahead of the shared base while UA Player has 20 independent commits and substantial LAMPA-specific code. A wholesale merge or rebase would overwrite UA Player behavior and produce difficult-to-review conflicts.

The implementation will therefore port behavior selectively in independently testable batches. Reference commits from Just+ may guide the implementation, but UA Player code will preserve its own component boundaries, resource names, styling, and server integrations.

## Batch 1: deterministic builds and playback foundation

### Resource correctness

The faulty test APK contains `R.string.skip_action` as resource id `0x7f110165`, while the incrementally compiled `PlayerActivity` inlines the previous id `0x7f110164`. That previous id now resolves to Material's non-translatable `side_sheet_behavior` string. This proves the label corruption is caused by stale incremental compilation rather than a bad translation.

All distributable and test APKs must be produced from a clean resource and Java compilation. The build verification must inspect the packaged resource table and reject an APK unless:

- `string/skip_action` resolves to `Пропустити` for Ukrainian;
- the compiled player does not reference `string/side_sheet_behavior` for the skip action;
- `apksigner verify` succeeds;
- the signing certificate matches the currently installed/public UA Player certificate when an update-compatible APK is requested.

### Playback resilience

Port the stable fixes that:

- distinguish initial-load timeouts from stalls during playback;
- retry recoverable network, decoder, and unexpected playback failures once with bounded fallback state;
- recover TV playback from a stalled hardware decoder;
- preserve or rebuild passthrough audio after pause, resume, seek, and audio-output replacement;
- reject truncated local media as an unplayable file instead of treating it as an application crash;
- ignore phantom HLS closed-caption tracks;
- preserve the Dolby Vision Profile 7 to HDR-compatible fallback already exposed by UA Player;
- handle HLS/DASH streams whose URL does not carry a normal extension;
- keep position, playlist identity, and per-episode subtitles attached to the correct playlist item.

Fallbacks must be bounded to prevent retry loops. UA Player must retain its existing lower-quality fallback for streams a device cannot decode smoothly.

## Batch 2: TV controls and local player utilities

Add or improve:

- tapping the time bar to seek;
- accelerated D-pad seeking with one committed seek after a burst or hold;
- Down to focus/open controls at the time bar and Up to dismiss controls;
- reliable Back handling on TV boxes below Android 13;
- visible focus and functional OK selection for episode, quality, audio, subtitle, speed, and settings panels;
- hold video for temporary 2× playback, restoring the previous speed on release;
- screen-control locking with an explicit swipe-to-unlock path;
- ten resize modes: Fit, Crop, Fill, 16:9, 4:3, 16:10, 2:1, 2.35:1, 2.39:1, and 5:4;
- optional volume and brightness gestures;
- optional player-only volume that does not alter system volume;
- loudness boost up to 200%, with a compatible fallback when the platform audio-effects path is unavailable;
- a sleep timer with 15/30/45/60/90-minute presets, end-of-file mode, custom time, and a 30-second fade-out;
- transfer-rate feedback while buffering;
- an on-demand playback-statistics panel showing the active container, resolution, codec, fps, bitrate, buffer, decoder, and audio format.

All new panels must reuse UA Player's navy, blue, gold, focus-border, typography, and transparency settings. Phone touch behavior and Android TV remote behavior must be tested independently.

## Batch 3: tracks, quality, playlist, and update experience

### Tracks and playlist

- Preserve side-panel presentation for quality, audio, subtitles, speed, and episodes.
- Hide controls that have no meaningful choices.
- Build readable track labels from embedded media metadata when available.
- Support an ordered list of preferred audio languages, using the first language actually present in the media.
- Allow promoting a language from the audio panel without leaving playback.
- Keep quality names consistent between the header and the quality panel.
- Keep per-episode poster, title, resume position, subtitle list, quality variants, and skip segments when switching episodes.

### Update dialog

UA Player continues to query stable releases only from `Hlushok/lampaua-player` on GitHub.

When a newer stable release exists, show a UA-styled dialog containing:

- title `Оновлення UA Player`;
- current version and available version in the form `1.6.0 → 1.7.0`;
- the GitHub Release body rendered as a limited, safe Markdown subset with headings, bullet lists, bold text, inline code, and clickable HTTPS links;
- `Пропустити цю версію`, which suppresses only that release;
- `Пізніше`, which closes the dialog but leaves the update indicator available;
- `Оновити`, which downloads the matching universal APK and launches the Android package installer;
- download progress and a cancel action;
- a visible update icon in player controls while an unskipped update remains pending.

Test builds, drafts, and prereleases must not trigger the stable-update prompt. The updater must tolerate missing release notes, missing APK assets, network errors, cancellation, and installer rejection without interrupting playback.

## Batch 4: skip behavior

Keep the existing priority of segments supplied by LAMPA/LampaUA and the UA server API. Add:

- modes for a five-second Skip button, a button visible for the entire segment, and automatic skipping;
- separate preferences for intros/recaps and end credits;
- the ability to cancel a pending automatic skip;
- an Undo action after an executed skip;
- validation that rejects negative, reversed, zero-length, episode-spanning, or otherwise implausible segments;
- the existing blue segment markers for every accepted intro, recap, advertising, preview, outro, and credits interval;
- automatic next-episode behavior only when an accepted end segment reaches the media boundary and a next playlist item exists.

No manual timing-offset control and no direct client requests to Just+'s external skip databases are included.

## GitHub Actions and build environment

Modernize both workflows:

- use `actions/checkout@v6`;
- use `actions/setup-java@v5`, whose internal runtime is Node.js 24;
- build UA Player with Temurin JDK 21;
- run pull-request and branch CI against `main`, not the obsolete `master` branch;
- use a clean build for signed/tagged artifacts;
- retain secret-based APK signing without exposing key material;
- upload test artifacts for CI validation without creating a Release;
- create a GitHub Release only from the existing explicit version-tag workflow and only when separately requested.

Node.js is not added to the Android application and does not become an application runtime dependency.

## Validation

Each batch must pass before the next batch is merged:

1. Java compilation, Android resource processing, lint, and available unit tests.
2. Clean universal debug APK build.
3. Packaged-resource inspection for Ukrainian labels, especially `skip_action`.
4. APK signature and package metadata verification.
5. Phone checks for touch, gestures, background/foreground, PiP, and installer flow.
6. Xiaomi TV Box S (3rd Gen) checks for focus, OK, Back, D-pad seeking, panels, 4K fallback, passthrough, skip, and episode switching.
7. Regression checks using official LAMPA metadata and UA Player's legacy `lampaua.playlist_json` metadata.
8. Network failure checks for media playback, skip lookup, and update lookup/download.

## Delivery

Implementation is split into reviewable commits and pull requests by batch. Test APKs may be shared locally or as CI artifacts, but no public release, release tag, or version announcement is created until the user approves the final tested build.
