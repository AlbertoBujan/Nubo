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
    // --- Despejado ---
    '11':  WeatherCode('Despejado', LucideIcons.sun),
    '11n': WeatherCode('Despejado noche', LucideIcons.moon),

    // --- Poco nuboso ---
    '12':  WeatherCode('Poco nuboso', LucideIcons.cloudSun),
    '12n': WeatherCode('Poco nuboso noche', LucideIcons.cloudMoon),

    // --- Intervalos nubosos ---
    '13':  WeatherCode('Intervalos nubosos', LucideIcons.cloudSun),
    '13n': WeatherCode('Intervalos nubosos noche', LucideIcons.cloudMoon),

    // --- Nuboso ---
    '14':  WeatherCode('Nuboso', LucideIcons.cloudSun),
    '14n': WeatherCode('Nuboso noche', LucideIcons.cloudMoon),

    // --- Muy nuboso ---
    '15':  WeatherCode('Muy nuboso', LucideIcons.cloud),
    '15n': WeatherCode('Muy nuboso noche', LucideIcons.cloud),

    // --- Cubierto ---
    '16':  WeatherCode('Cubierto', LucideIcons.cloud),
    '16n': WeatherCode('Cubierto noche', LucideIcons.cloud),

    // --- Nubes altas ---
    '17':  WeatherCode('Nubes altas', LucideIcons.cloudSun),
    '17n': WeatherCode('Nubes altas noche', LucideIcons.cloudMoon),

    // --- Intervalos nubosos con lluvia escasa ---
    '23':  WeatherCode('Intervalos nubosos con lluvia escasa', LucideIcons.cloudDrizzle),
    '23n': WeatherCode('Intervalos nubosos con lluvia escasa noche', LucideIcons.cloudDrizzle),

    // --- Nuboso con lluvia escasa ---
    '24':  WeatherCode('Nuboso con lluvia escasa', LucideIcons.cloudDrizzle),
    '24n': WeatherCode('Nuboso con lluvia escasa noche', LucideIcons.cloudDrizzle),

    // --- Muy nuboso con lluvia escasa ---
    '25':  WeatherCode('Muy nuboso con lluvia escasa', LucideIcons.cloudDrizzle),
    '25n': WeatherCode('Muy nuboso con lluvia escasa noche', LucideIcons.cloudDrizzle),

    // --- Cubierto con lluvia escasa ---
    '26':  WeatherCode('Cubierto con lluvia escasa', LucideIcons.cloudDrizzle),
    '26n': WeatherCode('Cubierto con lluvia escasa noche', LucideIcons.cloudDrizzle),

    // --- Intervalos nubosos con lluvia ---
    '33':  WeatherCode('Intervalos nubosos con lluvia', LucideIcons.cloudRain),
    '33n': WeatherCode('Intervalos nubosos con lluvia noche', LucideIcons.cloudRain),

    // --- Nuboso con lluvia ---
    '34':  WeatherCode('Nuboso con lluvia', LucideIcons.cloudRain),
    '34n': WeatherCode('Nuboso con lluvia noche', LucideIcons.cloudRain),

    // --- Muy nuboso con lluvia ---
    '35':  WeatherCode('Muy nuboso con lluvia', LucideIcons.cloudRain),
    '35n': WeatherCode('Muy nuboso con lluvia noche', LucideIcons.cloudRain),

    // --- Cubierto con lluvia ---
    '36':  WeatherCode('Cubierto con lluvia', LucideIcons.cloudRain),
    '36n': WeatherCode('Cubierto con lluvia noche', LucideIcons.cloudRain),

    // --- Intervalos nubosos con nieve escasa ---
    '43':  WeatherCode('Intervalos nubosos con nieve escasa', LucideIcons.cloudSnow),
    '43n': WeatherCode('Intervalos nubosos con nieve escasa noche', LucideIcons.cloudSnow),

    // --- Nuboso con nieve escasa ---
    '44':  WeatherCode('Nuboso con nieve escasa', LucideIcons.cloudSnow),
    '44n': WeatherCode('Nuboso con nieve escasa noche', LucideIcons.cloudSnow),

    // --- Muy nuboso con nieve escasa ---
    '45':  WeatherCode('Muy nuboso con nieve escasa', LucideIcons.cloudSnow),
    '45n': WeatherCode('Muy nuboso con nieve escasa noche', LucideIcons.cloudSnow),

    // --- Cubierto con nieve escasa ---
    '46':  WeatherCode('Cubierto con nieve escasa', LucideIcons.cloudSnow),
    '46n': WeatherCode('Cubierto con nieve escasa noche', LucideIcons.cloudSnow),

    // --- Intervalos nubosos con tormenta ---
    '51':  WeatherCode('Intervalos nubosos con tormenta', LucideIcons.cloudLightning),
    '51n': WeatherCode('Intervalos nubosos con tormenta noche', LucideIcons.cloudLightning),

    // --- Nuboso con tormenta ---
    '52':  WeatherCode('Nuboso con tormenta', LucideIcons.cloudLightning),
    '52n': WeatherCode('Nuboso con tormenta noche', LucideIcons.cloudLightning),

    // --- Muy nuboso con tormenta ---
    '53':  WeatherCode('Muy nuboso con tormenta', LucideIcons.cloudLightning),
    '53n': WeatherCode('Muy nuboso con tormenta noche', LucideIcons.cloudLightning),

    // --- Cubierto con tormenta ---
    '54':  WeatherCode('Cubierto con tormenta', LucideIcons.cloudLightning),
    '54n': WeatherCode('Cubierto con tormenta noche', LucideIcons.cloudLightning),

    // --- Intervalos nubosos con tormenta y lluvia escasa ---
    '61':  WeatherCode('Intervalos nubosos con tormenta y lluvia escasa', LucideIcons.cloudLightning),
    '61n': WeatherCode('Intervalos nubosos con tormenta y lluvia escasa noche', LucideIcons.cloudLightning),

    // --- Nuboso con tormenta y lluvia escasa ---
    '62':  WeatherCode('Nuboso con tormenta y lluvia escasa', LucideIcons.cloudLightning),
    '62n': WeatherCode('Nuboso con tormenta y lluvia escasa noche', LucideIcons.cloudLightning),

    // --- Muy nuboso con tormenta y lluvia escasa ---
    '63':  WeatherCode('Muy nuboso con tormenta y lluvia escasa', LucideIcons.cloudLightning),
    '63n': WeatherCode('Muy nuboso con tormenta y lluvia escasa noche', LucideIcons.cloudLightning),

    // --- Cubierto con tormenta y lluvia escasa ---
    '64':  WeatherCode('Cubierto con tormenta y lluvia escasa', LucideIcons.cloudLightning),
    '64n': WeatherCode('Cubierto con tormenta y lluvia escasa noche', LucideIcons.cloudLightning),

    // --- Intervalos nubosos con nieve ---
    '71':  WeatherCode('Intervalos nubosos con nieve', LucideIcons.cloudSnow),
    '71n': WeatherCode('Intervalos nubosos con nieve noche', LucideIcons.cloudSnow),

    // --- Nuboso con nieve ---
    '72':  WeatherCode('Nuboso con nieve', LucideIcons.cloudSnow),
    '72n': WeatherCode('Nuboso con nieve noche', LucideIcons.cloudSnow),

    // --- Muy nuboso con nieve ---
    '73':  WeatherCode('Muy nuboso con nieve', LucideIcons.cloudSnow),
    '73n': WeatherCode('Muy nuboso con nieve noche', LucideIcons.cloudSnow),

    // --- Cubierto con nieve ---
    '74':  WeatherCode('Cubierto con nieve', LucideIcons.cloudSnow),
    '74n': WeatherCode('Cubierto con nieve noche', LucideIcons.cloudSnow),

    // --- Niebla ---
    '81':  WeatherCode('Niebla', LucideIcons.cloudFog),
    '81n': WeatherCode('Niebla noche', LucideIcons.cloudFog),

    // --- Bruma ---
    '82':  WeatherCode('Bruma', LucideIcons.cloudFog),
    '82n': WeatherCode('Bruma noche', LucideIcons.cloudFog),

    // --- Calima ---
    '83':  WeatherCode('Calima', LucideIcons.haze),
    '83n': WeatherCode('Calima noche', LucideIcons.haze),
  };

  /// Obtiene el código de tiempo; si no se encuentra, devuelve un valor por defecto.
  static WeatherCode fromCode(String? code) {
    if (code == null || code.isEmpty) {
      return const WeatherCode('Desconocido', LucideIcons.cloudOff);
    }
    return codes[code] ?? const WeatherCode('Desconocido', LucideIcons.cloudOff);
  }
}
