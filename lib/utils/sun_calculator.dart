import 'dart:math';

/// Utilidad para calcular eventos solares (amanecer, atardecer) basados en ubicación y fecha.
class SunCalculator {
  /// Calcula los tiempos solares para una fecha y ubicación dadas.
  static SunTimes calculateTimes(DateTime date, double lat, double lng) {
    // Algoritmo simplificado de Sunrise Equation
    // Fuente: https://en.wikipedia.org/wiki/Sunrise_equation

    final julianDay = _getJulianDay(date);
    final n = julianDay - 2451545.0 + 0.0008;
    final jStar = n - lng / 360.0;
    final m = (357.5291 + 0.98560028 * jStar) % 360;
    
    // Ecuación del centro
    final c = 1.9148 * sin(_deg2rad(m)) +
              0.0200 * sin(_deg2rad(2 * m)) +
              0.0003 * sin(_deg2rad(3 * m));
    
    final lambda = (m + c + 182.5256 + 180) % 360;
    final jTransit = 2451545.0 + jStar + 0.0053 * sin(_deg2rad(m)) - 0.0069 * sin(_deg2rad(2 * lambda));

    // Declinación del sol
    final delta = asin(sin(_deg2rad(lambda)) * sin(_deg2rad(23.44)));

    // Hour angle
    final cosOmega = (sin(_deg2rad(-0.83)) - sin(_deg2rad(lat)) * sin(delta)) /
                     (cos(_deg2rad(lat)) * cos(delta));

    if (cosOmega < -1.0 || cosOmega > 1.0) {
      // Sol de medianoche o noche polar. Devolvemos nulls o tiempos aproximados.
      // Para simplificar, asumimos día/noche perpetuo en casos extremos.
      return SunTimes(
        sunrise: date.copyWith(hour: 6, minute: 0),
        sunset: date.copyWith(hour: 18, minute: 0),
      );
    }
    
    final omega = _rad2deg(acos(cosOmega));
    
    final jRise = jTransit - omega / 360.0;
    final jSet = jTransit + omega / 360.0;

    return SunTimes(
      sunrise: _getDateFromJulian(jRise).toLocal(),
      sunset: _getDateFromJulian(jSet).toLocal(),
    );
  }

  static double _getJulianDay(DateTime date) {
    // Calculo simple de día Juliano para fecha UTC al mediodía más cercano
    // Convertir a UTC para evitar líos de zona horaria en el cálculo base
    final utc = date.toUtc();
    final a = (14 - utc.month) ~/ 12;
    final y = utc.year + 4800 - a;
    final m = utc.month + 12 * a - 3;
    
    final jdn =
        utc.day + (153 * m + 2) ~/ 5 + 365 * y + y ~/ 4 - y ~/ 100 + y ~/ 400 - 32045;
        
    // Ajustar por la hora del día
    final timeFraction = (utc.hour - 12) / 24.0 +
                         utc.minute / 1440.0 +
                         utc.second / 86400.0;
                         
    return jdn.toDouble() + timeFraction;
  }

  static DateTime _getDateFromJulian(double jd) {
    final z = (jd + 0.5).floor();
    final f = (jd + 0.5) - z;
    
    int a = z;
    if (z >= 2299161) {
      final alpha = ((z - 1867216.25) / 36524.25).floor();
      a = z + 1 + alpha - (alpha / 4).floor();
    }
    
    final b = a + 1524;
    final c = ((b - 122.1) / 365.25).floor();
    final d = (365.25 * c).floor();
    final e = ((b - d) / 30.6001).floor();
    
    final day = b - d - (30.6001 * e).floor() + f;
    final month = e < 14 ? e - 1 : e - 13;
    final year = month > 2 ? c - 4716 : c - 4715;

    // Convertir fracción de día a horas/minutos
    final dayFrac = day - day.floor();
    final totalSeconds = (dayFrac * 86400).round();
    final hour = totalSeconds ~/ 3600;
    final minute = (totalSeconds % 3600) ~/ 60;
    final second = totalSeconds % 60;

    return DateTime.utc(year, month, day.floor(), hour, minute, second);
  }

  static double _deg2rad(double deg) => deg * pi / 180.0;
  static double _rad2deg(double rad) => rad * 180.0 / pi;
}

class SunTimes {
  final DateTime sunrise;
  final DateTime sunset;

  SunTimes({required this.sunrise, required this.sunset});
  
  @override
  String toString() => 'Sunrise: $sunrise, Sunset: $sunset';
}
