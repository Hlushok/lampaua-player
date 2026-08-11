# UA Player 1.6.1: Android TV branding, focus and direct updates

## Goal

Correct the Android TV presentation and remote behavior without changing the public app version. Android TV must show UA Player branding, every control in the bottom row must have an obvious remote-control focus state, Back must close the current player surface before requiring a second press to exit, and the updater must download the APK inside UA Player rather than hand the user to a browser.

## Scope

- Keep `versionName 1.6.1` and `versionCode 19`.
- Replace the inherited Just Player Android TV banner with a dark UA Player banner: the existing round UA logo on the left and `UA Player` on the right.
- Keep the existing UA adaptive and raster launcher icons, while verifying that the manifest packages only UA-branded launcher resources.
- Apply one consistent TV focus treatment to every bottom control, including built-in Media3 controls and buttons added by UA Player.
- Restore the current Just+ TV Back contract on every supported Android version: close an active seek/controller first, then show a localized two-second exit guard.
- Revalidate the official LAMPA Android intent contract used by both `com.justplus.player` and `com.lampaua.player`, including metadata, playlist, quality, subtitles, segments and MX-compatible playback results.
- Preserve the current in-app GitHub release downloader and system package installer flow. No browser or web download page is part of the update action.
- Republish the verified APK in the existing `v1.6.1` GitHub Release after merging the correction into `main`.

## TV banner

The `LEANBACK_LAUNCHER` uses `@mipmap/banner`; the current file is the remaining Just Player asset. Replace it with a 320 x 180 dark banner using the existing UA logo, Ukrainian blue/gold accents and a large high-contrast `UA Player` label. The banner must remain legible at TV launcher distance and must not introduce a second logo style.

## Bottom-control focus

The bottom buttons currently inherit Media3's subtle focus treatment. Introduce a UA-specific state selector and animator:

- focused: dark saturated blue fill, 3 dp gold outline, full-white icon and approximately 10 percent scale-up;
- pressed: slightly brighter blue fill with the same gold outline;
- unfocused: transparent background and normal scale;
- disabled: retain Media3's disabled opacity.

Apply the treatment to the common bottom-button style so it covers quality, subtitles, aspect ratio, playback tools, app settings, update, playlist and other controls consistently. The primary play/pause focus policy remains unchanged.

## TV Back and launcher compatibility

Register `OnBackInvokedDispatcher` on Android 13+ because the application enables the modern Back callback in its manifest. Below Android 13, let the framework deliver the tracked Back key to `onBackPressed()` instead of consuming it in the TV key interceptor. In `onBackPressed()`, dismiss an armed D-pad seek or visible controller first. When video is visible and no surface remains to close, the first Back displays `Натисніть «Назад» ще раз для виходу` for two seconds and the second exits.

Keep the MX-compatible result action and extras expected by the official LAMPA Android wrapper: `com.mxtech.intent.result.VIEW`, `end_by`, `position`, `duration` and the active media URI. Add source-level contract coverage for official `video_list.*`, current/per-episode quality arrays, subtitles, segments, IMDb/card ids and result fields. The public `lampa.mx` Media Station X page cannot launch an Android external player on non-Android televisions; on Android it depends on its host wrapper exposing the same external-player intent bridge.

## Direct updater

The current `Updater` already resolves the APK asset from GitHub, downloads it with OkHttp into app cache, exposes it through `FileProvider`, and launches Android's package installer. Keep that architecture and add regression checks ensuring:

- the selected asset is a direct HTTPS `.apk` URL;
- the update action calls the internal download flow and never launches a browser intent;
- the installer receives a `content://` URI with temporary read access;
- Android's mandatory system confirmation remains visible, including its unknown-source permission flow when required.

No silent installation is attempted.

## Verification and publication

- Unit/resource-contract tests for the TV banner, focus resources, updater contract, two-press Back policy and official LAMPA intent fields.
- JDK 21 unit tests and Android lint.
- Signed non-debug universal release build.
- Verify package name, version `1.6.1`/`19`, resources, skip bytecode and signer certificate `749d118bc8a16a7c0464b8dd0498c53da8a86a668d8f09f551e60cf7d88ee15e`.
- Visually inspect the generated TV banner and launcher icon.
- Merge through a PR, move tag `v1.6.1` to the new merge commit as explicitly requested, and replace `UA-Player-1.6.1.apk` in the existing release.

Users who already installed version code 19 will not receive a second automatic update prompt; they must reinstall the corrected APK manually. Users on older versions will receive the corrected `1.6.1` normally.
