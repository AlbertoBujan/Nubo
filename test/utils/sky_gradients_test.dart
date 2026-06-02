import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nubo/models/weather_enums.dart';
import 'package:nubo/utils/sky_gradients.dart';

void main() {
  group('SkyGradients.forPhase — devuelve gradiente no nulo para toda combinación', () {
    for (final phase in SunPhase.values) {
      for (final sky in SkyCondition.values) {
        test('$phase + $sky → 4 colores, 4 stops', () {
          final g = SkyGradients.forPhase(phase, sky);
          expect(g.colors, hasLength(4));
          expect(g.stops, hasLength(4));
          expect(g.stops, [0.0, 0.33, 0.67, 1.0]);
          expect(g.begin, Alignment.topCenter);
          expect(g.end, Alignment.bottomCenter);
        });
      }
    }
  });

  group('SkyGradients — gradientes diferenciados por fase', () {
    test('día despejado ≠ noche despejada', () {
      final day = SkyGradients.day(SkyCondition.clear);
      final night = SkyGradients.night(SkyCondition.clear);
      expect(day.colors, isNot(equals(night.colors)));
    });

    test('amanecer despejado ≠ atardecer despejado', () {
      final sunrise = SkyGradients.sunrise(SkyCondition.clear);
      final sunset = SkyGradients.sunset(SkyCondition.clear);
      expect(sunrise.colors, isNot(equals(sunset.colors)));
    });

    test('despejado ≠ cubierto para el mismo momento', () {
      final clear = SkyGradients.day(SkyCondition.clear);
      final overcast = SkyGradients.day(SkyCondition.overcast);
      expect(clear.colors, isNot(equals(overcast.colors)));
    });
  });

  group('SkyGradients.lerp', () {
    final a = SkyGradients.day(SkyCondition.clear);
    final b = SkyGradients.night(SkyCondition.clear);

    test('t=0 → idéntico a gradiente A', () {
      final result = SkyGradients.lerp(a, b, 0.0);
      for (int i = 0; i < 4; i++) {
        expect(result.colors[i], a.colors[i]);
      }
    });

    test('t=1 → idéntico a gradiente B', () {
      final result = SkyGradients.lerp(a, b, 1.0);
      for (int i = 0; i < 4; i++) {
        expect(result.colors[i], b.colors[i]);
      }
    });

    test('t=0.5 → colores intermedios', () {
      final result = SkyGradients.lerp(a, b, 0.5);
      for (int i = 0; i < 4; i++) {
        expect(result.colors[i], isNot(a.colors[i]));
        expect(result.colors[i], isNot(b.colors[i]));
      }
    });

    test('resultado mantiene 4 stops y orientación', () {
      final result = SkyGradients.lerp(a, b, 0.5);
      expect(result.colors, hasLength(4));
      expect(result.stops, [0.0, 0.33, 0.67, 1.0]);
      expect(result.begin, Alignment.topCenter);
      expect(result.end, Alignment.bottomCenter);
    });
  });
}
