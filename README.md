# 🌌 SPACE — a galaxy-grade private browser for Android

SPACE is a fast, modern Android web browser with a living galaxy for a face and
privacy as its default physics. Every page loads under an animated starfield,
behind a tracker shield, with nothing phoning home — because there is no home
to phone. No accounts, no telemetry, no cloud.

**Better than the big browsers where it counts:**

- **Shields up by default** — a bundled host-level blocker stops ads and
  trackers in every tab, with a per-site kill switch, your own custom rules,
  and a live "trackers blocked" counter on the start page.
- **Privacy features Chrome makes you dig for (or doesn't have):**
  HTTPS upgrading with automatic one-shot fallback, third-party cookie
  blocking, a generic fingerprint-resistant user agent, sites denied
  camera/mic/location *silently* unless you opt in, biometric app lock,
  AMOLED-true private mode, and one-tap or on-exit data clearing.
- **A UI worth looking at** — drifting parallax starfield, nebula glows and an
  aurora band rendered in a single Canvas pass; 8 neon accent palettes;
  system/light/dark/AMOLED themes; Material You dynamic color; glassmorphism
  chrome. Turn the animation down (or off) and it costs nearly nothing.
- **Bring-your-own AI copilot** — summarize, extract key points, explain
  simply, translate, or ask questions about the open page against *any*
  OpenAI-compatible endpoint (OpenAI, local Ollama, LM Studio, llama.cpp…).
  Your key is stored encrypted on-device and pages are only sent to the server
  **you** configured, only when **you** tap an action.
- **Everything a daily driver needs** — tabs + private tabs with a visual grid
  switcher, reopen-closed, session restore, bookmarks, searchable history with
  retention control, downloads, find-in-page, desktop mode, share / print /
  save-as-PDF, full-page screenshots, quick links on a clock start page, a
  password generator, and a searchable settings screen.

## Building

Requirements: **Android Studio Hedgehog+** (or plain Gradle 8.2+), **JDK 17**,
Android SDK 34. Minimum device: **Android 9 (API 28)**.

```bash
# In Android Studio: File → Open → this folder, then Run ▶
# Or from the command line:
./gradlew assembleDebug        # if the wrapper jar is missing: `gradle wrapper` once
./gradlew testDebugUnitTest    # pure-Kotlin unit tests (URL heuristics, blocklist matcher)
./gradlew assembleRelease      # minified; signs with your key if keystore.properties exists
```

Release signing is optional: copy `keystore.properties.example` to
`keystore.properties` and fill it in. Without it, release builds fall back to
the debug key so `assembleRelease` still produces an installable APK.

## The honest privacy model

SPACE renders pages with Android's system **WebView** (Chromium). That keeps
the app small, current with security patches, and fast — and it sets the
boundaries we're upfront about:

- **Private tabs** never touch history, cache, or disk-persisted sessions, and
  all session cookies are dropped when the last private tab closes. But
  WebView's cookie jar is process-global, so private tabs are not a fully
  isolated cookie universe the way Firefox's containers are.
- **Content blocking** is request-level host matching (bundled list + your
  rules), not full cosmetic filtering — you may see empty slots where ads died.
- **Safe Browsing** checks are performed by WebView itself and can be switched
  off in settings.

No analytics SDKs, no crash reporters, no network calls except: the pages you
open, your search engine (including its suggestion endpoint, which you can turn
off), and the AI endpoint you optionally configure. Backups are disabled so
your data never rides Google's cloud backup.

## Architecture

Single-module Kotlin + Jetpack Compose (Material 3), no DI framework — an
explicit `AppContainer` wires the graph.

```
app/src/main/java/com/spacebrowser/
├── SpaceApp.kt / MainActivity.kt    # process + activity glue, permissions, lock
├── core/
│   ├── browser/    # Tab, TabManager, WebView factory & clients, event bus
│   ├── adblock/    # HostMatcher (pure, tested) + AdBlocker runtime
│   ├── db/         # Room: history, bookmarks, quick links
│   ├── settings/   # DataStore-backed SpaceSettings + search engines
│   ├── net/        # search suggestions + OpenAI-compatible AI client
│   ├── security/   # EncryptedSharedPreferences key store
│   └── util/       # UrlUtil (pure, tested)
└── ui/
    ├── components/ # GalaxyBackground, glass modifiers, AddressBar
    ├── browser/    # BrowserScreen, WebViewHost, menu, site panel
    ├── home/ tabs/ library/ settings/ ai/ theme/
```

State flows one way: `SettingsRepository.flow` → `TabManager.applySettings` →
live WebViews. Tabs are plain objects holding Compose state; the WebView is a
lazily-created view the Compose layer only hosts, never owns.

## Docs

- [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) — a tour of every feature.
- [`CHANGELOG.md`](CHANGELOG.md)

## License

MIT — see [`LICENSE`](LICENSE).
