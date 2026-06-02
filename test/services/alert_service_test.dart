import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:nubo/services/alert_service.dart';

// URL ficticia que devuelve el paso 1 de AEMET
const _datosUrl = 'https://data.aemet.es/alerts.xml';

// Madrid: areaCode="72", provinciaCode="28" → geocodePrefix="7228"
String _buildCapXml({
  String language = 'es-ES',
  String nivel = 'amarillo',
  String geocodeValue = '7228001',
  String onset = '2099-06-01T10:00:00+00:00',
  String expires = '2099-06-01T20:00:00+00:00',
  String event = 'Aviso de viento',
  String headline = 'Aviso de viento de nivel amarillo. Comunidad de Madrid',
}) =>
    '''
<alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
  <info>
    <language>$language</language>
    <event>$event</event>
    <headline>$headline</headline>
    <description>Descripción del aviso</description>
    <instruction>Extremar precauciones</instruction>
    <onset>$onset</onset>
    <expires>$expires</expires>
    <parameter>
      <valueName>nivel</valueName>
      <value>$nivel</value>
    </parameter>
    <parameter>
      <valueName>probabilidad</valueName>
      <value>40%-70%</value>
    </parameter>
    <area>
      <areaDesc>Comunidad de Madrid</areaDesc>
      <geocode>
        <valueName>EMMA_ID</valueName>
        <value>$geocodeValue</value>
      </geocode>
    </area>
  </info>
</alert>
''';

/// Crea un MockClient que simula el flujo de dos pasos de AEMET.
/// Paso 1: devuelve JSON con la URL de datos.
/// Paso 2: devuelve el XML CAP proporcionado.
MockClient _twoStepClient(
  String capXml, {
  int step1StatusCode = 200,
  int step2StatusCode = 200,
}) {
  int callCount = 0;
  return MockClient((request) async {
    callCount++;
    if (callCount == 1) {
      if (step1StatusCode != 200) {
        return http.Response('Error', step1StatusCode);
      }
      return http.Response(
        jsonEncode({'datos': _datosUrl, 'descripcion': 'exito', 'estado': 200}),
        200,
      );
    }
    return http.Response(capXml, step2StatusCode);
  });
}

void main() {
  group('AlertService.fetchAlerts — validación de entrada', () {
    test('municipioId vacío → []', () async {
      final service = AlertService(
        client: MockClient((_) async => http.Response('', 200)),
      );
      expect(await service.fetchAlerts(''), isEmpty);
    });

    test('municipioId de 1 carácter → []', () async {
      final service = AlertService(
        client: MockClient((_) async => http.Response('', 200)),
      );
      expect(await service.fetchAlerts('2'), isEmpty);
    });

    test('provincia desconocida (99) → [] sin hacer petición HTTP', () async {
      bool requestMade = false;
      final service = AlertService(
        client: MockClient((_) async {
          requestMade = true;
          return http.Response('', 200);
        }),
      );
      final result = await service.fetchAlerts('99001');
      expect(result, isEmpty);
      expect(requestMade, isFalse);
    });
  });

  group('AlertService.fetchAlerts — errores HTTP', () {
    test('paso 1 devuelve 404 → []', () async {
      final service = AlertService(
        client: _twoStepClient('', step1StatusCode: 404),
      );
      expect(await service.fetchAlerts('28079'), isEmpty);
    });

    test('paso 1 devuelve 503 → []', () async {
      final service = AlertService(
        client: _twoStepClient('', step1StatusCode: 503),
      );
      expect(await service.fetchAlerts('28079'), isEmpty);
    });

    test('paso 1 sin campo "datos" → []', () async {
      final service = AlertService(
        client: MockClient(
          (_) async => http.Response(jsonEncode({'descripcion': 'ok'}), 200),
        ),
      );
      expect(await service.fetchAlerts('28079'), isEmpty);
    });

    test('paso 1 con JSON inválido → []', () async {
      final service = AlertService(
        client: MockClient((_) async => http.Response('not-json', 200)),
      );
      expect(await service.fetchAlerts('28079'), isEmpty);
    });

    test('paso 2 devuelve 404 → []', () async {
      final service = AlertService(
        client: _twoStepClient('', step2StatusCode: 404),
      );
      expect(await service.fetchAlerts('28079'), isEmpty);
    });
  });

  group('AlertService.fetchAlerts — parseo CAP XML', () {
    test('alerta válida → WeatherAlert con campos correctos', () async {
      final service = AlertService(client: _twoStepClient(_buildCapXml()));

      final result = await service.fetchAlerts('28079');

      expect(result, hasLength(1));
      final alert = result.first;
      expect(alert.nivel, 'amarillo');
      expect(alert.event, 'Aviso de viento');
      expect(alert.headline, contains('Comunidad de Madrid'));
      expect(alert.description, isNotEmpty);
      expect(alert.instruction, isNotEmpty);
      expect(alert.probability, '40%-70%');
      expect(alert.areaDescription, 'Comunidad de Madrid');
      expect(alert.onset, isNotNull);
      expect(alert.expires, isNotNull);
    });

    test('nivel "verde" → [] (no es alerta real)', () async {
      final service = AlertService(
        client: _twoStepClient(_buildCapXml(nivel: 'verde')),
      );
      expect(await service.fetchAlerts('28079'), isEmpty);
    });

    test('nivel "VERDE" en mayúsculas → [] (case-insensitive)', () async {
      final service = AlertService(
        client: _twoStepClient(_buildCapXml(nivel: 'VERDE')),
      );
      expect(await service.fetchAlerts('28079'), isEmpty);
    });

    test('idioma "en-US" → [] (solo español)', () async {
      final service = AlertService(
        client: _twoStepClient(_buildCapXml(language: 'en-US')),
      );
      expect(await service.fetchAlerts('28079'), isEmpty);
    });

    test('idioma "fr-FR" → []', () async {
      final service = AlertService(
        client: _twoStepClient(_buildCapXml(language: 'fr-FR')),
      );
      expect(await service.fetchAlerts('28079'), isEmpty);
    });

    test('alerta expirada (expires en el pasado) → []', () async {
      final service = AlertService(
        client: _twoStepClient(_buildCapXml(
          expires: '2000-01-01T00:00:00+00:00',
        )),
      );
      expect(await service.fetchAlerts('28079'), isEmpty);
    });

    test('geocode de otra provincia → [] (no coincide con Madrid)', () async {
      // Geocode de Cataluña (69+08 = "6908...") en petición de Madrid (7228...)
      final service = AlertService(
        client: _twoStepClient(_buildCapXml(geocodeValue: '6908001')),
      );
      expect(await service.fetchAlerts('28079'), isEmpty);
    });

    test('nivel naranja → alert.nivel = "naranja"', () async {
      final service = AlertService(
        client: _twoStepClient(_buildCapXml(nivel: 'naranja')),
      );
      final result = await service.fetchAlerts('28079');
      expect(result, hasLength(1));
      expect(result.first.nivel, 'naranja');
    });

    test('nivel rojo → alert.nivel = "rojo"', () async {
      final service = AlertService(
        client: _twoStepClient(_buildCapXml(nivel: 'rojo')),
      );
      final result = await service.fetchAlerts('28079');
      expect(result, hasLength(1));
      expect(result.first.nivel, 'rojo');
    });

    test('dos alertas en el XML → devuelve ambas', () async {
      final twoAlerts =
          _buildCapXml(nivel: 'amarillo', event: 'Aviso de viento') +
          _buildCapXml(nivel: 'naranja', event: 'Aviso de lluvia');
      final service = AlertService(client: _twoStepClient(twoAlerts));

      final result = await service.fetchAlerts('28079');
      expect(result, hasLength(2));
    });

    test('mezcla de cabeceras tar e XML válido → solo los válidos', () async {
      // AEMET concatena XMLs con cabeceras tar binarias entre ellos.
      // El contenido no-XML no interfiere con el regex de extracción.
      final mixed = 'cabecera-tar-00000000\n' + _buildCapXml();
      final service = AlertService(client: _twoStepClient(mixed));

      final result = await service.fetchAlerts('28079');
      expect(result, hasLength(1));
    });

    test('XML completamente inválido → []', () async {
      final service = AlertService(
        client: _twoStepClient('esto no es xml ni cap'),
      );
      expect(await service.fetchAlerts('28079'), isEmpty);
    });

    test('bloque alert sin event ni headline → descartado', () async {
      final xmlSinContenido = '''
<alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
  <info>
    <language>es-ES</language>
    <onset>2099-06-01T10:00:00+00:00</onset>
    <expires>2099-06-01T20:00:00+00:00</expires>
    <parameter>
      <valueName>nivel</valueName>
      <value>amarillo</value>
    </parameter>
    <area>
      <areaDesc>Madrid</areaDesc>
      <geocode>
        <valueName>EMMA_ID</valueName>
        <value>7228001</value>
      </geocode>
    </area>
  </info>
</alert>
''';
      final service = AlertService(client: _twoStepClient(xmlSinContenido));
      expect(await service.fetchAlerts('28079'), isEmpty);
    });
  });

  group('AlertService — cobertura de _provinciaToArea', () {
    Future<bool> _makesHttpRequest(String municipioId) async {
      bool requestMade = false;
      final service = AlertService(
        client: MockClient((_) async {
          requestMade = true;
          return http.Response(jsonEncode({'datos': _datosUrl}), 200);
        }),
      );
      await service.fetchAlerts(municipioId);
      return requestMade;
    }

    test('Madrid (28) → hace petición HTTP', () async {
      expect(await _makesHttpRequest('28079'), isTrue);
    });

    test('Barcelona (08) → hace petición HTTP', () async {
      expect(await _makesHttpRequest('08019'), isTrue);
    });

    test('Sevilla (41) → hace petición HTTP', () async {
      expect(await _makesHttpRequest('41091'), isTrue);
    });

    test('Las Palmas (35) → hace petición HTTP', () async {
      expect(await _makesHttpRequest('35016'), isTrue);
    });

    test('S.C. Tenerife (38) → hace petición HTTP', () async {
      expect(await _makesHttpRequest('38038'), isTrue);
    });

    test('Ceuta (51) → hace petición HTTP', () async {
      expect(await _makesHttpRequest('51001'), isTrue);
    });

    test('Melilla (52) → hace petición HTTP', () async {
      expect(await _makesHttpRequest('52001'), isTrue);
    });

    test('A Coruña (15) → hace petición HTTP', () async {
      expect(await _makesHttpRequest('15030'), isTrue);
    });

    test('Bizkaia (48) → hace petición HTTP', () async {
      expect(await _makesHttpRequest('48020'), isTrue);
    });

    test('Provincia inexistente (99) → NO hace petición HTTP', () async {
      expect(await _makesHttpRequest('99001'), isFalse);
    });
  });
}
