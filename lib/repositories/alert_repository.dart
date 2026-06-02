import '../models/weather_alert.dart';
import '../services/alert_service.dart';

abstract interface class AlertRepository {
  Future<List<WeatherAlert>> getAlerts(String municipioId);
}

class AlertRepositoryImpl implements AlertRepository {
  final AlertService _alertService;

  AlertRepositoryImpl({AlertService? alertService})
      : _alertService = alertService ?? AlertService();

  @override
  Future<List<WeatherAlert>> getAlerts(String municipioId) =>
      _alertService.fetchAlerts(municipioId);
}
