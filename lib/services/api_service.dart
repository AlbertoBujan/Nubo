import 'dart:convert';
import 'package:http/http.dart' as http;

/// Servicio HTTP para la API OpenData de AEMET.
///
/// Implementa el flujo de dos pasos obligatorio:
/// 1. Petición al endpoint con api_key en headers → devuelve JSON con URL temporal
/// 2. Petición GET a la URL temporal (sin token) → devuelve los datos finales
class AemetApiService {
  static const String _baseUrl = 'https://opendata.aemet.es/opendata';
  static const String _apiKey =
      'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJiaXJ0ZWJzQGdtYWlsLmNvbSIsImp0aSI6ImYwM2UxMjFmLTE2ODktNDdkMS1hYjNhLWI0MThlM2ZmMWNjMiIsImlzcyI6IkFFTUVUIiwiaWF0IjoxNzcxNDE3OTk3LCJ1c2VySWQiOiJmMDNlMTIxZi0xNjg5LTQ3ZDEtYWIzYS1iNDE4ZTNmZjFjYzIiLCJyb2xlIjoiIn0.npwJf-68OE2s0kIsRHVqjMqtmR9tedsgYrD03pjuYHc';

  final http.Client _client;

  AemetApiService({http.Client? client}) : _client = client ?? http.Client();

  /// Obtiene la predicción diaria para un municipio.
  ///
  /// [municipioId]: Código INE del municipio (ej: "28079" para Madrid).
  /// Retorna la lista JSON parseada con los datos de predicción.
  Future<List<dynamic>> fetchDailyForecast(String municipioId) async {
    final url =
        '$_baseUrl/api/prediccion/especifica/municipio/diaria/$municipioId';
    return _fetchAemetData(url);
  }

  /// Obtiene la predicción horaria para un municipio.
  ///
  /// [municipioId]: Código INE del municipio (ej: "28079" para Madrid).
  Future<List<dynamic>> fetchHourlyForecast(String municipioId) async {
    final url =
        '$_baseUrl/api/prediccion/especifica/municipio/horaria/$municipioId';
    return _fetchAemetData(url);
  }

  /// Método privado que implementa el flujo de dos pasos de la API de AEMET.
  ///
  /// PASO 1: Petición al endpoint con el token api_key en los headers.
  ///         La respuesta contiene un JSON con "estado", "datos" (url temporal), etc.
  ///
  /// PASO 2: GET a la URL temporal (sin token) para obtener el JSON final.
  Future<List<dynamic>> _fetchAemetData(String endpoint) async {
    // --- PASO 1: Solicitar la URL temporal ---
    final response1 = await _client.get(
      Uri.parse(endpoint),
      headers: {'api_key': _apiKey},
    );

    if (response1.statusCode != 200) {
      throw AemetApiException(
        'Error en paso 1: código ${response1.statusCode}',
        response1.statusCode,
      );
    }

    final body1 = jsonDecode(response1.body) as Map<String, dynamic>;
    final datosUrl = body1['datos'] as String?;

    if (datosUrl == null || datosUrl.isEmpty) {
      throw AemetApiException(
        'La API no devolvió URL de datos. Estado: ${body1['estado']}. '
        'Descripción: ${body1['descripcion'] ?? 'Sin descripción'}',
        body1['estado'] is int ? body1['estado'] as int : 500,
      );
    }

    // --- PASO 2: Obtener los datos finales desde la URL temporal ---
    final response2 = await _client.get(Uri.parse(datosUrl));

    if (response2.statusCode != 200) {
      throw AemetApiException(
        'Error en paso 2: código ${response2.statusCode}',
        response2.statusCode,
      );
    }

    // La API de AEMET puede devolver el JSON en Latin-1 (ISO-8859-1).
    // Intentamos decodificar como UTF-8 primero; si falla, usamos Latin-1.
    String decodedBody;
    try {
      decodedBody = utf8.decode(response2.bodyBytes);
    } catch (_) {
      decodedBody = latin1.decode(response2.bodyBytes);
    }
    final data = jsonDecode(decodedBody);

    if (data is List) {
      return data;
    } else {
      return [data];
    }
  }

  void dispose() {
    _client.close();
  }
}

/// Excepción personalizada para errores de la API de AEMET.
class AemetApiException implements Exception {
  final String message;
  final int? statusCode;

  AemetApiException(this.message, [this.statusCode]);

  @override
  String toString() => 'AemetApiException: $message (código: $statusCode)';
}
