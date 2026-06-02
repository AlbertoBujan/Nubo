import 'package:flutter_test/flutter_test.dart';
import 'package:nubo/models/hourly_forecast.dart';

// Usamos fechas en 2099 para que nunca sean filtradas por el filtro de "pasadas"
const _futureTime = '2099-06-01T12:00';

Map<String, dynamic> _buildJson({
  List<String>? time,
  List<dynamic>? temperature,
  List<dynamic>? weatherCode,
  List<dynamic>? isDay,
  List<dynamic>? precipProb,
  List<dynamic>? humidity,
  List<dynamic>? windSpeed,
  List<dynamic>? windDirection,
  List<dynamic>? dewPoint,
}) {
  return {
    'hourly': {
      'time': time ?? [_futureTime],
      'temperature_2m': temperature ?? [22.6],
      'weather_code': weatherCode ?? [0],
      'is_day': isDay ?? [1],
      'precipitation_probability': precipProb ?? [10],
      'relative_humidity_2m': humidity ?? [55],
      'wind_speed_10m': windSpeed ?? [15],
      'wind_direction_10m': windDirection ?? [90],
      'dew_point_2m': dewPoint ?? [12.0],
    }
  };
}

void main() {
  group('HourlyForecast.fromOpenMeteoJson', () {
    test('parsea entrada completa correctamente', () {
      final forecasts = HourlyForecast.fromOpenMeteoJson(_buildJson());

      expect(forecasts.length, 1);
      expect(forecasts[0].temperature, 23); // 22.6 redondeado
      expect(forecasts[0].skyStateCode, '0');
      expect(forecasts[0].skyDescription, 'Despejado');
      expect(forecasts[0].precipitationProbability, 10);
      expect(forecasts[0].humidity, 55);
      expect(forecasts[0].windSpeed, 15);
      expect(forecasts[0].windDirection, 'E'); // 90° → E
      expect(forecasts[0].windDirectionDegrees, 90);
    });

    test('aplica sufijo "n" al código cuando is_day = 0', () {
      final forecasts = HourlyForecast.fromOpenMeteoJson(_buildJson(
        weatherCode: [0],
        isDay: [0],
      ));

      expect(forecasts[0].skyStateCode, '0n');
      expect(forecasts[0].skyDescription, 'Despejado'); // 0n → moon → Despejado
    });

    test('no aplica sufijo cuando is_day = 1', () {
      final forecasts = HourlyForecast.fromOpenMeteoJson(_buildJson(
        weatherCode: [0],
        isDay: [1],
      ));

      expect(forecasts[0].skyStateCode, '0');
    });

    test('filtra entradas pasadas (más de 1h en el pasado)', () {
      final pastTime = DateTime.now().subtract(const Duration(hours: 2));
      final pastStr =
          '${pastTime.year}-${pastTime.month.toString().padLeft(2, '0')}-${pastTime.day.toString().padLeft(2, '0')}T${pastTime.hour.toString().padLeft(2, '0')}:00';

      final forecasts = HourlyForecast.fromOpenMeteoJson(_buildJson(
        time: [pastStr, _futureTime],
        temperature: [10.0, 22.0],
        weatherCode: [0, 0],
        isDay: [1, 1],
        precipProb: [0, 0],
        humidity: [50, 55],
        windSpeed: [10, 15],
        windDirection: [90, 90],
        dewPoint: [5.0, 12.0],
      ));

      expect(forecasts.length, 1);
      expect(forecasts[0].temperature, 22);
    });

    test('devuelve lista vacía si no hay clave "hourly"', () {
      final forecasts = HourlyForecast.fromOpenMeteoJson({});
      expect(forecasts, isEmpty);
    });

    test('devuelve lista vacía si hourly es null', () {
      final forecasts = HourlyForecast.fromOpenMeteoJson({'hourly': null});
      expect(forecasts, isEmpty);
    });

    test('omite entrada con fecha inválida', () {
      final forecasts = HourlyForecast.fromOpenMeteoJson(_buildJson(
        time: ['fecha-invalida', _futureTime],
        temperature: [10.0, 22.0],
        weatherCode: [0, 0],
        isDay: [1, 1],
        precipProb: [0, 0],
        humidity: [50, 55],
        windSpeed: [10, 15],
        windDirection: [90, 90],
        dewPoint: [5.0, 12.0],
      ));

      expect(forecasts.length, 1);
    });

    test('maneja windDirection null sin crash', () {
      final forecasts = HourlyForecast.fromOpenMeteoJson(_buildJson(
        windDirection: [null],
      ));

      expect(forecasts[0].windDirection, isNull);
      expect(forecasts[0].windDirectionDegrees, isNull);
    });

    test('maneja arrays vacíos en campos opcionales sin crash', () {
      final forecasts = HourlyForecast.fromOpenMeteoJson({
        'hourly': {
          'time': [_futureTime],
          'temperature_2m': [],
          'weather_code': [],
          'is_day': [],
          'precipitation_probability': [],
          'relative_humidity_2m': [],
          'wind_speed_10m': [],
          'wind_direction_10m': [],
          'dew_point_2m': [],
        }
      });

      expect(forecasts.length, 1);
      expect(forecasts[0].temperature, isNull);
      expect(forecasts[0].windSpeed, isNull);
    });
  });

  group('HourlyForecast._degreesToCompass (via fromOpenMeteoJson)', () {
    HourlyForecast _parse(num degrees) {
      final f = HourlyForecast.fromOpenMeteoJson(_buildJson(windDirection: [degrees]));
      return f.first;
    }

    test('0° → N', () => expect(_parse(0).windDirection, 'N'));
    test('90° → E', () => expect(_parse(90).windDirection, 'E'));
    test('180° → S', () => expect(_parse(180).windDirection, 'S'));
    test('270° → W', () => expect(_parse(270).windDirection, 'W'));
    test('45° → NE', () => expect(_parse(45).windDirection, 'NE'));
    test('135° → SE', () => expect(_parse(135).windDirection, 'SE'));
    test('225° → SW', () => expect(_parse(225).windDirection, 'SW'));
    test('315° → NW', () => expect(_parse(315).windDirection, 'NW'));
    test('337.5° → NNW', () => expect(_parse(337.5).windDirection, 'NNW'));
    test('348° → NNW', () => expect(_parse(348).windDirection, 'NNW'));
    test('349° → N (cruza el umbral)', () => expect(_parse(349).windDirection, 'N'));
  });
}
