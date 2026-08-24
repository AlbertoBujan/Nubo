# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Nubo is an Android-only weather app for Spain, written in Kotlin with Jetpack Compose. Forecast data (current/hourly/daily) comes from Open-Meteo; weather warnings (avisos) come from AEMET's CAP/XML endpoint. State is a single `WeatherViewModel` exposing a `StateFlow`; persistence is DataStore Preferences.

The app was migrated from Flutter in the `kotlin` branch. `main` still holds the Flutter code (last release v0.1.36) and stays deployable until the Kotlin version ships.

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
- `data/remote/OpenMeteoApi.kt` — one call returns hourly + daily. Returns the **raw JSON**, which is what gets cached, so the cache does not break when model shapes change.
- `data/remote/AemetApi.kt` — AEMET's two-step protocol: the endpoint returns a signed URL in `datos`, and the payload is fetched from there. Also holds `AemetAreas.provinciaToArea`, a hand-built INE-province → AEMET-area table that is not documented anywhere.
- `data/remote/AlertService.kt` — CAP parsing. AEMET concatenates several `<alert>` blocks in one body, so they are split with a regex before parsing. The regex uses a negative lookahead so a truncated block does not swallow the next valid one.
- `data/remote/MunicipioSearchService.kt` — downloads AEMET's ~8.000-municipality master once and keeps it in memory. Indexes both AEMET's name form ("Palmas de Gran Canaria, Las") and the natural one ("Las Palmas de Gran Canaria").
- `data/local/WeatherStorage.kt` — DataStore. Stores the raw Open-Meteo JSON plus alerts and sun times per municipality.
- `data/local/FlutterPreferencesMigration.kt` — **do not delete while Flutter users remain.** Reads the SharedPreferences the Flutter app left behind (same package) so updating does not wipe saved cities. The value format is `<base64 prefix>!<json>`; the `!` is undocumented and was found by dumping the real file.
- `domain/astro/SunCalc.kt` — own port of the SunCalc algorithms, replacing the Dart packages. Validated against physical facts (solstice day lengths, polar night, synodic month) rather than copied values.
- `domain/weather/DailyCodeAggregator.kt` — recomputes the daily icon from the hourly codes. Open-Meteo's `daily.weather_code` is the *most significant* phenomenon of the day, not the most durable, which made the icon systematically pessimistic.
- `ui/components/GlassCard.kt` — translucent card with a scrim that occludes the rain falling behind it. Compose has no native backdrop blur.
- `ui/components/WeatherEffects.kt` — rain particles and lightning on a Canvas, following Breezy Weather's approach.
- `work/BackgroundUpdateWorker.kt` — WorkManager periodic refresh; builds its own dependencies since it runs without UI.

Time handling: Open-Meteo returns timestamps already in the location's timezone (`timezone=auto`), so they are parsed as `LocalDateTime` and treated as device-local — the same simplification the Flutter app made.

`java.time` works on `minSdk 24` thanks to core library desugaring, enabled in `app/build.gradle.kts`.

## Gotchas

- The AEMET API key is baked into `AemetApi.kt`, as it was in the Flutter app. It is a free public OpenData key already published in previous APKs and in the git history.
- Weather icons are Material approximations: `material-icons-extended` has no "sun behind cloud", `Rainy` or `Foggy` equivalents to Lucide's.
- Date formatters must be given an explicit Spanish locale, or day names come out in the device's language.
- Robolectric is needed for tests touching `org.json`, pinned to `@Config(sdk = [34])` since it does not simulate API 36 yet.
