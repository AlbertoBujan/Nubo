import '../models/daily_forecast.dart';
import '../models/hourly_forecast.dart';
import '../services/api_service.dart';
import 'location_repository.dart';

/// Resultado de una consulta de previsión meteorológica.
///
/// Incluye el JSON crudo para que [WeatherStorageRepository] pueda
/// persistirlo sin volver a parsear.
class WeatherForecastResult {
  final List<DailyForecast> daily;
  final List<HourlyForecast> hourly;
  final Map<String, dynamic> rawJson;

  const WeatherForecastResult({
    required this.daily,
    required this.hourly,
    required this.rawJson,
  });
}

class WeatherRepositoryException implements Exception {
  final String message;
  const WeatherRepositoryException(this.message);

  @override
  String toString() => 'WeatherRepositoryException: $message';
}

abstract interface class WeatherRepository {
  Future<WeatherForecastResult> getForecast(String municipioId);
}

class WeatherRepositoryImpl implements WeatherRepository {
  final LocationRepository _locationRepo;
  final OpenMeteoApiService _apiService;

  WeatherRepositoryImpl({
    required LocationRepository locationRepository,
    OpenMeteoApiService? apiService,
  })  : _locationRepo = locationRepository,
        _apiService = apiService ?? OpenMeteoApiService();

  @override
  Future<WeatherForecastResult> getForecast(String municipioId) async {
    final coords = await _locationRepo.getCoordinates(municipioId);
    if (coords == null) {
      throw const WeatherRepositoryException(
        'No se encontraron coordenadas para la ubicación',
      );
    }

    final rawJson = await _apiService.fetchForecast(coords.lat, coords.lon);
    return WeatherForecastResult(
      daily: DailyForecast.fromOpenMeteoJson(rawJson),
      hourly: HourlyForecast.fromOpenMeteoJson(rawJson),
      rawJson: rawJson,
    );
  }
}
