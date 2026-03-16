import 'weather_enums.dart';

/// Modelo para la predicción meteorológica horaria.
///
/// Parsea el JSON horario de Open Meteo.
class HourlyForecast {
  final DateTime dateTime;
  final int? temperature;
  final String skyStateCode;
  final String skyDescription;
  final int? precipitationProbability;
  final int? humidity;
  final int? windSpeed;
  final String? windDirection;

  HourlyForecast({
    required this.dateTime,
    this.temperature,
    required this.skyStateCode,
    required this.skyDescription,
    this.precipitationProbability,
    this.humidity,
    this.windSpeed,
    this.windDirection,
  });

  /// Parsea la respuesta columbar (arrays paralelos) de Open Meteo
  static List<HourlyForecast> fromOpenMeteoJson(Map<String, dynamic> json) {
    final List<HourlyForecast> forecasts = [];

    final hourly = json['hourly'];
    if (hourly == null) return forecasts;

    final time = hourly['time'] as List<dynamic>? ?? [];
    final temperature = hourly['temperature_2m'] as List<dynamic>? ?? [];
    final precipProb = hourly['precipitation_probability'] as List<dynamic>? ?? [];
    final weatherCode = hourly['weather_code'] as List<dynamic>? ?? [];
    final humidity = hourly['relative_humidity_2m'] as List<dynamic>? ?? [];
    final windSpeed = hourly['wind_speed_10m'] as List<dynamic>? ?? [];
    final windDirection = hourly['wind_direction_10m'] as List<dynamic>? ?? [];
    final isDay = hourly['is_day'] as List<dynamic>? ?? [];

    for (int i = 0; i < time.length; i++) {
        final date = DateTime.tryParse(time[i] as String);
        if (date == null) continue;
        
         String? codeVal = weatherCode.length > i ? weatherCode[i]?.toString() : null;
         
         // Si es de noche, aplicamos el sufijo 'n' al código WMO
         if (codeVal != null && isDay.length > i) {
           final dayFlag = isDay[i];
           if (dayFlag == 0) {
             codeVal = '${codeVal}n';
           }
         }
         
        final skyCodeOb = WeatherCode.fromCode(codeVal);
        
        String? windDirStr;
        if (windDirection.length > i && windDirection[i] != null) {
             windDirStr = _degreesToCompass(windDirection[i] as num);
        }

        forecasts.add(HourlyForecast(
          dateTime: date,
          temperature: temperature.length > i ? (temperature[i] as num?)?.round() : null,
          skyStateCode: codeVal ?? '',
          skyDescription: skyCodeOb.description,
          precipitationProbability: precipProb.length > i ? (precipProb[i] as num?)?.round() : null,
          humidity: humidity.length > i ? (humidity[i] as num?)?.round() : null,
          windSpeed: windSpeed.length > i ? (windSpeed[i] as num?)?.round() : null,
          windDirection: windDirStr,
        ));
    }
    
    // Descartamos predicciones pasadas por más de 1 hora
    final now = DateTime.now();
    return forecasts.where((f) => f.dateTime.isAfter(now.subtract(const Duration(hours: 1)))).toList();
  }
  
  static String _degreesToCompass(num degrees) {
    const val = [
      "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
      "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    ];
    int index = ((degrees / 22.5) + 0.5).floor() % 16;
    return val[index];
  }
}
