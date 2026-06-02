import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nubo/models/weather_alert.dart';

WeatherAlert _makeAlert({
  String nivel = 'amarillo',
  DateTime? onset,
  DateTime? expires,
}) {
  return WeatherAlert(
    nivel: nivel,
    event: 'Aviso de viento',
    headline: 'Aviso de viento de nivel $nivel',
    description: 'Se esperan vientos fuertes',
    instruction: 'Extremar precauciones',
    areaDescription: 'Costa mediterránea',
    onset: onset,
    expires: expires,
    probability: '40%-70%',
  );
}

void main() {
  group('WeatherAlert.fromJson / toJson', () {
    test('round-trip completo conserva todos los campos', () {
      final original = _makeAlert(
        nivel: 'naranja',
        onset: DateTime(2099, 6, 1, 10, 0),
        expires: DateTime(2099, 6, 1, 20, 0),
      );
      final json = original.toJson();
      final restored = WeatherAlert.fromJson(json);

      expect(restored.nivel, original.nivel);
      expect(restored.event, original.event);
      expect(restored.headline, original.headline);
      expect(restored.description, original.description);
      expect(restored.instruction, original.instruction);
      expect(restored.areaDescription, original.areaDescription);
      expect(restored.probability, original.probability);
    });

    test('fromJson con campos null usa defaults vacíos', () {
      final alert = WeatherAlert.fromJson({});
      expect(alert.nivel, '');
      expect(alert.event, '');
      expect(alert.onset, isNull);
      expect(alert.expires, isNull);
    });

    test('fromJson parsea fecha ISO 8601 correctamente', () {
      final alert = WeatherAlert.fromJson({
        'nivel': 'rojo',
        'onset': '2099-06-01T10:00:00.000',
        'expires': '2099-06-01T20:00:00.000',
      });
      expect(alert.onset, isNotNull);
      expect(alert.expires, isNotNull);
      expect(alert.onset!.year, 2099);
    });

    test('fromJson con fecha inválida deja onset/expires null', () {
      final alert = WeatherAlert.fromJson({
        'nivel': 'amarillo',
        'onset': 'no-es-una-fecha',
        'expires': '???',
      });
      expect(alert.onset, isNull);
      expect(alert.expires, isNull);
    });
  });

  group('WeatherAlert.color', () {
    test('rojo devuelve color rojo', () {
      expect(_makeAlert(nivel: 'rojo').color, const Color(0xFFD32F2F));
    });

    test('naranja devuelve color naranja', () {
      expect(_makeAlert(nivel: 'naranja').color, const Color(0xFFFF8F00));
    });

    test('amarillo devuelve color amarillo', () {
      expect(_makeAlert(nivel: 'amarillo').color, const Color(0xFFFBC02D));
    });

    test('nivel desconocido devuelve color amarillo por defecto', () {
      expect(_makeAlert(nivel: 'verde').color, const Color(0xFFFBC02D));
    });

    test('nivel en mayúsculas funciona (case-insensitive)', () {
      expect(_makeAlert(nivel: 'ROJO').color, const Color(0xFFD32F2F));
    });
  });

  group('WeatherAlert.severity', () {
    test('rojo tiene severidad 3', () {
      expect(_makeAlert(nivel: 'rojo').severity, 3);
    });

    test('naranja tiene severidad 2', () {
      expect(_makeAlert(nivel: 'naranja').severity, 2);
    });

    test('amarillo tiene severidad 1', () {
      expect(_makeAlert(nivel: 'amarillo').severity, 1);
    });

    test('nivel desconocido tiene severidad 0', () {
      expect(_makeAlert(nivel: '').severity, 0);
    });
  });

  group('WeatherAlert.nivelDisplay', () {
    test('capitaliza correctamente', () {
      expect(_makeAlert(nivel: 'amarillo').nivelDisplay, 'Amarillo');
      expect(_makeAlert(nivel: 'naranja').nivelDisplay, 'Naranja');
      expect(_makeAlert(nivel: 'rojo').nivelDisplay, 'Rojo');
    });

    test('nivel vacío devuelve cadena vacía sin crash', () {
      expect(_makeAlert(nivel: '').nivelDisplay, '');
    });
  });

  group('WeatherAlert.isActiveOrUpcoming', () {
    test('alerta con expires en el futuro está activa', () {
      final alert = _makeAlert(expires: DateTime(2099, 12, 31));
      expect(alert.isActiveOrUpcoming, isTrue);
    });

    test('alerta con expires en el pasado no está activa', () {
      final alert = _makeAlert(expires: DateTime(2000, 1, 1));
      expect(alert.isActiveOrUpcoming, isFalse);
    });

    test('alerta con expires null está activa', () {
      final alert = _makeAlert(expires: null);
      expect(alert.isActiveOrUpcoming, isTrue);
    });
  });
}
