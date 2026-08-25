# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Nubo is an Android-only weather app, written in Kotlin with Jetpack Compose. Forecast data (current/hourly/daily) comes from Open-Meteo and works **worldwide**; location search comes from Open-Meteo's geocoding API, reverse geocoding (the "my location" button) from BigDataCloud. Weather warnings (avisos) are **Spain-only**, from AEMET's CAP/XML endpoint. State is a single `WeatherViewModel` exposing a `StateFlow`; persistence is DataStore Preferences.

The app was migrated from Flutter; v1.0.0 was the first native Kotlin release, succeeding v0.1.36. `main` is the Kotlin code and the only deployable branch.

## Commands

```bash
./gradlew testDebugUnitTest        # unit tests
./gradlew assembleDebug            # debug APK
./gradlew assembleRelease          # release APK (signed if key.properties exists)
./gradlew lintDebug                # Android lint
./gradlew :app:testDebugUnitTest --tests "*DailyCodeAggregatorTest*"   # single test class
```

Test results are XML under `app/build/test-results/`; the Gradle console output does not list individual tests.

Requires JDK 17+ (`local.properties` must point `sdk.dir` at the Android SDK).

### Release / deploy

`deploy.sh` does not bump anything — it reads `versionName` from `app/build.gradle.kts`, runs the tests, commits, tags `vX.Y.Z` and pushes the tag. That triggers `.github/workflows/release.yml`, which builds, signs with the keystore from the `SIGNING_KEY` secret, verifies the signature and publishes a GitHub Release, keeping only the latest 3.

So: bump `versionName` **and `versionCode`** in `app/build.gradle.kts` first, then run `./deploy.sh`. The script aborts if the tag already exists.

**`versionCode` must increase on every release.** The Flutter app shipped every version with `versionCode = 1` because `pubspec.yaml` never had a build number; Android needs it to go up to accept an update.

**Always test on an emulator/device before deploying.**

## Architecture

Layered under `app/src/main/java/com/nubo/nubo/`:

`data/remote` + `data/local` + `data/location` (raw HTTP, DataStore, GPS) → `data/repository` (interfaces + one `Impl`) → `ui/weather/WeatherViewModel` → `ui/weather` screens + `ui/components`.

Pure logic lives in `domain/` and has no Android dependencies, which is what makes it unit-testable without instrumentation.

Dependencies are resolved by hand in `di/ServiceLocator.kt` — no Hilt. One ViewModel and a handful of services do not justify an annotation processor.

Key components:
- `data/remote/OpenMeteoApi.kt` — one call returns hourly + daily. The request asks for the full seven days; the **hourly list is capped at `HourlyForecast.MAX_HOURS` (48)** when parsed, because past a couple of days the hour-by-hour detail says nothing the daily summary does not. `DailyForecast` keeps all seven days: it aggregates the raw `hourly` block of the JSON itself, not that capped list, so trimming does not weaken the daily icons. `apparent_temperature` and `uv_index` ride along in this same request — they feed the conditions card at no network cost. Returns the **raw JSON**, which is what gets cached, so the cache does not break when model shapes change.
- `data/remote/AirQualityApi.kt` — European AQI from Open-Meteo's **separate** air-quality endpoint, so it is a second request per location. It is fired in parallel with the forecast inside `WeatherRepositoryImpl` (chaining them would double every refresh for a secondary figure) and its failure is swallowed: the forecast still lands and the tile shows a dash, the same contract as alerts outside Spain. Only two days of hourly index are asked for — about 1 KB.
- `data/remote/AemetApi.kt` — AEMET's two-step protocol: the endpoint returns a signed URL in `datos`, and the payload is fetched from there.
- `data/remote/AlertService.kt` — CAP parsing. AEMET concatenates several `<alert>` blocks in one body, so they are split with a regex before parsing. The regex uses a negative lookahead so a truncated block does not swallow the next valid one. Alerts are matched **by the full 6-digit warning zone**, not by a province prefix — see below.
- `data/remote/GeocodingApi.kt` — Open-Meteo geocoding, free and keyless. The source of every new location: name, country, region, coordinates and IANA time zone. Ids are prefixed `om:` so they can never be confused with a legacy INE code.
- `data/remote/ReverseGeocodingApi.kt` — BigDataCloud, for the "my location" button. Open-Meteo geocodes forward only. Ids are `geo:lat,lon` rounded to 4 decimals so tapping twice does not create duplicates.
- `data/remote/AemetZoneService.kt` — downloads AEMET's ~8.000-municipality master once and keeps it in memory. Since search moved to Open-Meteo it only does two Spain-only jobs: resolve the **warning zone** (`zona_comarcal`) of a point, and fill in coordinates for cities saved in the old INE-only format. The download is lazy: someone who never looks at a Spanish city never pays for it.
- `data/local/WeatherStorage.kt` — DataStore. Stores the raw Open-Meteo JSON plus alerts and sun times per location.
- `data/local/FlutterPreferencesMigration.kt` — **do not delete while Flutter users remain.** Reads the SharedPreferences the Flutter app left behind (same package) so updating does not wipe saved cities. The value format is `<base64 prefix>!<json>`; the `!` is undocumented and was found by dumping the real file.
- `domain/astro/SunCalc.kt` — own port of the SunCalc algorithms, replacing the Dart packages. Validated against physical facts (solstice day lengths, polar night, synodic month) rather than copied values.
- `domain/weather/DailyCodeAggregator.kt` — recomputes the daily icon from the hourly codes. Open-Meteo's `daily.weather_code` is the *most significant* phenomenon of the day, not the most durable, which made the icon systematically pessimistic.
- `ui/components/HourlyView.kt` — hourly carousel plus the temperature chart. The column width is **computed** from the card width so exactly `VISIBLE_HOURS` (6) fit; it used to be a fixed 65 dp, unrelated to the screen, which cut the sixth hour in half. Scrolling snaps in blocks of six through `blockTarget`, which clamps the fling's projected landing to the two adjacent blocks so one gesture always moves one block. The chart is **not** inside the scrolling area: it spans the whole card and draws itself offset by `scrollState.value`, which keeps the curve in step with the columns without re-measuring 48 of them. Its min/max are computed over every hour, so the vertical scale does not jump between blocks. There is deliberately no y-axis legend: every point already carries its own temperature label, and the axis bounds were the padded limits of the chart, not values any hour actually reaches. Both curves are drawn through `extendToEdges`, which adds one extrapolated point at each end so they reach the card border instead of starting in mid-air half a column in — those points get no dot and no label because they are not hours. The hour list is trimmed to a multiple of six so the last block cannot land half-way, showing a clipped column at each edge; `HourlyForecast.MAX_HOURS` is already a multiple of six, so this only bites on a short payload.
- `ui/components/ConditionsCard.kt` — four tiles of present-moment context: air quality, UV, humidity and apparent temperature. It exists because the screen had nowhere for context data — the header is "what temperature is it" and every other card is evolution over time — and a whole card for the AQI alone would have been the clutter it was meant to avoid. Humidity was already downloaded and displayed nowhere. Only the two tiles that warn of something carry colour, so colour means "look at this" rather than decoration. Bands live in `domain/weather/AirQuality.kt`, colours in `AirQualityColors.kt`, following the `AlertLevel`/`toColor()` split; the two darkest official EAQI colours are lightened because they are designed for white backgrounds and vanish on the card.
- `ui/components/GlassCard.kt` — translucent card with a scrim that occludes the rain falling behind it. Compose has no native backdrop blur.
- `ui/components/WeatherEffects.kt` — rain, snow and lightning on a Canvas, following Breezy Weather's approach. Snowflakes are drawn as swaying dots, not streaks: falling straight they read as slow rain.
- `ui/components/NuboWeatherIcons.kt` — hand-drawn cloud-with-precipitation icons, reusing the cloud from `Icons.Outlined.Thunderstorm`. Material has no equivalent.
- `ui/weather/LocationList.kt` — the drawer's saved-location cards. Swipe right deletes (armed past 40% of the width, with haptics and a red backdrop); long-press picks a card up to reorder. The two gestures coexist because the reorder one needs a long press and **consumes** its events, which cancels the horizontal detector's own slop; without that consumption a diagonal drag fires both. Cards are painted with opaque colours (`CARD_IDLE`/`CARD_SELECTED`, the drawer background already blended with white) — translucent ones let the delete backdrop and its label show through the city name. The reorder arithmetic (`reorderTarget`/`reorderShift`) is pure and unit-tested; it assumes every card is the same height, so it divides by the pitch instead of measuring.
- `ui/weather/SettingsSheet.kt` — background interval, update check and about, in a bottom sheet behind the drawer's gear. There is no "refresh now": pull-to-refresh on the main screen does the same thing.
- `work/BackgroundUpdateWorker.kt` — WorkManager periodic refresh; builds its own dependencies since it runs without UI.

### Time zones

Open-Meteo returns timestamps already in the location's time zone (`timezone=auto`), so they are parsed as `LocalDateTime`. Until v1.1.0 they were then compared against the **device** clock, which only works while the device and the location share a zone. That made "Ahora", "Hoy", the sun arc and alert expiry wrong for any foreign city — and it was already subtly wrong inside Spain, where the Canaries run an hour behind the mainland.

Every one of those comparisons now uses the **location's** zone:

- `SavedLocation.timeZone` / `CityWeather.timeZone` carry it, and `CityWeather.nowThere` is what the UI compares against. `zoneOf()` in `domain/model/Zones.kt` falls back to the device zone rather than throwing.
- The authoritative value is the `timezone` field of the forecast response, not the one from geocoding: it is the zone the timestamps in *that* payload are expressed in. It is written back to the saved location on every fetch.
- `SunCalculator` and `MoonCalculator` take a `ZoneId` that **defaults to the device zone**. Forgetting to pass it is silent and wrong — it put Tokyo's sunrise at 22:08.
- CAP alert times get the zone too, so a Spanish warning reads correctly from abroad.

Adding a new place that compares a forecast timestamp to `LocalDateTime.now()` reintroduces the bug.

`java.time` works on `minSdk 24` thanks to core library desugaring, enabled in `app/build.gradle.kts`.

### AEMET warning zones

AEMET issues warnings per **zone**, which is sub-provincial: A Coruña has four, and three of them are coastal. The zone is the 6-digit `geocode` in each CAP alert — area (2) + INE province (2) + zone (2).

The zone of a municipality is the `zona_comarcal` field of AEMET's municipality master, and it matches that geocode exactly. All 8.122 municipalities carry it, so there is no fallback path to maintain.

Until v1.0.0 the filter compared only the first 4 digits, so every municipality got the warnings of its whole province — an inland town showed coastal warnings. Matching the full zone is what fixes it, and it must stay an **equality** check: a prefix comparison is what caused the bug.

The first 2 digits of the zone are the AEMET area the endpoint is queried by, so no province → area table is needed. The hand-built one that used to live in `AemetApi.kt` was verified to agree with `zona_comarcal[:2]` for all 8.122 municipalities and then deleted. Note it never worked in the Canary and Balearic Islands anyway: there AEMET does not use INE province digits in the zone code (Adeje is `659603`).

## Gotchas

- The AEMET API key is baked into `AemetApi.kt`, as it was in the Flutter app. It is a free public OpenData key already published in previous APKs and in the git history.
- The air-quality series is stored and re-read like the forecast, **as an hourly series and not as a single "current" value**: the cache keeps the raw JSON, so hours later the current hour has to be picked again. One stored "now" value would be served stale while the temperature beside it stayed correct.
- **Warnings exist only in Spain.** `AlertRepositoryImpl` returns an empty list everywhere else. The candidates for widening it are MeteoAlarm (≈38 European countries, but its CAP carries only an `EMMA_ID` with no polygon, so it needs the zone geometry from somewhere) and the US NWS (`api.weather.gov`, free, keyless, and it resolves the zone server-side from `?point=lat,lon`).
- **Saved locations have two id formats.** New ones are `om:` or `geo:` prefixed; anything without a colon is a legacy INE code from a version before the app went worldwide, is assumed to be in Spain, and gets its coordinates filled in from AEMET's master on first launch (`WeatherViewModel.completeLegacy`). Do not delete that path while those installs remain — same reasoning as `FlutterPreferencesMigration`.
- Weather icons are mostly Material approximations: `material-icons-extended` has no "sun behind cloud" or `Foggy` equivalent to Lucide's. Precipitation is the exception — those are drawn in `NuboWeatherIcons.kt`.
- Date formatters must be given an explicit Spanish locale, or day names come out in the device's language. The UI is Spanish-only for now: strings are inline in the composables, not in `strings.xml`.
- **The order of the saved locations is the order of the pages**, and DataStore stores them in a `Set`, which has none. `WeatherStorage.saveLocations` writes the index as a prefix (`"0:…"`) and `loadLocations` sorts by it. Anything that reorders must go through those two.
- Robolectric is needed for tests touching `org.json`, pinned to `@Config(sdk = [34])` since it does not simulate API 36 yet.
