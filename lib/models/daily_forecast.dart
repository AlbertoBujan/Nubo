/// Modelo para la predicción meteorológica diaria de AEMET.
///
/// Parsea el complejo JSON de la API de AEMET, extrayendo temperatura
/// máxima/mínima, estado del cielo y probabilidad de precipitación.
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

  /// Parsea la lista de predicciones diarias del JSON de AEMET.
  ///
  /// La estructura del JSON de AEMET es compleja:
  /// ```json
  /// [{ "prediccion": { "dia": [
  ///   { "fecha": "2024-01-01T00:00:00",
  ///     "temperatura": { "maxima": 15, "minima": 5 },
  ///     "estadoCielo": [{ "value": "12", "descripcion": "Poco nuboso", "periodo": "00-24" }],
  ///     "probPrecipitacion": [{ "value": 10, "periodo": "00-24" }]
  ///   }, ...
  /// ] } }]
  /// ```
  static List<DailyForecast> fromAemetJson(List<dynamic> json) {
    final List<DailyForecast> forecasts = [];

    if (json.isEmpty) return forecasts;

    final prediccion = json[0]['prediccion'];
    if (prediccion == null) return forecasts;

    final dias = prediccion['dia'] as List<dynamic>? ?? [];

    for (final dia in dias) {
      try {
        final fecha = DateTime.parse(dia['fecha'] as String);

        // Temperaturas máxima y mínima
        final temp = dia['temperatura'] as Map<String, dynamic>?;
        final maxima = temp?['maxima'];
        final minima = temp?['minima'];

        // Estado del cielo: tomamos el primer valor con periodo "00-24"
        // o el primer valor disponible
        final estadoCielo = dia['estadoCielo'] as List<dynamic>? ?? [];
        String skyCode = '';
        String skyDesc = '';
        for (final estado in estadoCielo) {
          final value = estado['value']?.toString() ?? '';
          if (value.isNotEmpty) {
            skyCode = value;
            skyDesc = estado['descripcion']?.toString() ?? '';
            // Preferimos el periodo de todo el día
            final periodo = estado['periodo']?.toString() ?? '';
            if (periodo == '00-24') break;
          }
        }

        // Probabilidad de precipitación: tomamos el mayor valor del día
        final probPrecip =
            dia['probPrecipitacion'] as List<dynamic>? ?? [];
        int? maxPrecipProb;
        for (final prob in probPrecip) {
          final value = prob['value'];
          if (value != null) {
            final intVal = value is int ? value : int.tryParse(value.toString());
            if (intVal != null) {
              maxPrecipProb = (maxPrecipProb == null)
                  ? intVal
                  : (intVal > maxPrecipProb ? intVal : maxPrecipProb);
            }
          }
        }

        forecasts.add(DailyForecast(
          date: fecha,
          tempMax: maxima is int ? maxima : int.tryParse(maxima?.toString() ?? ''),
          tempMin: minima is int ? minima : int.tryParse(minima?.toString() ?? ''),
          skyStateCode: skyCode,
          skyDescription: skyDesc,
          precipitationProbability: maxPrecipProb,
        ));
      } catch (e) {
        // Si falla el parseo de un día, continuamos con el siguiente
        continue;
      }
    }

    return forecasts;
  }
}
