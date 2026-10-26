# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

GrandTV ("Nhà của bà" on screen) is a tiny Android TV home-screen launcher for an elderly user (bà Ngọ). It shows a time-of-day greeting, a clock, the date, the weather, and exactly three app tiles: YouTube, VTV Go and YouTube Kids. The user sees everything in Vietnamese, and most code comments are in Vietnamese too. `HUONG-DAN.md` is the end-user setup guide (in Vietnamese).

## Commands

```bash
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew lint                 # lint is configured with abortOnError false
```

To build from the CLI you need an Android SDK, either through `local.properties` (`sdk.dir=...`, which Android Studio creates on first open) or through `ANDROID_HOME`. AGP 8.10 also needs JDK 17+. On this dev machine the default `java` is 8, so prefix builds with `JAVA_HOME=/opt/java/jdk-17.0.12 PATH=/opt/java/jdk-17.0.12/bin:$PATH`. The project has no tests.

The target box is an Amlogic Android 9 (SDK 28) device with a 1280x720 screen at 213 dpi, which works out to about 960x540 dp. The current layout just fits, so check any UI change against that size: `adb exec-out screencap -p > screen.png`.

Install on the TV box and set it as the home screen:

```bash
adb connect <IP_box>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity vn.nhaba.launcher/.MainActivity
```

## Constraints

- **Framework APIs only.** `android.useAndroidX=false` is set and there are no dependencies: no AndroidX, no support libs, no OkHttp, no JSON libraries. Networking uses `HttpURLConnection` and JSON uses `org.json`. Keep it that way unless the user asks otherwise.
- `minSdk 21`, Java 8 source level. The existing code uses anonymous inner classes rather than lambdas, so match that style.
- The app is driven by a TV remote's D-pad, not touch. Every interactive view has to be focusable, and focus feedback is a scale animation.
- User-editable settings live in `Config.java`: `TEN`, `XUNG_HO`, `VI_DO`/`KINH_DO`, and `THANH_PHO`. Strings shown to the user are hardcoded in Vietnamese in `MainActivity`. They are not kept in `strings.xml`.

## Architecture

Everything lives in `MainActivity` (a plain `android.app.Activity`). The manifest registers it as a `HOME` activity with `singleTask` and `clearTaskOnLaunch`. `onBackPressed()` is deliberately a no-op so the user can't leave the home screen by accident.

**Tick loop.** One `Runnable tick` on the main `Handler` fires every second between `onResume` and `onPause`, and it does three things:
- It updates the clock and date. The text is rewritten only when the minute changes.
- It tracks the time-of-day period: morning 4–12h, afternoon 12–18h, evening otherwise. When the period changes, it switches the greeting text and swaps the root background `GradientDrawable`.
- It schedules the weather fetch: every 30 min after a success, or 2 min after a failure.

**Weather.** `fetchWeather()` runs on a raw `Thread` and guards against overlap with `volatile` flags (`fetching`, `lastWeatherOk`, `lastWeatherTry`).
- If `Config.VI_DO` and `Config.KINH_DO` are both 0, it gets a location once from `ipwho.is` and caches it in `ipLat`/`ipLon`/`ipCity`.
- It then calls the Open-Meteo forecast API, which needs no API key.
- `describe()` maps WMO weather codes to an emoji and a Vietnamese label.
- `showWeather()` adds a caring reminder line (`w_note`) for storms, rain, heat or cold, using `Config.XUNG_HO`.

**App tiles.** Each `AppTile` has an ordered list of candidate package names plus fallback label keywords.
- `buildTiles()` creates the tile views in code inside the `@id/tiles` row of `activity_main.xml`.
- `resolveTile()` runs on every `onResume`, so tiles pick up apps that were just installed or removed. It tries each package with `getLeanbackLaunchIntentForPackage` and then `getLaunchIntentForPackage`. If none match, it scans `LEANBACK_LAUNCHER`/`LAUNCHER` activities for a keyword match. `skipKids` keeps the YouTube tile from matching YouTube Kids.
- For the tile image it uses the activity banner, then the application banner, then the icon.
- If an app isn't installed, its tile is dimmed to 0.6 alpha with a "?" icon. Clicking it opens `market://details?id=<packages[0]>`.
- Package visibility on Android 11+ depends on the `<queries>` block in `AndroidManifest.xml`. It already covers every launcher activity, so new tiles don't need manifest changes.

**New VTV Go films.** `fetchFilms()` works like the weather fetch: a raw thread, scheduled from `tick`, refreshing every hour and retrying after 5 min on failure.
- It reads VTV Go's public, no-login v21 API: `vod/list-vod` for node `533566` ("PHIM MỚI TRÊN VTVGO"), sorted newest first.
- The server returns at most 20 episodes per page, so the code pages through results until it has 8 distinct `seriesID`s, keeping only the newest episode of each.
- `showFilms()` lays those 8 out as 2 rows of 4 cards. It rebuilds the rows only when the film list changes, so the focused card isn't lost.
- Clicking a card opens `vtvgo://play?mediaType=MOVIE&contentId=<id>`. This deep link only works with VTV Go 12.10+ ("VTVgo The Next"). If it can't open, the card launches the VTV Go tile as usual.
- `newCard()` is the shared builder for app tiles and film cards. It gives every card the same focus zoom (with `translationZ`, so the focused card draws above its neighbours) and the same long-press menu.

**Settings menu.** Holding OK on a tile, or pressing the remote's MENU key, opens `showMenu()`. That dialog is the only way to reach TV Settings or the Play Store from this launcher.
