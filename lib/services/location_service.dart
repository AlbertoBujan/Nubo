import 'dart:async';
import 'package:geolocator/geolocator.dart';

/// Servicio de geolocalización GPS.
///
/// Gestiona la petición de permisos y la obtención de la posición actual.
class LocationService {
  /// Solicita permisos si es necesario y obtiene la posición actual.
  ///
  /// Estrategia:
  /// 1. Intenta obtener la última posición conocida (instantáneo).
  /// 2. Si no existe o es muy antigua, lanza getCurrentPosition con 30s de timeout.
  ///
  /// Lanza [LocationException] si el GPS está desactivado, se deniega
  /// el permiso o se agota el tiempo de espera.
  Future<Position> getCurrentPosition() async {
    // Verificar si el servicio GPS está habilitado
    final serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      throw const LocationException(
        'El GPS está desactivado. Actívalo en los ajustes del dispositivo.',
      );
    }

    // Verificar y pedir permisos
    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) {
        throw const LocationException('Permiso de ubicación denegado.');
      }
    }
    if (permission == LocationPermission.deniedForever) {
      throw const LocationException(
        'Permiso de ubicación denegado permanentemente. '
        'Habilítalo manualmente en Ajustes > Aplicaciones > Nubo.',
      );
    }

    // --- Estrategia 1: última posición conocida (sin latencia) ---
    try {
      final last = await Geolocator.getLastKnownPosition();
      if (last != null) {
        final age = DateTime.now().difference(last.timestamp);
        // Usamos la caché si tiene menos de 5 minutos
        if (age.inMinutes < 5) return last;
      }
    } catch (_) {
      // Si falla, continuamos con getCurrentPosition
    }

    // --- Estrategia 2: posición en tiempo real con timeout de 30s ---
    try {
      return await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.medium,
          timeLimit: Duration(seconds: 30),
        ),
      );
    } on TimeoutException {
      throw const LocationException(
        'No se pudo obtener la ubicación a tiempo. '
        'Asegúrate de estar al aire libre o con buena señal GPS.',
      );
    }
  }
}

/// Excepción personalizada para errores de geolocalización.
class LocationException implements Exception {
  final String message;
  const LocationException(this.message);

  @override
  String toString() => 'LocationException: $message';
}

