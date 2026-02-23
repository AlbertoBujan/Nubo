import 'package:flutter/material.dart';

/// Modelo para una alerta meteorológica de AEMET (formato CAP).
class WeatherAlert {
  /// Nivel de alerta: amarillo, naranja, rojo
  final String nivel;

  /// Evento (ej: "Aviso de costeros de nivel amarillo")
  final String event;

  /// Titular (ej: "Aviso de costeros de nivel amarillo. Costa - Costa granadina")
  final String headline;

  /// Descripción detallada
  final String description;

  /// Instrucciones de seguridad
  final String instruction;

  /// Zona afectada
  final String areaDescription;

  /// Inicio de vigencia
  final DateTime? onset;

  /// Fin de vigencia
  final DateTime? expires;

  /// Probabilidad (ej: "40%-70%")
  final String probability;

  const WeatherAlert({
    required this.nivel,
    required this.event,
    required this.headline,
    required this.description,
    required this.instruction,
    required this.areaDescription,
    required this.onset,
    required this.expires,
    required this.probability,
  });

  Map<String, dynamic> toJson() => {
        'nivel': nivel,
        'event': event,
        'headline': headline,
        'description': description,
        'instruction': instruction,
        'areaDescription': areaDescription,
        'onset': onset?.toIso8601String(),
        'expires': expires?.toIso8601String(),
        'probability': probability,
      };

  factory WeatherAlert.fromJson(Map<String, dynamic> json) => WeatherAlert(
        nivel: json['nivel'] as String? ?? '',
        event: json['event'] as String? ?? '',
        headline: json['headline'] as String? ?? '',
        description: json['description'] as String? ?? '',
        instruction: json['instruction'] as String? ?? '',
        areaDescription: json['areaDescription'] as String? ?? '',
        onset: json['onset'] != null ? DateTime.tryParse(json['onset'])?.toLocal() : null,
        expires: json['expires'] != null ? DateTime.tryParse(json['expires'])?.toLocal() : null,
        probability: json['probability'] as String? ?? '',
      );

  /// Color del nivel de alerta.
  Color get color {
    switch (nivel.toLowerCase()) {
      case 'rojo':
        return const Color(0xFFD32F2F);
      case 'naranja':
        return const Color(0xFFFF8F00);
      case 'amarillo':
        return const Color(0xFFFBC02D);
      default:
        return const Color(0xFFFBC02D);
    }
  }

  /// Color de fondo suave para la caja de alerta.
  Color get backgroundColor {
    switch (nivel.toLowerCase()) {
      case 'rojo':
        return const Color(0x33D32F2F);
      case 'naranja':
        return const Color(0x33FF8F00);
      case 'amarillo':
        return const Color(0x33FBC02D);
      default:
        return const Color(0x33FBC02D);
    }
  }

  /// Color del borde.
  Color get borderColor {
    switch (nivel.toLowerCase()) {
      case 'rojo':
        return const Color(0x66D32F2F);
      case 'naranja':
        return const Color(0x66FF8F00);
      case 'amarillo':
        return const Color(0x66FBC02D);
      default:
        return const Color(0x66FBC02D);
    }
  }

  /// Texto del nivel capitalizado.
  String get nivelDisplay =>
      nivel.isNotEmpty ? nivel[0].toUpperCase() + nivel.substring(1) : nivel;

  /// Devuelve true si la alerta es vigente o próxima.
  bool get isActiveOrUpcoming {
    final now = DateTime.now();
    if (expires != null && expires!.isBefore(now)) return false;
    return true;
  }
}
