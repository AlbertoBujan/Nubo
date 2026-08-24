import 'weather_enums.dart';

/// Modelo para la predicción meteorológica diaria.
///
/// Parsea el JSON array de la API de Open Meteo.
class DailyForecast {
  final DateTime date;
  final int? tempMax;
  final int? tempMin;
  final String skyStateCode; // Código AEMET del estado del cielo
  final String skyDescription;
  final int? precipitationProbability;

  DailyForecast({
    required this.date,
    this.tempMax,
    this.tempMin,
    required this.skyStateCode,
    required this.skyDescription,
    this.precipitationProbability,
  });

  /// Peso de una hora diurna al decidir la nubosidad dominante del día.
  ///
  /// Las horas de noche cuentan 1. Así una noche nublada no "gana" a un día
  /// entero despejado, que es lo que el usuario percibe como el tiempo del día.
  static const int _dayHourWeight = 2;

  /// Parsea la respuesta "columbar" (arrays paralelos) de Open Meteo bajo la clave "daily".
  ///
  /// Open Meteo devuelve en `daily.weather_code` el fenómeno *más significativo*
  /// del día, no el más duradero: una hora de nubes en un día despejado ya
  /// convierte el día entero en "intervalos nubosos". Para evitar ese sesgo
  /// pesimista, si el JSON trae datos horarios se recalcula el código de cada
  /// día a partir de sus horas (ver [_dominantCodeForDay]); si no los trae, se
  /// usa el código diario de Open Meteo tal cual.
  static List<DailyForecast> fromOpenMeteoJson(Map<String, dynamic> json) {
    final List<DailyForecast> forecasts = [];

    final daily = json['daily'];
    if (daily == null) return forecasts;

    final time = daily['time'] as List<dynamic>? ?? [];
    final weatherCode = daily['weather_code'] as List<dynamic>? ?? [];
    final tempMax = daily['temperature_2m_max'] as List<dynamic>? ?? [];
    final tempMin = daily['temperature_2m_min'] as List<dynamic>? ?? [];
    final precipProb = daily['precipitation_probability_max'] as List<dynamic>? ?? [];

    final hoursByDay = _hourlySamplesByDay(json);

    for (int i = 0; i < time.length; i++) {
        final rawDate = time[i] as String;
        final date = DateTime.tryParse(rawDate);
        if (date == null) continue;

        final apiCode = weatherCode.length > i ? weatherCode[i]?.toString() : null;
        final dayHours = hoursByDay[_dayKey(rawDate)];
        final codeVal = _dominantCodeForDay(dayHours) ?? apiCode;
        final skyCodeOb = WeatherCode.fromCode(codeVal);

        forecasts.add(DailyForecast(
          date: date,
          tempMax: tempMax.length > i ? (tempMax[i] as num?)?.round() : null,
          tempMin: tempMin.length > i ? (tempMin[i] as num?)?.round() : null,
          skyStateCode: codeVal ?? '',
          skyDescription: skyCodeOb.description,
          precipitationProbability: precipProb.length > i ? (precipProb[i] as num?)?.round() : null,
        ));
    }
    return forecasts;
  }

  /// Agrupa las horas del bloque `hourly` por día (`YYYY-MM-DD`).
  ///
  /// Se leen los arrays crudos en vez de reutilizar `HourlyForecast`, porque
  /// este descarta las horas ya pasadas y aquí interesa el día completo.
  static Map<String, List<_HourSample>> _hourlySamplesByDay(
      Map<String, dynamic> json) {
    final result = <String, List<_HourSample>>{};

    final hourly = json['hourly'];
    if (hourly == null) return result;

    final time = hourly['time'] as List<dynamic>? ?? [];
    final weatherCode = hourly['weather_code'] as List<dynamic>? ?? [];
    final isDay = hourly['is_day'] as List<dynamic>? ?? [];

    for (int i = 0; i < time.length; i++) {
      if (weatherCode.length <= i) break;

      final numeric = WeatherCodeGroup.numericValue(weatherCode[i]?.toString());
      if (numeric == null) continue;

      // Sin dato de is_day asumimos hora diurna: no distorsiona el peso
      // relativo mientras se aplique igual a todas las horas del día.
      final daytime = isDay.length > i ? isDay[i] != 0 : true;

      result
          .putIfAbsent(_dayKey(time[i] as String), () => <_HourSample>[])
          .add(_HourSample(code: numeric, isDay: daytime));
    }

    return result;
  }

  /// Clave de agrupación por día: los 10 primeros caracteres (`YYYY-MM-DD`).
  ///
  /// Open Meteo devuelve todas las marcas de tiempo en la zona horaria de la
  /// localización (`timezone=auto`), así que comparar el prefijo textual evita
  /// tener que reconstruir fechas y no introduce desfases de huso.
  static String _dayKey(String timestamp) =>
      timestamp.length >= 10 ? timestamp.substring(0, 10) : timestamp;

  /// Calcula el código WMO que mejor representa un día a partir de sus horas.
  ///
  /// Prioriza el fenómeno significativo más severo que dure al menos sus horas
  /// mínimas ([WeatherCodeGroup.minHours]) — así una tormenta breve pero real
  /// sigue avisando — y, si ninguno llega al umbral, devuelve la nubosidad
  /// dominante ponderando las horas de luz.
  ///
  /// Devuelve `null` si no hay horas con las que decidir, para que quien llame
  /// pueda recurrir al código diario de la API.
  static String? _dominantCodeForDay(List<_HourSample>? hours) {
    if (hours == null || hours.isEmpty) return null;

    final hoursByGroup = <WeatherCodeGroup, int>{};
    final weightByGroup = <WeatherCodeGroup, int>{};
    final codeCountsByGroup = <WeatherCodeGroup, Map<int, int>>{};

    for (final hour in hours) {
      final group = WeatherCodeGroup.fromCode(hour.code.toString());
      hoursByGroup.update(group, (v) => v + 1, ifAbsent: () => 1);
      weightByGroup.update(
        group,
        (v) => v + (hour.isDay ? _dayHourWeight : 1),
        ifAbsent: () => hour.isDay ? _dayHourWeight : 1,
      );
      codeCountsByGroup
          .putIfAbsent(group, () => <int, int>{})
          .update(hour.code, (v) => v + 1, ifAbsent: () => 1);
    }

    final winner = _pickGroup(hoursByGroup, weightByGroup);
    return _representativeCode(codeCountsByGroup[winner]!).toString();
  }

  /// Elige la familia que representa al día.
  static WeatherCodeGroup _pickGroup(
    Map<WeatherCodeGroup, int> hoursByGroup,
    Map<WeatherCodeGroup, int> weightByGroup,
  ) {
    // 1. Fenómenos significativos que duran lo suficiente → gana el más severo.
    final significant = hoursByGroup.keys
        .where((g) => g.isSignificant && hoursByGroup[g]! >= g.minHours)
        .toList();
    if (significant.isNotEmpty) {
      return significant.reduce((a, b) => a.severity >= b.severity ? a : b);
    }

    // 2. Si no, la nubosidad dominante: más peso gana, y a igualdad el cielo
    //    más cargado, para no vender como despejado un día a medias.
    final cloudGroups =
        weightByGroup.keys.where((g) => !g.isSignificant).toList();
    if (cloudGroups.isNotEmpty) {
      return cloudGroups.reduce((a, b) {
        final weightA = weightByGroup[a]!;
        final weightB = weightByGroup[b]!;
        if (weightA != weightB) return weightA > weightB ? a : b;
        return a.severity >= b.severity ? a : b;
      });
    }

    // 3. Día compuesto solo por fenómenos breves (ninguno llega a su umbral):
    //    gana el más duradero, y a igualdad el más severo.
    return hoursByGroup.keys.reduce((a, b) {
      final hoursA = hoursByGroup[a]!;
      final hoursB = hoursByGroup[b]!;
      if (hoursA != hoursB) return hoursA > hoursB ? a : b;
      return a.severity >= b.severity ? a : b;
    });
  }

  /// Dentro de la familia ganadora, elige el código concreto más frecuente
  /// (a igualdad, el de mayor intensidad) para conservar el matiz del icono
  /// y de la descripción: "lluvia fuerte" en vez de un genérico "lluvia".
  static int _representativeCode(Map<int, int> codeCounts) {
    return codeCounts.keys.reduce((a, b) {
      final countA = codeCounts[a]!;
      final countB = codeCounts[b]!;
      if (countA != countB) return countA > countB ? a : b;
      return a >= b ? a : b;
    });
  }
}

/// Una hora suelta reducida a lo que necesita la agregación diaria.
class _HourSample {
  final int code;
  final bool isDay;

  const _HourSample({required this.code, required this.isDay});
}
