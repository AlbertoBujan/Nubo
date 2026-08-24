import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nubo/widgets/weather_effects_overlay.dart';

/// Los CustomPaint que pinta el overlay, ignorando los que MaterialApp
/// añade por su cuenta al árbol.
Finder _painters() => find.descendant(
      of: find.byType(WeatherEffectsOverlay),
      matching: find.byType(CustomPaint),
    );

void main() {
  group('WeatherEffect.fromSkyCode', () {
    test('cielo despejado o nublado no dibuja nada', () {
      expect(WeatherEffect.fromSkyCode('0'), WeatherEffect.none);
      expect(WeatherEffect.fromSkyCode('2'), WeatherEffect.none);
      expect(WeatherEffect.fromSkyCode('3'), WeatherEffect.none);
      expect(WeatherEffect.fromSkyCode('45'), WeatherEffect.none);
    });

    test('la nieve no cae como lluvia', () {
      expect(WeatherEffect.fromSkyCode('73'), WeatherEffect.none);
      expect(WeatherEffect.fromSkyCode('86'), WeatherEffect.none);
    });

    test('la llovizna usa el efecto más suave', () {
      expect(WeatherEffect.fromSkyCode('51'), WeatherEffect.drizzle);
      expect(WeatherEffect.fromSkyCode('57'), WeatherEffect.drizzle);
    });

    test('distingue lluvia débil de fuerte', () {
      expect(WeatherEffect.fromSkyCode('61'), WeatherEffect.rain);
      expect(WeatherEffect.fromSkyCode('80'), WeatherEffect.rain);
      expect(WeatherEffect.fromSkyCode('65'), WeatherEffect.heavyRain);
      expect(WeatherEffect.fromSkyCode('82'), WeatherEffect.heavyRain);
    });

    test('la tormenta es el único efecto con destellos', () {
      expect(WeatherEffect.fromSkyCode('95'), WeatherEffect.thunder);
      expect(WeatherEffect.fromSkyCode('99'), WeatherEffect.thunder);
      expect(WeatherEffect.thunder.hasFlashes, isTrue);
      expect(WeatherEffect.heavyRain.hasFlashes, isFalse);
    });

    test('ignora el sufijo nocturno', () {
      expect(WeatherEffect.fromSkyCode('61n'), WeatherEffect.rain);
      expect(WeatherEffect.fromSkyCode('95n'), WeatherEffect.thunder);
    });

    test('código nulo o inválido no dibuja nada', () {
      expect(WeatherEffect.fromSkyCode(null), WeatherEffect.none);
      expect(WeatherEffect.fromSkyCode(''), WeatherEffect.none);
      expect(WeatherEffect.fromSkyCode('abc'), WeatherEffect.none);
    });

    test('a más intensidad, más gotas', () {
      expect(WeatherEffect.drizzle.dropCount,
          lessThan(WeatherEffect.rain.dropCount));
      expect(WeatherEffect.rain.dropCount,
          lessThan(WeatherEffect.heavyRain.dropCount));
      expect(WeatherEffect.none.dropCount, 0);
    });
  });

  group('WeatherEffectsOverlay', () {
    testWidgets('no intercepta los gestos de la página que hay debajo',
        (tester) async {
      var tapped = false;

      await tester.pumpWidget(
        MaterialApp(
          home: Stack(
            children: [
              GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => tapped = true,
                child: const SizedBox.expand(),
              ),
              const Positioned.fill(
                child: WeatherEffectsOverlay(effect: WeatherEffect.thunder),
              ),
            ],
          ),
        ),
      );

      await tester.tap(find.byType(WeatherEffectsOverlay), warnIfMissed: false);
      await tester.pump();

      expect(tapped, isTrue);
    });

    testWidgets('sin fenómeno no pinta partículas', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: WeatherEffectsOverlay(effect: WeatherEffect.none),
        ),
      );
      await tester.pump(const Duration(milliseconds: 100));

      expect(_painters(), findsNothing);
    });

    testWidgets('con lluvia pinta y sigue animando', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: WeatherEffectsOverlay(effect: WeatherEffect.rain),
        ),
      );
      await tester.pump(const Duration(milliseconds: 100));

      expect(_painters(), findsWidgets);

      // Un frame posterior debe seguir produciendo pintura (animación viva).
      await tester.pump(const Duration(milliseconds: 300));
      expect(_painters(), findsWidgets);
    });

    testWidgets('cambiar de fenómeno no rompe el widget', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: WeatherEffectsOverlay(effect: WeatherEffect.thunder),
        ),
      );
      await tester.pump(const Duration(milliseconds: 200));

      await tester.pumpWidget(
        const MaterialApp(
          home: WeatherEffectsOverlay(effect: WeatherEffect.none),
        ),
      );
      await tester.pump(const Duration(milliseconds: 200));

      expect(tester.takeException(), isNull);
    });
  });
}
