import 'package:flutter_test/flutter_test.dart';
import 'package:nubo/utils/sun_calculator.dart';

void main() {
  group('SunCalculator.calculateTimes', () {
    // Madrid: lat=40.4168, lng=-3.7038
    const double madridLat = 40.4168;
    const double madridLng = -3.7038;

    test('amanecer es anterior al atardecer', () {
      final times = SunCalculator.calculateTimes(
        DateTime(2024, 6, 21),
        madridLat,
        madridLng,
      );

      expect(times.sunrise.isBefore(times.sunset), isTrue);
    });

    test('amanecer está entre medianoche y mediodía', () {
      final times = SunCalculator.calculateTimes(
        DateTime(2024, 6, 21),
        madridLat,
        madridLng,
      );

      expect(times.sunrise.hour, lessThan(12));
    });

    test('atardecer está entre mediodía y medianoche', () {
      final times = SunCalculator.calculateTimes(
        DateTime(2024, 6, 21),
        madridLat,
        madridLng,
      );

      expect(times.sunset.hour, greaterThanOrEqualTo(12));
    });

    test('en solsticio de verano el día es más largo que en invierno', () {
      final summer = SunCalculator.calculateTimes(
        DateTime(2024, 6, 21),
        madridLat,
        madridLng,
      );
      final winter = SunCalculator.calculateTimes(
        DateTime(2024, 12, 21),
        madridLat,
        madridLng,
      );

      final summerDaylight = summer.sunset.difference(summer.sunrise);
      final winterDaylight = winter.sunset.difference(winter.sunrise);

      expect(summerDaylight, greaterThan(winterDaylight));
    });

    test('funciona con coordenadas de Barcelona', () {
      final times = SunCalculator.calculateTimes(
        DateTime(2024, 3, 21),
        41.3851,
        2.1734,
      );

      expect(times.sunrise.isBefore(times.sunset), isTrue);
    });

    test('funciona con coordenadas de Canarias (distinto huso)', () {
      final times = SunCalculator.calculateTimes(
        DateTime(2024, 6, 21),
        28.1235,
        -15.4363, // Las Palmas de Gran Canaria
      );

      expect(times.sunrise.isBefore(times.sunset), isTrue);
    });
  });

  group('SunTimes', () {
    test('toString no lanza excepción', () {
      final times = SunCalculator.calculateTimes(
        DateTime(2024, 6, 21),
        40.4168,
        -3.7038,
      );

      expect(() => times.toString(), returnsNormally);
      expect(times.toString(), contains('Sunrise'));
      expect(times.toString(), contains('Sunset'));
    });
  });
}
