# UA Player Subtitle Timing and Ukrainian Auto-Translation Design

## Goal

Add the subtitle timing work merged into Just+ Player's subtitle feature branch and extend UA Player's online subtitle flow with automatic translation to Ukrainian when no original Ukrainian subtitle is available.

The reviewed upstream source is:

- Just+ subtitle branch: `just-plus-player/feature/subtitles` at `d2892550cb1467377457175975116284643c5117`.
- Subtitle timing and hot-attach work: PR #130, commits `d55277b2a4e7663ae46015ce8d593a3b0620f803` and `774b873acce77d1ec314772b76cf8c232e36f396`.

The translation pipeline is a UA Player implementation. It does not copy a translation service or source-selection policy from Just+ Player.

## Project Invariants

- Keep package id `com.lampaua.player`, version `1.6.1`, version code `19`, and the existing signing certificate.
- Keep UA Player branding, Ukrainian-first UI, LAMPA extras, playlist behavior, result contract, updater, and server skip integration.
- Do not add Just+ branding, Sentry, telemetry, remote diagnostics, or direct skip database calls.
- Do not send media URLs, titles, external ids, timestamps, room state, device data, or credentials to the translation service.
- Do not block, pause, seek, rebuild, or re-prepare video merely to translate or attach subtitles.
- Do not create a release, tag, or version change as part of this work.

## User Flow

The existing subtitle language preference remains authoritative. Auto-translation applies only when Ukrainian (`ukr`) is in the preferred subtitle languages and the new auto-translation preference is enabled.

For each media item:

1. Use an existing embedded, launcher-provided, cached, or online Ukrainian subtitle when available.
2. If no Ukrainian subtitle is available, search for a foreign text subtitle.
3. Prefer non-Ukrainian languages already present in the user's ordered subtitle preference, followed by `eng`, `rus`, and `pol`, with duplicates removed.
4. Download one usable source file, translate only its cue text to Ukrainian, and cache the generated subtitle separately from original Ukrainian files.
5. Paint the translated subtitle without rebuilding the player.

When translation is disabled, the existing ordered-language search behavior remains unchanged. Embedded bitmap subtitles and embedded text tracks are not translated because the online pipeline has no independent source document for them.

The new preference is enabled by default and explains that subtitle text is sent to an external Google translation endpoint. The target language is fixed to Ukrainian and is not configurable.

## Subtitle Timing and Hot Attach

Adapt Just+ Player's `SubtitleOffset` and `SubtitleTimeline` classes with attribution.

`SubtitleTimeline` reads a selected external subtitle through Media3's data source and parser, keeps its cues addressable by media time, and lets the UI repaint immediately after a seek. `SubtitleOffset` wraps the text renderer and applies a session-scoped offset from -30 to +30 seconds:

- external text files are painted from their timeline at `position - offset`;
- embedded text tracks use renderer clock advancement for negative offsets and bounded cue holding for positive offsets;
- pause, seek, playback speed, item changes, and player rebuilds use media time rather than wall-clock timers;
- the offset resets to zero on item changes and never persists across episodes.

Expose the offset in the existing player side menu directly below the skip controls, only while a text subtitle is active or a timeline is being painted. Use the existing offset panel interaction and UA Player styling.

An online subtitle that arrives during playback is parsed into a timeline and painted directly. The current `setMediaItems()` plus `prepare()` path remains only as a fallback when Media3 cannot parse the external file. This avoids reopening HTTP and torrent-backed media for a subtitle-only change.

## Search and Translation Policy

Split automatic search into two phases when Ukrainian auto-translation is active:

- Phase 1 asks all enabled subtitle sources for Ukrainian only.
- Phase 2 runs only after Phase 1 has a definitive empty result and asks for the foreign fallback languages.

A source timeout or transport failure is not a definitive empty result and must not be cached as "no Ukrainian subtitle." The existing source response marker and miss TTL remain the basis for that distinction.

Direct Ukrainian cache files retain their existing name and always win. Generated files use a distinct cache name containing `auto-ukr` and the normalized source language. Existing cache cleanup owns their lifetime. A translated cache hit may be reused without another translation request, but it never overwrites or masquerades as an original Ukrainian subtitle.

The downloaded foreign file is never automatically displayed. It is attached only after a complete Ukrainian output has been written atomically. A partial or failed translation leaves playback and subtitle selection unchanged.

## Translation Transport

Use the existing OkHttp dependency and the HTTPS endpoint `https://translate.googleapis.com/translate_a/single` with a form-encoded `POST` request:

- `client=gtx`;
- source language from the normalized search result;
- `tl=uk`;
- `dt=t`;
- one bounded batch in `q`.

The endpoint is unofficial and therefore isolated behind a small transport interface. Translation policy, subtitle parsing, batching, cache naming, and UI do not depend on response transport details. A future supported backend can replace the transport without changing playback code.

Requests are sequential and bounded by the subtitle worker's lifecycle and OkHttp timeouts. Each batch is limited by both cue count and UTF-8 byte count. The implementation retries transient transport failures at most once and never performs an unbounded request loop.

Only visible cue text is sent. Sequence numbers, timestamps, media identifiers, file paths, HTML/ASS formatting tokens, and diagnostic context stay local. Logs include only a coarse stage and error class, never request bodies or translated text.

## Subtitle Document Integrity

Add a pure Java SubRip document component that separates cue identifiers, timing lines, text, and formatting tokens. It accepts UTF-8 SubRip input with LF or CRLF and preserves the original cue order and timings.

Text from several cues is joined with deterministic ASCII boundary markers. A translated response is accepted only when every expected marker appears exactly once and in the original order. Formatting tags are represented by local placeholders and restored after translation. If batch validation fails, retry with smaller batches down to one cue. If an individual cue still cannot be mapped safely, fail the whole generated subtitle rather than mixing untranslated text into an output labeled Ukrainian.

Input limits protect memory and endpoint usage. Oversized, binary, malformed, or unsupported subtitle documents are not translated. WebVTT, TTML, ASS/SSA, bitmap subtitles, and embedded tracks continue through existing playback behavior; they are not silently rewritten as SubRip.

The final generated file is written to a temporary cache file, flushed, then atomically renamed to its cache name. Cancellation, item changes, parse failures, HTTP failures, malformed JSON, marker damage, or write failures remove the temporary file.

## Lifecycle and UI

The existing subtitle search generation id guards search, download, translation, and attachment. Every asynchronous callback rechecks the generation and current media identity before touching preferences or the player.

Changing media, disabling auto-translation, choosing another subtitle, turning subtitles off, or destroying the activity prevents a late translation from attaching. In-flight network work may finish within its timeout but its result is discarded.

User feedback is limited to:

- a short notice that Ukrainian translation is in progress after a foreign subtitle is downloaded;
- a success notice when the translated track is ready;
- a non-blocking failure notice when no safe translation can be produced.

The video and any currently selected subtitle continue uninterrupted while translation runs.

## Error Handling

- Direct Ukrainian subtitles always take precedence over generated subtitles.
- Network and translation failures never spend playback recovery budgets.
- Unsupported source languages or formats fail closed without attaching the foreign file.
- HTTP throttling, server errors, invalid JSON, and marker corruption use bounded failure paths.
- A cached generated file is reused only when non-empty and structurally valid.
- No translation failure is reported as a player/source failure or included in remote telemetry because no telemetry exists.

## Testing

Write tests before implementation for:

- direct Ukrainian selection winning over translation;
- fallback-language ordering and deduplication;
- translation disabled preserving the existing search policy;
- SubRip parsing with CRLF, multiline cues, formatting tags, overlapping cues, and missing trailing newline;
- deterministic batching under byte and cue limits;
- exact marker validation, out-of-order markers, duplicates, missing markers, and single-cue fallback;
- restoration of formatting placeholders;
- malformed/oversized input rejection;
- Google response parsing without logging payload text;
- generated cache naming and direct-cache precedence;
- cancellation and media-generation checks;
- source contracts for fixed target `uk`, HTTPS, form POST, bounded retries, and atomic cache writes;
- subtitle offset reset, timing direction, timeline lookup, and renderer lifecycle contracts.

After targeted tests, run the full unit suite, Android lint, latest and legacy debug/release builds, `git diff --check`, privacy scans, and APK verification. Verify package, version, certificate SHA-256, and APK SHA-256 independently for the signed preflight artifact.

Device testing remains necessary for long subtitle files, TV focus, pause/seek/speed behavior, torrent-backed playback while a subtitle arrives, and real endpoint throttling. Automated tests cannot prove vendor renderer timing or network service availability.

## Attribution and Delivery

Record Just+ Player and Oleksandr Zhyzhchenko for the adapted subtitle timing and hot-attach implementation. The Ukrainian translation pipeline is UA Player-specific and uses the existing OkHttp and JSON dependencies.

Implement on `codex/sync-justplus-2026-08` in reviewable commits. Update PR #11, wait for green CI, and stop before merge or publication. A release still requires a separate direct user command.
