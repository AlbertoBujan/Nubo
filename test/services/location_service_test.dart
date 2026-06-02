import 'package:flutter_test/flutter_test.dart';
import 'package:nubo/services/location_service.dart';

// LocationService.getCurrentPosition() no es testable directamente porque
// depende de Geolocator (métodos estáticos). Para testearlo habría que:
//   1. Refactorizar LocationService para aceptar un GeolocatorPlatform inyectable.
//   2. Mockear GeolocatorPlatform.instance con un mock manual o via mockito.
// Esa refactorización es responsabilidad de flutter-architect.
// Por ahora, cubrimos LocationException que sí tiene lógica propia.

void main() {
  group('LocationException', () {
    test('preserva el mensaje', () {
      const e = LocationException('GPS desactivado');
      expect(e.message, 'GPS desactivado');
    });

    test('toString incluye "LocationException" y el mensaje', () {
      const e = LocationException('Permiso denegado');
      expect(e.toString(), contains('LocationException'));
      expect(e.toString(), contains('Permiso denegado'));
    });

    test('es una Exception', () {
      expect(const LocationException('test'), isA<Exception>());
    });

    test('admite constructor const', () {
      const e = LocationException('constante');
      expect(e.message, 'constante');
    });

    test('mensaje vacío no lanza', () {
      const e = LocationException('');
      expect(e.message, isEmpty);
      expect(() => e.toString(), returnsNormally);
    });

    test('mensaje con caracteres especiales no lanza', () {
      const e = LocationException(r'Ü$ñ@ció€n especial');
      expect(e.message, contains('ñ'));
    });
  });
}
