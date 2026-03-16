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

  /// Parsea la respuesta "columbar" (arrays paralelos) de Open Meteo bajo la clave "daily".
  static List<DailyForecast> fromOpenMeteoJson(Map<String, dynamic> json) {
    final List<DailyForecast> forecasts = [];

    final daily = json['daily'];
    if (daily == null) return forecasts;

    final time = daily['time'] as List<dynamic>? ?? [];
    final weatherCode = daily['weather_code'] as List<dynamic>? ?? [];
    final tempMax = daily['temperature_2m_max'] as List<dynamic>? ?? [];
    final tempMin = daily['temperature_2m_min'] as List<dynamic>? ?? [];
    final precipProb = daily['precipitation_probability_max'] as List<dynamic>? ?? [];

    for (int i = 0; i < time.length; i++) {
        final date = DateTime.tryParse(time[i] as String);
        if (date == null) continue;

        final codeVal = weatherCode.length > i ? weatherCode[i]?.toString() : null;
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
}
