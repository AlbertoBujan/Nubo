import 'package:apsl_sun_calc/apsl_sun_calc.dart';

/// Datos calculados del ciclo lunar para un día y ubicación.
class MoonData {
  final DateTime? moonrise;
  final DateTime? moonset;
  final double phase;        // 0.0-1.0 (0=nueva, 0.5=llena)
  final double illumination; // 0.0-1.0 fracción iluminada
  final String phaseName;

  MoonData({
    this.moonrise,
    this.moonset,
    required this.phase,
    required this.illumination,
    required this.phaseName,
  });

  @override
  String toString() => 'MoonData(phase: $phaseName, illumination: ${(illumination * 100).round()}%, '
      'rise: $moonrise, set: $moonset)';
}

/// Utilidad para calcular datos lunares usando apsl_sun_calc.
class MoonCalculator {
  /// Calcula los datos lunares para una fecha y ubicación.
  static MoonData calculate(DateTime date, double lat, double lng) {
    // Iluminación y fase
    final illum = SunCalc.getMoonIllumination(date);
    final phase = illum['phase'] as double;
    final fraction = illum['fraction'] as double;

    // Nombre de la fase
    final phaseName = _phaseName(phase);

    // Moonrise y moonset por búsqueda iterativa
    final moonrise = _findMoonEvent(date, lat, lng, true);
    final moonset = _findMoonEvent(date, lat, lng, false);

    return MoonData(
      moonrise: moonrise,
      moonset: moonset,
      phase: phase,
      illumination: fraction,
      phaseName: phaseName,
    );
  }

  /// Busca el momento en que la luna cruza el horizonte mediante búsqueda iterativa.
  /// [isRise] true para moonrise, false para moonset.
  static DateTime? _findMoonEvent(DateTime date, double lat, double lng, bool isRise) {
    // Buscamos en ventana de 24h desde medianoche local
    final startOfDay = DateTime(date.year, date.month, date.day);

    // Muestreo cada 10 minutos para encontrar el cruce
    const stepMinutes = 10;
    const totalSteps = (24 * 60) ~/ stepMinutes;

    double? prevAltitude;
    DateTime? crossingStart;

    for (int i = 0; i <= totalSteps; i++) {
      final t = startOfDay.add(Duration(minutes: i * stepMinutes));
      final pos = SunCalc.getMoonPosition(t, lat, lng);
      final altitude = pos['altitude'] as double;

      if (prevAltitude != null) {
        // Detectar cruce del horizonte (altitud = ~-0.01 rad para refracción)
        const horizon = -0.0145; // ~0.833° bajo horizonte por refracción atmosférica
        if (isRise && prevAltitude <= horizon && altitude > horizon) {
          crossingStart = t.subtract(Duration(minutes: stepMinutes));
          break;
        }
        if (!isRise && prevAltitude >= horizon && altitude < horizon) {
          crossingStart = t.subtract(Duration(minutes: stepMinutes));
          break;
        }
      }
      prevAltitude = altitude;
    }

    if (crossingStart == null) return null;

    // Refinar con búsqueda binaria (precisión ~1 minuto)
    var lo = crossingStart;
    var hi = crossingStart.add(Duration(minutes: stepMinutes));

    for (int i = 0; i < 8; i++) {
      final mid = lo.add(Duration(
        milliseconds: hi.difference(lo).inMilliseconds ~/ 2,
      ));
      final pos = SunCalc.getMoonPosition(mid, lat, lng);
      final alt = pos['altitude'] as double;
      const horizon = -0.0145;

      if (isRise) {
        if (alt <= horizon) {
          lo = mid;
        } else {
          hi = mid;
        }
      } else {
        if (alt >= horizon) {
          lo = mid;
        } else {
          hi = mid;
        }
      }
    }

    return lo.add(Duration(
      milliseconds: hi.difference(lo).inMilliseconds ~/ 2,
    ));
  }

  /// Nombre de la fase lunar en español.
  static String _phaseName(double phase) {
    if (phase < 0.03 || phase > 0.97) return 'Luna nueva';
    if (phase < 0.22) return 'Creciente cóncava';
    if (phase < 0.28) return 'Cuarto creciente';
    if (phase < 0.47) return 'Creciente convexa';
    if (phase < 0.53) return 'Luna llena';
    if (phase < 0.72) return 'Menguante convexa';
    if (phase < 0.78) return 'Cuarto menguante';
    return 'Menguante cóncava';
  }
}
