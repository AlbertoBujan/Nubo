import 'package:flutter/material.dart';
import '../models/weather_enums.dart';

/// Gradientes de fondo según la fase solar y la condición del cielo.
///
/// Funciones puras: no tienen estado, no dependen de Provider.
/// Cada gradiente tiene 4 stops para permitir interpolación con [lerp].
class SkyGradients {
  SkyGradients._();

  /// Devuelve el gradiente correspondiente a [phase] y [sky].
  static LinearGradient forPhase(SunPhase phase, SkyCondition sky) {
    switch (phase) {
      case SunPhase.day:
        return day(sky);
      case SunPhase.sunrise:
        return sunrise(sky);
      case SunPhase.sunset:
        return sunset(sky);
      case SunPhase.night:
        return night(sky);
    }
  }

  /// Interpola linealmente entre dos gradientes de 4 colores.
  static LinearGradient lerp(LinearGradient a, LinearGradient b, double t) {
    return LinearGradient(
      begin: Alignment.topCenter,
      end: Alignment.bottomCenter,
      colors: [
        Color.lerp(a.colors[0], b.colors[0], t)!,
        Color.lerp(a.colors[1], b.colors[1], t)!,
        Color.lerp(a.colors[2], b.colors[2], t)!,
        Color.lerp(a.colors[3], b.colors[3], t)!,
      ],
      stops: const [0.0, 0.33, 0.67, 1.0],
    );
  }

  // ---------------------------------------------------------------------------
  // DÍA
  // ---------------------------------------------------------------------------

  static LinearGradient day(SkyCondition sky) {
    switch (sky) {
      case SkyCondition.clear:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF0F5298),
            Color(0xFF1F73BA),
            Color(0xFF3C99DC),
            Color(0xFF4DA8E8),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.partlyCloudy:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF4A6B8A),
            Color(0xFF627D96),
            Color(0xFF7A8FA0),
            Color(0xFF9EAAB6),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.overcast:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF4B5563),
            Color(0xFF5A6370),
            Color(0xFF6B7280),
            Color(0xFF555E69),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
    }
  }

  // ---------------------------------------------------------------------------
  // AMANECER
  // ---------------------------------------------------------------------------

  static LinearGradient sunrise(SkyCondition sky) {
    switch (sky) {
      case SkyCondition.clear:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF141E30),
            Color(0xFF243B55),
            Color(0xFFCC2B5E),
            Color(0xFF753A88),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.partlyCloudy:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF2C3E50),
            Color(0xFF5D6D7E),
            Color(0xFF9B6B8A),
            Color(0xFF8E99A4),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.overcast:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF3D3D3D),
            Color(0xFF4B4646),
            Color(0xFF5A5050),
            Color(0xFF6B6360),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
    }
  }

  // ---------------------------------------------------------------------------
  // ATARDECER
  // ---------------------------------------------------------------------------

  static LinearGradient sunset(SkyCondition sky) {
    switch (sky) {
      case SkyCondition.clear:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF3E1E68),
            Color(0xFF82306B),
            Color(0xFFC6426E),
            Color(0xFFF9A825),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.partlyCloudy:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF3D3456),
            Color(0xFF634760),
            Color(0xFF8A5A6A),
            Color(0xFF7A6E65),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.overcast:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF3D3D3D),
            Color(0xFF4C4444),
            Color(0xFF5A4A4A),
            Color(0xFF4A4545),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
    }
  }

  // ---------------------------------------------------------------------------
  // NOCHE
  // ---------------------------------------------------------------------------

  static LinearGradient night(SkyCondition sky) {
    switch (sky) {
      case SkyCondition.clear:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF1A1A2E),
            Color(0xFF16213E),
            Color(0xFF0F3460),
            Color(0xFF0A0A12),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.partlyCloudy:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF1C2333),
            Color(0xFF232938),
            Color(0xFF2A2F3D),
            Color(0xFF252830),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.overcast:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF1A1A1E),
            Color(0xFF202023),
            Color(0xFF252528),
            Color(0xFF1E1E20),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
    }
  }
}
