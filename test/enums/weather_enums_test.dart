import 'package:flutter_test/flutter_test.dart';
import 'package:nubo/models/weather_enums.dart';

void main() {
  group('WeatherCode.fromCode', () {
    test('null devuelve Desconocido', () {
      expect(WeatherCode.fromCode(null).description, 'Desconocido');
    });

    test('cadena vacía devuelve Desconocido', () {
      expect(WeatherCode.fromCode('').description, 'Desconocido');
    });

    test('código no mapeado devuelve Desconocido', () {
      expect(WeatherCode.fromCode('999').description, 'Desconocido');
      expect(WeatherCode.fromCode('abc').description, 'Desconocido');
    });

    test('"0" → Despejado (día)', () {
      expect(WeatherCode.fromCode('0').description, 'Despejado');
    });

    test('"0n" → Despejado (noche)', () {
      expect(WeatherCode.fromCode('0n').description, 'Despejado');
    });

    test('"61" → Lluvia débil', () {
      expect(WeatherCode.fromCode('61').description, 'Lluvia débil');
    });

    test('"95" → Tormenta', () {
      expect(WeatherCode.fromCode('95').description, 'Tormenta');
    });

    test('"71" → Nieve débil', () {
      expect(WeatherCode.fromCode('71').description, 'Nieve débil');
    });

    test('"45" → Niebla', () {
      expect(WeatherCode.fromCode('45').description, 'Niebla');
    });

    test('todos los códigos del mapa tienen descripción no vacía', () {
      for (final entry in WeatherCode.codes.entries) {
        expect(
          entry.value.description,
          isNotEmpty,
          reason: 'El código "${entry.key}" tiene descripción vacía',
        );
      }
    });

    test('todos los códigos nocturnos terminan en "n"', () {
      final nightCodes = WeatherCode.codes.keys.where((k) => k.endsWith('n'));
      expect(nightCodes, isNotEmpty);
      for (final code in nightCodes) {
        expect(code.endsWith('n'), isTrue);
      }
    });
  });

  group('SkyCondition.fromCode', () {
    test('null → clear', () {
      expect(SkyCondition.fromCode(null), SkyCondition.clear);
    });

    test('cadena vacía → clear', () {
      expect(SkyCondition.fromCode(''), SkyCondition.clear);
    });

    test('"0" → clear', () {
      expect(SkyCondition.fromCode('0'), SkyCondition.clear);
    });

    test('"1" → clear', () {
      expect(SkyCondition.fromCode('1'), SkyCondition.clear);
    });

    test('"2" → partlyCloudy', () {
      expect(SkyCondition.fromCode('2'), SkyCondition.partlyCloudy);
    });

    test('"3" → partlyCloudy', () {
      expect(SkyCondition.fromCode('3'), SkyCondition.partlyCloudy);
    });

    test('"45" → partlyCloudy (niebla)', () {
      expect(SkyCondition.fromCode('45'), SkyCondition.partlyCloudy);
    });

    test('"48" → partlyCloudy (niebla escarchada)', () {
      expect(SkyCondition.fromCode('48'), SkyCondition.partlyCloudy);
    });

    test('"51" → overcast', () {
      expect(SkyCondition.fromCode('51'), SkyCondition.overcast);
    });

    test('"95" → overcast (tormenta)', () {
      expect(SkyCondition.fromCode('95'), SkyCondition.overcast);
    });

    test('código nocturno "0n" → clear', () {
      expect(SkyCondition.fromCode('0n'), SkyCondition.clear);
    });

    test('código nocturno "3n" → partlyCloudy', () {
      expect(SkyCondition.fromCode('3n'), SkyCondition.partlyCloudy);
    });

    test('código nocturno "61n" → overcast', () {
      expect(SkyCondition.fromCode('61n'), SkyCondition.overcast);
    });

    test('código no numérico → clear por defecto', () {
      expect(SkyCondition.fromCode('abc'), SkyCondition.clear);
    });
  });
}
