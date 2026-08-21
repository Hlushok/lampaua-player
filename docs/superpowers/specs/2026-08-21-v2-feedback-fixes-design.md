# UA Player 2.0 Feedback Fixes Design

## Scope

This change set addresses the two playback reports received for UA Player 2.0.0 and the approved settings feedback. It is prepared on a feature branch and must not publish a tag, APK, or GitHub Release.

## Playback Recovery

Stream-type fallback is only a startup format-discovery tool. A source that has already reached `STATE_READY` must retain its selected media type after a transient read failure. In particular, a direct MP4 item from a LAMPA playlist must never be rebuilt as HLS merely because its declared type is `video/*`.

An HLS `PlaylistStuckException` is different from an ordinary interrupted read. Re-preparing the same failed HLS media source retains its stuck playlist tracker, so recovery must save the current position and rebuild the player/media source. Recovery remains bounded by the existing three-attempt source budget, which resets after stable playback.

Low-latency HLS can expose a very short sliding window whose relative playback position repeatedly jumps backwards while video continues normally. The stall watchdog treats that backwards rollover as progress for live media instead of forcing a false live-edge rejoin. Every transition back to `STATE_READY` also clears the loading ring and transfer-rate label, including recovery paths after initial startup.

## Diagnostics

Diagnostic reports keep the network scheme, host, and port but redact every path, query, fragment, credential, and named secret. The separate container field provides the media-type information needed for support without exposing signed URL path tokens or titles.

## Subtitles

Online subtitle search remains opt-in, but individual third-party source switches are removed from the user interface. When search is enabled, all currently integrated sources are attempted automatically, including OpenSubtitles.com with the existing built-in public API key.

The subtitle appearance screen gains a non-interactive preview card at its bottom. It observes the same preference values used by playback and updates immediately for embedded styling, scale, text color, background, edge, and bold settings.

Subtitle timing remains per playback session and appears in the `More` tools menu only when a subtitle is active. The panel adds explicit labels explaining that negative values show subtitles earlier and positive values show them later.

## Player Controls

A `Player buttons` preference screen allows optional controls below the timeline to be hidden independently. Play/Pause and the `More` tools button remain mandatory. Dynamic buttons such as playlist, quality, subtitles, and Watch Together must respect both feature availability and the stored visibility preference.

## Verification

Add focused JVM regression tests for fallback policy, recovery policy, sliding live-window progress, diagnostics sanitization, resources, and preference contracts. Run all unit tests, lint, debug build, and release build. Create a pull request and wait for green CI, but do not merge or publish.
