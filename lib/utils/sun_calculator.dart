import 'package:sunrise_sunset_calc/sunrise_sunset_calc.dart';

/// Utilidad para calcular eventos solares (amanecer, atardecer) basados en ubicación y fecha.
class SunCalculator {
  /// Calcula los tiempos solares para una fecha y ubicación dadas usando un paquete probado.
  static SunTimes calculateTimes(DateTime date, double lat, double lng) {
    // Calculamos el amanecer y atardecer con el offset de la zona horaria actual
    final result = getSunriseSunset(lat, lng, date.timeZoneOffset, date);
    
    // El resultado devuelve DateTime, nos aseguramos que estén en la zona del dispositivo
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
