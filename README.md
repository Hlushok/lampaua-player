# UA Player

Open-source Android video player for Lampa and LampaUA, based on [Just Player](https://github.com/moneytoo/Player).

- Android application ID: `com.lampaua.player`
- Current version: `2.0.1` (`versionCode 21`)
- Android 6.0+ and Android TV
- Media3/ExoPlayer playback engine

## Install

Download the current universal APK from the public
[UA Player releases](https://github.com/Hlushok/lampaua-player/releases/latest) and open it on a
phone, tablet or TV box. Existing installs update in place when the APK is signed with the same
UA Player certificate.

## Features

- HLS, DASH, RTMP, RTSP and regular HTTP media streams, with IPTV/live-window recovery;
- torrent-aware buffering with patient connected-load windows and in-place retry for transient network reads;
- video quality, audio track and subtitle selection;
- ordered preferred audio and subtitle languages, in-player subtitle styling and opt-in online subtitle search;
- subtitle timing from -30 to +30 seconds and interruption-free attachment of newly found text subtitles;
- optional Ukrainian-only auto-translation when no original Ukrainian SubRip subtitle is available; only cue text is sent to an unofficial external Google translation endpoint;
- richer MKV, AVI and MP4 track, frame-rate and bitrate metadata;
- episode playlists with titles, thumbnails and remote-control navigation;
- automatic playback of the next episode;
- configurable intro/recap and end-credit skipping, with timeline markers, cancellable automatic skipping and a short undo action;
- LAMPA-compatible Watch Together rooms with passwords, optional public listing and locally generated QR invites;
- Ukrainian interface and a dark blue/yellow TV layout;
- playback position/result reporting back to Lampa;
- AV1/dav1d and FFmpeg extension decoders;
- 4K-oriented buffering, decoder fallback and dropped-frame fallback;
- bounded retry and request coalescing for deferred Lampac stream resolution;
- compatibility recovery after renderer failures, Dolby Vision profile 7 conversion/fallback and manifest detection for extensionless streams;
- TV-focused D-pad navigation, picture-synchronised accelerated seeking, EXIT handling and input locking;
- fit, crop, stretch and explicit aspect-ratio viewing modes;
- held-touch speed steering, configurable remaining-time display and persisted playback speed;
- sleep timer, an optional compact transfer line, live statistics and local sanitized playback reports;
- decoded surround sound by default, with optional Dolby/DTS pass-through for compatible receivers;
- bounded recovery that keeps an already working TV decoder alive across long pauses and stalled network ranges;
- optional player-volume boost up to 200% with safe passthrough fallback;
- independent update discovery with formatted notes from public GitHub Releases.

## LAMPA integration

UA Player supports the public extended external-player contract introduced in LAMPA 1.12.6:

- `video_list`
- `video_list.name`
- `video_list.filename`
- `video_list.thumbnail`
- `video_list.segments`
- `video_list.season`
- `video_list.episode`
- `video_list.imdb_id`
- `video_list.tmdb_id`
- `video_list.id`
- `video_list.subtitles`
- `quality_levels` / `quality_urls`
- `video_list.quality_levels.$index` / `video_list.quality_urls.$index`

It also remains compatible with the LampaUA JSON bridge:

- `lampaua.playlist_json`
- `lampaua.playlist_index`
- `lampaua.auto_next`
- `lampaua.playback_results`

The JSON playlist can contain direct `url` values or short-lived `resolver_url` values, headers, quality variants, subtitles, identifiers and skip segments. Ready segments supplied by Lampa or a balancer take priority.

## Example playlist

```json
{
  "current_index": 0,
  "auto_next": true,
  "items": [
    {
      "title": "Серія 1",
      "url": "https://example.test/s01e01.m3u8",
      "thumbnail": "https://example.test/s01e01.jpg",
      "imdb_id": "tt1234567",
      "season": 1,
      "episode": 1,
      "headers": { "Referer": "https://example.test/" },
      "subtitles": [
        {
          "url": "https://example.test/s01e01-uk.vtt",
          "label": "Українська",
          "language": "uk"
        }
      ]
    }
  ]
}
```

## Build

Requirements:

- JDK 21
- Android SDK 37
- Android SDK build-tools

Build a universal APK:

```bash
./gradlew :app:assembleLatestUniversalDebug
```

On Windows, create a clean test APK and validate its resources and signature:

```powershell
.\scripts\build_test_apk.ps1
```

Build the unsigned release APK:

```bash
./gradlew :app:assembleLatestUniversalRelease
```

Release signing keys are not stored in this repository.

## Project boundaries

This repository contains only the UA Player Android application. Lampac modules, server-side skip-source aggregation, production configuration and signing keys are maintained separately and are not required to inspect or build the player.

## Credits and license

UA Player is derived from [moneytoo/Player](https://github.com/moneytoo/Player). The project retains the upstream [Unlicense](LICENSE). Third-party AndroidX Media/decoder components keep their respective licenses.

The player core is synchronized through stable Just+ `v1.4.7` after reviewing [just-plus-player/just-plus-player](https://github.com/just-plus-player/just-plus-player) by Oleksandr Zhyzhchenko. UA Player retains its own identity and LAMPA/LampaUA integration layer, and does not include Just+ branding, Sentry or remote diagnostic uploads. Anonymous room aliases are adapted from [LocalSend](https://github.com/localsend/localsend) under Apache-2.0; attribution is retained in the source.
