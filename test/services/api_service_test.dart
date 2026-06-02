import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:nubo/services/api_service.dart';

const _validForecastJson = {
  'latitude': 40.42,
  'longitude': -3.70,
  'hourly': {
    'time': ['2099-06-01T12:00'],
    'temperature_2m': [22.5],
    'weather_code': [0],
    'is_day': [1],
    'precipitation_probability': [10],
    'relative_humidity_2m': [55],
    'wind_speed_10m': [15],
    'wind_direction_10m': [90],
    'dew_point_2m': [12.0],
  },
  'daily': {
    'time': ['2099-06-01'],
    'weather_code': [0],
    'temperature_2m_max': [28.0],
    'temperature_2m_min': [15.0],
    'precipitation_probability_max': [10],
  },
};

void main() {
  group('OpenMeteoApiService.fetchForecast', () {
    test('200 → devuelve el JSON parseado', () async {
      final client = MockClient(
        (_) async => http.Response(jsonEncode(_validForecastJson), 200),
      );
      final service = OpenMeteoApiService(client: client);

      final result = await service.fetchForecast(40.42, -3.70);

      expect(result, isA<Map<String, dynamic>>());
      expect(result['latitude'], 40.42);
      expect(result.containsKey('hourly'), isTrue);
      expect(result.containsKey('daily'), isTrue);
    });

    test('la URL incluye lat, lon y parámetros requeridos', () async {
      Uri? capturedUri;
      final client = MockClient((request) async {
        capturedUri = request.url;
        return http.Response(jsonEncode(_validForecastJson), 200);
      });
      final service = OpenMeteoApiService(client: client);

      await service.fetchForecast(40.4168, -3.7038);

      expect(capturedUri, isNotNull);
      final url = capturedUri.toString();
      expect(url, contains('40.4168'));
      expect(url, contains('-3.7038'));
      expect(url, contains('hourly='));
      expect(url, contains('daily='));
      expect(url, contains('timezone=auto'));
      expect(url, contains('temperature_2m'));
      expect(url, contains('weather_code'));
    });

    test('404 → lanza OpenMeteoApiException con statusCode 404', () async {
      final client = MockClient((_) async => http.Response('Not Found', 404));
      final service = OpenMeteoApiService(client: client);

      await expectLater(
        service.fetchForecast(40.42, -3.70),
        throwsA(isA<OpenMeteoApiException>().having(
          (e) => e.statusCode,
          'statusCode',
          404,
        )),
      );
    });

    test('500 → lanza OpenMeteoApiException con statusCode 500', () async {
      final client = MockClient(
        (_) async => http.Response('Internal Server Error', 500),
      );
      final service = OpenMeteoApiService(client: client);

      await expectLater(
        service.fetchForecast(40.42, -3.70),
        throwsA(isA<OpenMeteoApiException>().having(
          (e) => e.statusCode,
          'statusCode',
          500,
        )),
      );
    });

    test('403 → lanza OpenMeteoApiException con statusCode 403', () async {
      final client = MockClient((_) async => http.Response('Forbidden', 403));
      final service = OpenMeteoApiService(client: client);

      await expectLater(
        service.fetchForecast(40.42, -3.70),
        throwsA(isA<OpenMeteoApiException>().having(
          (e) => e.statusCode,
          'statusCode',
          403,
        )),
      );
    });

    test('OpenMeteoApiException contiene mensaje descriptivo', () async {
      final client = MockClient((_) async => http.Response('Bad Gateway', 502));
      final service = OpenMeteoApiService(client: client);

      try {
        await service.fetchForecast(40.42, -3.70);
        fail('Debería haber lanzado OpenMeteoApiException');
      } on OpenMeteoApiException catch (e) {
        expect(e.message, isNotEmpty);
        expect(e.statusCode, 502);
      }
    });

    // Tarda ~500ms (un reintento real con delay)
    test('429 seguido de 200 → reintenta y devuelve resultado', () async {
      int callCount = 0;
      final client = MockClient((_) async {
        callCount++;
        if (callCount == 1) return http.Response('Too Many Requests', 429);
        return http.Response(jsonEncode(_validForecastJson), 200);
      });
      final service = OpenMeteoApiService(client: client);

      final result = await service.fetchForecast(40.42, -3.70);

      expect(result, isA<Map<String, dynamic>>());
      expect(callCount, greaterThanOrEqualTo(2));
    });

    test('dispose no lanza excepción', () {
      final client = MockClient((_) async => http.Response('', 200));
      final service = OpenMeteoApiService(client: client);
      expect(() => service.dispose(), returnsNormally);
    });

    test('JSON inválido en respuesta 200 → lanza excepción', () async {
      final client = MockClient((_) async => http.Response('no-es-json', 200));
      final service = OpenMeteoApiService(client: client);

      await expectLater(
        service.fetchForecast(40.42, -3.70),
        throwsA(anything), // FormatException o OpenMeteoApiException
      );
    });
  });

  group('OpenMeteoApiException', () {
    test('toString incluye mensaje y statusCode', () {
      final e = OpenMeteoApiException('Error de prueba', 503);
      final str = e.toString();
      expect(str, contains('Error de prueba'));
      expect(str, contains('503'));
    });

    test('statusCode puede ser null sin lanzar excepción', () {
      final e = OpenMeteoApiException('Sin código');
      expect(e.statusCode, isNull);
      expect(() => e.toString(), returnsNormally);
    });

    test('es una Exception', () {
      expect(OpenMeteoApiException('test'), isA<Exception>());
    });
  });
}
