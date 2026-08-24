import 'package:flutter/widgets.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

/// Mapa de códigos de estado del cielo de AEMET a descripciones e iconos Lucide.
///
/// La API de AEMET usa códigos numéricos (como strings) para describir
/// el estado del cielo. Aquí los mapeamos a texto legible e iconos Lucide.
class WeatherCode {
  final String description;
  final IconData icon;

  const WeatherCode(this.description, this.icon);

  /// Mapa completo de códigos de estado del cielo AEMET → icono Lucide.
  /// Ref: https://opendata.aemet.es/dist/index.html
  ///
  /// Mapeo Lucide:
  ///   Despejado día → Sun | noche → Moon
  ///   Intervalos nubosos día → CloudSun | noche → CloudMoon
  ///   Nublado/Cubierto → Cloud
  ///   Lluvia escasa/llovizna → CloudDrizzle
  ///   Lluvia moderada/fuerte → CloudRain
  ///   Tormenta → CloudLightning
  ///   Nieve → CloudSnow
  ///   Niebla/Bruma → CloudFog
  ///   Calima → Haze
  static const Map<String, WeatherCode> codes = {
    // --- 0: Despejado ---
    '0': WeatherCode('Despejado', LucideIcons.sun),
    '0n': WeatherCode('Despejado', LucideIcons.moon),

    // --- 1: Principalmente despejado ---
    '1': WeatherCode('Poco nuboso', LucideIcons.cloudSun),
    '1n': WeatherCode('Poco nuboso', LucideIcons.cloudMoon),

    // --- 2: Parcialmente nublado ---
    '2': WeatherCode('Intervalos nubosos', LucideIcons.cloudSun),
    '2n': WeatherCode('Intervalos nubosos', LucideIcons.cloudMoon),

    // --- 3: Cubierto ---
    '3': WeatherCode('Cubierto', LucideIcons.cloud),
    '3n': WeatherCode('Cubierto', LucideIcons.cloud),

    // --- 45, 48: Niebla ---
    '45': WeatherCode('Niebla', LucideIcons.cloudFog),
    '45n': WeatherCode('Niebla', LucideIcons.cloudFog),
    '48': WeatherCode('Niebla escarchada', LucideIcons.cloudFog),
    '48n': WeatherCode('Niebla escarchada', LucideIcons.cloudFog),

    // --- 51, 53, 55: Llovizna ---
    '51': WeatherCode('Llovizna ligera', LucideIcons.cloudDrizzle),
    '51n': WeatherCode('Llovizna ligera', LucideIcons.cloudDrizzle),
    '53': WeatherCode('Llovizna moderada', LucideIcons.cloudDrizzle),
    '53n': WeatherCode('Llovizna moderada', LucideIcons.cloudDrizzle),
    '55': WeatherCode('Llovizna densa', LucideIcons.cloudDrizzle),
    '55n': WeatherCode('Llovizna densa', LucideIcons.cloudDrizzle),

    // --- 56, 57: Llovizna helada ---
    '56': WeatherCode('Llovizna helada ligera', LucideIcons.cloudDrizzle),
    '56n': WeatherCode('Llovizna helada ligera', LucideIcons.cloudDrizzle),
    '57': WeatherCode('Llovizna helada densa', LucideIcons.cloudDrizzle),
    '57n': WeatherCode('Llovizna helada densa', LucideIcons.cloudDrizzle),

    // --- 61, 63, 65: Lluvia ---
    '61': WeatherCode('Lluvia débil', LucideIcons.cloudRain),
    '61n': WeatherCode('Lluvia débil', LucideIcons.cloudRain),
    '63': WeatherCode('Lluvia moderada', LucideIcons.cloudRain),
    '63n': WeatherCode('Lluvia moderada', LucideIcons.cloudRain),
    '65': WeatherCode('Lluvia fuerte', LucideIcons.cloudRain),
    '65n': WeatherCode('Lluvia fuerte', LucideIcons.cloudRain),

    // --- 66, 67: Lluvia helada ---
    '66': WeatherCode('Lluvia helada débil', LucideIcons.cloudRain),
    '66n': WeatherCode('Lluvia helada débil', LucideIcons.cloudRain),
    '67': WeatherCode('Lluvia helada fuerte', LucideIcons.cloudRain),
    '67n': WeatherCode('Lluvia helada fuerte', LucideIcons.cloudRain),

    // --- 71, 73, 75: Nieve ---
    '71': WeatherCode('Nieve débil', LucideIcons.cloudSnow),
    '71n': WeatherCode('Nieve débil', LucideIcons.cloudSnow),
    '73': WeatherCode('Nieve moderada', LucideIcons.cloudSnow),
    '73n': WeatherCode('Nieve moderada', LucideIcons.cloudSnow),
    '75': WeatherCode('Nieve fuerte', LucideIcons.cloudSnow),
    '75n': WeatherCode('Nieve fuerte', LucideIcons.cloudSnow),

    // --- 77: Granizo ---
    '77': WeatherCode('Granizo menudo', LucideIcons.cloudSnow),
    '77n': WeatherCode('Granizo menudo', LucideIcons.cloudSnow),

    // --- 80, 81, 82: Chubascos de lluvia ---
    '80': WeatherCode('Chubascos débiles', LucideIcons.cloudRain),
    '80n': WeatherCode('Chubascos débiles', LucideIcons.cloudRain),
    '81': WeatherCode('Chubascos moderados', LucideIcons.cloudRain),
    '81n': WeatherCode('Chubascos moderados', LucideIcons.cloudRain),
    '82': WeatherCode('Chubascos fuertes', LucideIcons.cloudRain),
    '82n': WeatherCode('Chubascos fuertes', LucideIcons.cloudRain),

    // --- 85, 86: Chubascos de nieve ---
    '85': WeatherCode('Chubascos de nieve débiles', LucideIcons.cloudSnow),
    '85n': WeatherCode('Chubascos de nieve débiles', LucideIcons.cloudSnow),
    '86': WeatherCode('Chubascos de nieve fuertes', LucideIcons.cloudSnow),
    '86n': WeatherCode('Chubascos de nieve fuertes', LucideIcons.cloudSnow),

    // --- 95: Tormenta ---
    '95': WeatherCode('Tormenta', LucideIcons.cloudLightning),
    '95n': WeatherCode('Tormenta', LucideIcons.cloudLightning),

    // --- 96, 99: Tormenta con granizo ---
    '96': WeatherCode('Tormenta con granizo', LucideIcons.cloudLightning),
    '96n': WeatherCode('Tormenta con granizo', LucideIcons.cloudLightning),
    '99': WeatherCode('Tormenta con granizo fuerte', LucideIcons.cloudLightning),
    '99n': WeatherCode('Tormenta con granizo fuerte', LucideIcons.cloudLightning),
  };

  /// Obtiene el código de tiempo; si no se encuentra, devuelve un valor por defecto.
  static WeatherCode fromCode(String? code) {
    if (code == null || code.isEmpty) {
      return const WeatherCode('Desconocido', LucideIcons.cloudOff);
    }
    return codes[code] ?? const WeatherCode('Desconocido', LucideIcons.cloudOff);
  }
}

/// Clasificación del estado del cielo en 3 categorías para el fondo dinámico.
enum SkyCondition {
  /// Despejado o poco nuboso (códigos WMO 0, 1)
  clear,

  /// Intervalos nubosos, cubierto, niebla (códigos WMO 2, 3, 45, 48)
  partlyCloudy,

  /// Precipitación: lluvia, llovizna, nieve, tormenta, granizo (códigos WMO 51+)
  overcast;

  /// Clasifica un código WMO (con posible sufijo 'n' de noche) en una categoría.
  static SkyCondition fromCode(String? code) {
    if (code == null || code.isEmpty) return clear;

    // Extraer la parte numérica (quitar sufijo 'n' si existe)
    final numericStr = code.replaceAll('n', '');
    final numeric = int.tryParse(numericStr);
    if (numeric == null) return clear;

    if (numeric <= 1) return clear;
    if (numeric <= 3 || numeric == 45 || numeric == 48) return partlyCloudy;
    return overcast; // 51+ (lluvia, nieve, tormenta, etc.)
  }
}

/// Agrupación de códigos WMO por familia de fenómeno, ordenada por severidad.
///
/// Se usa para decidir qué icono representa mejor un día completo a partir de
/// sus horas (ver [DailyForecast.fromOpenMeteoJson]): permite contar horas por
/// familia en vez de por código exacto, y comparar cuál es el fenómeno más
/// relevante del día.
enum WeatherCodeGroup {
  /// Despejado (WMO 0).
  clear(0),

  /// Poco nuboso / intervalos nubosos (WMO 1, 2).
  partlyCloudy(1),

  /// Cubierto (WMO 3).
  cloudy(2),

  /// Niebla o niebla escarchada (WMO 45, 48).
  fog(3),

  /// Llovizna, incluida la helada (WMO 51-57).
  drizzle(4),

  /// Lluvia y chubascos, incluida la helada (WMO 61-67, 80-82).
  rain(5),

  /// Nieve, granizo menudo y chubascos de nieve (WMO 71-77, 85, 86).
  snow(6),

  /// Tormenta, con o sin granizo (WMO 95, 96, 99).
  thunder(7);

  const WeatherCodeGroup(this.severity);

  /// Orden relativo de severidad; a mayor valor, fenómeno más relevante.
  final int severity;

  /// Familias que describen un fenómeno concreto (no solo nubosidad).
  ///
  /// Solo estas pueden "adueñarse" del icono del día, y únicamente si duran
  /// las horas mínimas que exige [minHours].
  bool get isSignificant => severity >= WeatherCodeGroup.fog.severity;

  /// Horas mínimas que debe durar el fenómeno para representar al día entero.
  ///
  /// Las tormentas bajan el umbral porque son relevantes aunque sean breves.
  int get minHours => switch (this) {
        WeatherCodeGroup.thunder => 2,
        _ => isSignificant ? 3 : 0,
      };

  /// Clasifica un código WMO (con posible sufijo 'n' de noche) en su familia.
  static WeatherCodeGroup fromCode(String? code) {
    final numeric = numericValue(code);
    if (numeric == null) return clear;

    if (numeric == 0) return clear;
    if (numeric <= 2) return partlyCloudy;
    if (numeric == 3) return cloudy;
    if (numeric == 45 || numeric == 48) return fog;
    if (numeric >= 51 && numeric <= 57) return drizzle;
    if (numeric >= 61 && numeric <= 67) return rain;
    if (numeric >= 71 && numeric <= 77) return snow;
    if (numeric >= 80 && numeric <= 82) return rain;
    if (numeric == 85 || numeric == 86) return snow;
    if (numeric >= 95) return thunder;
    return clear;
  }

  /// Extrae la parte numérica de un código WMO, ignorando el sufijo 'n'.
  static int? numericValue(String? code) {
    if (code == null || code.isEmpty) return null;
    return int.tryParse(code.replaceAll('n', ''));
  }

  /// Familias en las que cae agua, y que por tanto pintan gotas en el fondo.
  bool get hasRain =>
      this == WeatherCodeGroup.drizzle ||
      this == WeatherCodeGroup.rain ||
      this == WeatherCodeGroup.thunder;

  /// Familias que además pintan destellos de rayo.
  bool get hasThunder => this == WeatherCodeGroup.thunder;
}

/// Fase solar del momento actual, usada para el fondo dinámico de la app.
enum SunPhase { night, sunrise, day, sunset }
