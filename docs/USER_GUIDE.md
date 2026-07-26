# SPACE User Guide

## First launch

You land on the **start page**: a clock over the galaxy, a search pill, your
quick links, and the all-time trackers-blocked counter. Type in the address
bar to search (DuckDuckGo by default) or enter a URL — SPACE figures out which
you meant.

## The command bar

From left to right:

- **Shield** — shows how many trackers were blocked on this page. Tap it for
  the site panel: connection security, the block count, a per-site shields
  toggle, and Copy URL.
- **Address pill** — the lock/warning icon reflects HTTPS state. Tap to edit;
  suggestions blend your history with your engine's live suggestions (which
  you can disable). Private tabs never query the network for suggestions.
- **Tab counter** — opens the tab grid.
- **⋮ menu** — everything else.

While a page loads, a thin progress line runs under the bar; the reload button
becomes a stop button.

## Tabs

The grid shows live thumbnails. Switch between **Browsing** and **Private**
with the segmented control; each side shows its own tabs and counts. Close
with ✕, reopen the last closed tab from the toolbar's restore icon, and
long-press nothing — everything is one tap. The FAB opens a new tab of
whichever kind you're viewing.

**Private tabs** (violet accents) write no history, no cache, and are never
saved into session restore. Closing the last private tab drops all session
cookies. See the README for the one honest caveat about WebView's shared
cookie jar.

Normal tabs are **restored after you exit** — pages come back lazily as you
select them, so relaunch stays instant.

## The menu

Quick row: forward · reload · bookmark · share · home. Below it:

- **New tab / New private tab**
- **Library** — bookmarks, searchable history (with per-item delete and
  clear-all), and downloads with live progress.
- **Find in page** — match counter and next/previous.
- **Desktop site** — per-tab toggle.
- **Add to start page** — pins the page as a quick link (long-press a quick
  link to remove it).
- **Print / Save as PDF** — via Android's print dialog.
- **Capture page screenshot** — grabs the visible page and opens the share
  sheet.
- **AI assistant** — see below.
- **Password generator** — length 8–40, digits/symbols toggles,
  cryptographically random, one-tap copy.
- **Settings**, **Exit SPACE** (exit runs your clear-on-exit choices first).

## The AI assistant

Settings → **AI assistant**: set an endpoint (any OpenAI-compatible base URL,
e.g. `https://api.openai.com/v1` or `http://192.168.1.20:11434/v1` for
Ollama), a model name, and an API key if the server needs one. The key lives
in Android's encrypted storage.

Then from the menu: **Summarize · Key points · Explain simply · Translate**
(8 languages) or free-form questions. Page text goes only to your configured
server, only when you trigger an action.

## Settings highlights

Use the search field at the top — every setting is filterable.

- **Appearance** — theme (System / Light / Dark / AMOLED), 8 accent palettes,
  Material You dynamic color (Android 12+), galaxy animation toggle +
  intensity, dark mode for websites.
- **Privacy & shields** — the blocker with rule count, custom rules (one host
  per line blocks it and all subdomains), allowlist management, HTTPS
  upgrading, third-party cookie blocking, Safe Browsing, generic user agent,
  camera/mic/location ask-permission gates (off = silent deny), and
  clear-data-now.
- **Search & browsing** — engine (DuckDuckGo, Brave, Startpage, Google, Bing,
  Ecosia, or a custom `%s` template), suggestions toggle, JavaScript toggle,
  block-images data saver.
- **Data** — clear history/cookies/cache on exit; history retention
  (forever / 7 / 30 / 90 days).
- **Security** — biometric/screen-lock **app lock**; SPACE re-locks whenever
  it leaves the foreground.

## Little touches

- Back walks: find bar → page history → start page → previous tab.
- `http://` links try HTTPS first and fall back once, remembering the
  exception for the session.
- Downloads land in your Downloads folder with a system notification, and the
  Library's Downloads tab tracks progress live.
- SPACE registers as a browser, so "Open with" and web links can use it.
