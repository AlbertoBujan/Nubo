import 'package:flutter_test/flutter_test.dart';
import 'package:nubo/models/daily_forecast.dart';

Map<String, dynamic> _buildJson({
  List<String>? time,
  List<dynamic>? weatherCode,
  List<dynamic>? tempMax,
  List<dynamic>? tempMin,
  List<dynamic>? precipProb,
}) {
  return {
    'daily': {
      'time': time ?? ['2099-06-01', '2099-06-02'],
      'weather_code': weatherCode ?? [0, 61],
      'temperature_2m_max': tempMax ?? [28.4, 22.7],
      'temperature_2m_min': tempMin ?? [15.1, 12.9],
      'precipitation_probability_max': precipProb ?? [10, 80],
    }
  };
}

void main() {
  group('DailyForecast.fromOpenMeteoJson', () {
    test('parsea lista completa correctamente', () {
      final forecasts = DailyForecast.fromOpenMeteoJson(_buildJson());

      expect(forecasts.length, 2);
      expect(forecasts[0].date, DateTime(2099, 6, 1));
      expect(forecasts[0].tempMax, 28);
      expect(forecasts[0].tempMin, 15);
      expect(forecasts[0].precipitationProbability, 10);
      expect(forecasts[0].skyStateCode, '0');
      expect(forecasts[0].skyDescription, 'Despejado');
    });

    test('redondea temperaturas correctamente', () {
      final forecasts = DailyForecast.fromOpenMeteoJson(_buildJson(
        tempMax: [22.5],
        tempMin: [10.4],
        time: ['2099-06-01'],
        weatherCode: [0],
        precipProb: [0],
      ));

      expect(forecasts[0].tempMax, 23); // 22.5 → 23
      expect(forecasts[0].tempMin, 10); // 10.4 → 10
    });

    test('devuelve lista vacía si no hay clave "daily"', () {
      final forecasts = DailyForecast.fromOpenMeteoJson({});
      expect(forecasts, isEmpty);
    });

    test('devuelve lista vacía si daily es null', () {
      final forecasts = DailyForecast.fromOpenMeteoJson({'daily': null});
      expect(forecasts, isEmpty);
    });

    test('omite entradas con fecha inválida', () {
      final forecasts = DailyForecast.fromOpenMeteoJson(_buildJson(
        time: ['2099-06-01', 'fecha-invalida', '2099-06-03'],
        weatherCode: [0, 61, 80],
        tempMax: [25.0, 20.0, 18.0],
        tempMin: [10.0, 9.0, 8.0],
        precipProb: [5, 40, 70],
      ));

      expect(forecasts.length, 2);
      expect(forecasts[0].date, DateTime(2099, 6, 1));
      expect(forecasts[1].date, DateTime(2099, 6, 3));
    });

    test('maneja weatherCode null con descripción por defecto', () {
      final forecasts = DailyForecast.fromOpenMeteoJson(_buildJson(
        time: ['2099-06-01'],
        weatherCode: [null],
        tempMax: [20.0],
        tempMin: [10.0],
        precipProb: [0],
      ));

      expect(forecasts.length, 1);
      expect(forecasts[0].skyStateCode, '');
      expect(forecasts[0].skyDescription, 'Desconocido');
    });

    test('maneja código desconocido con descripción por defecto', () {
      final forecasts = DailyForecast.fromOpenMeteoJson(_buildJson(
        time: ['2099-06-01'],
        weatherCode: [999],
        tempMax: [20.0],
        tempMin: [10.0],
        precipProb: [0],
      ));

      expect(forecasts[0].skyDescription, 'Desconocido');
    });

    test('maneja arrays más cortos que time sin crash', () {
      final forecasts = DailyForecast.fromOpenMeteoJson({
        'daily': {
          'time': ['2099-06-01', '2099-06-02'],
          'weather_code': [0],       // solo 1 elemento
          'temperature_2m_max': [],  // vacío
          'temperature_2m_min': [],  // vacío
          'precipitation_probability_max': [10],
        }
      });

      expect(forecasts.length, 2);
      expect(forecasts[1].tempMax, isNull);
      expect(forecasts[1].tempMin, isNull);
    });

    test('maneja temperaturas null en el array', () {
      final forecasts = DailyForecast.fromOpenMeteoJson(_buildJson(
        time: ['2099-06-01'],
        weatherCode: [0],
        tempMax: [null],
        tempMin: [null],
        precipProb: [null],
      ));

      expect(forecasts[0].tempMax, isNull);
      expect(forecasts[0].tempMin, isNull);
      expect(forecasts[0].precipitationProbability, isNull);
    });

    test('mapea código 61 a lluvia débil', () {
      final forecasts = DailyForecast.fromOpenMeteoJson(_buildJson(
        time: ['2099-06-01'],
        weatherCode: [61],
        tempMax: [20.0],
        tempMin: [10.0],
        precipProb: [80],
      ));

      expect(forecasts[0].skyStateCode, '61');
      expect(forecasts[0].skyDescription, 'Lluvia débil');
    });
  });
}
