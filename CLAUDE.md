# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Nubo is a Flutter (Android-only) weather app. Weather data (current/hourly/daily forecast) comes from Open-Meteo; weather alerts (avisos) come from AEMET's CAP/XML endpoint. State management is `provider` (`ChangeNotifier`), persistence is `shared_preferences`.

## Commands

```bash
flutter pub get                 # install dependencies
flutter analyze                 # lint (uses flutter_lints via analysis_options.yaml)
flutter test                    # run all unit/widget tests
flutter test test/services/api_service_test.dart   # run a single test file
flutter test --plain-name "some test name"         # run a single test by name
flutter build apk --release --no-shrink            # release APK (matches CI)
```

Integration tests live in `integration_test/` and `test_driver/` and require a connected device/emulator (`adb devices` to check).

### Release / deploy

`deploy.sh` bumps nothing itself — it reads the version already set in `pubspec.yaml`, commits, tags `vX.Y.Z`, and pushes the tag. Pushing the tag triggers `.github/workflows/release.yml`, which builds, signs (secrets: `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`, `ALIAS`), and publishes a GitHub Release, keeping only the latest 3 releases. So: bump `version:` in `pubspec.yaml` first, then run `./deploy.sh`.

**Always test on an emulator/device before deploying** — do not run `deploy.sh` on unverified changes.

## Architecture

Layered structure under `lib/`: `services/` (raw HTTP/SDK calls) → `repositories/` (abstract interfaces + one `Impl`, own the domain-facing contracts) → `providers/` (single `WeatherProvider`, the app's only `ChangeNotifier`) → `screens/` + `widgets/` (UI, read state via `context.watch`/`Provider.of`).

Repositories are defined as `abstract interface class X` with an `XImpl` that takes its service dependencies as optional constructor params (defaults to `ServiceName()`) — this is the seam used for testing (see `test/repositories/`, which passes fakes/mocks instead of real services). Follow this pattern for any new repository.

Key components:
- `services/api_service.dart` (`OpenMeteoApiService`) — Open-Meteo forecast fetch, with retry/backoff and timeout in `_getWithRetry`.
- `services/alert_service.dart` (`AlertService`) — AEMET CAP alerts. Two-step fetch (get a signed data URL, then fetch+parse the XML/tar-concatenated CAP payload with manual regex splitting, since AEMET concatenates multiple `<alert>` blocks in one body). Contains a hardcoded province→AEMET-area code table (`_provinciaToArea`) and API key. `.env` (`AEMET_API_KEY`) exists but is **not** currently wired up (no `flutter_dotenv` dependency) — the key baked into `alert_service.dart` is what's actually used.
- `services/municipio_search_service.dart` — municipio (Spanish town) name search and nearest-municipio lookup, backing `LocationRepository`.
- `services/background_update_service.dart` — WorkManager periodic background refresh. `callbackDispatcher` must stay a top-level/`@pragma('vm:entry-point')` function (WorkManager isolate requirement); it re-instantiates repositories directly rather than going through `WeatherProvider`.
- `services/update_service.dart` — in-app update checker/installer (downloads APK from GitHub Releases via `open_filex`).
- `repositories/weather_repository.dart` — combines `LocationRepository` (municipio → lat/lon) with `OpenMeteoApiService`, returns `WeatherForecastResult` including the raw JSON (kept raw so `WeatherStorageRepository` can persist it without re-parsing).
- `repositories/weather_storage_repository.dart` — SharedPreferences-backed cache; stores the *raw* Open-Meteo JSON, alerts, and sun times together under `weather_data_<municipioId>`, and saved locations under `saved_locations`.
- `providers/weather_provider.dart` — the central state holder. Keeps per-municipioId maps for cache, loading state, error state, alerts, sun/moon data, plus the `PageView` index for swiping between saved cities. This is the widest-reaching file in the app; most feature work touches it.
- `utils/sun_calculator.dart`, `utils/moon_calculator.dart`, `utils/sky_gradients.dart` — derive sun phase / moon phase / background gradient from coordinates and time, driving the dynamic day/night background.

Models (`lib/models/`) generally expose a `fromOpenMeteoJson`/`fromJson` factory and are plain data classes (no codegen — no `freezed`/`json_serializable`).

## Agents

Nubo-specific work is often delegated to specialized subagents (`flutter-architect`, `flutter-qa-senior`, `flutter-release`, `security-auditor-nubo`, `api-reliability`) rather than implemented inline. When the user says "usa el agente X" or "con sonnet/opus" for a task matching one of these, dispatch to that subagent instead of implementing directly.
