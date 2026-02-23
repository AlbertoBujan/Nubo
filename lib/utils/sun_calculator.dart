import 'package:sunrise_sunset_calc/sunrise_sunset_calc.dart';

/// Utilidad para calcular eventos solares (amanecer, atardecer) basados en ubicación y fecha.
class SunCalculator {
  /// Calcula los tiempos solares para una fecha y ubicación dadas usando un paquete probado.
  static SunTimes calculateTimes(DateTime date, double lat, double lng) {
    // Calculamos el amanecer y atardecer en UTC puro para evitar doble suma horaria
    final result = getSunriseSunset(lat, lng, const Duration(seconds: 0), date.toUtc());
    
    // Luego convertimos el resultado UTC puro a la zona horaria del dispositivo
    return SunTimes(
      sunrise: result.sunrise.toLocal(),
      sunset: result.sunset.toLocal(),
    );
  }
}

class SunTimes {
  final DateTime sunrise;
  final DateTime sunset;

  SunTimes({required this.sunrise, required this.sunset});
  
  @override
  String toString() => 'Sunrise: $sunrise, Sunset: $sunset';
}
