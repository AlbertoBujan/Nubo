import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator/geolocator.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:nubo/models/saved_location.dart';
import 'package:nubo/models/weather_alert.dart';
import 'package:nubo/providers/weather_provider.dart';
import 'package:nubo/repositories/alert_repository.dart';
import 'package:nubo/repositories/location_repository.dart';
import 'package:nubo/repositories/weather_repository.dart';
import 'package:nubo/services/api_service.dart';
import 'package:nubo/services/location_service.dart';

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

WeatherForecastResult _defaultForecast() => WeatherForecastResult(
      daily: [],
      hourly: [],
      rawJson: Map<String, dynamic>.from(_forecastJson),
    );

// ---------------------------------------------------------------------------
// Fakes de repositorios
// ---------------------------------------------------------------------------

class FakeWeatherRepository implements WeatherRepository {
  int callCount = 0;
  Exception? exception;
  WeatherForecastResult Function()? resultBuilder;

  FakeWeatherRepository({this.exception, this.resultBuilder});

  @override
  Future<WeatherForecastResult> getForecast(String municipioId) async {
    callCount++;
    if (exception != null) throw exception!;
    return resultBuilder != null ? resultBuilder!() : _defaultForecast();
  }
}

class FakeAlertRepository implements AlertRepository {
  List<WeatherAlert> alerts;
  int callCount = 0;

  FakeAlertRepository({List<WeatherAlert>? alerts}) : alerts = alerts ?? [];

  @override
  Future<List<WeatherAlert>> getAlerts(String municipioId) async {
    callCount++;
    return alerts;
  }
}

class FakeLocationRepository implements LocationRepository {
  final Map<String, ({double lat, double lon})?> coordinatesMap;
  SavedLocation? nearest;
  List<SavedLocation> searchResults;
  Position? position;
  LocationException? locationException;

  FakeLocationRepository({
    Map<String, ({double lat, double lon})?>? coordinatesMap,
    this.nearest,
    List<SavedLocation>? searchResults,
    this.position,
    this.locationException,
  })  : coordinatesMap = coordinatesMap ??
            {
              '28079': (lat: 40.4169, lon: -3.7033),
              '08019': (lat: 41.3825, lon: 2.1769),
            },
        searchResults = searchResults ?? [];

  @override
  Future<({double lat, double lon})?> getCoordinates(String municipioId) async =>
      coordinatesMap[municipioId];

  @override
  Future<SavedLocation?> findNearest(double lat, double lon) async => nearest;

  @override
  Future<List<SavedLocation>> searchByName(String query) async => searchResults;

  @override
  Future<Position> getCurrentPosition() async {
    if (locationException != null) throw locationException!;
    return position ?? _fakePosition();
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

Position _fakePosition() => Position(
      latitude: 40.4169,
      longitude: -3.7033,
      timestamp: DateTime(2099, 6, 1, 12),
      accuracy: 10.0,
      altitude: 650.0,
      altitudeAccuracy: 10.0,
      heading: 0.0,
      headingAccuracy: 0.0,
      speed: 0.0,
      speedAccuracy: 0.0,
    );

SavedLocation _madrid() => SavedLocation(municipioId: '28079', nombre: 'Madrid');
SavedLocation _barcelona() => SavedLocation(municipioId: '08019', nombre: 'Barcelona');

WeatherProvider _buildProvider({
  FakeWeatherRepository? weather,
  FakeAlertRepository? alerts,
  FakeLocationRepository? location,
}) =>
    WeatherProvider(
      weatherRepository: weather ?? FakeWeatherRepository(),
      alertRepository: alerts ?? FakeAlertRepository(),
      locationRepository: location ?? FakeLocationRepository(),
    );

Future<void> _pump() => Future.delayed(Duration.zero);

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('WeatherProvider — estado inicial', () {
    test('no tiene localizaciones al arrancar', () {
      final p = _buildProvider();
      expect(p.savedLocations, isEmpty);
      p.dispose();
    });

    test('currentLocation es null sin localizaciones', () {
      final p = _buildProvider();
      expect(p.currentLocation, isNull);
      p.dispose();
    });

    test('cityName devuelve "Sin localización"', () {
      final p = _buildProvider();
      expect(p.cityName, 'Sin localización');
      p.dispose();
    });

    test('currentMunicipioId es cadena vacía', () {
      final p = _buildProvider();
      expect(p.currentMunicipioId, '');
      p.dispose();
    });

    test('getters *For(id) devuelven vacío y sin error', () {
      final p = _buildProvider();
      expect(p.dailyForecastsFor('28079'), isEmpty);
      expect(p.hourlyForecastsFor('28079'), isEmpty);
      expect(p.alertsFor('28079'), isEmpty);
      expect(p.isLoadingFor('28079'), isFalse);
      expect(p.errorMessageFor('28079'), isNull);
      p.dispose();
    });

    test('currentIndex empieza en 0', () {
      final p = _buildProvider();
      expect(p.currentIndex, 0);
      p.dispose();
    });
  });

  group('WeatherProvider.init()', () {
    test('con prefs vacíos: savedLocations queda vacío', () async {
      final p = _buildProvider();
      await p.init();
      expect(p.savedLocations, isEmpty);
      p.dispose();
    });

    test('carga localizaciones persistidas en SharedPreferences', () async {
      SharedPreferences.setMockInitialValues({
        'saved_locations': [_madrid().toPrefsString()],
      });
      final p = _buildProvider();
      await p.init();
      expect(p.savedLocations, hasLength(1));
      expect(p.savedLocations.first.municipioId, '28079');
      p.dispose();
    });

    test('entradas corruptas en prefs son ignoradas', () async {
      SharedPreferences.setMockInitialValues({
        'saved_locations': ['formato_invalido_sin_pipe'],
      });
      final p = _buildProvider();
      await p.init();
      expect(p.savedLocations, isEmpty);
      p.dispose();
    });
  });

  group('WeatherProvider.loadWeather()', () {
    test('carga datos y los guarda en caché', () async {
      final weather = FakeWeatherRepository();
      final p = _buildProvider(weather: weather);

      await p.loadWeather('28079');

      expect(weather.callCount, 1);
      p.dispose();
    });

    test('segunda llamada con caché existente no recarga (cache-hit)', () async {
      final weather = FakeWeatherRepository();
      final p = _buildProvider(weather: weather);

      await p.loadWeather('28079');
      await p.loadWeather('28079');

      expect(weather.callCount, 1);
      p.dispose();
    });

    test('isLoadingFor es false tras completar la carga', () async {
      final p = _buildProvider();
      await p.loadWeather('28079');
      expect(p.isLoadingFor('28079'), isFalse);
      p.dispose();
    });

    test('error de API → errorMessage no vacío', () async {
      final weather = FakeWeatherRepository(
        exception: OpenMeteoApiException('timeout', 500),
      );
      final p = _buildProvider(weather: weather);

      await p.loadWeather('28079');

      expect(p.errorMessageFor('28079'), isNotNull);
      expect(p.errorMessageFor('28079'), isNotEmpty);
      p.dispose();
    });

    test('sin coordenadas → errorMessage no vacío', () async {
      final weather = FakeWeatherRepository(
        exception: Exception('No se encontraron coordenadas'),
      );
      final p = _buildProvider(weather: weather);

      await p.loadWeather('28079');

      expect(p.errorMessageFor('28079'), isNotNull);
      p.dispose();
    });

    test('carga exitosa limpia errorMessage previo', () async {
      final weather = FakeWeatherRepository(
        exception: OpenMeteoApiException('err', 500),
      );
      final p = _buildProvider(weather: weather);
      await p.loadWeather('28079');

      weather.exception = null;
      await p.refreshWeather('28079');

      expect(p.errorMessageFor('28079'), isNull);
      p.dispose();
    });

    test('carga alertas tras obtener el forecast', () async {
      final alerts = FakeAlertRepository(alerts: [
        WeatherAlert(
          nivel: 'amarillo',
          event: 'Viento',
          headline: 'Aviso',
          description: '',
          instruction: '',
          areaDescription: 'Madrid',
          onset: null,
          expires: DateTime(2099),
          probability: '40%',
        ),
      ]);
      final p = _buildProvider(alerts: alerts);

      await p.loadWeather('28079');

      expect(p.alertsFor('28079'), hasLength(1));
      p.dispose();
    });
  });

  group('WeatherProvider.refreshWeather()', () {
    test('borra la caché y vuelve a llamar al repositorio', () async {
      final weather = FakeWeatherRepository();
      final p = _buildProvider(weather: weather);

      await p.loadWeather('28079');
      expect(weather.callCount, 1);

      await p.refreshWeather('28079');
      expect(weather.callCount, 2);
      p.dispose();
    });

    test('id vacío → no hace nada', () async {
      final weather = FakeWeatherRepository();
      final p = _buildProvider(weather: weather);
      await p.refreshWeather('');
      expect(weather.callCount, 0);
      p.dispose();
    });
  });

  group('WeatherProvider.addLocation()', () {
    test('añade nueva localización a la lista', () async {
      final p = _buildProvider();
      await p.addLocation(_madrid());
      expect(p.savedLocations, hasLength(1));
      expect(p.savedLocations.first.municipioId, '28079');
      p.dispose();
    });

    test('no duplica si el municipioId ya existe', () async {
      final p = _buildProvider();
      await p.addLocation(_madrid());
      await p.addLocation(_madrid());
      expect(p.savedLocations, hasLength(1));
      p.dispose();
    });

    test('municipioId existente con switchTo=true cambia currentIndex', () async {
      final p = _buildProvider();
      await p.addLocation(_madrid());
      await p.addLocation(_barcelona());
      expect(p.currentIndex, 1);

      await p.addLocation(_madrid(), switchTo: true);
      expect(p.currentIndex, 0);
      p.dispose();
    });

    test('municipioId existente con switchTo=false no cambia index', () async {
      final p = _buildProvider();
      await p.addLocation(_madrid());
      await p.addLocation(_barcelona());
      expect(p.currentIndex, 1);

      await p.addLocation(_madrid(), switchTo: false);
      expect(p.currentIndex, 1);
      p.dispose();
    });

    test('después de añadir, llama al repositorio de previsión', () async {
      final weather = FakeWeatherRepository();
      final p = _buildProvider(weather: weather);
      await p.addLocation(_madrid());
      expect(weather.callCount, greaterThanOrEqualTo(1));
      p.dispose();
    });
  });

  group('WeatherProvider.removeLocation()', () {
    test('elimina la localización por índice', () async {
      final p = _buildProvider();
      await p.addLocation(_madrid());
      await p.removeLocation(0);
      expect(p.savedLocations, isEmpty);
      p.dispose();
    });

    test('limpia la caché de la localización eliminada', () async {
      final p = _buildProvider();
      await p.addLocation(_madrid());
      await p.removeLocation(0);
      expect(p.dailyForecastsFor('28079'), isEmpty);
      p.dispose();
    });

    test('ajusta currentIndex cuando se elimina la última ciudad', () async {
      final p = _buildProvider();
      await p.addLocation(_madrid());
      await p.addLocation(_barcelona());
      await p.switchToIndex(1);
      await p.removeLocation(1);
      expect(p.currentIndex, lessThan(p.savedLocations.length + 1));
      p.dispose();
    });

    test('índice fuera de rango → no hace nada', () async {
      final p = _buildProvider();
      await p.addLocation(_madrid());
      await p.removeLocation(99);
      expect(p.savedLocations, hasLength(1));
      p.dispose();
    });

    test('índice negativo → no hace nada', () async {
      final p = _buildProvider();
      await p.addLocation(_madrid());
      await p.removeLocation(-1);
      expect(p.savedLocations, hasLength(1));
      p.dispose();
    });
  });

  group('WeatherProvider.switchToIndex()', () {
    test('cambia currentIndex correctamente', () async {
      final p = _buildProvider();
      await p.addLocation(_madrid());
      await p.addLocation(_barcelona());

      await p.switchToIndex(1);
      expect(p.currentIndex, 1);

      await p.switchToIndex(0);
      expect(p.currentIndex, 0);
      p.dispose();
    });

    test('índice fuera de rango → no cambia', () async {
      final p = _buildProvider();
      await p.addLocation(_madrid());
      await p.switchToIndex(5);
      expect(p.currentIndex, 0);
      p.dispose();
    });
  });

  group('WeatherProvider.searchMunicipios()', () {
    test('query vacía → limpia searchResults y isSearching=false', () async {
      final p = _buildProvider();
      await p.searchMunicipios('');
      expect(p.searchResults, isEmpty);
      expect(p.isSearching, isFalse);
      p.dispose();
    });

    test('query con espacios → limpia resultados', () async {
      final p = _buildProvider();
      await p.searchMunicipios('   ');
      expect(p.searchResults, isEmpty);
      p.dispose();
    });

    test('devuelve los resultados del repositorio de localización', () async {
      final location = FakeLocationRepository(
        searchResults: [_madrid(), _barcelona()],
      );
      final p = _buildProvider(location: location);

      await p.searchMunicipios('mad');

      expect(p.searchResults, hasLength(2));
      expect(p.isSearching, isFalse);
      p.dispose();
    });

    test('isSearching es false al terminar', () async {
      final p = _buildProvider();
      await p.searchMunicipios('test');
      expect(p.isSearching, isFalse);
      p.dispose();
    });
  });

  group('WeatherProvider.clearSearch()', () {
    test('limpia searchResults', () async {
      final location = FakeLocationRepository(searchResults: [_madrid()]);
      final p = _buildProvider(location: location);
      await p.searchMunicipios('mad');
      expect(p.searchResults, isNotEmpty);

      p.clearSearch();
      expect(p.searchResults, isEmpty);
      p.dispose();
    });

    test('pone isSearching a false', () {
      final p = _buildProvider();
      p.clearSearch();
      expect(p.isSearching, isFalse);
      p.dispose();
    });
  });

  group('WeatherProvider.refreshAllWeather()', () {
    test('lista vacía → no hace peticiones', () async {
      final weather = FakeWeatherRepository();
      final p = _buildProvider(weather: weather);
      await p.refreshAllWeather();
      expect(weather.callCount, 0);
      p.dispose();
    });

    test('refresca todas las ciudades', () async {
      final weather = FakeWeatherRepository();
      final p = _buildProvider(weather: weather);
      await p.addLocation(_madrid());
      await p.addLocation(_barcelona());
      final countAfterAdd = weather.callCount;

      await p.refreshAllWeather();
      expect(weather.callCount, greaterThan(countAfterAdd));
      p.dispose();
    });

    test('isRefreshing es false al terminar', () async {
      final p = _buildProvider();
      await p.addLocation(_madrid());
      await p.refreshAllWeather();
      expect(p.isRefreshing, isFalse);
      p.dispose();
    });
  });

  group('WeatherProvider.loadWeatherByGps()', () {
    test('municipio encontrado → se añade a la lista', () async {
      final location = FakeLocationRepository(
        nearest: _madrid(),
        position: _fakePosition(),
      );
      final p = _buildProvider(location: location);

      await p.loadWeatherByGps();

      expect(p.savedLocations.any((l) => l.municipioId == '28079'), isTrue);
      p.dispose();
    });

    test('GPS denegado → no lanza, isLocating=false', () async {
      final location = FakeLocationRepository(
        locationException: const LocationException('Permiso denegado'),
      );
      final p = _buildProvider(location: location);
      await p.addLocation(_madrid());

      await p.loadWeatherByGps();

      expect(p.isLocating, isFalse);
      p.dispose();
    });

    test('isLocating es false al terminar', () async {
      final location = FakeLocationRepository(
        locationException: const LocationException('GPS off'),
      );
      final p = _buildProvider(location: location);
      await p.loadWeatherByGps();
      expect(p.isLocating, isFalse);
      p.dispose();
    });
  });

  group('WeatherProvider — getters de ciudad activa', () {
    test('lastRefreshText vacío sin datos', () {
      final p = _buildProvider();
      expect(p.lastRefreshText, '');
      p.dispose();
    });

    test('lastRefreshText no vacío tras cargar datos', () async {
      final p = _buildProvider();
      await p.addLocation(_madrid());
      await _pump();
      expect(p.lastRefreshText, isNotEmpty);
      p.dispose();
    });

    test('todayTempRange devuelve (null, null) sin datos', () {
      final p = _buildProvider();
      expect(p.todayTempRange, (null, null));
      p.dispose();
    });

    test('cityNameFor devuelve "Desconocido" para id desconocido', () {
      final p = _buildProvider();
      expect(p.cityNameFor('99999'), 'Desconocido');
      p.dispose();
    });
  });
}
