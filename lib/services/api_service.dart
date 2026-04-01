import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;

/// Servicio HTTP para la API de Open Meteo.
class OpenMeteoApiService {
  static const String _baseUrl = 'https://api.open-meteo.com/v1';

  final http.Client _client;

  OpenMeteoApiService({http.Client? client}) : _client = client ?? http.Client();

  /// Obtiene la predicción horaria y diaria en una sola petición a OpenMeteo.
  Future<Map<String, dynamic>> fetchForecast(double lat, double lon) async {
    final url = '$_baseUrl/forecast?latitude=$lat&longitude=$lon'
        '&hourly=temperature_2m,relative_humidity_2m,precipitation_probability,weather_code,wind_speed_10m,wind_direction_10m,is_day,dew_point_2m'
        '&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max'
        '&timezone=auto';
        
    return _getWithRetry(url);
  }

  /// Método privado con lógica de reintento y timeout
  Future<Map<String, dynamic>> _getWithRetry(String url) async {
    const int maxRetries = 3;
    int retryDelayMillis = 500;

    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        final response = await _client
            .get(Uri.parse(url))
            .timeout(const Duration(seconds: 4));

        if (response.statusCode == 429 && attempt < maxRetries) {
          await Future.delayed(Duration(milliseconds: retryDelayMillis));
          retryDelayMillis *= 2;
          continue;
        }

        if (response.statusCode != 200) {
           throw OpenMeteoApiException('Error del servidor. Código ${response.statusCode}', response.statusCode);
        }

        return jsonDecode(response.body) as Map<String, dynamic>;
      } catch (e) {
        if (e is OpenMeteoApiException) rethrow;
        if (attempt == maxRetries) {
          throw OpenMeteoApiException('Error de red persistente: $e', 500);
        }
        await Future.delayed(Duration(milliseconds: retryDelayMillis));
        retryDelayMillis *= 2;
      }
    }
    throw OpenMeteoApiException('Error de red persistente', 500);
  }

  void dispose() {
    _client.close();
  }
}

/// Excepción personalizada para errores de la API de Open Meteo.
class OpenMeteoApiException implements Exception {
  final String message;
  final int? statusCode;

  OpenMeteoApiException(this.message, [this.statusCode]);

  @override
  String toString() => 'OpenMeteoApiException: $message (código: $statusCode)';
}
