import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

/// Widget que representa la flecha direccional del viento.
/// Rota el icono según la dirección del viento en grados (geográfica).
class WindCompassArrow extends StatelessWidget {
  final int windDirectionDegrees;
  final double size;
  final Color color;

  const WindCompassArrow({
    super.key,
    required this.windDirectionDegrees,
    this.size = 12.0,
    this.color = Colors.white54,
  });

  @override
  Widget build(BuildContext context) {
    // La dirección del viento indica de dónde viene.
    // Sumamos 180° para mostrar hacia dónde se dirige.
    final double rotation = (windDirectionDegrees + 180) * (math.pi / 180.0);

    return Transform.rotate(
      angle: rotation,
      child: Icon(
        LucideIcons.navigation,
        color: color,
        size: size,
      ),
    );
  }
}
