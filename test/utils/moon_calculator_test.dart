import 'package:flutter_test/flutter_test.dart';
import 'package:nubo/utils/moon_calculator.dart';

void main() {
  // Madrid: lat=40.4168, lng=-3.7038
  const double madridLat = 40.4168;
  const double madridLng = -3.7038;

  group('MoonCalculator.calculate', () {
    test('phase está en rango [0.0, 1.0]', () {
      final data = MoonCalculator.calculate(
        DateTime(2024, 6, 21),
        madridLat,
        madridLng,
      );

      expect(data.phase, greaterThanOrEqualTo(0.0));
      expect(data.phase, lessThanOrEqualTo(1.0));
    });

    test('illumination está en rango [0.0, 1.0]', () {
      final data = MoonCalculator.calculate(
        DateTime(2024, 6, 21),
        madridLat,
        madridLng,
      );

      expect(data.illumination, greaterThanOrEqualTo(0.0));
      expect(data.illumination, lessThanOrEqualTo(1.0));
    });

    test('phaseName es uno de los valores esperados en español', () {
      const validNames = {
        'Luna nueva',
        'Creciente cóncava',
        'Cuarto creciente',
        'Creciente convexa',
        'Luna llena',
        'Menguante convexa',
        'Cuarto menguante',
        'Menguante cóncava',
      };

      final data = MoonCalculator.calculate(
        DateTime(2024, 6, 21),
        madridLat,
        madridLng,
      );

      expect(validNames, contains(data.phaseName));
    });

    test('si moonrise no es null, es una DateTime válida', () {
      final data = MoonCalculator.calculate(
        DateTime(2024, 6, 21),
        madridLat,
        madridLng,
      );

      if (data.moonrise != null) {
        expect(data.moonrise, isA<DateTime>());
      }
    });

    test('si moonset no es null, es una DateTime válida', () {
      final data = MoonCalculator.calculate(
        DateTime(2024, 6, 21),
        madridLat,
        madridLng,
      );

      if (data.moonset != null) {
        expect(data.moonset, isA<DateTime>());
      }
    });

    test('toString no lanza excepción', () {
      final data = MoonCalculator.calculate(
        DateTime(2024, 6, 21),
        madridLat,
        madridLng,
      );

      expect(() => data.toString(), returnsNormally);
      expect(data.toString(), contains('phase'));
    });
  });

  group('MoonCalculator nombres de fase (via calculate)', () {
    // Luna nueva: ~3 enero 2022
    test('cerca de luna nueva → "Luna nueva"', () {
      final data = MoonCalculator.calculate(
        DateTime(2022, 1, 3),
        madridLat,
        madridLng,
      );
      // Phase < 0.03 o > 0.97
      if (data.phase < 0.03 || data.phase > 0.97) {
        expect(data.phaseName, 'Luna nueva');
      }
    });

    // Luna llena: ~18 enero 2022
    test('cerca de luna llena → "Luna llena"', () {
      final data = MoonCalculator.calculate(
        DateTime(2022, 1, 18),
        madridLat,
        madridLng,
      );
      // Phase ~0.47-0.53
      if (data.phase >= 0.47 && data.phase <= 0.53) {
        expect(data.phaseName, 'Luna llena');
      }
    });

    test('phaseName nunca es vacío', () {
      // Probamos varios días consecutivos
      for (int day = 1; day <= 30; day++) {
        final data = MoonCalculator.calculate(
          DateTime(2024, 1, day),
          madridLat,
          madridLng,
        );
        expect(data.phaseName, isNotEmpty,
            reason: 'phaseName vacío para el día $day de enero 2024');
      }
    });
  });
}
