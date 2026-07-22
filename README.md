# UA Player

Open-source Android video player for Lampa and LampaUA, based on [Just Player](https://github.com/moneytoo/Player).

- Android application ID: `com.lampaua.player`
- Current version: `1.6.0` (`versionCode 18`)
- Android 6.0+ and Android TV
- Media3/ExoPlayer playback engine

## Features

- HLS, DASH, RTMP, RTSP and regular HTTP media streams;
- video quality, audio track and subtitle selection;
- episode playlists with titles, thumbnails and remote-control navigation;
- automatic playback of the next episode;
- configurable intro/recap and end-credit skipping, with timeline markers and the **Пропустити** action;
- Ukrainian interface and a dark blue/yellow TV layout;
- playback position/result reporting back to Lampa;
- AV1/dav1d and FFmpeg extension decoders;
- 4K-oriented buffering, decoder fallback and dropped-frame fallback;
- bounded retry and request coalescing for deferred Lampac stream resolution;
- compatibility recovery after renderer failures and manifest detection for extensionless streams;
- independent update discovery through public GitHub Releases.

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

- JDK 17
- Android SDK 36
- Android SDK build-tools

Build a universal APK:

```bash
./gradlew :app:assembleLatestUniversalDebug
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
