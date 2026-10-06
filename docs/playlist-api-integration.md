# Playlist API: стан інтеграції Android, 2026-10-06

Пакет: `com.lampaua.player`. Контрольна приватна перевірочна збірка
мала `2.0.4 (24)` і наведені нижче розмір та SHA-256. Після прямої команди
користувача на публікацію підготовлено `2.0.5 (25)`. Публікацію та фактичний
публічний APK слід підтвердити окремою перевіркою GitHub Actions/Release.

## Реалізовано

- Оновлено стабільне ядро й підключено вкладений `playlist` Bundle з
  пріоритетом над старим JSON-мостом. Старі формати запуску залишаються.
- Items/start_index, назва серіалу окремо від серії, IMDb/TMDB,
  сезон/серія, thumbnail/logo/background, item/root headers і segments.
- URI/qualities/voices; початкові selected, пам'ять якості за роздільністю,
  власні/спільні субтитри озвучення. Source switch утримує позицію й паузу.
- Дробові clip boundaries, позиція й duration відносно кліпу.
- Детермінований вибір аудіо та основних субтитрів: viewer, item keys,
  selected external subtitle, root keys, пам'ять озвучення, мови й selector.
  Index перевіряється на підтримку й label; ordinal — на count.
- Caller index/off блокує автоматичний пошук додаткових основних доріжок.
  Ручний пошук залишається доступним; назва серії не підмінює серіал.
- Незалежна пам'ять озвучення до 300 назв, без запису API-прогресу до
  звичайної локальної історії. Автопереклад залишається тільки українською.
- Заголовки обираються для конкретного item/source; облікові дані
  старого URI не стають глобальними для наступних елементів.
- Session journal: незаймані positions_sec = -1, visits при playback,
  переходах і повторі; quality/voice rebuild не створює зайвого visit.
  Journal зберігає останні 500 visits і переживає recreate/process restore.
  Позиції, URI/якість, озвучення й per-item choices також відновлюються.
- `com.lampaua.player.result`, setResult і PendingIntent callback;
  snapshot зі старим callback перед заміною запуску. Interval off за
  замовчуванням, позитивний мінімум 30 секунд, тільки під час playback.
- Real declared languages, labels/ordinal/count/chosen_by/voice_label;
  subtitle_index = -1 лише для off; refused request окремо від playback
  error; malformed optional keys дають bounded warnings, а не відмову.

## Перевірено локально

- `testLatestUniversalDebugUnitTest`: **67 tests, 0 failures, 0 errors**.
  Серед них 26 поведінкових тестів нового parser, track rules,
  header mapping, journal, callback та пам'яті озвучення.
- `lintLatestUniversalDebug`: **0 errors/fatal**, 338 неблокуючих warnings.
- `assembleLatestUniversalRelease`: успішно; javac debug/release успішний.
- Приватний APK: `test-builds/UA-Player-playlist-api-private.apk`.
  Розмір 38 806 766 bytes; ABI arm64-v8a/armeabi-v7a; release,
  debuggable=false; zipalign, aapt2, apkanalyzer та verifier успішні.
- SHA-256 APK:
  `81dd7eb867f57f6902f24eb0f87ccc36cba76b921585a99b018be8832a519a59`.
- Сумісний сертифікат SHA-256:
  `749d118bc8a16a7c0464b8dd0498c53da8a86a668d8f09f551e60cf7d88ee15e`;
  підписи v1/v2/v3.

## Що ще не підтверджено

ADB-пристрою немає. Ці перевірки **не підтверджують** реальне відтворення,
рендеринг субтитрів, пульт/фокус Google TV або portrait/landscape на телефоні.
Перед публікацією перевірити живу LAMPA: старт S1E5, E5→E7→E5,
quality/voice на паузі, own/shared/empty subtitles, clip, restart,
Home/Back/PiP і отримані caller snapshots без подвійного застосування.

Контракт згадує словник приблизно 900 студій та їхніх варіантів. Його немає
у відкритому source tree перевіреного тегу; assets офіційного APK також не
містять такого відкритого набору. Реалізовано основні aliases із прикладів
і generic whole-word matching зі збереженням forced/SDH/commentary/18+.
Точна еквівалентність усім aliases цього словника **не підтверджена**.
Для неї потрібне доступне джерело даних; не замінювати його довільним fuzzy
matching, яке може мовчки вибрати інше озвучення.

## Для Windows

Передати Codex файл `docs/WINDOWS_PLAYLIST_API_TASK.md` і незмінений
`docs/playlist-api-contract.md`. Windows реалізує той самий контракт через
свій Desktop/Flyleaf transport, не Android Bundle/PendingIntent і не
Android source/package. До Windows-проєкту тут змін не внесено.
