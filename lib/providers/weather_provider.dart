import 'dart:convert';
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
import '../models/weather_enums.dart';
import 'dart:async';

/// Provider principal para la gestión del estado meteorológico.
///
/// Gestiona una lista de localizaciones guardadas, con caché de datos
/// por ciudad para evitar recargas al deslizar el PageView.
class WeatherProvider extends ChangeNotifier {
  final OpenMeteoApiService _apiService;
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
  Timer? _bgTimer;

  // --- Estado de refresco global (pull-to-refresh) ---
  bool _isRefreshing = false;

  // --- Getters ---
  List<SavedLocation> get savedLocations => _savedLocations;
  SunPhase get currentPhase => _currentPhase;
  SunTimes? get currentSunTimes => _sunTimesCache[currentMunicipioId];
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
    OpenMeteoApiService? apiService,
    MunicipioSearchService? searchService,
    LocationService? locationService,
    AlertService? alertService,
  })  : _apiService = apiService ?? OpenMeteoApiService(),
        _searchService = searchService ?? MunicipioSearchService(),
        _locationService = locationService ?? LocationService(),
        _alertService = alertService ?? AlertService() {
    // Iniciar timer para actualizar el fondo cada minuto
    _bgTimer = Timer.periodic(const Duration(minutes: 1), (_) {
      _updateSunPhase();
    });
    // Calcular fase solar inmediatamente al construir
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

  /// Carga las localizaciones guardadas desde SharedPreferences y
  /// descarga el tiempo para la primera (si existe).
  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getStringList(_prefsKey) ?? [];

    _savedLocations = raw
        .map(SavedLocation.fromPrefsString)
        .whereType<SavedLocation>()
        .toList();

    // Ya no añadimos Madrid por defecto. La lista puede estar vacía.


    _currentIndex = 0;

    // Intentar recuperar los datos en caché de las localizaciones
    await _loadPersistedWeatherData();

    notifyListeners();

    // No disparamos de forma automática la carga con loadWeather.
    // Solo mostramos lo que había en caché. Si está vacío, el usuario usará el botón.
    // Sin embargo, si hemos actualizado el page view, notificaremos al fondo
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
      final coords = await _searchService.getCoordinates(municipioId);
      if (coords == null) throw Exception('No se encontraron coordenadas para la ubicación');

      final result = await _apiService.fetchForecast(coords.lat, coords.lon);

      final dailyList = DailyForecast.fromOpenMeteoJson(result);
      final hourlyList = HourlyForecast.fromOpenMeteoJson(result);
      final updatedTime = DateTime.now();

      _cache[municipioId] = (
        daily: dailyList,
        hourly: hourlyList,
        lastUpdated: updatedTime,
      );
      _errorMap[municipioId] = null;

      // Cargar alertas en paralelo y esperar a que terminen para poder guardarlas en caché
      await _loadAlerts(municipioId);

      // Persistir en memoria física al terminar la descarga API
      await _persistWeatherData(
        municipioId,
        result,
        _alertsCache[municipioId] ?? [], // Pasamos las alertas obtenidas
        updatedTime,
      );

      // Actualizar fase solar
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

  /// Recarga datos sin mostrar estado de carga (mantiene datos anteriores visibles).
  Future<void> _silentLoadWeather(String municipioId) async {
    try {
      final coords = await _searchService.getCoordinates(municipioId);
      if (coords == null) throw Exception('No coord');

      final result = await _apiService.fetchForecast(coords.lat, coords.lon);

      final dailyList = DailyForecast.fromOpenMeteoJson(result);
      final hourlyList = HourlyForecast.fromOpenMeteoJson(result);
      final updatedTime = DateTime.now();

      _cache[municipioId] = (
        daily: dailyList,
        hourly: hourlyList,
        lastUpdated: updatedTime,
      );
      _errorMap[municipioId] = null;
      _alertsCache.remove(municipioId);
      await _loadAlerts(municipioId);

      await _persistWeatherData(
        municipioId,
        result,
        _alertsCache[municipioId] ?? [],
        updatedTime,
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

    // Eliminar también la persistencia en disco de estos datos
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('weather_data_$id');

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

  // ---------------------------------------------------------------------------
  // Persistencia de caché
  // ---------------------------------------------------------------------------

  Future<void> _persistLocations() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(
      _prefsKey,
      _savedLocations.map((l) => l.toPrefsString()).toList(),
    );
  }

  Future<void> _persistWeatherData(
      String municipioId, 
      Map<String, dynamic> openMeteoJson,
      List<WeatherAlert> currentAlerts,
      DateTime updateTime,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final dataString = jsonEncode({
      'openMeteo': openMeteoJson,
      'alerts': currentAlerts.map((a) => a.toJson()).toList(), // Serializamos alertas
      'sunTimes': _sunTimesCache[municipioId] != null ? {
        'sunrise': _sunTimesCache[municipioId]!.sunrise.toIso8601String(),
        'sunset': _sunTimesCache[municipioId]!.sunset.toIso8601String(),
      } : null,
      'lastUpdated': updateTime.toIso8601String(),
    });
    await prefs.setString('weather_data_$municipioId', dataString);
  }

  Future<void> _loadPersistedWeatherData() async {
    final prefs = await SharedPreferences.getInstance();

    for (var loc in _savedLocations) {
      final key = 'weather_data_${loc.municipioId}';
      final jsonStr = prefs.getString(key);
      
      if (jsonStr != null) {
        try {
          final Map<String, dynamic> decoded = jsonDecode(jsonStr);
          if (!decoded.containsKey('openMeteo')) throw Exception('Old data format');
          
          final daily = DailyForecast.fromOpenMeteoJson(decoded['openMeteo']);
          final hourly = HourlyForecast.fromOpenMeteoJson(decoded['openMeteo']);
          final updated = DateTime.parse(decoded['lastUpdated']);
          
          // Rehidratar alertas si existen en el JSON antiguo/nuevo
          if (decoded.containsKey('alerts') && decoded['alerts'] != null) {
            final List<dynamic> alertsRaw = decoded['alerts'];
            _alertsCache[loc.municipioId] = alertsRaw
                .map((a) => WeatherAlert.fromJson(a as Map<String, dynamic>))
                .toList();
          }

          // Rehidratar SunTimes
          if (decoded.containsKey('sunTimes') && decoded['sunTimes'] != null) {
            final stMap = decoded['sunTimes'];
            _sunTimesCache[loc.municipioId] = SunTimes(
              sunrise: DateTime.parse(stMap['sunrise']).toLocal(),
              sunset: DateTime.parse(stMap['sunset']).toLocal(),
            );
          }
          
          _cache[loc.municipioId] = (
            daily: daily,
            hourly: hourly,
            lastUpdated: updated,
          );
        } catch (_) {
          // Si el JSON está malformado o es de otra versión, se borra.
          prefs.remove(key);
        }
      }
    }
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
      final coords = await _searchService.getCoordinates(id);
      if (coords != null) {
        lat = coords.lat;
        lon = coords.lon;
      }
    }

    final now = DateTime.now().millisecondsSinceEpoch;
    final sunTimes = SunCalculator.calculateTimes(DateTime.now(), lat, lon);
    
    // Almacenar en la caché local para acceso síncrono
    if (id.isNotEmpty) {
      _sunTimesCache[id] = sunTimes;
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
    
    // Siempre notificamos, ya que las vistas pueden estar bloqueadas a la espera 
    // de que _sunTimesCache esté rellenado.
    notifyListeners();
  }

  /// Gradiente de fondo según la fase solar y cielo del municipio activo.
  LinearGradient get backgroundGradient => gradientForMunicipio(currentMunicipioId);

  /// Gradiente de fondo para un municipio concreto (usado para interpolar en swipe).
  LinearGradient gradientForMunicipio(String id) {
    final skyCode = id.isNotEmpty ? currentSkyCodeFor(id) : '';
    final sky = SkyCondition.fromCode(skyCode);

    switch (_currentPhase) {
      case SunPhase.day:
        return _dayGradient(sky);
      case SunPhase.sunrise:
        return _sunriseGradient(sky);
      case SunPhase.sunset:
        return _sunsetGradient(sky);
      default:
        return _nightGradient(sky);
    }
  }

  /// Interpola linealmente entre dos gradientes (ambos deben tener 4 colores).
  static LinearGradient lerpGradient(LinearGradient a, LinearGradient b, double t) {
    return LinearGradient(
      begin: Alignment.topCenter,
      end: Alignment.bottomCenter,
      colors: [
        Color.lerp(a.colors[0], b.colors[0], t)!,
        Color.lerp(a.colors[1], b.colors[1], t)!,
        Color.lerp(a.colors[2], b.colors[2], t)!,
        Color.lerp(a.colors[3], b.colors[3], t)!,
      ],
      stops: const [0.0, 0.33, 0.67, 1.0],
    );
  }

  // --- Gradientes de DÍA (4 stops) ---
  LinearGradient _dayGradient(SkyCondition sky) {
    switch (sky) {
      case SkyCondition.clear:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF0F5298),
            Color(0xFF1F73BA),
            Color(0xFF3C99DC),
            Color(0xFF4DA8E8),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.partlyCloudy:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF4A6B8A),
            Color(0xFF627D96),
            Color(0xFF7A8FA0),
            Color(0xFF9EAAB6),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.overcast:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF4B5563),
            Color(0xFF5A6370),
            Color(0xFF6B7280),
            Color(0xFF555E69),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
    }
  }

  // --- Gradientes de AMANECER (4 stops) ---
  LinearGradient _sunriseGradient(SkyCondition sky) {
    switch (sky) {
      case SkyCondition.clear:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF141E30),
            Color(0xFF243B55),
            Color(0xFFCC2B5E),
            Color(0xFF753A88),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.partlyCloudy:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF2C3E50),
            Color(0xFF5D6D7E),
            Color(0xFF9B6B8A),
            Color(0xFF8E99A4),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.overcast:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF3D3D3D),
            Color(0xFF4B4646),
            Color(0xFF5A5050),
            Color(0xFF6B6360),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
    }
  }

  // --- Gradientes de ATARDECER (4 stops) ---
  LinearGradient _sunsetGradient(SkyCondition sky) {
    switch (sky) {
      case SkyCondition.clear:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF3E1E68),
            Color(0xFF82306B),
            Color(0xFFC6426E),
            Color(0xFFF9A825),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.partlyCloudy:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF3D3456),
            Color(0xFF634760),
            Color(0xFF8A5A6A),
            Color(0xFF7A6E65),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.overcast:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF3D3D3D),
            Color(0xFF4C4444),
            Color(0xFF5A4A4A),
            Color(0xFF4A4545),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
    }
  }

  // --- Gradientes de NOCHE (4 stops) ---
  LinearGradient _nightGradient(SkyCondition sky) {
    switch (sky) {
      case SkyCondition.clear:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF1A1A2E),
            Color(0xFF16213E),
            Color(0xFF0F3460),
            Color(0xFF0A0A12),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.partlyCloudy:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF1C2333),
            Color(0xFF232938),
            Color(0xFF2A2F3D),
            Color(0xFF252830),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
      case SkyCondition.overcast:
        return const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFF1A1A1E),
            Color(0xFF202023),
            Color(0xFF252528),
            Color(0xFF1E1E20),
          ],
          stops: [0.0, 0.33, 0.67, 1.0],
        );
    }
  }
}

enum SunPhase { night, sunrise, day, sunset }

