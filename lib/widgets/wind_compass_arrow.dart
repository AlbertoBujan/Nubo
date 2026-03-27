import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_compass/flutter_compass.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

/// Widget aislado que representa la flecha direccional del viento.
/// Escucha en tiempo real la rotación de brújula del dispositivo móvil,
/// girando la flecha relativa al marco tridimensional nativo de forma óptima. 
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
    // Calculamos a dónde viaja el viento (hacia dónde empuja)
    final double absoluteWindTarget = (windDirectionDegrees + 180).toDouble();

    // StreamBuilder al nivel más bajo posible (solo envuelve Transform)
    // para no redibujar tarjetas, Textos ni Layouts y por tanto ahorrar preciosa batería.
    return StreamBuilder<CompassEvent>(
      stream: FlutterCompass.events,
      builder: (context, snapshot) {
        // En emuladores o si el usuario denegó permisos ambientales, la brújula devuelve 0 (Alineado al norte)
        double deviceHeading = 0.0;
        
        if (snapshot.hasData && snapshot.data != null && snapshot.data!.heading != null) {
          deviceHeading = snapshot.data!.heading!;
        }

        // Restamos la orientación del teléfono a la orientación absoluta del objetivo del viento.
        // Esto logra el efecto de "ventana de realidad aumentada"
        final double relativeRotation = (absoluteWindTarget - deviceHeading) * (math.pi / 180.0);

        return Transform.rotate(
          angle: relativeRotation,
          child: Icon(
            LucideIcons.navigation,
            color: color,
            size: size,
          ),
        );
      },
    );
  }
}
