import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:nubo/services/municipio_search_service.dart';

// Fixtures de municipios en el formato real de AEMET
// Nota: lat/lon usan coma como separador decimal, igual que la API real
const _municipiosJson = [
  {'id': 'id28079', 'nombre': 'Madrid', 'latitud_dec': '40,4169', 'longitud_dec': '-3,7033'},
  {'id': 'id08019', 'nombre': 'Barcelona', 'latitud_dec': '41,3825', 'longitud_dec': '2,1769'},
  {'id': 'id29067', 'nombre': 'Málaga', 'latitud_dec': '36,7167', 'longitud_dec': '-4,4167'},
  {'id': 'id15030', 'nombre': 'Coruña, A', 'latitud_dec': '43,3623', 'longitud_dec': '-8,4115'},
  {'id': 'id46250', 'nombre': 'Valencia/València', 'latitud_dec': '39,4699', 'longitud_dec': '-0,3763'},
  {'id': 'id41091', 'nombre': 'Sevilla (capital)', 'latitud_dec': '37,3886', 'longitud_dec': '-5,9823'},
  {'id': 'id50297', 'nombre': 'Zaragoza', 'latitud_dec': '41,6523', 'longitud_dec': '-0,8806'},
  {'id': 'id47186', 'nombre': 'Valladolid', 'latitud_dec': '41,6523', 'longitud_dec': '-4,7245'},
  {'id': 'id18087', 'nombre': 'Granada', 'latitud_dec': '37,1773', 'longitud_dec': '-3,5986'},
  {'id': 'id30030', 'nombre': 'Murcia', 'latitud_dec': '37,9838', 'longitud_dec': '-1,1297'},
  {'id': 'id14021', 'nombre': 'Córdoba', 'latitud_dec': '37,8882', 'longitud_dec': '-4,7794'},
];

const _datosUrl = 'https://data.aemet.es/municipios.json';

/// Crea un MockClient que simula el flujo de dos pasos de AEMET.
MockClient _twoStepClient(
  String municipiosJson, {
  int step1StatusCode = 200,
  int step2StatusCode = 200,
}) {
  int callCount = 0;
  return MockClient((request) async {
    callCount++;
    if (callCount == 1) {
      if (step1StatusCode != 200) return http.Response('Error', step1StatusCode);
      return http.Response(
        jsonEncode({'datos': _datosUrl, 'descripcion': 'exito', 'estado': 200}),
        200,
      );
    }
    return http.Response(municipiosJson, step2StatusCode);
  });
}

MunicipioSearchService _loadedService() =>
    MunicipioSearchService(client: _twoStepClient(jsonEncode(_municipiosJson)));

void main() {
  group('MunicipioSearchService.searchByName — validación de entrada', () {
    test('query vacía → []', () async {
      final service = _loadedService();
      expect(await service.searchByName(''), isEmpty);
    });

    test('query solo espacios → []', () async {
      final service = _loadedService();
      expect(await service.searchByName('   '), isEmpty);
    });
  });

  group('MunicipioSearchService.searchByName — búsqueda', () {
    test('query exacta → devuelve coincidencia', () async {
      final service = _loadedService();
      final results = await service.searchByName('Madrid');
      expect(results.any((l) => l.nombre == 'Madrid'), isTrue);
    });

    test('query en minúsculas → insensible a mayúsculas', () async {
      final service = _loadedService();
      final results = await service.searchByName('madrid');
      expect(results.any((l) => l.nombre == 'Madrid'), isTrue);
    });

    test('query sin tilde → encuentra nombre con tilde', () async {
      final service = _loadedService();
      final results = await service.searchByName('malaga');
      expect(results.any((l) => l.nombre == 'Málaga'), isTrue);
    });

    test('query parcial → devuelve resultados que contienen el texto', () async {
      final service = _loadedService();
      final results = await service.searchByName('val');
      // Debe encontrar "Valencia/València" y "Valladolid"
      expect(results.length, greaterThanOrEqualTo(2));
    });

    test('query sin resultados → []', () async {
      final service = _loadedService();
      final results = await service.searchByName('xyzxyzxyz');
      expect(results, isEmpty);
    });

    test('devuelve como máximo 10 resultados', () async {
      // Los 11 municipios del fixture contienen 'a' → debería limitarse a 10
      final service = _loadedService();
      final results = await service.searchByName('a');
      expect(results.length, lessThanOrEqualTo(10));
    });

    test('cada resultado tiene municipioId no vacío', () async {
      final service = _loadedService();
      final results = await service.searchByName('a');
      for (final loc in results) {
        expect(loc.municipioId, isNotEmpty);
      }
    });

    test('municipioId no contiene el prefijo "id" de AEMET', () async {
      final service = _loadedService();
      final results = await service.searchByName('Madrid');
      expect(results.first.municipioId, '28079');
      expect(results.first.municipioId, isNot(startsWith('id')));
    });
  });

  group('MunicipioSearchService.searchByName — limpieza de nombres', () {
    test('"Sevilla (capital)" → elimina el sufijo entre paréntesis', () async {
      final service = _loadedService();
      final results = await service.searchByName('Sevilla');
      expect(results.first.nombre, 'Sevilla');
    });

    test('"Valencia/València" → se conserva sin modificar (sin paréntesis)', () async {
      final service = _loadedService();
      final results = await service.searchByName('Valencia');
      expect(results.any((l) => l.nombre.contains('València')), isTrue);
    });

    test('"Coruña, A" → reordenado por SavedLocation a "A Coruña"', () async {
      final service = _loadedService();
      final results = await service.searchByName('Coruña');
      expect(results.any((l) => l.nombre == 'A Coruña'), isTrue);
    });
  });

  group('MunicipioSearchService.searchByName — errores HTTP', () {
    test('fallo de red → devuelve [] sin lanzar', () async {
      final service = MunicipioSearchService(
        client: MockClient((_) async => throw Exception('Sin conexión')),
      );
      expect(await service.searchByName('Madrid'), isEmpty);
    });

    test('paso 1 con 404 → devuelve []', () async {
      final service = MunicipioSearchService(
        client: _twoStepClient('', step1StatusCode: 404),
      );
      expect(await service.searchByName('Madrid'), isEmpty);
    });

    test('paso 1 sin "datos" → devuelve []', () async {
      final service = MunicipioSearchService(
        client: MockClient((_) async =>
            http.Response(jsonEncode({'descripcion': 'ok'}), 200)),
      );
      expect(await service.searchByName('Madrid'), isEmpty);
    });

    test('paso 2 con 404 → devuelve []', () async {
      final service = MunicipioSearchService(
        client: _twoStepClient('', step2StatusCode: 404),
      );
      expect(await service.searchByName('Madrid'), isEmpty);
    });

    test('paso 2 con JSON inválido → devuelve []', () async {
      final service = MunicipioSearchService(
        client: _twoStepClient('not-json'),
      );
      expect(await service.searchByName('Madrid'), isEmpty);
    });
  });

  group('MunicipioSearchService — caché (_ensureLoaded solo una vez)', () {
    test('múltiples búsquedas solo hacen 2 peticiones HTTP en total', () async {
      int httpCallCount = 0;
      final client = MockClient((request) async {
        httpCallCount++;
        if (httpCallCount == 1) {
          return http.Response(
            jsonEncode({'datos': _datosUrl}),
            200,
          );
        }
        if (httpCallCount == 2) {
          return http.Response(jsonEncode(_municipiosJson), 200);
        }
        // Cualquier llamada adicional no debería ocurrir
        return http.Response('no-deberia-llamarse', 500);
      });

      final service = MunicipioSearchService(client: client);
      await service.searchByName('Madrid');
      await service.searchByName('Barcelona');
      await service.searchByName('Sevilla');

      expect(httpCallCount, 2); // paso 1 + paso 2, solo en la primera búsqueda
    });
  });

  group('MunicipioSearchService.findNearestMunicipio', () {
    test('devuelve el municipio más cercano a las coordenadas', () async {
      final service = _loadedService();
      // Coordenadas de Madrid: 40.4168, -3.7038
      final nearest = await service.findNearestMunicipio(40.42, -3.70);
      expect(nearest, isNotNull);
      expect(nearest!.municipioId, '28079'); // Madrid
    });

    test('devuelve el municipio más cercano a Barcelona', () async {
      final service = _loadedService();
      // Coordenadas de Barcelona: 41.3825, 2.1769
      final nearest = await service.findNearestMunicipio(41.38, 2.18);
      expect(nearest, isNotNull);
      expect(nearest!.municipioId, '08019');
    });

    test('cache vacía (error de red) → null', () async {
      final service = MunicipioSearchService(
        client: MockClient((_) async => throw Exception('Sin conexión')),
      );
      final nearest = await service.findNearestMunicipio(40.42, -3.70);
      expect(nearest, isNull);
    });

    test('ignora entradas sin latitud o longitud', () async {
      final municipiosConNulos = [
        {'id': 'id99001', 'nombre': 'SinCoordenadas'},
        {'id': 'id28079', 'nombre': 'Madrid', 'latitud_dec': '40,4169', 'longitud_dec': '-3,7033'},
      ];
      final service = MunicipioSearchService(
        client: _twoStepClient(jsonEncode(municipiosConNulos)),
      );
      final nearest = await service.findNearestMunicipio(40.42, -3.70);
      expect(nearest, isNotNull);
      expect(nearest!.municipioId, '28079');
    });

    test('ignora entradas con lat/lon no parseables', () async {
      final municipiosMalFormados = [
        {'id': 'id99002', 'nombre': 'MalFormado', 'latitud_dec': 'no-es-numero', 'longitud_dec': 'tampoco'},
        {'id': 'id28079', 'nombre': 'Madrid', 'latitud_dec': '40,4169', 'longitud_dec': '-3,7033'},
      ];
      final service = MunicipioSearchService(
        client: _twoStepClient(jsonEncode(municipiosMalFormados)),
      );
      final nearest = await service.findNearestMunicipio(40.42, -3.70);
      expect(nearest!.municipioId, '28079');
    });
  });

  group('MunicipioSearchService.getCoordinates', () {
    test('ID sin prefijo → devuelve coordenadas', () async {
      final service = _loadedService();
      final coords = await service.getCoordinates('28079');
      expect(coords, isNotNull);
      expect(coords!.lat, closeTo(40.4169, 0.001));
      expect(coords.lon, closeTo(-3.7033, 0.001));
    });

    test('ID con prefijo "id" → devuelve coordenadas (sin doble prefijo)', () async {
      final service = _loadedService();
      final coords = await service.getCoordinates('id28079');
      expect(coords, isNotNull);
      expect(coords!.lat, closeTo(40.4169, 0.001));
    });

    test('ID inexistente → null', () async {
      final service = _loadedService();
      final coords = await service.getCoordinates('99999');
      expect(coords, isNull);
    });

    test('ID vacío → null', () async {
      final service = _loadedService();
      final coords = await service.getCoordinates('');
      expect(coords, isNull);
    });

    test('entrada con lat/lon inválidos → null', () async {
      final municipiosConCoordsMalas = [
        {'id': 'id99003', 'nombre': 'Raro', 'latitud_dec': 'NaN', 'longitud_dec': 'inf'},
      ];
      final service = MunicipioSearchService(
        client: _twoStepClient(jsonEncode(municipiosConCoordsMalas)),
      );
      final coords = await service.getCoordinates('99003');
      expect(coords, isNull);
    });

    test('lat/lon con coma decimal (formato AEMET) se parsean correctamente', () async {
      final service = _loadedService();
      final coords = await service.getCoordinates('29067'); // Málaga
      expect(coords, isNotNull);
      expect(coords!.lat, closeTo(36.7167, 0.001));
      expect(coords.lon, closeTo(-4.4167, 0.001));
    });
  });
}
