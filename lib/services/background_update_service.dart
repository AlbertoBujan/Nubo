import 'package:flutter/widgets.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:workmanager/workmanager.dart';
import '../repositories/alert_repository.dart';
import '../repositories/location_repository.dart';
import '../repositories/weather_repository.dart';
import '../repositories/weather_storage_repository.dart';

// ── Callback de top-level requerido por workmanager ──────────────────────────
// Debe ser una función de nivel superior (no un método de clase).
@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((taskName, _) async {
    if (taskName != BackgroundUpdateService.taskName) return true;
    return _runBackgroundUpdate();
  });
}

Future<bool> _runBackgroundUpdate() async {
  WidgetsFlutterBinding.ensureInitialized();
  final startTime = DateTime.now().toIso8601String();
  debugPrint('[BgUpdate] Tarea iniciada — $startTime');

  try {
    final storage = WeatherStorageRepositoryImpl();
    final locations = await storage.loadLocations();

    if (locations.isEmpty) {
      debugPrint('[BgUpdate] Sin localizaciones guardadas — tarea completada (0 ciudades)');
      return true;
    }

    debugPrint('[BgUpdate] Procesando ${locations.length} localización(es)');

    final locationRepo = LocationRepositoryImpl();
    final weatherRepo = WeatherRepositoryImpl(locationRepository: locationRepo);
    final alertRepo = AlertRepositoryImpl();

    int successCount = 0;

    for (final loc in locations) {
      debugPrint('[BgUpdate] Procesando: ${loc.municipioId}');
      try {
        final forecast = await weatherRepo.getForecast(loc.municipioId);
        final alerts = await alertRepo.getAlerts(loc.municipioId);
        await storage.saveWeather(
          municipioId: loc.municipioId,
          rawJson: forecast.rawJson,
          alerts: alerts,
          updatedAt: DateTime.now(),
        );
        successCount++;
        debugPrint('[BgUpdate] Guardado correctamente: ${loc.municipioId}');
      } catch (e) {
        // fallo por ciudad → continuar con las demás
        debugPrint('[BgUpdate] ERROR en ${loc.municipioId}: $e');
      }
    }

    debugPrint('[BgUpdate] Tarea completada — $successCount/${locations.length} ciudades actualizadas');
    return true;
  } catch (e) {
    debugPrint('[BgUpdate] ERROR fatal en tarea de fondo: $e');
    return false;
  }
}

// ── Intervalo de actualización en segundo plano ───────────────────────────────

enum BackgroundUpdateInterval {
  off,
  every12h,
  every24h;

  Duration? get frequency => switch (this) {
    BackgroundUpdateInterval.off => null,
    BackgroundUpdateInterval.every12h => const Duration(hours: 12),
    BackgroundUpdateInterval.every24h => const Duration(hours: 24),
  };

  String get label => switch (this) {
    BackgroundUpdateInterval.off => 'Desactivado',
    BackgroundUpdateInterval.every12h => 'Cada 12 horas',
    BackgroundUpdateInterval.every24h => 'Cada 24 horas',
  };
}

// ── Servicio de gestión de la tarea periódica ─────────────────────────────────

class BackgroundUpdateService {
  static const String taskName = 'nubo.backgroundWeatherUpdate';
  static const String _uniqueTaskId = 'nubo-bg-weather';
  static const String _prefsKey = 'bg_update_interval';

  /// Inicializa WorkManager. Llamar una sola vez en main() antes de runApp.
  static Future<void> initialize() async {
    await Workmanager().initialize(callbackDispatcher, isInDebugMode: false);
  }

  /// Re-registra la tarea periódica al arrancar la app según la preferencia guardada.
  /// Usa ExistingWorkPolicy.keep para no cancelar una tarea ya activa.
  /// No llama cancelByUniqueName, por lo que es seguro llamarlo justo después de initialize().
  static Future<void> reregisterOnStartup() async {
    final interval = await getInterval();
    if (interval == BackgroundUpdateInterval.off) return;
    await Workmanager().registerPeriodicTask(
      _uniqueTaskId,
      taskName,
      frequency: interval.frequency!,
      constraints: Constraints(networkType: NetworkType.connected),
      existingWorkPolicy: ExistingWorkPolicy.keep,
    );
  }

  /// Lee el intervalo guardado (por defecto: off).
  static Future<BackgroundUpdateInterval> getInterval() async {
    final prefs = await SharedPreferences.getInstance();
    final index = prefs.getInt(_prefsKey) ?? 0;
    return BackgroundUpdateInterval.values[
        index.clamp(0, BackgroundUpdateInterval.values.length - 1)];
  }

  /// Persiste el intervalo y (re)programa o cancela el PeriodicTask.
  static Future<void> setInterval(BackgroundUpdateInterval interval) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_prefsKey, interval.index);

    await Workmanager().cancelByUniqueName(_uniqueTaskId);

    if (interval != BackgroundUpdateInterval.off) {
      await Workmanager().registerPeriodicTask(
        _uniqueTaskId,
        taskName,
        frequency: interval.frequency!,
        constraints: Constraints(networkType: NetworkType.connected),
        existingWorkPolicy: ExistingWorkPolicy.replace,
      );
    }
  }
}
