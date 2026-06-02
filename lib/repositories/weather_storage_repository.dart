import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/daily_forecast.dart';
import '../models/hourly_forecast.dart';
import '../models/saved_location.dart';
import '../models/weather_alert.dart';
import '../utils/sun_calculator.dart';

/// Datos meteorológicos rehidratados desde la caché persistida.
class CachedWeatherData {
  final List<DailyForecast> daily;
  final List<HourlyForecast> hourly;
  final List<WeatherAlert> alerts;
  final SunTimes? sunTimes;
  final DateTime lastUpdated;

  const CachedWeatherData({
    required this.daily,
    required this.hourly,
    required this.alerts,
    this.sunTimes,
    required this.lastUpdated,
  });
}

/// Contrato para persistir y recuperar localizaciones y datos meteorológicos.
abstract interface class WeatherStorageRepository {
  Future<List<SavedLocation>> loadLocations();
  Future<void> saveLocations(List<SavedLocation> locations);
  Future<CachedWeatherData?> loadCachedWeather(String municipioId);
  Future<void> saveWeather({
    required String municipioId,
    required Map<String, dynamic> rawJson,
    required List<WeatherAlert> alerts,
    SunTimes? sunTimes,
    required DateTime updatedAt,
  });
  Future<void> removeWeather(String municipioId);
}

/// Implementación basada en SharedPreferences.
class WeatherStorageRepositoryImpl implements WeatherStorageRepository {
  static const String _locationsKey = 'saved_locations';

  @override
  Future<List<SavedLocation>> loadLocations() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getStringList(_locationsKey) ?? [];
    return raw
        .map(SavedLocation.fromPrefsString)
        .whereType<SavedLocation>()
        .toList();
  }

  @override
  Future<void> saveLocations(List<SavedLocation> locations) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(
      _locationsKey,
      locations.map((l) => l.toPrefsString()).toList(),
    );
  }

  @override
  Future<CachedWeatherData?> loadCachedWeather(String municipioId) async {
    final prefs = await SharedPreferences.getInstance();
    final jsonStr = prefs.getString('weather_data_$municipioId');
    if (jsonStr == null) return null;

    try {
      final decoded = jsonDecode(jsonStr) as Map<String, dynamic>;
      if (!decoded.containsKey('openMeteo')) return null;

      final daily = DailyForecast.fromOpenMeteoJson(
          decoded['openMeteo'] as Map<String, dynamic>);
      final hourly = HourlyForecast.fromOpenMeteoJson(
          decoded['openMeteo'] as Map<String, dynamic>);
      final lastUpdated = DateTime.parse(decoded['lastUpdated'] as String);

      final alerts = decoded['alerts'] != null
          ? (decoded['alerts'] as List)
              .map((a) => WeatherAlert.fromJson(a as Map<String, dynamic>))
              .toList()
          : <WeatherAlert>[];

      SunTimes? sunTimes;
      if (decoded['sunTimes'] != null) {
        final st = decoded['sunTimes'] as Map<String, dynamic>;
        sunTimes = SunTimes(
          sunrise: DateTime.parse(st['sunrise'] as String).toLocal(),
          sunset: DateTime.parse(st['sunset'] as String).toLocal(),
        );
      }

      return CachedWeatherData(
        daily: daily,
        hourly: hourly,
        alerts: alerts,
        sunTimes: sunTimes,
        lastUpdated: lastUpdated,
      );
    } catch (_) {
      final prefs2 = await SharedPreferences.getInstance();
      await prefs2.remove('weather_data_$municipioId');
      return null;
    }
  }

  @override
  Future<void> saveWeather({
    required String municipioId,
    required Map<String, dynamic> rawJson,
    required List<WeatherAlert> alerts,
    SunTimes? sunTimes,
    required DateTime updatedAt,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    final dataString = jsonEncode({
      'openMeteo': rawJson,
      'alerts': alerts.map((a) => a.toJson()).toList(),
      'sunTimes': sunTimes != null
          ? {
              'sunrise': sunTimes.sunrise.toIso8601String(),
              'sunset': sunTimes.sunset.toIso8601String(),
            }
          : null,
      'lastUpdated': updatedAt.toIso8601String(),
    });
    await prefs.setString('weather_data_$municipioId', dataString);
  }

  @override
  Future<void> removeWeather(String municipioId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('weather_data_$municipioId');
  }
}
