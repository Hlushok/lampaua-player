# Just+ v1.4.7 donor audit

## Range

- Baseline: `9b2479e0c6ed83b468b1427207f0f4fe15ec64b8`
- Target: `f26a71e8e931ed1859a162bd3829d66419aba2b9` (`v1.4.7`)
- Commits: 35
- Changed paths: 21

## Path decisions

| Donor path | Decision | Reason | Evidence |
|---|---|---|---|
| `.github/workflows/android-build.yml` | Exclude donor hunk | Its repository-name guard would disable UA Player releases; retain UA's tag workflow. | Planned |
| `.github/workflows/android.yml` | Exclude donor deletion | UA Player needs its `main` CI workflow. | Planned |
| `README.md` | Adapt | Retain UA identity/version and document imported user-facing behavior. | Planned |
| `app/build.gradle` | Semantic merge | Import SDK/dependencies while retaining UA package/version/archive/signing/ABI rules. | `donorV147BuildStackMatches`; Media3 1.11.0 dependency graph resolved |
| `app/libs/lib-decoder-av1-release.aar` | Import byte-for-byte | Must match Media3 1.11.0. | Git blob `f6fcfe1d2a6ad393e0a139b2f2fad735fad28041` matches donor |
| `app/libs/lib-decoder-ffmpeg-release.aar` | Import byte-for-byte | Must match Media3 1.11.0. | Git blob `87b10d80f56724cde7306225e9890180c11dd04a` matches donor |
| `app/libs/lib-decoder-iamf-release.aar` | Import byte-for-byte | Must match Media3 1.11.0. | Git blob `1ca4ba4f9bdc6bf79b3956c511badd7945411fe6` matches donor |
| `app/libs/lib-exoplayer-release.aar` | Import byte-for-byte | Must match Media3 1.11.0. | Git blob `7f55e3c7def6f4672d42ebde095b97b1ce09b52a` matches donor |
| `app/src/main/java/com/brouken/player/App.java` | Exclude donor hunk | UA Player has no Sentry bootstrap; diagnostics stay local until explicitly shared. | Planned |
| `app/src/main/java/com/brouken/player/BottomBarLayout.java` | Import | Donor-owned measured-height parking. | `donorV147BottomBarAndTransferContractsStayIntegrated` passed |
| `app/src/main/java/com/brouken/player/CustomPlayerView.java` | Semantic merge | Route gesture seeks through the donor one-in-flight gate. | Planned |
| `app/src/main/java/com/brouken/player/PlayerActivity.java` | Semantic merge; donor wins | Full donor runtime behavior plus approved UA hooks only. | Bottom-bar/transfer slice compiled; focused contract passed |
| `app/src/main/java/com/brouken/player/Prefs.java` | Semantic merge; donor wins | Donor defaults/settings plus UA-only preferences. | `showTransfer` imported; focused contract passed |
| `app/src/main/java/com/brouken/player/Utils.java` | Semantic merge | Donor diagnostic capacity without Sentry transport. | Planned |
| `app/src/main/res/layout/exo_player_control_view.xml` | Semantic merge; donor wins | Donor bottom-bar container and scrim geometry plus UA resources. | Measured-height container compiled; focused contract passed |
| `app/src/main/res/values-ru/strings.xml` | Import donor additions | Keep complete resource parity. | Transfer strings imported; resources compiled |
| `app/src/main/res/values-uk/strings.xml` | Adapt donor additions | Preserve correct Ukrainian copy. | Transfer strings imported; resources compiled |
| `app/src/main/res/values/strings.xml` | Import donor additions | Default resource contract. | Transfer strings imported; resources compiled |
| `app/src/main/res/xml/root_preferences.xml` | Semantic merge; donor wins | Donor rows/order/defaults; no translation-target picker. | `showTransfer` row imported; focused contract passed |
| `build.gradle` | Import donor tool version | Android Gradle Plugin 9.4.0. | `donorV147BuildStackMatches` passed |
| `gradle/wrapper/gradle-wrapper.properties` | Import donor tool version | Gradle 9.7.1. | Gradle 9.7.1 executed successfully |

## Commit ledger

| Commit | Donor change | Accounted by |
|---|---|---|
| `aeee30e9` | Never revoke `audio/raw` from passthrough | Audio/recovery task |
| `821f224c` | Build the release candidate as well as master | Workflow exclusion |
| `b8d2958b` | Merge PR 172 | Workflow exclusion |
| `f741a677` | Draw control scrims to the bottom edge | Bottom controls task |
| `faba3896` | Merge PR 173 | Bottom controls task |
| `0e893f3a` | Merge PR 164 | Audio/recovery task |
| `84311177` | Merge master into develop | Covered by child commits |
| `8be0e7cf` | Point CI at renamed integration branch | Workflow exclusion |
| `219bd4b8` | Merge PR 175 | Workflow exclusion |
| `cd013ef5` | Park bottom bar by measured height | Bottom controls task |
| `aa16c121` | Merge PR 180 | Bottom controls task |
| `3928cba1` | Make picture and bar follow remote seek | TV seek task |
| `a897cbc9` | Merge PR 181 | TV seek task |
| `116dfe34` | Add transfer line above seek bar | Bottom controls task |
| `95c103c8` | Merge PR 182 | Bottom controls task |
| `d0a04683` | Return receiver route without stopping playback | Audio/recovery task |
| `bf6c736a` | Merge PR 183 | Audio/recovery task |
| `b6dd12f2` | Merge PR 184 | Covered by child commits |
| `befeeb3a` | Put install first and add Downloader code | README adaptation |
| `bb4c2fcb` | Merge PR 185 | README adaptation |
| `ac9073ee` | Record player actions before a failure | Diagnostics task |
| `a8a82d3e` | Keep TV playback when decoder cannot be recreated | Audio/recovery task |
| `7d01f554` | Decode surround by default; optional bitstream | Audio/recovery task |
| `cef5f9d6` | Merge PR 188 | Diagnostics/audio task |
| `685ed8e1` | Handle TV remote EXIT | TV seek task |
| `d98e9652` | Merge PR 190 | TV seek task |
| `ca4e911f` | Aim held-seek stride | TV seek task |
| `12a779e2` | Merge PR 191 | TV seek task |
| `08741d31` | Merge PR 192 | README adaptation |
| `3c60d467` | Merge PR 193 | Covered by child commits |
| `05235d1a` | Wait for picture before next held seek | TV seek task |
| `8bae9eca` | Cap seek ladder by file share | TV seek task |
| `736dbe97` | Merge PR 202 | TV seek task |
| `422026df` | Merge PR 203 | Covered by child commits |
| `f26a71e8` | Release v1.4.7 | Build/runtime and README adaptation |

## Final verification evidence

This section is filled with exact commit IDs, blob IDs, test totals, lint
counts, APK metadata, certificate, ABI list, size, hash, and ADB status after
the implementation gates pass.
