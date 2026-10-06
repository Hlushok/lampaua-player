# Завдання для Codex: повний Playlist API у UA Player Windows

Працювати тільки у `D:\opt\lampaua-player-windows`. Не змінювати Android-проєкт.
Зберегти чинний дизайн, Windows-двигун, ідентичність UA Player, запуск із LampaUA Desktop,
продовження перегляду та старі формати активації. Не публікувати реліз без окремої команди.

## Контракт і джерела

- Повний контракт: `D:\opt\lampaua-player\docs\playlist-api-contract.md` — незмінена копія
  переданого користувачем API.md, SHA-256
  `9cf1d80c59a30cdf5722627f303fa9e1f2b29a1cd777c65363657633b6b73531`.
- LAMPA PR: https://github.com/lampa-app/LAMPA/pull/82 — злитий 5 жовтня 2026,
  merge `a8e7d702b116481348ddf164e1647ae3a0454b07`.
- Android-код для порівняння семантики: `PlaylistApi.java`, `PlaylistSession.java`,
  `PlaylistTrackRules.java`, `PlaylistAudioMemory.java`, `PlaylistHeaders.java` та
  інтеграційні точки у `PlayerActivity.java`. Шляхи відносно
  `D:\opt\lampaua-player\app\src\main\java\com\brouken\player`.
- Не припускати, що опублікований Java-код тегу 2.1.1 вже реалізує описане API:
  контракт і відкриті джерела на контрольній перевірці розходилися.

Це перенесення поведінки, а не Android-механіки. Bundle/Intent/PendingIntent,
Media3/JNI та package `com.lampaua.player` у Windows не копіювати. Спочатку звірити
актуальні parser, resolver, activation/result transport та player adapter Windows.
Той самий вкладений контракт передавати через наявний безпечний Desktop/Windows
транспорт. Ідентифікатор сесії, зв'язок із Desktop та авторизацію callback зберегти.
Не замінювати транспорт вигаданим callback URL або довільним HTTP-запитом.

## Що реалізувати

1. Повний плейлист передається один раз. Типізована модель, а не кілька незв'язаних
   масивів. Кореневі `title`, `logo`, `background`, `start_index`, `headers`, `items`,
   налаштування доріжок і `report_interval_sec`; відсутність позицій не означає
   відкритий/переглянутий елемент.
2. Кожний item: назва серіалу й окремий `episode_title`, зображення,
   IMDb/TMDB, сезон/серія, власні заголовки, субтитри, сегменти, позиція,
   `uri`/`qualities` або `voices`. Назва серії не замінює назву серіалу в пошуку
   субтитрів. Використовувати наявний resolver без регресії торрентів/онлайн/IPTV.
3. Одиниці API — **секунди**. Позиції й журнал не конвертувати двічі. Усередині
   адаптера Windows дозволені мілісекунди. `clip_start_sec`/`clip_end_sec` дробові;
   seek, прогрес і duration відносні до кліпу. Некоректний кінець ігнорується.
4. Заголовки root+item об'єднуються без урахування регістру, item перемагає.
   Останній непарний ключ/пари з null пропускаються. Обирати заголовки за URI
   конкретного item/voice/quality/subtitle; невідома URI фрагмента — поточного item.
   Не переносити Authorization/User-Agent попередньої серії або сервера випадково.
5. `qualities`: перша selected, інакше explicit URI, інакше перша дійсна якість.
   Перемикання зберігає позицію, паузу, звук і субтитри та не створює новий visit.
6. `voices` — окремі потоки, а не звукові доріжки всередині одного потоку.
   Вибір: ручний цього launch → selected → пам'ять озвучення для IMDb/TMDB →
   звичка в порядку **налаштувань плеєра**, не caller audio_languages → перша.
   Пам'ять/звичка застосовуються для 2+ voices без selected. Ручне озвучення
   переноситься на наступні серії за label. Позиція, пауза й відповідна якість
   залишаються. Власний список субтитрів voice замінює спільний, навіть порожній.
7. Доріжки обираються один раз після першого дійсного списку для кожного item,
   окремо audio/text. Зберегти вибір при late HLS metadata, повторному відкритті
   item та rebuild. Для нової voice повторно обирати audio; text — лише якщо
   змінюється власний список субтитрів. Не відновлювати старі track ID іншого потоку.
8. Повний порядок: ручний launch-вибір → item index/off → selected зовнішній
   subtitle → item label → item language ordinal/count → root index/label/ordinal/off
   → per-title audio memory → caller languages → player choice/звичка.
   Порожній subtitle_languages — off; порожній audio_languages не вимикає звук.
   Item selected subtitle перемагає root off, але не item off.
9. Індекси відповідають меню й рахують unsupported tracks. Не включати вигаданий
   порожній CEA-608. Embedded subtitles перед caller external у порядку масиву.
   Валідація index+label, ordinal+count, fallback без примусового track 0.
   ISO 639-1/2T/2B і BCP47 нормалізуються; und можна вивести з label, але не
   повертати вигадану declared language. Full subtitles перед forced/SDH.
10. Label matching: студійні aliases, цілі слова, codec/channel/bitrate/language
    noise, kind-only labels. Forced/SDH/commentary/18+ відрізняються. Не робити
    нечітке fuzzy matching, яке мовчки вибере інше озвучення. Точний словник
    ~900 студій з документа не був доступний у відкритому тегу: окремо звірити
    його джерело й не називати невеликий набір aliases повною еквівалентністю.
11. Per-title audio memory тільки для не-live IMDb/TMDB, максимум 300 назв.
    Записувати ручний вибір/звичку нового title, не caller index/label/ordinal.
    Звичка обирає dub **в межах обраної мови**, не саму мову. Не додавати
    per-title subtitle memory. Автопереклад у нашому продукті — тільки на українську.
12. Caller володіє resume-позиціями API-сесії. Не записувати їх як звичайне
    локальне resume плеєра. Session journal має окреме приватне сховище;
    переживає recreate/process restart. Старий локальний launch працює як раніше.

## Звіти про прогрес

Повні ідемпотентні snapshots, прив'язані до session ID, а не дельти:

- поточні uri/index/position_sec/duration_sec;
- positions_sec на всі items: -1 не відкривався; duration переглянуто до кінця;
  не позначати всі попередні серії переглянутими при переході на п'яту;
- history: окремий visit при першому playback і кожному переході/повторі;
  quality/voice rebuild не новий visit; максимум останні 500 visits;
  index/started_at Unix seconds/position_sec/duration_sec (-1 невідомо);
  поточний верхній duration_sec має 0, коли невідомий;
- реальні declared language/label, language ordinal/count, chosen_by для
  audio/subtitle; voice_label; subtitle_index тільки -1 для off;
- subtitle, знайдений самим плеєром: label, але без вигаданих language/count/ordinal;
- end_by: completion/user/cancelled/error; error_message — людське повідомлення.
  Після успішного playback помилка очищається;
- refused request: index -1, uri null, порожні positions/history, без warnings;
  malformed optional keys — bounded warnings (20 із підсумком решти), не відмова.

Звіт при закритті, мінімізації/припиненні playback, кінці, закритті PiP, заміні
активації та періодично **тільки під час playback**. Default interval off,
позитивний мінімум 30 секунд; LAMPA наразі надсилає 120. При новій активації
спочатку snapshot старої сесії її старому callback, потім нова сесія.
На Android callback зберігає caller extras/identity; Windows має зберегти
еквівалентну кореляцію без копіювання PendingIntent.

## Acceptance

Обов'язкові тести parser + deterministic selection + session/report + player
adapter/реальне вікно. Сценарії: S1E5 старт і незаймані S1E1–E4; E5→E7→E5;
finish/repeat; unknown duration/live; shared URL з різними clip; quality на паузі;
voice на паузі; own/shared/empty voice subtitles; unsupported index; label miss;
ordinal count mismatch; explicit off/selected priority; late HLS tracks;
different per-item authorization; app restart; callback старої активації;
повторний snapshot без подвійного збереження; journal cap; помилка→retry.

Не замінювати тестуванням UI перевірку контракту й навпаки. APK/Java Android не
є Windows-збіркою. У підсумку назвати реальні Windows-тести, результат engine
перевірки, неперевірені сценарії й шлях локального тестового інсталятора.
