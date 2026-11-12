# Architecture

## Build

No Gradle and no runtime libraries. `build.sh` runs `aapt2` (resources + assets) → `javac
--release 11` against `android.jar` (API 34) → `d8` (min API 24) → `zipalign` → `apksigner` (local debug key, or the private release key via
`RELEASE_KEYSTORE`).
Fonts (Inter, OFL) and the Presentation web app are plain assets; the QR encoder is vendored
source (Nayuki, MIT) under `src/io/nayuki/qrcodegen`.

## Code map

| Path | Responsibility |
|---|---|
| `MainActivity.java` | Home screen: grid/dock, focus & move, launch zoom, carousel (Continue Watching + news + recommendations), status row, Liquid Glass, Control Center (incl. Memory/Clean), profile switcher, idle mode, menus |
| `I18n.java`, `RussianTranslations.java` | English/Russian UI, dates, templates and symbolic keys; one Russian dictionary |
| `SetupActivity.java` | First-time setup pages (Apple-style, one question per page) |
| `SettingsActivity.java` | All settings, tvOS list layout |
| `Suggestions.java` | JustWatch / TMDB / Stremio (Cinemeta) recommendations: fetch, cache, artwork, deep links |
| `Search.java` | Search data: JustWatch title search (every service in India) and Cinemeta, matching each service to its installed app, Open / Get, recent searches |
| `SearchPanel.java` | The search screen: one-row keyboard (Russian, English, digits, voice), Apps & Settings and Movies & TV rows, the How to Watch sheet |
| `MediaWatchService.java` | Notification listener → media sessions → Continue Watching store; DND access |
| `Profiles.java` | Profiles (max 3), unique emoji avatars, per-profile storage names |
| `SleepReceiver.java` | Sleep via device admin (or PowerService); sleep timer alarm |
| `Inputs.java` | TV inputs from the TV input framework; switching via the passthrough channel URI |
| `News.java` | News for the top shelf: Indian newsrooms' RSS (India, local city, world), images, freshness |
| `Perms.java` | On-screen permission and default-home requests, with adb only as a last resort |
| `Weather.java` | Forecast from Open-Meteo, MET Norway or wttr.in (with fallback) in one format, IP location / city search, cache, refresh schedule |
| `WeatherView.java`, `WeatherIcons.java` | Weather overview card and forecast panel (drawn in code), multicolour weather icons |
| `Clicks.java` | Navigation sounds (Android TV system sound effects at 60%) |
| `PowerService.java` | Accessibility service the user turns on: sleep (Power Off / Sleep Timer) where device admin is unavailable; Force stop for Clean |
| `AirPlayGuard.java` | Keeps the AirPlay receiver app visible: mDNS check, restart via its settings / start-up receivers |
| `Glyphs.java` | Line glyphs drawn in code (Control Center, Presentation tile, inputs, refresh) |
| `Ui.java` | Resolution-based sizing (960×540 design grid), fonts |
| `Scenes.java`, `SceneHome.java` | Scene rendering and dock; worker preparation of current/next scene, UI draws ready layers |
| `CustomMedia.java`, `WeightedLruCache.java` | Custom artwork and byte-budgeted LRU; evictions release references without recycling borrowed images |
| `LatestTask.java` | One active/one pending preparation, generation checks and disposal of unpublished obsolete results |
| `HomeDashboard.java`, `RequestGroup.java` | Dashboard data; shared requests per signature, bounded workers, failure backoff |
| `VolumePollPolicy.java` | Event-driven idle volume handling and slower fallback polling, faster checks only during a hold |
| `present/Presenter.java` | Presentation entry point: settings, server lifecycle, player bridge, boot start |
| `present/AdminServer.java` | HTTP server (port 8080), API, streaming multipart uploads |
| `present/Repo.java`, `Model.java`, `Pairing.java` | SQLite storage, slide model & validation, pairing tokens |
| `present/PresentationActivity.java` | Full-screen player (images, MediaPlayer video, WebView with auto-scroll) |
| `present/PresentIcon.java` | The Presentation tile icon (drawn in code; `assets/present/icon.svg` is the same design) |
| `present/LegacyCss.java`, `Qr.java`, `Net.java` | `@layer` unwrapping for old WebViews, QR bitmaps, LAN URLs |
| `assets/present/` | Browser admin app (`index.html`, `app.css`, `app.js`) |

## Design decisions

- **Measure Apple first.** Geometry comes from Apple's tvOS 26 press screenshot scaled to
  1920×1080 (dock 48 px from the edges, 248×148 px cards, 51 px gaps, dock top at 73%, focus 1.2×)
  and the HIG (60/80 pt safe area, 800×480 icons, focus/pressed states).
- **Liquid Glass without a backdrop-blur API.** The launcher knows what is behind its glass (its
  wallpaper or the carousel artwork), so it bakes a small blurred, saturation-boosted copy once per
  background and the GPU draws glass as shader-filled rounded rectangles aligned to the surface's
  screen position, plus magnified edge bands (lensing) and a gradient specular rim.
- **Recommendations without system access.** Android's preview/Watch Next rows need a
  system-signature permission (verified: 0 rows readable), so the top shelf uses JustWatch/TMDB
  and Continue Watching uses published media sessions.

- **Search without a list of services.** JustWatch says which services in India have a title and
  gives each one's links; tvOS Launcher finds the app for each service itself (the package in the Android
  TV link, an installed non-browser handler of the web link, a remembered match, or an installed
  app with the same name), so new services need no update. Android 11+ hides other apps unless
  declared, so the manifest's `<queries>` lists launcher apps, voice input, the speech service
  and https handlers.
- **Search stays light.** It is an overlay in `MainActivity` (no new activity): apps match from
  the list already in memory on each key press; titles are fetched 350 ms after typing pauses with
  the previous request cut off, the last 20 searches are cached, posters are decoded at card size
  into a heap-capped LRU, and while Search is open the Home layers under it stop drawing and the
  carousel pauses. Typing measured p50 6 ms / p90 11 ms per frame on the 1 GB Android 9 TV.
- **Dictation.** `SpeechRecognizer` (Google's TV voice service) with partial results fills the
  field live after Android's microphone prompt; otherwise `RecognizerIntent` opens the TV's voice
  prompt. The remotes' Assistant key is routed by Android to Google Assistant before apps, so the
  remote shortcut is holding Back for 1 s (Back is replayed when released sooner).

## Performance (Mi Box, Mali-450 MP, GLES 2.0)

- Startup: the app list and wallpaper are prepared on background threads. The shell appears without
  waiting for package-manager queries; only the current catalog generation is applied on the UI thread.
- Scenes prepare images, clock fonts and glass on a worker, retaining the displayed/transition or next
  scene. `onDraw` reads ready layers. Published bitmaps are released by reference; only unpublished
  obsolete results may be recycled. Device frame times for this change are pending (X4Q Pro deferred).
- Custom wallpaper cache: 8–24 MiB depending on heap; custom icons: 2–8 MiB; settings thumbnails: 4 MiB.
  Android memory-pressure callbacks trim these caches. These limits do not include active Views or GPU memory.
- Dashboard fetches share one in-flight task per signature, with three worker threads, a 24-task queue
  and 30-second failure backoff. Explicit refresh bypasses backoff while still sharing running work.
- Volume handling waits for native callbacks while idle; without a native listener (or in projector mode),
  idle fallback polls every 400 ms plus dump time. The same single writer thread handles Binder dumps.

- No perpetual animations (no wallpaper drift, no idle sway): idle draws 0 frames.
- Nothing is rendered in software on the UI thread during transitions (the original glass cache
  caused ~1 s freezes moving to/from the dock).
- Legibility gradients are baked into the wallpaper and artwork; the window background is removed
  to avoid hidden full-screen fills.
- Measure with `adb shell dumpsys gfxinfo com.techgenius.tvoslauncher` — dock navigation runs at a 16 ms
  median; full-screen transitions are limited by the GPU's fill rate (~30 fps on this box).
