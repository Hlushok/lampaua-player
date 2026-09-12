# Player core update policy

UA Player uses the newest stable player-core release as the complete source of
truth for playback, subtitle rendering and search, settings behavior, focus,
Back handling, orientation, audio, decoding, buffering, recovery and layouts.

Future updates must import the complete stable core rather than copy isolated
fixes. Only these UA Player boundaries are reconciled afterward:

- package, name, icons and navy/blue/gold presentation;
- LAMPA and LampaUA launch, playlist and result contracts;
- Ukrainian as the only automatic-translation target;
- UA updater destination, release version and signing compatibility;
- product-owned deep links and network policy.

Do not preserve a second implementation of a feature already owned by the
stable core. Validate the full source range, both screen orientations, phone and
TV navigation, external subtitles, playlist metadata, unit tests, lint, release
build, APK alignment, package metadata, ABIs and signatures before publication.

Commit and release messages describe the resulting core update and fixes in
neutral product terms. Detailed source-comparison notes are not published.
