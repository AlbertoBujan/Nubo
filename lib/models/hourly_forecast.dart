/// Modelo para la predicción meteorológica horaria de AEMET.
///
/// Parsea el JSON horario, extrayendo temperatura, estado del cielo
/// y precipitación para cada hora del día.
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

  /// Parsea la lista de predicciones horarias del JSON de AEMET.
  ///
  /// La estructura del JSON horario de AEMET:
  /// ```json
  /// [{ "prediccion": { "dia": [
  ///   { "fecha": "2024-01-01T00:00:00",
  ///     "temperatura": [{ "value": 5, "periodo": "00" }, { "value": 6, "periodo": "01" }, ...],
  ///     "estadoCielo": [{ "value": "12", "descripcion": "Poco nuboso", "periodo": "00" }, ...],
  ///     "probPrecipitacion": [{ "value": 0, "periodo": "0006" }, ...],
  ///     "humedadRelativa": [{ "value": 80, "periodo": "00" }, ...],
  ///     "vientoAndRachaMax": [{ "direccion": ["N"], "velocidad": [10], "periodo": "00" }, ...]
  ///   }, ...
  /// ] } }]
  /// ```
  static List<HourlyForecast> fromAemetJson(List<dynamic> json) {
    final List<HourlyForecast> forecasts = [];

    if (json.isEmpty) return forecasts;

    final prediccion = json[0]['prediccion'];
    if (prediccion == null) return forecasts;

    final dias = prediccion['dia'] as List<dynamic>? ?? [];

    for (final dia in dias) {
      try {
        final fecha = DateTime.parse(dia['fecha'] as String);

        // Parseamos las temperaturas por hora
        final temperaturas = dia['temperatura'] as List<dynamic>? ?? [];
        final tempMap = <String, int>{};
        for (final t in temperaturas) {
          final periodo = t['periodo']?.toString() ?? '';
          final value = t['value'];
          if (periodo.isNotEmpty && value != null) {
            tempMap[periodo] = value is int ? value : (int.tryParse(value.toString()) ?? 0);
          }
        }

        // Estado del cielo por hora
        final estadoCielo = dia['estadoCielo'] as List<dynamic>? ?? [];
        final skyMap = <String, Map<String, String>>{};
        for (final e in estadoCielo) {
          final periodo = e['periodo']?.toString() ?? '';
          final value = e['value']?.toString() ?? '';
          final desc = e['descripcion']?.toString() ?? '';
          if (periodo.isNotEmpty && periodo.length <= 2) {
            skyMap[periodo] = {'code': value, 'desc': desc};
          }
        }

        // Probabilidad de precipitación (viene en rangos de 6h: "0006", "0612", etc.)
        final probPrecip =
            dia['probPrecipitacion'] as List<dynamic>? ?? [];
        final precipMap = <String, int>{};
        for (final p in probPrecip) {
          final periodo = p['periodo']?.toString() ?? '';
          final value = p['value'];
          if (periodo.isNotEmpty && value != null) {
            final intVal = value is int ? value : (int.tryParse(value.toString()) ?? 0);
            precipMap[periodo] = intVal;
          }
        }

        // Humedad relativa por hora
        final humedadRel = dia['humedadRelativa'] as List<dynamic>? ?? [];
        final humMap = <String, int>{};
        for (final h in humedadRel) {
          final periodo = h['periodo']?.toString() ?? '';
          final value = h['value'];
          if (periodo.isNotEmpty && value != null) {
            humMap[periodo] = value is int ? value : (int.tryParse(value.toString()) ?? 0);
          }
        }

        // Creamos un forecast por cada hora que tenga temperatura
        for (final entry in tempMap.entries) {
          final hora = entry.key.padLeft(2, '0');
          final horaInt = int.tryParse(hora) ?? 0;
          final dateTime = DateTime(fecha.year, fecha.month, fecha.day, horaInt);

          final sky = skyMap[hora];
          
          // Buscar precipitación para esta hora (en rangos de 6h)
          int? precipProb;
          for (final pe in precipMap.entries) {
            if (pe.key.length == 4) {
              final start = int.tryParse(pe.key.substring(0, 2)) ?? 0;
              final end = int.tryParse(pe.key.substring(2, 4)) ?? 0;
              if (horaInt >= start && horaInt < end) {
                precipProb = pe.value;
                break;
              }
            }
          }

          forecasts.add(HourlyForecast(
            dateTime: dateTime,
            temperature: entry.value,
            skyStateCode: sky?['code'] ?? '',
            skyDescription: sky?['desc'] ?? '',
            precipitationProbability: precipProb,
            humidity: humMap[hora],
          ));
        }
      } catch (e) {
        continue;
      }
    }

    // Ordenamos por fecha/hora
    forecasts.sort((a, b) => a.dateTime.compareTo(b.dateTime));
    return forecasts;
  }
}
