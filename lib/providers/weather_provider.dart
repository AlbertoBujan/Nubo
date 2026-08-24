import 'dart:async';
import 'package:flutter/material.dart';
import '../models/daily_forecast.dart';
import '../models/hourly_forecast.dart';
import '../models/saved_location.dart';
import '../models/weather_alert.dart';
import '../utils/sun_calculator.dart';
import '../utils/moon_calculator.dart';
import '../utils/sky_gradients.dart';
import '../models/weather_enums.dart';
import '../services/api_service.dart' show OpenMeteoApiException;
import '../services/location_service.dart' show LocationException;
import '../repositories/weather_storage_repository.dart';
import '../repositories/weather_repository.dart';
import '../repositories/alert_repository.dart';
import '../repositories/location_repository.dart';

/// Provider principal para la gestión del estado meteorológico.
///
/// Gestiona una lista de localizaciones guardadas, con caché de datos
/// por ciudad para evitar recargas al deslizar el PageView.
class WeatherProvider extends ChangeNotifier {
  late final WeatherRepository _weatherRepo;
  late final AlertRepository _alertRepo;
  late final LocationRepository _locationRepo;
  late final WeatherStorageRepository _storage;

  // --- Lista de localizaciones guardadas ---
  List<SavedLocation> _savedLocations = [];

  // --- Caché de datos por municipioId ---
  final Map<
      String,
      ({
        List<DailyForecast> daily,
        List<HourlyForecast> hourly,
        DateTime lastUpdated,
      })> _cache = {};

  // --- Estado de carga por página ---
  final Map<String, bool> _loadingMap = {};
  final Map<String, String?> _errorMap = {};

  // --- Caché de alertas por municipioId ---
  final Map<String, List<WeatherAlert>> _alertsCache = {};

  // --- Índice de la página activa en el PageView ---
  int _currentIndex = 0;

  // --- Estado de geolocalización ---
  bool _isLocating = false;

  // --- Resultados de búsqueda ---
  List<SavedLocation> _searchResults = [];
  bool _isSearching = false;

  // --- Fondo dinámico e iluminación ---
  SunPhase _currentPhase = SunPhase.night;
  final Map<String, SunTimes> _sunTimesCache = {};
  final Map<String, MoonData> _moonDataCache = {};

  /// Gradientes ya resueltos por municipio. Ver [gradientForMunicipio].
  final Map<String, LinearGradient> _gradientCache = {};
  Timer? _bgTimer;

  // --- Estado de refresco global (pull-to-refresh) ---
  bool _isRefreshing = false;

  // --- Getters ---
  List<SavedLocation> get savedLocations => _savedLocations;
  SunPhase get currentPhase => _currentPhase;
  SunTimes? get currentSunTimes => sunTimesFor(currentMunicipioId);
  MoonData? get currentMoonData => moonDataFor(currentMunicipioId);

  /// Horas de sol de un municipio concreto.
  ///
  /// Las páginas del PageView deben usar esta versión y no [currentSunTimes]:
  /// el getter global depende del índice activo, así que al cambiar de página
  /// mutaría a la vez para todas y forzaría a reconstruirlas todas.
  SunTimes? sunTimesFor(String id) => _sunTimesCache[id];

  /// Datos lunares de un municipio concreto. Ver nota en [sunTimesFor].
  MoonData? moonDataFor(String id) => _moonDataCache[id];
  int get currentIndex => _currentIndex;
  bool get isLocating => _isLocating;
  List<SavedLocation> get searchResults => _searchResults;
  bool get isSearching => _isSearching;
  bool get isRefreshing => _isRefreshing;

  /// Localización activa actualmente.
  SavedLocation? get currentLocation =>
      _savedLocations.isEmpty ? null : _savedLocations[_currentIndex];

  /// Nombre de la ciudad activa.
  String get cityName => currentLocation?.nombre ?? 'Sin localización';

  /// ID del municipio activo.
  String get currentMunicipioId => currentLocation?.municipioId ?? '';

  // --- Getters de datos de la ciudad activa ---

  bool get isLoading =>
      currentLocation != null && (_loadingMap[currentMunicipioId] ?? false);

  String? get errorMessage => _errorMap[currentMunicipioId];

  List<DailyForecast> get dailyForecasts =>
      _cache[currentMunicipioId]?.daily ?? [];

  List<HourlyForecast> get hourlyForecasts =>
      _cache[currentMunicipioId]?.hourly ?? [];

  /// Alertas activas para la localización actual.
  List<WeatherAlert> get alerts =>
      _alertsCache[currentMunicipioId] ?? [];

  /// Temperatura actual (la más próxima a la hora actual).
  int? get currentTemperature {
    final forecasts = hourlyForecasts;
    if (forecasts.isEmpty) return null;
    return _closestHourly(forecasts).temperature;
  }

  /// Código de estado del cielo actual.
  String get currentSkyCode {
    final forecasts = hourlyForecasts;
    if (forecasts.isEmpty) return '';
    return _closestHourly(forecasts).skyStateCode;
  }

  /// Condición del cielo actual clasificada en 3 categorías.
  SkyCondition get currentSkyCondition =>
      SkyCondition.fromCode(currentSkyCode);

  /// Descripción del cielo actual.
  String get currentSkyDescription {
    final forecasts = hourlyForecasts;
    if (forecasts.isEmpty) return '';
    return _closestHourly(forecasts).skyDescription;
  }

  /// Fecha de la última actualización para la ciudad activa
  DateTime? get lastUpdated => _cache[currentMunicipioId]?.lastUpdated;

  /// Texto amigable de última actualización (ej: "Hace 5 min")
  String get lastRefreshText {
    final updated = lastUpdated;
    if (updated == null) return '';
    
    final diff = DateTime.now().difference(updated);
    if (diff.inDays >= 1) return 'Hace ${diff.inDays} d';
    if (diff.inHours >= 1) return 'Hace ${diff.inHours} h';
    if (diff.inMinutes >= 1) return 'Hace ${diff.inMinutes} min';
    return 'Actualizado'; // Menos de 1 minuto
  }

  /// Temperaturas máxima y mínima del día actual.
  (int?, int?) get todayTempRange {
    final forecasts = dailyForecasts;
    if (forecasts.isEmpty) return (null, null);
    final today = DateTime.now();
    for (final f in forecasts) {
      if (f.date.year == today.year &&
          f.date.month == today.month &&
          f.date.day == today.day) {
        return (f.tempMax, f.tempMin);
      }
    }
    return (forecasts.first.tempMax, forecasts.first.tempMin);
  }

  // --- Getters por municipioId (para animaciones suaves) ---
  bool isLoadingFor(String id) => _loadingMap[id] ?? false;
  String? errorMessageFor(String id) => _errorMap[id];
  List<DailyForecast> dailyForecastsFor(String id) => _cache[id]?.daily ?? [];
  List<HourlyForecast> hourlyForecastsFor(String id) => _cache[id]?.hourly ?? [];
  List<WeatherAlert> alertsFor(String id) => _alertsCache[id] ?? [];

  int? currentTemperatureFor(String id) {
    if (hourlyForecastsFor(id).isEmpty) return null;
    return _closestHourly(hourlyForecastsFor(id)).temperature;
  }

  String currentSkyCodeFor(String id) {
    if (hourlyForecastsFor(id).isEmpty) return '';
    return _closestHourly(hourlyForecastsFor(id)).skyStateCode;
  }

  String currentSkyDescriptionFor(String id) {
    if (hourlyForecastsFor(id).isEmpty) return '';
    return _closestHourly(hourlyForecastsFor(id)).skyDescription;
  }

  (int?, int?) todayTempRangeFor(String id) {
    final forecasts = dailyForecastsFor(id);
    if (forecasts.isEmpty) return (null, null);
    final today = DateTime.now();
    for (final f in forecasts) {
      if (f.date.year == today.year && f.date.month == today.month && f.date.day == today.day) {
        return (f.tempMax, f.tempMin);
      }
    }
    return (forecasts.first.tempMax, forecasts.first.tempMin);
  }

  String cityNameFor(String id) {
    return _savedLocations.firstWhere(
      (l) => l.municipioId == id, 
      orElse: () => SavedLocation(municipioId: id, nombre: 'Desconocido')
    ).nombre;
  }

  WeatherProvider({
    WeatherRepository? weatherRepository,
    AlertRepository? alertRepository,
    LocationRepository? locationRepository,
    WeatherStorageRepository? storage,
  }) {
    final locRepo = locationRepository ?? LocationRepositoryImpl();
    _locationRepo = locRepo;
    _alertRepo = alertRepository ?? AlertRepositoryImpl();
    _weatherRepo = weatherRepository ?? WeatherRepositoryImpl(locationRepository: locRepo);
    _storage = storage ?? WeatherStorageRepositoryImpl();

    _bgTimer = Timer.periodic(const Duration(minutes: 1), (_) => _updateSunPhase());
    _updateSunPhase();
  }

  @override
  void dispose() {
    _bgTimer?.cancel();
    super.dispose();
  }

  // ---------------------------------------------------------------------------
  // Inicialización
  // ---------------------------------------------------------------------------

  /// Carga las localizaciones guardadas y rehidrata la caché persistida.
  Future<void> init() async {
    _savedLocations = await _storage.loadLocations();
    _currentIndex = 0;

    for (final loc in _savedLocations) {
      final cached = await _storage.loadCachedWeather(loc.municipioId);
      if (cached != null) {
        _cache[loc.municipioId] = (
          daily: cached.daily,
          hourly: cached.hourly,
          lastUpdated: cached.lastUpdated,
        );
        _alertsCache[loc.municipioId] = cached.alerts;
        if (cached.sunTimes != null) {
          _sunTimesCache[loc.municipioId] = cached.sunTimes!;
        }
      }
    }

    notifyListeners();
    await _updateSunPhase();
  }

  // ---------------------------------------------------------------------------
  // Carga de datos meteorológicos
  // ---------------------------------------------------------------------------

  /// Carga los datos meteorológicos para [municipioId].
  Future<void> loadWeather(String municipioId) async {
    // Si ya hay datos en caché, no recargar
    if (_cache.containsKey(municipioId)) {
      notifyListeners();
      return;
    }

    _loadingMap[municipioId] = true;
    _errorMap[municipioId] = null;
    notifyListeners();

    try {
      final forecast = await _weatherRepo.getForecast(municipioId);
      final updatedTime = DateTime.now();

      _cache[municipioId] = (
        daily: forecast.daily,
        hourly: forecast.hourly,
        lastUpdated: updatedTime,
      );
      _errorMap[municipioId] = null;

      await _loadAlerts(municipioId);

      await _storage.saveWeather(
        municipioId: municipioId,
        rawJson: forecast.rawJson,
        alerts: _alertsCache[municipioId] ?? [],
        sunTimes: _sunTimesCache[municipioId],
        updatedAt: updatedTime,
      );

      await _updateSunPhase();
    } on OpenMeteoApiException catch (e) {
      _errorMap[municipioId] = e.message;
    } catch (e) {
      _errorMap[municipioId] = 'Error de conexión: $e';
    } finally {
      _loadingMap[municipioId] = false;
      notifyListeners();
    }
  }

  /// Fuerza la recarga de datos para el municipio activo (pull-to-refresh).
  Future<void> refreshCurrentWeather() async {
    await refreshWeather(currentMunicipioId);
  }

  Future<void> refreshWeather(String id) async {
    if (id.isEmpty) return;
    _cache.remove(id);
    _alertsCache.remove(id);
    await loadWeather(id);
  }

  Future<void> _loadAlerts(String municipioId) async {
    try {
      _alertsCache[municipioId] = await _alertRepo.getAlerts(municipioId);
      notifyListeners();
    } catch (_) {
      _alertsCache[municipioId] = [];
    }
  }

  /// Refresca los datos de TODAS las ciudades guardadas.
  /// No borra cache para que los datos sigan visibles durante la recarga.
  Future<void> refreshAllWeather() async {
    if (_savedLocations.isEmpty) return;

    _isRefreshing = true;
    notifyListeners();

    try {
      await Future.wait(
        _savedLocations.map((loc) => _silentLoadWeather(loc.municipioId)),
      );
    } finally {
      _isRefreshing = false;
      notifyListeners();
    }
  }

  Future<void> _silentLoadWeather(String municipioId) async {
    try {
      final forecast = await _weatherRepo.getForecast(municipioId);
      final updatedTime = DateTime.now();

      _cache[municipioId] = (
        daily: forecast.daily,
        hourly: forecast.hourly,
        lastUpdated: updatedTime,
      );
      _errorMap[municipioId] = null;
      _alertsCache.remove(municipioId);
      await _loadAlerts(municipioId);

      await _storage.saveWeather(
        municipioId: municipioId,
        rawJson: forecast.rawJson,
        alerts: _alertsCache[municipioId] ?? [],
        sunTimes: _sunTimesCache[municipioId],
        updatedAt: updatedTime,
      );
      await _updateSunPhase();
    } catch (_) {
      // En caso de error silencioso, mantenemos los datos anteriores
    }
  }

  // ---------------------------------------------------------------------------
  // Geolocalización
  // ---------------------------------------------------------------------------

  /// Obtiene la posición GPS, busca el municipio más cercano y lo añade/activa.
  Future<void> loadWeatherByGps() async {
    _isLocating = true;
    notifyListeners();

    try {
      final position = await _locationRepo.getCurrentPosition();
      final nearest = await _locationRepo.findNearest(
        position.latitude,
        position.longitude,
      );

      if (nearest != null) {
        await addLocation(nearest, switchTo: true);
      }
    } on LocationException catch (e) {
      // Propagar el error al municipio activo para mostrarlo en la UI
      final id = currentMunicipioId;
      if (id.isNotEmpty) {
        _errorMap[id] = e.message;
      }
    } catch (e) {
      final id = currentMunicipioId;
      if (id.isNotEmpty) {
        _errorMap[id] = 'Error de geolocalización: $e';
      }
    } finally {
      _isLocating = false;
      notifyListeners();
    }
  }

  // ---------------------------------------------------------------------------
  // Gestión de localizaciones guardadas
  // ---------------------------------------------------------------------------

  /// Añade una localización. Si ya existe, opcionalmente cambia a ella.
  Future<void> addLocation(SavedLocation loc, {bool switchTo = true}) async {
    final existingIdx =
        _savedLocations.indexWhere((l) => l.municipioId == loc.municipioId);

    if (existingIdx >= 0) {
      // Ya existe: solo cambiar a ella si se pide
      if (switchTo) {
        _currentIndex = existingIdx;
        notifyListeners();
        await loadWeather(loc.municipioId);
      }
      return;
    }

    _savedLocations.add(loc);
    await _persistLocations();

    if (switchTo) {
      _currentIndex = _savedLocations.length - 1;
    }
    notifyListeners();

    // Cargar datos por primera vez explícitamente porque es ubicación nueva
    await loadWeather(loc.municipioId);
  }

  /// Elimina una localización por índice.
  Future<void> removeLocation(int index) async {
    if (index < 0 || index >= _savedLocations.length) return;
    final id = _savedLocations[index].municipioId;

    _savedLocations.removeAt(index);
    _cache.remove(id);
    _errorMap.remove(id);
    _loadingMap.remove(id);
    _sunTimesCache.remove(id);
    _moonDataCache.remove(id);
    _alertsCache.remove(id);
    _gradientCache.remove(id);

    await _storage.removeWeather(id);

    // Si quedó vacío, no añadimos Madrid por defecto.

    // Ajustar índice si es necesario
    if (_currentIndex >= _savedLocations.length) {
      _currentIndex = _savedLocations.length - 1;
    }

    await _persistLocations();
    notifyListeners();

    // Si pasamos a otra no forzamos la carga. Simplemente actualiza la UI
    // con lo que sea que tenga en su caché (o pide que la cargue el form vacío).
    await _updateSunPhase();
  }

  /// Cambia la página activa (llamado desde el PageView onPageChanged).
  Future<void> switchToIndex(int index) async {
    if (index < 0 || index >= _savedLocations.length) return;
    _currentIndex = index;
    notifyListeners();

    // Diferir trabajo pesado para no provocar rebuilds durante la animación del swipe.
    // _loadAlerts y _updateSunPhase disparan notifyListeners() adicionales.
    await Future.delayed(const Duration(milliseconds: 400));

    // Verificar que el índice no haya cambiado durante la espera (swipes rápidos)
    if (_currentIndex != index) return;

    final id = _savedLocations[index].municipioId;
    if (!_alertsCache.containsKey(id)) {
        _loadAlerts(id);
    }
    await _updateSunPhase();
  }

  // ---------------------------------------------------------------------------
  // Búsqueda
  // ---------------------------------------------------------------------------

  /// Busca municipios por nombre para el autocompletado.
  Future<void> searchMunicipios(String query) async {
    if (query.trim().isEmpty) {
      _searchResults = [];
      _isSearching = false;
      notifyListeners();
      return;
    }

    _isSearching = true;
    notifyListeners();

    try {
      _searchResults = await _locationRepo.searchByName(query);
    } catch (_) {
      _searchResults = [];
    } finally {
      _isSearching = false;
      notifyListeners();
    }
  }

  /// Limpia los resultados de búsqueda.
  void clearSearch() {
    _searchResults = [];
    _isSearching = false;
    notifyListeners();
  }

  // ---------------------------------------------------------------------------
  // Helpers privados
  // ---------------------------------------------------------------------------

  HourlyForecast _closestHourly(List<HourlyForecast> forecasts) {
    final now = DateTime.now();
    HourlyForecast closest = forecasts.first;
    Duration minDiff = closest.dateTime.difference(now).abs();

    for (final f in forecasts) {
      final diff = f.dateTime.difference(now).abs();
      if (diff < minDiff) {
        minDiff = diff;
        closest = f;
      }
    }
    return closest;
  }

  Future<void> _persistLocations() async {
    await _storage.saveLocations(_savedLocations);
  }

  // ---------------------------------------------------------------------------
  // Fondo Dinámico
  // ---------------------------------------------------------------------------

  /// Calcula la fase solar actual basada en la localización activa.
  /// Si no hay localización activa, usa coordenadas por defecto (centro de España).
  Future<void> _updateSunPhase() async {
    double lat = 40.4168; // Madrid por defecto
    double lon = -3.7038;

    final id = currentMunicipioId;
    if (id.isNotEmpty) {
      final coords = await _locationRepo.getCoordinates(id);
      if (coords != null) {
        lat = coords.lat;
        lon = coords.lon;
      }
    }

    final now = DateTime.now().millisecondsSinceEpoch;
    final sunTimes = SunCalculator.calculateTimes(DateTime.now(), lat, lon);
    final moonData = MoonCalculator.calculate(DateTime.now(), lat, lon);
    
    // Almacenar en la caché local para acceso síncrono
    if (id.isNotEmpty) {
      _sunTimesCache[id] = sunTimes;
      _moonDataCache[id] = moonData;
    }
    
    // Calcular offsets para transiciones (30 minutos)
    const transitionMs = 30 * 60 * 1000;
    
    final sunriseTime = sunTimes.sunrise.millisecondsSinceEpoch;
    final sunriseStart = sunriseTime - transitionMs;
    final sunriseEnd = sunriseTime + transitionMs;
    
    final sunsetTime = sunTimes.sunset.millisecondsSinceEpoch;
    final sunsetStart = sunsetTime - transitionMs;
    final sunsetEnd = sunsetTime + transitionMs;

    SunPhase newPhase;

    if (now >= sunriseStart && now < sunriseEnd) {
      newPhase = SunPhase.sunrise;
    } else if (now >= sunriseEnd && now < sunsetStart) {
      newPhase = SunPhase.day;
    } else if (now >= sunsetStart && now < sunsetEnd) {
      newPhase = SunPhase.sunset;
    } else {
      newPhase = SunPhase.night;
    }

    if (_currentPhase != newPhase) {
      _currentPhase = newPhase;
    }

    // Se invalida siempre, no solo al cambiar de fase: el código de cielo
    // depende de la hora más cercana, que también se mueve con el tiempo.
    // Como este método corre cada minuto, la caché nunca queda obsoleta.
    _gradientCache.clear();


    // Siempre notificamos, ya que las vistas pueden estar bloqueadas a la espera 
    // de que _sunTimesCache esté rellenado.
    notifyListeners();
  }

  /// Gradiente de fondo según la fase solar y cielo del municipio activo.
  LinearGradient get backgroundGradient => gradientForMunicipio(currentMunicipioId);

  /// Gradiente de fondo para un municipio concreto (usado para interpolar en swipe).
  ///
  /// El resultado se memoriza porque durante el swipe se pide dos veces por
  /// fotograma, y resolver el código de cielo implica recorrer todas las horas
  /// de la predicción buscando la más cercana. Sus entradas (fase solar y hora
  /// actual) solo cambian cuando corre [_updateSunPhase], que es quien invalida
  /// la caché, así que memorizar no introduce desfase perceptible.
  LinearGradient gradientForMunicipio(String id) {
    final cached = _gradientCache[id];
    if (cached != null) return cached;

    final skyCode = id.isNotEmpty ? currentSkyCodeFor(id) : '';
    final sky = SkyCondition.fromCode(skyCode);
    final gradient = SkyGradients.forPhase(_currentPhase, sky);

    _gradientCache[id] = gradient;
    return gradient;
  }

  /// Interpola linealmente entre dos gradientes (ambos deben tener 4 colores).
  static LinearGradient lerpGradient(LinearGradient a, LinearGradient b, double t) =>
      SkyGradients.lerp(a, b, t);
}


