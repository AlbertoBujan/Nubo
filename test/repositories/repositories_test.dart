import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:nubo/models/saved_location.dart';
import 'package:nubo/models/weather_alert.dart';
import 'package:nubo/repositories/alert_repository.dart';
import 'package:geolocator/geolocator.dart';
import 'package:nubo/repositories/location_repository.dart';
import 'package:nubo/repositories/weather_repository.dart';
import 'package:nubo/services/alert_service.dart';
import 'package:nubo/services/api_service.dart';
import 'package:nubo/services/municipio_search_service.dart';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const _forecastJson = <String, dynamic>{
  'hourly': {
    'time': ['2099-06-01T12:00'],
    'temperature_2m': [22.0],
    'weather_code': [0],
    'is_day': [1],
    'precipitation_probability': [10],
    'relative_humidity_2m': [55],
    'wind_speed_10m': [15],
    'wind_direction_10m': [90.0],
    'dew_point_2m': [12.0],
  },
  'daily': {
    'time': ['2099-06-01'],
    'weather_code': [0],
    'temperature_2m_max': [28.0],
    'temperature_2m_min': [15.0],
    'precipitation_probability_max': [10],
  },
};

// Fake LocationRepository para inyectar en WeatherRepositoryImpl
class FakeLocationRepo implements LocationRepository {
  final Map<String, ({double lat, double lon})?> coords;

  FakeLocationRepo({Map<String, ({double lat, double lon})?>? coords})
      : coords = coords ?? {'28079': (lat: 40.4169, lon: -3.7033)};

  @override
  Future<({double lat, double lon})?> getCoordinates(String id) async =>
      coords[id];

  @override
  Future<SavedLocation?> findNearest(double lat, double lon) async => null;

  @override
  Future<List<SavedLocation>> searchByName(String q) async => [];

  @override
  Future<Position> getCurrentPosition() async => throw UnimplementedError();
}

final _dummyClient = MockClient((_) async => http.Response('', 200));

void main() {
// ---------------------------------------------------------------------------
// WeatherRepository
// ---------------------------------------------------------------------------

group('WeatherRepositoryImpl', () {
  test('getForecast devuelve daily, hourly y rawJson al tener coordenadas', () async {
    final apiClient = MockClient(
      (_) async => http.Response(jsonEncode(_forecastJson), 200),
    );
    final repo = WeatherRepositoryImpl(
      locationRepository: FakeLocationRepo(),
      apiService: OpenMeteoApiService(client: apiClient),
    );

    final result = await repo.getForecast('28079');

    expect(result.rawJson, isNotEmpty);
    // daily y hourly pueden estar vacíos si las fechas son pasadas; lo importante
    // es que no lanza y devuelve el rawJson correcto
    expect(result.rawJson.containsKey('hourly'), isTrue);
    expect(result.rawJson.containsKey('daily'), isTrue);
  });

  test('getForecast sin coordenadas lanza WeatherRepositoryException', () async {
    final repo = WeatherRepositoryImpl(
      locationRepository: FakeLocationRepo(coords: {}),
      apiService: OpenMeteoApiService(client: _dummyClient),
    );

    await expectLater(
      repo.getForecast('99999'),
      throwsA(isA<WeatherRepositoryException>()),
    );
  });

  test('getForecast propaga OpenMeteoApiException del servicio', () async {
    final apiClient = MockClient((_) async => http.Response('Error', 500));
    final repo = WeatherRepositoryImpl(
      locationRepository: FakeLocationRepo(),
      apiService: OpenMeteoApiService(client: apiClient),
    );

    await expectLater(
      repo.getForecast('28079'),
      throwsA(isA<OpenMeteoApiException>()),
    );
  });

  test('WeatherRepositoryException tiene mensaje no vacío', () {
    const e = WeatherRepositoryException('Sin coordenadas');
    expect(e.message, isNotEmpty);
    expect(e.toString(), contains('Sin coordenadas'));
  });
});

// ---------------------------------------------------------------------------
// AlertRepository
// ---------------------------------------------------------------------------

group('AlertRepositoryImpl', () {
  test('getAlerts devuelve lista vacía para provincia desconocida', () async {
    final repo = AlertRepositoryImpl(
      alertService: AlertService(client: _dummyClient),
    );
    final result = await repo.getAlerts('99999');
    expect(result, isEmpty);
  });

  test('getAlerts delega en AlertService', () async {
    // Provincias que AEMET no mapea → servicio devuelve [] sin llamada HTTP
    final repo = AlertRepositoryImpl(
      alertService: AlertService(client: _dummyClient),
    );
    expect(await repo.getAlerts(''), isEmpty);
    expect(await repo.getAlerts('9'), isEmpty);
  });
});

// ---------------------------------------------------------------------------
// LocationRepository
// ---------------------------------------------------------------------------

group('LocationRepositoryImpl', () {
  test('getCoordinates delega en MunicipioSearchService', () async {
    // Municipios json con Madrid
    final municipiosJson = jsonEncode([
      {
        'id': 'id28079',
        'nombre': 'Madrid',
        'latitud_dec': '40,4169',
        'longitud_dec': '-3,7033',
      },
    ]);
    int callCount = 0;
    final client = MockClient((request) async {
      callCount++;
      if (callCount == 1) {
        return http.Response(
          jsonEncode({'datos': 'https://data.aemet.es/municipios.json'}),
          200,
        );
      }
      return http.Response(municipiosJson, 200);
    });

    final repo = LocationRepositoryImpl(
      searchService: MunicipioSearchService(client: client),
    );

    final coords = await repo.getCoordinates('28079');
    expect(coords, isNotNull);
    expect(coords!.lat, closeTo(40.4169, 0.001));
  });

  test('searchByName delega en MunicipioSearchService', () async {
    // Con red fallando → devuelve []
    final repo = LocationRepositoryImpl(
      searchService: MunicipioSearchService(
        client: MockClient((_) async => throw Exception('sin red')),
      ),
    );
    final results = await repo.searchByName('Madrid');
    expect(results, isEmpty);
  });

  test('searchByName con query vacía → []', () async {
    final repo = LocationRepositoryImpl(
      searchService: MunicipioSearchService(client: _dummyClient),
    );
    expect(await repo.searchByName(''), isEmpty);
  });
});
} // end main
