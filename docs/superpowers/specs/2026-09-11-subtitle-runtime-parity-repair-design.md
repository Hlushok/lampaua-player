# Subtitle Runtime Parity Repair Design

## Scope

Repair the three runtime defects reported after UA Player 2.0.1 while retaining
the approved UA Player product layer:

- selected primary subtitles must render, and primary plus secondary subtitles
  must remain usable together;
- manual subtitle search must start with the series title and expose the current
  Just+ title/season/episode selection flow;
- the calculated playback finish time must remain visible on phones and Google
  TV in both orientations;
- Google TV must use the television control row even if an external launch
  reports a non-TV UI mode, rather than showing an empty touch rotation tile.

The stable Just+ v1.4.7 tag at commit
`f26a71e8e931ed1859a162bd3829d66419aba2b9` is the behavioral source of truth
for subtitle renderer state, selection order, subtitle layout, and search UX.

## Diagnosis

UA Player currently disables subtitles with Media3's track-type-wide disabled
flag. That flag disables both text renderers, so selecting a primary track can
leave the selected row highlighted while neither the primary nor secondary
subtitle renderer emits cues. The current selection path also omits the donor's
explicit primary-renderer re-enable and restored main-line state.

The LAMPA playlist parser stores only per-episode titles. Applying an episode
overwrites `apiTitle`, and the manual subtitle dialog therefore starts with a
value such as `Серія 5` instead of the series name supplied by the launch
contract.

The finish label contains the calculated time, but it is rendered as a wrapping
two-line phrase inside a narrow fixed-width header column. Its trailing time is
therefore clipped on the reported phone and Google TV layouts.

Some Google TV firmware exposes the Leanback/television package features while
an externally launched activity reports a non-TV UI mode. The old detector
trusted that mode first and could therefore render the touch-device rotation
tile as an empty landscape rectangle instead of using the TV focus row.

## Design

### Subtitle renderer state

Port the complete Just+ v1.4.7 primary/secondary text-renderer state machine
into `PlayerActivity`:

- clear the legacy track-type-wide disabled flag;
- disable or enable only the primary text renderer when the main line is off or
  on;
- explicitly restore `mainLineOff` when a subtitle file or embedded track is
  selected;
- apply the donor track-selection ordering when Media3 publishes new tracks;
- restore the donor consolidated subtitle positioning so the two lines and
  controls do not leave the subtitle view in a visually blank state.

Automatic translation remains available for every supported source language,
but Ukrainian remains the only target and no target selector is reintroduced.

### LAMPA title identity and subtitle search

Extend `LampaPlaylist` with a root-level series/collection title. Parse the
existing LAMPA root `title` and explicit `series_title` aliases, serialize it in
session snapshots, and expose it without changing per-episode display titles.
The official LAMPA playlist builder must copy the launch title into that root
field.

Manual subtitle search will prefer the preserved series title, then a genuine
non-episode launch title, and never substitute a synthetic `Серія N` label. The
dialog will use the donor v1.4.7 live title search, media identity, and
season/episode catalogue flow, adapted to UA Player's navy/gold dialog and side
menu components. The current LAMPA season and episode seed the donor flow.

### Finish-time layout

Render the finish value as a compact single-line localized string, for example
`до 18:04`. Give the label a layout width that keeps the time visible and allow
ellipsis only as a last-resort safeguard. The calculated time is the important
part and must not be placed after a wrapping phrase.

### Google TV controls

Treat TV UI mode, Leanback, television hardware, and Fire TV features as
independent authoritative television signals. This keeps rotation/lock touch
controls out of the header and routes display actions through the remote-focus
row on Google TV.

## Verification and release

Add regression tests for renderer state, callback ordering, series-title
round-trip, search-title selection, and finish-time layout. Run the full unit
suite, release lint, debug and signed release builds, then validate APK metadata,
the established ARM release ABIs, 16 KiB alignment, v1/v2/v3 signatures,
signing-certificate continuity, and SHA-256.

The user has directly authorized publication. After all checks pass, release
this repair as public stable `v2.0.2` (`versionCode 22`), without replacing or
editing the existing `v2.0.1` release. ADB/real-device validation remains an
explicitly reported limitation if no device is connected.

## Boundaries

- Preserve package `com.lampaua.player`, UA Player branding, navy/gold visuals,
  LAMPA/LampaUA contracts, Watch Together, playlists, skip segments, session
  recovery, and the existing signing certificate.
- Do not modify Lampac, LampaUASkip, the Windows player, or LampaUa Desktop.
- Do not add telemetry or publish private signing material.
