import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:nubo/models/saved_location.dart';
import 'package:nubo/models/weather_alert.dart';
import 'package:nubo/repositories/weather_storage_repository.dart';
import 'package:nubo/utils/sun_calculator.dart';

// JSON mínimo válido para los parsers de forecast (fechas futuras)
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

WeatherAlert _alert({String nivel = 'amarillo'}) => WeatherAlert(
      nivel: nivel,
      event: 'Aviso',
      headline: 'Aviso de prueba',
      description: '',
      instruction: '',
      areaDescription: 'Madrid',
      onset: null,
      expires: DateTime(2099),
      probability: '40%',
    );

void main() {
  late WeatherStorageRepositoryImpl repo;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    repo = WeatherStorageRepositoryImpl();
  });

  // ---------------------------------------------------------------------------
  // loadLocations / saveLocations
  // ---------------------------------------------------------------------------

  group('WeatherStorageRepository — localizaciones', () {
    test('loadLocations devuelve lista vacía sin datos previos', () async {
      final result = await repo.loadLocations();
      expect(result, isEmpty);
    });

    test('saveLocations + loadLocations hace round-trip', () async {
      final locations = [
        SavedLocation(municipioId: '28079', nombre: 'Madrid'),
        SavedLocation(municipioId: '08019', nombre: 'Barcelona'),
      ];
      await repo.saveLocations(locations);
      final loaded = await repo.loadLocations();

      expect(loaded, hasLength(2));
      expect(loaded[0].municipioId, '28079');
      expect(loaded[1].municipioId, '08019');
    });

    test('saveLocations sobreescribe la lista anterior', () async {
      await repo.saveLocations([
        SavedLocation(municipioId: '28079', nombre: 'Madrid'),
      ]);
      await repo.saveLocations([
        SavedLocation(municipioId: '08019', nombre: 'Barcelona'),
      ]);
      final loaded = await repo.loadLocations();

      expect(loaded, hasLength(1));
      expect(loaded.first.municipioId, '08019');
    });

    test('saveLocations con lista vacía borra las localizaciones', () async {
      await repo.saveLocations([
        SavedLocation(municipioId: '28079', nombre: 'Madrid'),
      ]);
      await repo.saveLocations([]);
      final loaded = await repo.loadLocations();
      expect(loaded, isEmpty);
    });

    test('entradas corruptas en prefs son ignoradas al cargar', () async {
      SharedPreferences.setMockInitialValues({
        'saved_locations': ['sin_pipe_invalido', '28079|Madrid'],
      });
      final loaded = await repo.loadLocations();
      expect(loaded, hasLength(1));
      expect(loaded.first.municipioId, '28079');
    });
  });

  // ---------------------------------------------------------------------------
  // saveWeather / loadCachedWeather
  // ---------------------------------------------------------------------------

  group('WeatherStorageRepository — caché de tiempo', () {
    test('loadCachedWeather devuelve null sin datos previos', () async {
      final result = await repo.loadCachedWeather('28079');
      expect(result, isNull);
    });

    test('saveWeather + loadCachedWeather hace round-trip básico', () async {
      await repo.saveWeather(
        municipioId: '28079',
        rawJson: _forecastJson,
        alerts: [],
        updatedAt: DateTime(2099, 6, 1, 12),
      );
      final cached = await repo.loadCachedWeather('28079');

      expect(cached, isNotNull);
      expect(cached!.daily, isNotEmpty);
      expect(cached.hourly, isNotEmpty);
      expect(cached.alerts, isEmpty);
      expect(cached.sunTimes, isNull);
      expect(cached.lastUpdated, DateTime(2099, 6, 1, 12));
    });

    test('persiste y recupera alertas correctamente', () async {
      await repo.saveWeather(
        municipioId: '28079',
        rawJson: _forecastJson,
        alerts: [_alert(nivel: 'naranja'), _alert(nivel: 'rojo')],
        updatedAt: DateTime(2099, 6, 1),
      );
      final cached = await repo.loadCachedWeather('28079');

      expect(cached!.alerts, hasLength(2));
      expect(cached.alerts[0].nivel, 'naranja');
      expect(cached.alerts[1].nivel, 'rojo');
    });

    test('persiste y recupera SunTimes correctamente', () async {
      final sunrise = DateTime(2099, 6, 1, 6, 30);
      final sunset = DateTime(2099, 6, 1, 21, 45);

      await repo.saveWeather(
        municipioId: '28079',
        rawJson: _forecastJson,
        alerts: [],
        sunTimes: SunTimes(sunrise: sunrise, sunset: sunset),
        updatedAt: DateTime(2099, 6, 1),
      );
      final cached = await repo.loadCachedWeather('28079');

      expect(cached!.sunTimes, isNotNull);
      expect(cached.sunTimes!.sunrise.hour, sunrise.toLocal().hour);
      expect(cached.sunTimes!.sunset.hour, sunset.toLocal().hour);
    });

    test('datos de distintos municipios son independientes', () async {
      await repo.saveWeather(
        municipioId: '28079',
        rawJson: _forecastJson,
        alerts: [_alert(nivel: 'rojo')],
        updatedAt: DateTime(2099, 6, 1),
      );
      await repo.saveWeather(
        municipioId: '08019',
        rawJson: _forecastJson,
        alerts: [],
        updatedAt: DateTime(2099, 6, 2),
      );

      final madrid = await repo.loadCachedWeather('28079');
      final barcelona = await repo.loadCachedWeather('08019');

      expect(madrid!.alerts, hasLength(1));
      expect(barcelona!.alerts, isEmpty);
      expect(madrid.lastUpdated.day, 1);
      expect(barcelona.lastUpdated.day, 2);
    });

    test('JSON corrupto en prefs devuelve null y borra la entrada', () async {
      SharedPreferences.setMockInitialValues({
        'weather_data_28079': 'esto-no-es-json',
      });
      final cached = await repo.loadCachedWeather('28079');
      expect(cached, isNull);

      // La entrada corrupta debe haberse borrado
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('weather_data_28079'), isNull);
    });

    test('JSON sin clave "openMeteo" devuelve null', () async {
      SharedPreferences.setMockInitialValues({
        'weather_data_28079': jsonEncode({'lastUpdated': '2099-01-01T00:00:00.000'}),
      });
      final cached = await repo.loadCachedWeather('28079');
      expect(cached, isNull);
    });
  });

  // ---------------------------------------------------------------------------
  // removeWeather
  // ---------------------------------------------------------------------------

  group('WeatherStorageRepository — removeWeather', () {
    test('elimina los datos del municipio indicado', () async {
      await repo.saveWeather(
        municipioId: '28079',
        rawJson: _forecastJson,
        alerts: [],
        updatedAt: DateTime(2099, 6, 1),
      );
      expect(await repo.loadCachedWeather('28079'), isNotNull);

      await repo.removeWeather('28079');
      expect(await repo.loadCachedWeather('28079'), isNull);
    });

    test('no afecta a los datos de otros municipios', () async {
      await repo.saveWeather(
        municipioId: '28079',
        rawJson: _forecastJson,
        alerts: [],
        updatedAt: DateTime(2099, 6, 1),
      );
      await repo.saveWeather(
        municipioId: '08019',
        rawJson: _forecastJson,
        alerts: [],
        updatedAt: DateTime(2099, 6, 1),
      );

      await repo.removeWeather('28079');

      expect(await repo.loadCachedWeather('28079'), isNull);
      expect(await repo.loadCachedWeather('08019'), isNotNull);
    });

    test('removeWeather con id inexistente no lanza', () async {
      await expectLater(repo.removeWeather('99999'), completes);
    });
  });
}
