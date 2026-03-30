import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import '../providers/weather_provider.dart';

/// Tarjeta dual Sol + Luna con arcos animados estilo Breezy Weather.
class SunMoonCard extends StatelessWidget {
  final WeatherProvider provider;

  const SunMoonCard({super.key, required this.provider});

  @override
  Widget build(BuildContext context) {
    final sunTimes = provider.currentSunTimes;
    final moonData = provider.currentMoonData;
    if (sunTimes == null) return const SizedBox.shrink();

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Row(
        children: [
          // --- Tarjeta Sol ---
          Expanded(
            child: _ArcCard(
              title: 'Sol',
              titleIcon: const Icon(LucideIcons.sun, color: Colors.amber, size: 16),
              startTime: sunTimes.sunrise,
              endTime: sunTimes.sunset,
              startLabel: DateFormat('HH:mm').format(sunTimes.sunrise),
              endLabel: DateFormat('HH:mm').format(sunTimes.sunset),
              arcColor: Colors.amber.shade600,
              arcTrailColor: Colors.amber.shade800.withValues(alpha: 0.4),
              iconBuilder: (size) => Icon(
                LucideIcons.sun,
                color: Colors.amber,
                size: size,
              ),
            ),
          ),
          const SizedBox(width: 10),
          // --- Tarjeta Luna ---
          Expanded(
            child: _ArcCard(
              title: moonData?.phaseName ?? 'Luna',
              titleIcon: CustomPaint(
                size: const Size(16, 16),
                painter: _MoonPhasePainter(
                  phase: moonData?.phase ?? 0.0,
                  color: Colors.blueGrey.shade200,
                ),
              ),
              startTime: moonData?.moonrise,
              endTime: moonData?.moonset,
              startLabel: moonData?.moonrise != null
                  ? DateFormat('HH:mm').format(moonData!.moonrise!)
                  : '--:--',
              endLabel: moonData?.moonset != null
                  ? DateFormat('HH:mm').format(moonData!.moonset!)
                  : '--:--',
              arcColor: Colors.blueGrey.shade300,
              arcTrailColor: Colors.blueGrey.shade500.withValues(alpha: 0.3),
              iconBuilder: (size) => Icon(
                LucideIcons.moon,
                color: Colors.blueGrey.shade200,
                size: size,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Tarjeta individual con arco, icono animado y horas.
class _ArcCard extends StatelessWidget {
  final String title;
  final Widget titleIcon;
  final DateTime? startTime;
  final DateTime? endTime;
  final String startLabel;
  final String endLabel;
  final Color arcColor;
  final Color arcTrailColor;
  final Widget Function(double size) iconBuilder;

  const _ArcCard({
    required this.title,
    required this.titleIcon,
    required this.startTime,
    required this.endTime,
    required this.startLabel,
    required this.endLabel,
    required this.arcColor,
    required this.arcTrailColor,
    required this.iconBuilder,
  });

  /// Calcula el progreso (0.0-1.0) del astro entre start y end.
  double _progress() {
    if (startTime == null || endTime == null) return 0.5;

    final now = DateTime.now();
    
    // Ajuste: si moonset es antes que moonrise (cruce de medianoche),
    // sumamos 24h al moonset para el cálculo.
    var adjustedEnd = endTime!;
    if (adjustedEnd.isBefore(startTime!)) {
      adjustedEnd = adjustedEnd.add(const Duration(days: 1));
    }

    final total = adjustedEnd.difference(startTime!).inMinutes;
    if (total <= 0) return 0.5;

    var adjustedNow = now;
    // Si el now es antes del start y el ciclo cruza la medianoche
    if (now.isBefore(startTime!) && endTime!.isBefore(startTime!)) {
      // Estamos en la parte "post-medianoche" del ciclo
      adjustedNow = now.add(const Duration(days: 1));
    }

    final elapsed = adjustedNow.difference(startTime!).inMinutes;
    return (elapsed / total).clamp(0.0, 1.0);
  }

  @override
  Widget build(BuildContext context) {
    final progress = _progress();

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.1),
          width: 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Título
          Row(
            children: [
              titleIcon,
              const SizedBox(width: 5),
              Text(
                title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          // Arco
          SizedBox(
            height: 70,
            child: LayoutBuilder(
              builder: (context, constraints) {
                return CustomPaint(
                  size: Size(constraints.maxWidth, 70),
                  painter: _ArcPainter(
                    progress: progress,
                    arcColor: arcColor,
                    trailColor: arcTrailColor,
                  ),
                  child: _buildIconOnArc(constraints.maxWidth, 70, progress),
                );
              },
            ),
          ),
          const SizedBox(height: 6),
          // Horas
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                startLabel,
                style: const TextStyle(
                  color: Colors.white70,
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                ),
              ),
              Text(
                endLabel,
                style: const TextStyle(
                  color: Colors.white70,
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  /// Posiciona el icono sobre el arco según el progreso.
  Widget _buildIconOnArc(double width, double height, double progress) {
    // El arco va de pi a 0 (izquierda a derecha)
    final angle = math.pi * (1.0 - progress);
    final horizontalPadding = 12.0;
    final arcWidth = width - horizontalPadding * 2;
    final centerX = horizontalPadding + arcWidth / 2;
    final centerY = height - 8; // Base del arco
    final radiusX = arcWidth / 2;
    final radiusY = height - 18; // Radio vertical del arco

    final iconX = centerX + radiusX * math.cos(angle);
    final iconY = centerY - radiusY * math.sin(angle);

    const iconSize = 18.0;

    return Stack(
      clipBehavior: Clip.none,
      children: [
        Positioned(
          left: iconX - iconSize / 2,
          top: iconY - iconSize / 2,
          child: iconBuilder(iconSize),
        ),
      ],
    );
  }
}

/// Pinta el arco semicircular con línea punteada y progreso sólido.
class _ArcPainter extends CustomPainter {
  final double progress;
  final Color arcColor;
  final Color trailColor;

  _ArcPainter({
    required this.progress,
    required this.arcColor,
    required this.trailColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final horizontalPadding = 12.0;
    final arcWidth = size.width - horizontalPadding * 2;
    final centerX = horizontalPadding + arcWidth / 2;
    final baseY = size.height - 8;
    final radiusX = arcWidth / 2;
    final radiusY = size.height - 18;

    // --- Línea horizontal del horizonte (punteada) ---
    final horizonPaint = Paint()
      ..color = Colors.white.withValues(alpha: 0.15)
      ..strokeWidth = 1;

    const dashWidth = 4.0;
    const dashSpace = 4.0;
    var startX = horizontalPadding;
    while (startX < size.width - horizontalPadding) {
      canvas.drawLine(
        Offset(startX, baseY),
        Offset(math.min(startX + dashWidth, size.width - horizontalPadding), baseY),
        horizonPaint,
      );
      startX += dashWidth + dashSpace;
    }

    // --- Arco completo (punteado, tenue) ---
    final trailPaint = Paint()
      ..color = trailColor
      ..strokeWidth = 2.0
      ..style = PaintingStyle.stroke;

    _drawDottedArc(canvas, centerX, baseY, radiusX, radiusY, 0.0, 1.0, trailPaint);

    // --- Arco recorrido (sólido, brillante) ---
    if (progress > 0.0) {
      final activePaint = Paint()
        ..color = arcColor
        ..strokeWidth = 2.5
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round;

      // Calcular margen para que la línea se detenga un poco antes del centro del icono
      final approxArcLength = math.pi * ((radiusX + radiusY) / 2);
      final gapProgress = 10.0 / approxArcLength; // Espacio para el icono (aprox 14 px)
      final drawProgress = math.max(0.0, progress - gapProgress);

      if (drawProgress > 0.0) {
        _drawSolidArc(canvas, centerX, baseY, radiusX, radiusY, 0.0, drawProgress, activePaint);
      }
    }
  }

  void _drawDottedArc(Canvas canvas, double cx, double cy, double rx, double ry,
      double startProgress, double endProgress, Paint paint) {
    const totalDots = 40;
    final dotRadius = 1.2;

    for (int i = 0; i <= totalDots; i++) {
      final t = startProgress + (endProgress - startProgress) * (i / totalDots);
      final angle = math.pi * (1.0 - t);
      final x = cx + rx * math.cos(angle);
      final y = cy - ry * math.sin(angle);
      canvas.drawCircle(Offset(x, y), dotRadius, paint..style = PaintingStyle.fill);
    }
  }

  void _drawSolidArc(Canvas canvas, double cx, double cy, double rx, double ry,
      double startProgress, double endProgress, Paint paint) {
    final path = Path();
    const segments = 60;

    for (int i = 0; i <= segments; i++) {
      final t = startProgress + (endProgress - startProgress) * (i / segments);
      final angle = math.pi * (1.0 - t);
      final x = cx + rx * math.cos(angle);
      final y = cy - ry * math.sin(angle);

      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(_ArcPainter oldDelegate) =>
      oldDelegate.progress != progress;
}

/// Pinta la fase lunar con estilo similar a Lucide Icons (contorno limpio).
class _MoonPhasePainter extends CustomPainter {
  final double phase;  // 0.0 = nueva, 0.5 = llena
  final Color color;

  _MoonPhasePainter({required this.phase, required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2 - 1;

    // Contorno exterior de la luna
    final outlinePaint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5;
    canvas.drawCircle(center, radius, outlinePaint);

    // Parte iluminada (relleno)
    final fillPaint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;

    // Calculamos la curvatura del terminador
    // phase 0.0 = nueva (todo oscuro), 0.25 = cuarto creciente (mitad derecha)
    // phase 0.5 = llena (todo iluminado), 0.75 = cuarto menguante (mitad izquierda)
    
    final path = Path();

    if (phase < 0.01 || phase > 0.99) {
      // Luna nueva: solo contorno, sin relleno
      return;
    }

    if ((phase - 0.5).abs() < 0.01) {
      // Luna llena: círculo completo
      canvas.drawCircle(center, radius - 0.5, fillPaint);
      return;
    }

    // Para otras fases, dibujamos el terminador como una elipse
    // El terminador divide la luna en parte iluminada y oscura
    final sweepRight = phase < 0.5; // Creciente: ilumina desde la derecha
    
    // Factor de curvatura del terminador (-1 a 1)
    double terminator;
    if (phase < 0.25) {
      // Creciente cóncava: terminador curvado hacia la derecha
      terminator = 1.0 - (phase / 0.25);
    } else if (phase < 0.5) {
      // Creciente convexa: terminador curvado hacia la izquierda
      terminator = -((phase - 0.25) / 0.25);
    } else if (phase < 0.75) {
      // Menguante convexa: terminador curvado hacia la derecha
      terminator = -1.0 + ((phase - 0.5) / 0.25);
    } else {
      // Menguante cóncava: terminador curvado hacia la izquierda
      terminator = (phase - 0.75) / 0.25;
    }

    // Dibujamos la parte iluminada
    // Medio círculo (lado iluminado) + curva del terminador
    const segments = 60;

    if (sweepRight) {
      // Fase creciente: iluminar lado derecho
      // Semicírculo derecho (de arriba a abajo por la derecha)
      for (int i = 0; i <= segments; i++) {
        final angle = -math.pi / 2 + math.pi * (i / segments);
        final x = center.dx + radius * math.cos(angle);
        final y = center.dy + radius * math.sin(angle);
        if (i == 0) {
          path.moveTo(x, y);
        } else {
          path.lineTo(x, y);
        }
      }
      // Terminador (de abajo a arriba por el centro)
      for (int i = segments; i >= 0; i--) {
        final angle = -math.pi / 2 + math.pi * (i / segments);
        final x = center.dx + radius * terminator * math.cos(angle);
        final y = center.dy + radius * math.sin(angle);
        path.lineTo(x, y);
      }
    } else {
      // Fase menguante: iluminar lado izquierdo
      // Semicírculo izquierdo (de arriba a abajo por la izquierda)
      for (int i = 0; i <= segments; i++) {
        final angle = math.pi / 2 + math.pi * (i / segments);
        final x = center.dx + radius * math.cos(angle);
        final y = center.dy + radius * math.sin(angle);
        if (i == 0) {
          path.moveTo(x, y);
        } else {
          path.lineTo(x, y);
        }
      }
      // Terminador (de abajo a arriba por el centro)
      for (int i = segments; i >= 0; i--) {
        final angle = math.pi / 2 + math.pi * (i / segments);
        final x = center.dx - radius * terminator * math.cos(angle);
        final y = center.dy + radius * math.sin(angle);
        path.lineTo(x, y);
      }
    }

    path.close();
    canvas.drawPath(path, fillPaint);
  }

  @override
  bool shouldRepaint(_MoonPhasePainter oldDelegate) =>
      oldDelegate.phase != phase || oldDelegate.color != color;
}
