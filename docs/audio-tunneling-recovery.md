# Tunneled PCM recovery — local verification

The Xiaomi MiTV-MOOR2 report ended in `ERROR_CODE_AUDIO_TRACK_INIT_FAILED`:
E-AC3 passthrough stalled after a late seek; software decoding then produced
six-channel PCM at 48 kHz, whose tunneled AudioTrack could not open. This does
not establish that the file is corrupt or that the device cannot play E-AC3.

Report: https://termbin.com/vbyvk (UA Player 2.0.6/26, Android 14/API 34).
The earlier read-only diagnosis found this sequence:

1. E-AC3 bitstream audio was playing with tunneling enabled.
2. A seek from about 51:39 to 53:37, near the episode end, was followed by
   ten seconds without progress and `ERROR_CODE_TIMEOUT`.
3. Recovery revoked passthrough for that codec in the session and selected
   FFmpeg decoding. The sink then refused six-channel PCM at 48 kHz:
   `AudioTrack init failed 0 Config(48000, 252, 2, 288000)`.
4. A source/container retry did not repair the audio output; the final result
   was `ERROR_CODE_AUDIO_TRACK_INIT_FAILED` / `Cannot create AudioTrack`.

The existing `recoverByDisablingTunneling()` is start-only. That guard remains
appropriate for an unexplained frozen-video timeout; the new, separate audio
fallback handles confirmed tunneled PCM initialization failures at any position.
This does not prove why the first stall occurred. Disabling tunneling manually
and reopening the same episode/track remains the comparison test; another audio
track helps distinguish a track-specific failure. The earlier diagnosis made
no changes; the present patch is the separately authorized implementation.

## Changes

- Determine stereo downmix eligibility from the speaker layout, not the maximum
  channel count advertised for compressed bitstreams. Before API 33 use the
  conservative advertised limit, because the layout may be a stereo placeholder.
- Downmix decoded 3–8-channel PCM only for a stereo route with tunneling active.
  Keep sample rate/frame count, centre dialogue, left/right placement, and PCM
  encoding; clip mixed peaks instead of integer overflow. Compressed passthrough
  and multichannel routes bypass this processor.
- Switch mixing matrices only on flush, so old-layout PCM may drain safely when
  a decoder announces a different channel count.
- On a tunneled PCM AudioTrack initialization failure, retry once without
  tunneling for that item, preserving position, pause state, selected tracks and
  existing recovery budgets. Keep the user preference unchanged. Retain the
  guard across rebuild/recreation; reset it for a new item or launch.
- Keep UA presentation, package, signature and Playlist API unchanged.

## Evidence and scope

The public 2.4.5 release APK was inspected, not installed or executed. Its SHA-256
matches GitHub's digest:
`b0743a8b24983d0c69b35579a11669a7a61ac2f6b83cfd9ea2b5ee5860d8af12`.
The targeted audio classes show the same two-stage strategy. The APK is
obfuscated and full decompilation reports errors; this is not recovery of its
complete original source tree.

The public repository still carries Unlicense; the APK contains dependency
notices but no separate application licence file was found in its archive.
That alone is not confirmation of licence coverage for newly unpublished code.
No generated application classes or new native binaries are included in UA
Player. The patch is written against our existing open source base and the
already-bundled Media3 audio mixing API, without replacing the core libraries.
Inspection material remains under ignored `test-builds/audio-audit-2.4.5/`.

## Real-device acceptance still required

On the reporting Xiaomi, use the same E-AC3 track and output route with tunneling
enabled; repeat the late seek/credits skip. Capture the resulting log. Look for
the speaker-layout diagnostic, stereo downmix when eligible, or one item-local
non-tunneled retry if AudioTrack is refused. Check pause/seek, a track changing
between stereo and surround, next episode, and that the global tunneling switch
has not changed. Verify a multichannel HDMI receiver separately: it must not be
downmixed just because compressed-codec and PCM capability limits differ.

Unit tests and a signed APK do not establish that playback on this TV is fixed.
The patch was initially local only. After explicit publication approval it is
prepared as UA Player 2.0.7, build 27; publication still requires a successful
release workflow and independent verification of its public APK.
