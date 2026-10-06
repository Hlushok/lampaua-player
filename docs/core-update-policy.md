# Player core update policy

UA Player uses the newest stable player-core release as the complete source of
truth for playback, subtitle rendering and search, settings behavior, focus,
Back handling, orientation, audio, decoding, buffering and recovery. Product
presentation is maintained separately; importing core layouts does not make
their launcher, visual identity or developer information the UA Player UI.

Future updates must import the complete stable core rather than copy isolated
fixes. Only these UA Player boundaries are reconciled afterward:

- package, name, icons and navy/blue/gold presentation;
- UA home page and its four actions; keep new browsing destinations behind it;
- dark appearance and Super AMOLED enabled by default; retain saved appearance
  choices on update, and keep the home button's visible color flow independent
  of its finite three-cycle scale pulse;
- primary author and source repository shown in About belong to UA Player;
- LAMPA and LampaUA launch, playlist and result contracts;
- Ukrainian as the only automatic-translation target;
- UA updater destination, release version and signing compatibility;
- product-owned deep links and network policy.

Do not preserve a second implementation of a feature already owned by the
stable core. Validate the full source range, both screen orientations, phone and
TV navigation, external subtitles, playlist metadata, unit tests, lint, release
build, APK alignment, package metadata, ABIs and signatures before publication.
Keep LICENSE/NOTICE and required third-party attribution intact. Verify both
launcher entry points, About identity, transparent logo and the home layout
after every core update; package/name checks alone are not sufficient.

Commit and release messages describe the resulting core update and fixes in
neutral product terms. Detailed source-comparison notes are not published.

Fetch source updates with `--no-tags` into private core refs. Product release
tags belong only to UA Player release commits; never publish imported source
tags or use `git push --tags` for a product release.
