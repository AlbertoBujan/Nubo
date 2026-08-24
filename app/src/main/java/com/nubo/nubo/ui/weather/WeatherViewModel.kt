package com.nubo.nubo.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nubo.nubo.data.local.WeatherStorage
import com.nubo.nubo.data.location.LocationException
import com.nubo.nubo.data.remote.OpenMeteoException
import com.nubo.nubo.data.repository.AlertRepository
import com.nubo.nubo.data.repository.LocationRepository
import com.nubo.nubo.data.repository.WeatherRepository
import com.nubo.nubo.domain.astro.MoonCalculator
import com.nubo.nubo.domain.astro.SunCalculator
import com.nubo.nubo.domain.astro.SunTimes
import com.nubo.nubo.domain.model.SavedLocation
import com.nubo.nubo.domain.weather.SunPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Estado de la pantalla del tiempo.
 *
 * Sustituye al `WeatherProvider` de la app Flutter. Mantiene los datos por
 * municipio para que al deslizar entre ciudades cada página conserve los suyos
 * y no se reconstruyan todas a la vez.
 */
class WeatherViewModel(
    private val weatherRepository: WeatherRepository,
    private val alertRepository: AlertRepository,
    private val locationRepository: LocationRepository,
    private val storage: WeatherStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    /** Día para el que están calculados los datos solares de cada municipio. */
    private val astroDay = mutableMapOf<String, LocalDate>()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch { initialize() }
        viewModelScope.launch { trackSunPhase() }
    }

    // ── Arranque ────────────────────────────────────────────────────────────

    private suspend fun initialize() {
        val locations = storage.loadLocations()
        val cities = locations.associate { location ->
            val cached = storage.loadWeather(location.municipioId)
            location.municipioId to CityWeather(
                municipioId = location.municipioId,
                name = location.nombre,
                daily = cached?.daily.orEmpty(),
                hourly = cached?.hourly.orEmpty(),
                alerts = cached?.alerts.orEmpty(),
                sunTimes = cached?.sunTimes,
                lastUpdated = cached?.lastUpdated,
            )
        }

        _uiState.update {
            it.copy(
                locations = locations,
                cities = cities,
                currentIndex = 0,
                backgroundInterval = BackgroundInterval.entries.getOrElse(
                    storage.loadBackgroundIntervalIndex(),
                ) { BackgroundInterval.OFF },
                isInitialized = true,
            )
        }

        locations.forEach { refreshAstro(it.municipioId) }
        updateSunPhase()

        // Las ciudades sin caché se cargan de red; las que la tienen se
        // muestran ya y se refrescan al vuelo.
        locations.forEach { location -> loadWeather(location.municipioId) }
    }

    /**
     * Recalcula la fase solar cada minuto.
     *
     * Solo la fase, que es una comparación de horas: los datos solares y
     * lunares se recalculan al cambiar el día, porque encontrar el orto lunar
     * exige un barrido iterativo y no vale la pena repetirlo cada minuto.
     */
    private suspend fun trackSunPhase() {
        while (true) {
            updateSunPhase()
            delay(SUN_PHASE_REFRESH_MILLIS)
        }
    }

    private fun updateSunPhase() {
        val sunTimes = _uiState.value.currentCity?.sunTimes
        _uiState.update { it.copy(sunPhase = phaseFor(sunTimes)) }
    }

    // ── Carga de datos ──────────────────────────────────────────────────────

    /** Carga el tiempo de un municipio si no está ya en memoria. */
    fun loadWeather(municipioId: String, force: Boolean = false) {
        if (municipioId.isBlank()) return
        val existing = _uiState.value.cities[municipioId]
        if (!force && existing?.hasData == true) return
        if (existing?.isLoading == true) return

        viewModelScope.launch {
            updateCity(municipioId) { it.copy(isLoading = true, error = null) }

            try {
                fetchInto(municipioId)
                updateCity(municipioId) { it.copy(isLoading = false, error = null) }
            } catch (e: OpenMeteoException) {
                updateCity(municipioId) { it.copy(isLoading = false, error = e.message) }
            } catch (e: Exception) {
                updateCity(municipioId) {
                    it.copy(isLoading = false, error = "Error de conexión: ${e.message}")
                }
            }
        }
    }

    /** Descarga predicción y avisos, actualiza el estado y persiste. */
    private suspend fun fetchInto(municipioId: String) {
        val forecast = weatherRepository.getForecast(municipioId)
        val updatedAt = LocalDateTime.now()

        updateCity(municipioId) {
            it.copy(daily = forecast.daily, hourly = forecast.hourly, lastUpdated = updatedAt)
        }

        refreshAstro(municipioId)
        updateSunPhase()

        // Un fallo de AEMET no debe invalidar la predicción ya obtenida.
        val alerts = runCatching { alertRepository.getAlerts(municipioId) }.getOrDefault(emptyList())
        updateCity(municipioId) { it.copy(alerts = alerts) }

        storage.saveWeather(
            municipioId = municipioId,
            rawJson = forecast.rawJson,
            alerts = alerts,
            sunTimes = _uiState.value.cities[municipioId]?.sunTimes,
            updatedAt = updatedAt,
        )
    }

    /** Fuerza la recarga de un municipio. */
    fun refreshWeather(municipioId: String) = loadWeather(municipioId, force = true)

    /** Recarga todas las ciudades a la vez, sin vaciar lo que ya se muestra. */
    fun refreshAll() {
        if (_uiState.value.locations.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                _uiState.value.locations
                    .map { location -> async { runCatching { fetchInto(location.municipioId) } } }
                    .awaitAll()
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // ── Datos solares y lunares ─────────────────────────────────────────────

    /** Recalcula sol y luna del municipio si aún no se hizo hoy. */
    private suspend fun refreshAstro(municipioId: String) {
        val today = LocalDate.now()
        if (astroDay[municipioId] == today) return

        val coords = runCatching { locationRepository.getCoordinates(municipioId) }.getOrNull()
            ?: return

        val now = LocalDateTime.now()
        val sunTimes = SunCalculator.calculateTimes(now, coords.lat, coords.lon)
        val moonData = MoonCalculator.calculate(now, coords.lat, coords.lon)

        astroDay[municipioId] = today
        updateCity(municipioId) { it.copy(sunTimes = sunTimes, moonData = moonData) }
    }

    // ── Localizaciones ──────────────────────────────────────────────────────

    fun addLocation(location: SavedLocation, switchTo: Boolean = true) {
        viewModelScope.launch {
            val state = _uiState.value
            val existingIndex = state.locations.indexOfFirst {
                it.municipioId == location.municipioId
            }

            if (existingIndex >= 0) {
                if (switchTo) _uiState.update { it.copy(currentIndex = existingIndex) }
                loadWeather(location.municipioId)
                return@launch
            }

            val locations = state.locations + location
            _uiState.update {
                it.copy(
                    locations = locations,
                    cities = it.cities + (
                        location.municipioId to CityWeather(
                            municipioId = location.municipioId,
                            name = location.nombre,
                        )
                        ),
                    currentIndex = if (switchTo) locations.lastIndex else it.currentIndex,
                )
            }

            storage.saveLocations(locations)
            refreshAstro(location.municipioId)
            loadWeather(location.municipioId)
        }
    }

    fun removeLocation(index: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            val location = state.locations.getOrNull(index) ?: return@launch

            val locations = state.locations.toMutableList().apply { removeAt(index) }
            astroDay.remove(location.municipioId)

            _uiState.update {
                it.copy(
                    locations = locations,
                    cities = it.cities - location.municipioId,
                    currentIndex = it.currentIndex.coerceIn(0, maxOf(0, locations.lastIndex)),
                )
            }

            storage.saveLocations(locations)
            storage.removeWeather(location.municipioId)
            updateSunPhase()
        }
    }

    /**
     * Fija la ciudad activa.
     *
     * Se llama cuando el pager ya se ha asentado, no durante el arrastre: en la
     * app Flutter actualizar el índice a mitad del gesto provocaba un tirón al
     * final del swipe.
     */
    fun onPageSettled(index: Int) {
        if (index == _uiState.value.currentIndex) return
        if (index !in _uiState.value.locations.indices) return

        _uiState.update { it.copy(currentIndex = index) }
        updateSunPhase()

        val municipioId = _uiState.value.locations[index].municipioId
        viewModelScope.launch { refreshAstro(municipioId) }
        loadWeather(municipioId)
    }

    // ── Geolocalización ─────────────────────────────────────────────────────

    fun addCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true) }
            try {
                val position = locationRepository.getCurrentPosition()
                val nearest = locationRepository.findNearest(position.lat, position.lon)
                if (nearest != null) addLocation(nearest, switchTo = true)
            } catch (e: LocationException) {
                reportLocationError(e.message)
            } catch (e: Exception) {
                reportLocationError("Error de geolocalización: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLocating = false) }
            }
        }
    }

    private fun reportLocationError(message: String?) {
        val id = _uiState.value.currentMunicipioId
        if (id.isNotEmpty()) updateCity(id) { it.copy(error = message) }
    }

    // ── Búsqueda ────────────────────────────────────────────────────────────

    fun search(query: String) {
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            // Pequeño respiro para no buscar en cada pulsación.
            delay(SEARCH_DEBOUNCE_MILLIS)
            val results = runCatching { locationRepository.searchByName(query) }
                .getOrDefault(emptyList())
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
    }

    // ── Actualización en segundo plano ──────────────────────────────────────

    fun setBackgroundInterval(interval: BackgroundInterval) {
        viewModelScope.launch {
            storage.saveBackgroundIntervalIndex(interval.ordinal)
            _uiState.update { it.copy(backgroundInterval = interval) }
        }
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    private fun updateCity(municipioId: String, transform: (CityWeather) -> CityWeather) {
        _uiState.update { state ->
            val name = state.locations.firstOrNull { it.municipioId == municipioId }?.nombre
                ?: state.cities[municipioId]?.name
                ?: "Desconocido"
            val current = state.cities[municipioId]
                ?: CityWeather(municipioId = municipioId, name = name)
            state.copy(cities = state.cities + (municipioId to transform(current).copy(name = name)))
        }
    }

    companion object {
        private const val SUN_PHASE_REFRESH_MILLIS = 60_000L
        private const val SEARCH_DEBOUNCE_MILLIS = 200L

        /** Ventana a cada lado del orto y del ocaso donde el cielo transiciona. */
        private val TRANSITION = Duration.ofMinutes(30)

        /** Fase solar del momento a partir del amanecer y el ocaso del día. */
        fun phaseFor(
            sunTimes: SunTimes?,
            now: LocalDateTime = LocalDateTime.now(),
        ): SunPhase {
            // Sin datos solares se asume de día: es el fondo más neutro y
            // evita arrancar la app en negro mientras se calcula.
            if (sunTimes == null) return SunPhase.DAY

            val sunriseStart = sunTimes.sunrise.minus(TRANSITION)
            val sunriseEnd = sunTimes.sunrise.plus(TRANSITION)
            val sunsetStart = sunTimes.sunset.minus(TRANSITION)
            val sunsetEnd = sunTimes.sunset.plus(TRANSITION)

            return when {
                now >= sunriseStart && now < sunriseEnd -> SunPhase.SUNRISE
                now >= sunriseEnd && now < sunsetStart -> SunPhase.DAY
                now >= sunsetStart && now < sunsetEnd -> SunPhase.SUNSET
                else -> SunPhase.NIGHT
            }
        }
    }
}
