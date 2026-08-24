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

  group('DailyForecast código dominante del día', () {
    test('una hora de nubes no convierte en nuboso un día despejado', () {
      // Open Meteo reportaría "2" (el fenómeno más significativo del día),
      // pero 23 de las 24 horas están despejadas.
      final json = _buildJsonWithHourly(
        dailyCode: 2,
        hourlyCodes: [..._repeat(0, 23), 2],
      );

      final forecasts = DailyForecast.fromOpenMeteoJson(json);

      expect(forecasts[0].skyStateCode, '0');
      expect(forecasts[0].skyDescription, 'Despejado');
    });

    test('dos horas de lluvia no convierten en lluvioso un día despejado', () {
      final json = _buildJsonWithHourly(
        dailyCode: 61,
        hourlyCodes: [..._repeat(0, 22), 61, 61],
      );

      final forecasts = DailyForecast.fromOpenMeteoJson(json);

      expect(forecasts[0].skyStateCode, '0');
    });

    test('la lluvia sostenida sí representa al día', () {
      final json = _buildJsonWithHourly(
        dailyCode: 61,
        hourlyCodes: [..._repeat(0, 18), ..._repeat(61, 6)],
      );

      final forecasts = DailyForecast.fromOpenMeteoJson(json);

      expect(forecasts[0].skyStateCode, '61');
    });

    test('una tormenta breve sigue avisando pese al umbral menor', () {
      // Solo 2 horas, por debajo del umbral general de 3h, pero las tormentas
      // son relevantes aunque duren poco.
      final json = _buildJsonWithHourly(
        dailyCode: 95,
        hourlyCodes: [..._repeat(0, 22), 95, 95],
      );

      final forecasts = DailyForecast.fromOpenMeteoJson(json);

      expect(forecasts[0].skyStateCode, '95');
      expect(forecasts[0].skyDescription, 'Tormenta');
    });

    test('gana el fenómeno más severo entre los que superan su umbral', () {
      final json = _buildJsonWithHourly(
        dailyCode: 51,
        hourlyCodes: [..._repeat(51, 12), ..._repeat(61, 6), ..._repeat(0, 6)],
      );

      final forecasts = DailyForecast.fromOpenMeteoJson(json);

      // Aunque la llovizna dura el doble, la lluvia supera su umbral y es
      // el fenómeno más severo del día.
      expect(forecasts[0].skyStateCode, '61');
    });

    test('conserva la intensidad concreta dentro de la familia ganadora', () {
      final json = _buildJsonWithHourly(
        dailyCode: 61,
        hourlyCodes: [..._repeat(0, 16), ..._repeat(65, 5), ..._repeat(61, 3)],
      );

      final forecasts = DailyForecast.fromOpenMeteoJson(json);

      expect(forecasts[0].skyStateCode, '65');
      expect(forecasts[0].skyDescription, 'Lluvia fuerte');
    });

    test('la noche nublada no gana a un día de luz despejado', () {
      // 12h de noche cubierta y 12h de día despejado: pesa más el día.
      final json = _buildJsonWithHourly(
        dailyCode: 3,
        hourlyCodes: [..._repeat(3, 12), ..._repeat(0, 12)],
        isDay: [..._repeat(0, 12), ..._repeat(1, 12)],
      );

      final forecasts = DailyForecast.fromOpenMeteoJson(json);

      expect(forecasts[0].skyStateCode, '0');
    });

    test('devuelve el código diario de la API si no hay datos horarios', () {
      final forecasts = DailyForecast.fromOpenMeteoJson(_buildJson(
        time: ['2099-06-01'],
        weatherCode: [61],
      ));

      expect(forecasts[0].skyStateCode, '61');
    });

    test('cae al código diario si el día no tiene horas propias', () {
      // Las horas pertenecen a otro día distinto al del bloque `daily`.
      final json = _buildJsonWithHourly(
        dailyCode: 61,
        hourlyCodes: _repeat(0, 24),
        hourlyDate: '2099-06-05',
      );

      final forecasts = DailyForecast.fromOpenMeteoJson(json);

      expect(forecasts[0].skyStateCode, '61');
    });

    test('ignora el sufijo nocturno al agrupar por familia', () {
      final json = _buildJsonWithHourly(
        dailyCode: 0,
        hourlyCodes: [..._repeat(0, 20), ..._repeat(61, 4)],
        isDay: [..._repeat(1, 20), ..._repeat(0, 4)],
      );

      final forecasts = DailyForecast.fromOpenMeteoJson(json);

      // La lluvia nocturna supera su umbral de 3h y representa al día,
      // pero el icono diario nunca lleva sufijo 'n'.
      expect(forecasts[0].skyStateCode, '61');
    });

    test('un día compuesto solo de fenómenos breves elige el más duradero', () {
      final json = _buildJsonWithHourly(
        dailyCode: 61,
        hourlyCodes: [45, 45, 51, 61],
      );

      final forecasts = DailyForecast.fromOpenMeteoJson(json);

      expect(forecasts[0].skyStateCode, '45');
    });
  });
}

List<int> _repeat(int value, int times) => List.filled(times, value);

/// Construye un JSON de Open Meteo con un único día y sus horas.
///
/// [dailyCode] es el código que devolvería la API para el día (el "más
/// significativo"), y [hourlyCodes] las horas reales de ese día.
Map<String, dynamic> _buildJsonWithHourly({
  required int dailyCode,
  required List<int> hourlyCodes,
  List<int>? isDay,
  String date = '2099-06-01',
  String? hourlyDate,
}) {
  final hoursDate = hourlyDate ?? date;

  return {
    'daily': {
      'time': [date],
      'weather_code': [dailyCode],
      'temperature_2m_max': [25.0],
      'temperature_2m_min': [14.0],
      'precipitation_probability_max': [20],
    },
    'hourly': {
      'time': [
        for (int i = 0; i < hourlyCodes.length; i++)
          '${hoursDate}T${i.toString().padLeft(2, '0')}:00',
      ],
      'weather_code': hourlyCodes,
      'is_day': ?isDay,
    },
  };
}
