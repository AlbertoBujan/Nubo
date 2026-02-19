import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/daily_forecast.dart';
import '../models/hourly_forecast.dart';
import '../models/saved_location.dart';
import '../models/weather_alert.dart';
import '../services/alert_service.dart';
import '../services/api_service.dart';
import '../services/location_service.dart';
import '../services/municipio_search_service.dart';
import '../utils/sun_calculator.dart';
import 'dart:async';

/// Provider principal para la gestión del estado meteorológico.
///
/// Gestiona una lista de localizaciones guardadas, con caché de datos
/// por ciudad para evitar recargas al deslizar el PageView.
class WeatherProvider extends ChangeNotifier {
  final AemetApiService _apiService;
  final MunicipioSearchService _searchService;
  final LocationService _locationService;
  final AlertService _alertService;

  static const String _prefsKey = 'saved_locations';

  // --- Lista de localizaciones guardadas ---
  List<SavedLocation> _savedLocations = [];

  // --- Caché de datos por municipioId ---
  final Map<
      String,
      ({
        List<DailyForecast> daily,
        List<HourlyForecast> hourly,
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

  // --- Fondo dinámico ---
  SunPhase _currentPhase = SunPhase.night;
  Timer? _bgTimer;

  // --- Getters ---
  List<SavedLocation> get savedLocations => _savedLocations;
  int get currentIndex => _currentIndex;
  bool get isLocating => _isLocating;
  List<SavedLocation> get searchResults => _searchResults;
  bool get isSearching => _isSearching;

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

  /// Descripción del cielo actual.
  String get currentSkyDescription {
    final forecasts = hourlyForecasts;
    if (forecasts.isEmpty) return '';
    return _closestHourly(forecasts).skyDescription;
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

  WeatherProvider({
    AemetApiService? apiService,
    MunicipioSearchService? searchService,
    LocationService? locationService,
    AlertService? alertService,
  })  : _apiService = apiService ?? AemetApiService(),
        _searchService = searchService ?? MunicipioSearchService(),
        _locationService = locationService ?? LocationService(),
        _alertService = alertService ?? AlertService() {
    // Iniciar timer para actualizar el fondo cada minuto
    _bgTimer = Timer.periodic(const Duration(minutes: 1), (_) {
      _updateSunPhase();
    });
  }

  @override
  void dispose() {
    _bgTimer?.cancel();
    super.dispose();
  }

  // ---------------------------------------------------------------------------
  // Inicialización
  // ---------------------------------------------------------------------------

  /// Carga las localizaciones guardadas desde SharedPreferences y
  /// descarga el tiempo para la primera (si existe).
  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getStringList(_prefsKey) ?? [];

    _savedLocations = raw
        .map(SavedLocation.fromPrefsString)
        .whereType<SavedLocation>()
        .toList();

    // Si no hay ninguna guardada, añadir Madrid por defecto
    if (_savedLocations.isEmpty) {
      _savedLocations = [
        const SavedLocation(municipioId: '28079', nombre: 'Madrid'),
      ];
      await _persistLocations();
    }

    _currentIndex = 0;
    notifyListeners();

    // Cargar datos para la localización inicial
    await loadWeather(_savedLocations[0].municipioId);
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
      final results = await Future.wait([
        _apiService.fetchDailyForecast(municipioId),
        _apiService.fetchHourlyForecast(municipioId),
      ]);

      _cache[municipioId] = (
        daily: DailyForecast.fromAemetJson(results[0]),
        hourly: HourlyForecast.fromAemetJson(results[1]),
      );
      _errorMap[municipioId] = null;

      // Cargar alertas en paralelo (no bloquea la UI si falla)
      _loadAlerts(municipioId);

      // Actualizar fase solar
      await _updateSunPhase();
    } on AemetApiException catch (e) {
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
    final id = currentMunicipioId;
    if (id.isEmpty) return;
    _cache.remove(id);
    _alertsCache.remove(id);
    await loadWeather(id);
  }

  /// Carga alertas meteorológicas para un municipio (no bloquea la UI).
  Future<void> _loadAlerts(String municipioId) async {
    try {
      final alerts = await _alertService.fetchAlerts(municipioId);
      _alertsCache[municipioId] = alerts;
      notifyListeners();
    } catch (_) {
      // Si falla, simplemente no mostramos alertas
      _alertsCache[municipioId] = [];
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
      final position = await _locationService.getCurrentPosition();
      final nearest = await _searchService.findNearestMunicipio(
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

    // Cargar datos en background
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

    // Si quedó vacío, añadir Madrid por defecto
    if (_savedLocations.isEmpty) {
      _savedLocations = [
        const SavedLocation(municipioId: '28079', nombre: 'Madrid'),
      ];
    }

    // Ajustar índice si es necesario
    if (_currentIndex >= _savedLocations.length) {
      _currentIndex = _savedLocations.length - 1;
    }

    await _persistLocations();
    notifyListeners();

    // Cargar datos si la caché no existe para la nueva ciudad activa
    await loadWeather(currentMunicipioId);
  }

  /// Cambia la página activa (llamado desde el PageView onPageChanged).
  Future<void> switchToIndex(int index) async {
    if (index < 0 || index >= _savedLocations.length) return;
    _currentIndex = index;
    notifyListeners();

    // Cargar datos si aún no están en caché
    final id = _savedLocations[index].municipioId;
    await loadWeather(id);
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
      _searchResults = await _searchService.searchByName(query);
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
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(
      _prefsKey,
      _savedLocations.map((l) => l.toPrefsString()).toList(),
    );
  }

  // ---------------------------------------------------------------------------
  // Fondo Dinámico
  // ---------------------------------------------------------------------------

  /// Calcula la fase solar actual basada en la localización activa.
  Future<void> _updateSunPhase() async {
    final id = currentMunicipioId;
    if (id.isEmpty) return;

    // Obtener coordenadas desde el servicio de búsqueda (caché)
    final coords = await _searchService.getCoordinates(id);
    if (coords == null) return;

    final now = DateTime.now();
    final sunTimes = SunCalculator.calculateTimes(now, coords.lat, coords.lon);
    
    // Calcular offsets para transiciones (30 minutos)
    const transitionDuration = Duration(minutes: 30);
    
    final sunriseStart = sunTimes.sunrise.subtract(transitionDuration);
    final sunriseEnd = sunTimes.sunrise.add(transitionDuration);
    
    final sunsetStart = sunTimes.sunset.subtract(transitionDuration);
    final sunsetEnd = sunTimes.sunset.add(transitionDuration);

    SunPhase newPhase;

    if (now.isAfter(sunriseStart) && now.isBefore(sunriseEnd)) {
      newPhase = SunPhase.sunrise;
    } else if (now.isAfter(sunriseEnd) && now.isBefore(sunsetStart)) {
      newPhase = SunPhase.day;
    } else if (now.isAfter(sunsetStart) && now.isBefore(sunsetEnd)) {
      newPhase = SunPhase.sunset;
    } else {
      newPhase = SunPhase.night;
    }

    if (_currentPhase != newPhase) {
      _currentPhase = newPhase;
      notifyListeners();
    }
  }

  /// Gradiente de fondo según la fase solar actual.
  LinearGradient get backgroundGradient {
    switch (_currentPhase) {
      case SunPhase.sunrise:
        // Azul oscuro a naranjas/rosas suaves
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF141E30), // Azul noche
            Color(0xFF243B55), // Azul grisáceo
            Color(0xFFCC2B5E), // Rosa intenso
            Color(0xFF753A88), // Morado
          ],
          stops: [0.0, 0.4, 0.8, 1.0],
        );
      case SunPhase.day:
        // Azul celeste vibrante a azul claro
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF4CA1AF), 
            Color(0xFFC4E0E5), 
          ],
        );
      case SunPhase.sunset:
        // Naranja rojizo a morado intenso
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF3E1E68), // Morado oscuro
            Color(0xFFC6426E), // Rosa rojizo
            Color(0xFFF9A825), // Naranja/Amarillo
          ],
          stops: [0.0, 0.6, 1.0],
        );
      default:
        // Azul noche profundo a negro (Original)
        return LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            const Color(0xFF1A1A2E),
            const Color(0xFF16213E),
            const Color(0xFF0F3460),
            Colors.black.withValues(alpha: 0.9),
          ],
          stops: const [0.0, 0.3, 0.7, 1.0],
        );
    }
  }
}

enum SunPhase { night, sunrise, day, sunset }
