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
import com.nubo.nubo.domain.astro.SkyPath
import com.nubo.nubo.domain.astro.SunCalculator
import com.nubo.nubo.domain.astro.SunTimes
import com.nubo.nubo.data.remote.Coordinates
import com.nubo.nubo.domain.geo.distanceKm
import com.nubo.nubo.domain.model.SavedLocation
import com.nubo.nubo.domain.model.zoneOf
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

    /** Candidatos crudos de la última búsqueda, para reordenar sin volver a pedir. */
    private var candidates: List<SavedLocation> = emptyList()

    /** Punto de referencia del orden por cercanía; ver [resolveSearchOrigin]. */
    private var searchOrigin: Coordinates? = null

    /**
     * Última ubicación eliminada, a la espera de que expire el "Deshacer".
     *
     * Se guarda también su [CityWeather] para que al deshacer la tarjeta
     * vuelva con sus datos puestos en vez de parpadear en blanco mientras se
     * vuelve a descargar. El borrado en disco sí es inmediato: si la app muere
     * durante esos segundos, deshacer ya no es una opción y lo que queda es
     * coherente con lo que se ve.
     */
    private var lastRemoved: RemovedLocation? = null

    init {
        viewModelScope.launch { initialize() }
        viewModelScope.launch { trackSunPhase() }
    }

    // ── Arranque ────────────────────────────────────────────────────────────

    private suspend fun initialize() {
        val locations = completeLegacy(storage.loadLocations())
        val cities = locations.associate { location ->
            val cached = storage.loadWeather(location.locationId)
            location.locationId to CityWeather(
                locationId = location.locationId,
                name = location.nombre,
                timeZone = location.timeZone,
                daily = cached?.daily.orEmpty(),
                hourly = cached?.hourly.orEmpty(),
                alerts = cached?.alerts.orEmpty(),
                airQuality = cached?.airQuality.orEmpty(),
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

        locations.forEach { refreshAstro(it.locationId) }
        updateSunPhase()

        // Las ciudades sin caché se cargan de red; las que la tienen se
        // muestran ya y se refrescan al vuelo.
        locations.forEach { location -> loadWeather(location.locationId) }
    }

    /**
     * Rellena las ciudades guardadas antes de que la app saliera de España.
     *
     * Solo tenían el código INE; las coordenadas y la zona horaria salen del
     * maestro de AEMET. Sin esto no se puede ni pedir la predicción, porque
     * ya no hay ningún listado del que deducir dónde está el municipio.
     */
    private suspend fun completeLegacy(locations: List<SavedLocation>): List<SavedLocation> {
        if (locations.none { it.isLegacyIne && !it.hasCoordinates }) return locations

        val completed = locations.map { location ->
            if (!location.isLegacyIne || location.hasCoordinates) {
                location
            } else {
                runCatching { locationRepository.resolveLegacy(location) }.getOrNull() ?: location
            }
        }

        // Se persisten ya en el formato nuevo para no repetir la resolución
        // en cada arranque.
        if (completed.any { it.hasCoordinates }) storage.saveLocations(completed)
        return completed
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
    fun loadWeather(locationId: String, force: Boolean = false) {
        if (locationId.isBlank()) return
        val existing = _uiState.value.cities[locationId]
        if (!force && existing?.hasData == true) return
        if (existing?.isLoading == true) return

        viewModelScope.launch {
            updateCity(locationId) { it.copy(isLoading = true, error = null) }

            try {
                fetchInto(locationId)
                updateCity(locationId) { it.copy(isLoading = false, error = null) }
            } catch (e: OpenMeteoException) {
                updateCity(locationId) { it.copy(isLoading = false, error = e.message) }
            } catch (e: Exception) {
                updateCity(locationId) {
                    it.copy(isLoading = false, error = "Error de conexión: ${e.message}")
                }
            }
        }
    }

    /** Descarga predicción y avisos, actualiza el estado y persiste. */
    private suspend fun fetchInto(locationId: String) {
        val location = locationOf(locationId) ?: return
        val forecast = weatherRepository.getForecast(location)
        val updatedAt = LocalDateTime.now()

        // La zona horaria autoritativa es la de la respuesta: la del buscador
        // puede faltar, y las guardadas de antes no tienen ninguna.
        forecast.timeZone?.let { rememberTimeZone(locationId, it) }

        updateCity(locationId) {
            it.copy(
                daily = forecast.daily,
                hourly = forecast.hourly,
                // Si el endpoint del aire falló se conserva lo que hubiera:
                // un dato de hace una hora dice más que un hueco.
                airQuality = forecast.airQuality.ifEmpty { it.airQuality },
                lastUpdated = updatedAt,
                timeZone = forecast.timeZone ?: it.timeZone,
            )
        }

        refreshAstro(locationId)
        updateSunPhase()

        // Un fallo de AEMET no debe invalidar la predicción ya obtenida.
        val alerts = runCatching { alertRepository.getAlerts(location) }.getOrDefault(emptyList())
        updateCity(locationId) { it.copy(alerts = alerts) }

        storage.saveWeather(
            locationId = locationId,
            rawJson = forecast.rawJson,
            alerts = alerts,
            sunTimes = _uiState.value.cities[locationId]?.sunTimes,
            updatedAt = updatedAt,
            airQualityJson = forecast.airQualityRawJson,
        )
    }

    /** La localización guardada con ese id, si sigue en la lista. */
    private fun locationOf(locationId: String): SavedLocation? =
        _uiState.value.locations.firstOrNull { it.locationId == locationId }

    /** Guarda la zona horaria en la localización, si ha cambiado. */
    private suspend fun rememberTimeZone(locationId: String, timeZone: String) {
        val locations = _uiState.value.locations
        val index = locations.indexOfFirst { it.locationId == locationId }
        if (index < 0 || locations[index].timeZone == timeZone) return

        val updated = locations.toMutableList()
        updated[index] = updated[index].copy(timeZone = timeZone)
        _uiState.update { it.copy(locations = updated) }
        storage.saveLocations(updated)
    }

    /** Fuerza la recarga de un municipio. */
    fun refreshWeather(locationId: String) = loadWeather(locationId, force = true)

    /** Recarga todas las ciudades a la vez, sin vaciar lo que ya se muestra. */
    fun refreshAll() {
        if (_uiState.value.locations.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                _uiState.value.locations
                    .map { location -> async { runCatching { fetchInto(location.locationId) } } }
                    .awaitAll()
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // ── Datos solares y lunares ─────────────────────────────────────────────

    /** Recalcula sol y luna del sitio si aún no se hizo hoy. */
    private suspend fun refreshAstro(locationId: String) {
        val location = locationOf(locationId) ?: return
        val lat = location.lat ?: return
        val lon = location.lon ?: return

        // "Hoy" y "ahora" son los del sitio, no los del teléfono: en Tokio el
        // amanecer que toca calcular no es el del día que aquí es hoy.
        val zone = zoneOf(_uiState.value.cities[locationId]?.timeZone ?: location.timeZone)
        val now = LocalDateTime.now(zone)
        val today = now.toLocalDate()
        if (astroDay[locationId] == today) return

        // La zona va explícita: por defecto ambos calculan contra la del
        // dispositivo, y entonces el orto de Tokio salía en hora de Madrid.
        val sunTimes = SunCalculator.calculateTimes(now, lat, lon, zone)
        val moonData = MoonCalculator.calculate(now, lat, lon, zone)

        // La forma del arco depende de la latitud y del día, así que se
        // muestrea aquí y no en el Canvas, que se redibuja constantemente.
        val sunPath = sunTimes?.let {
            SkyPath.sun(it.sunrise, it.sunset, lat, lon, zone)
        }.orEmpty()

        val moonPath = if (moonData.moonrise != null && moonData.moonset != null) {
            SkyPath.moon(moonData.moonrise, moonData.moonset, lat, lon, zone)
        } else {
            emptyList()
        }

        astroDay[locationId] = today
        updateCity(locationId) {
            it.copy(
                sunTimes = sunTimes,
                moonData = moonData,
                sunPath = sunPath,
                moonPath = moonPath,
            )
        }
    }

    // ── Localizaciones ──────────────────────────────────────────────────────

    fun addLocation(location: SavedLocation, switchTo: Boolean = true) {
        viewModelScope.launch {
            val state = _uiState.value
            val existingIndex = state.locations.indexOfFirst {
                it.locationId == location.locationId
            }

            if (existingIndex >= 0) {
                if (switchTo) _uiState.update { it.copy(currentIndex = existingIndex) }
                loadWeather(location.locationId)
                return@launch
            }

            val locations = state.locations + location
            _uiState.update {
                it.copy(
                    locations = locations,
                    cities = it.cities + (
                        location.locationId to CityWeather(
                            locationId = location.locationId,
                            name = location.nombre,
                            timeZone = location.timeZone,
                        )
                        ),
                    currentIndex = if (switchTo) locations.lastIndex else it.currentIndex,
                )
            }

            storage.saveLocations(locations)
            refreshAstro(location.locationId)
            loadWeather(location.locationId)
        }
    }

    fun removeLocation(index: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            val location = state.locations.getOrNull(index) ?: return@launch

            val locations = state.locations.toMutableList().apply { removeAt(index) }
            astroDay.remove(location.locationId)

            _uiState.update {
                it.copy(
                    locations = locations,
                    cities = it.cities - location.locationId,
                    currentIndex = it.currentIndex.coerceIn(0, maxOf(0, locations.lastIndex)),
                )
            }

            lastRemoved = RemovedLocation(
                index = index,
                location = location,
                weather = state.cities[location.locationId],
            )

            storage.saveLocations(locations)
            storage.removeWeather(location.locationId)
            updateSunPhase()
        }
    }

    /**
     * Devuelve a su sitio la última ubicación eliminada.
     *
     * El índice se recorta contra la lista actual porque entre el borrado y el
     * "Deshacer" pueden haber cambiado cosas —añadir otra ciudad, reordenar—,
     * y en ese caso vale más colocarla al final que fallar.
     */
    fun undoRemove() {
        val removed = lastRemoved ?: return
        lastRemoved = null

        viewModelScope.launch {
            val state = _uiState.value
            if (state.locations.any { it.locationId == removed.location.locationId }) return@launch

            val index = removed.index.coerceIn(0, state.locations.size)
            val locations = state.locations.toMutableList()
                .apply { add(index, removed.location) }

            _uiState.update {
                it.copy(
                    locations = locations,
                    cities = it.cities + (
                        removed.location.locationId to (
                            removed.weather ?: CityWeather(
                                locationId = removed.location.locationId,
                                name = removed.location.nombre,
                                timeZone = removed.location.timeZone,
                            )
                            )
                        ),
                    currentIndex = index,
                )
            }

            storage.saveLocations(locations)
            refreshAstro(removed.location.locationId)
            // La caché en disco se borró al eliminar: este `loadWeather` es lo
            // que la vuelve a escribir.
            loadWeather(removed.location.locationId)
            updateSunPhase()
        }
    }

    /**
     * Cambia una ubicación de posición en la lista.
     *
     * El orden es el de la lista guardada: `WeatherStorage` lo conserva
     * escribiendo el índice como prefijo de cada entrada, porque DataStore la
     * almacena en un `Set`.
     */
    fun moveLocation(from: Int, to: Int) {
        if (from == to) return

        viewModelScope.launch {
            val state = _uiState.value
            if (from !in state.locations.indices) return@launch
            if (to !in state.locations.indices) return@launch

            val current = state.currentLocation
            val locations = state.locations.toMutableList().apply {
                add(to, removeAt(from))
            }

            // La ciudad que se está viendo sigue siendo la misma aunque su
            // índice haya cambiado; si no se recalcula, reordenar salta de
            // página.
            val currentIndex = locations
                .indexOfFirst { it.locationId == current?.locationId }
                .takeIf { it >= 0 } ?: state.currentIndex

            _uiState.update { it.copy(locations = locations, currentIndex = currentIndex) }
            storage.saveLocations(locations)
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

        val locationId = _uiState.value.locations[index].locationId
        viewModelScope.launch { refreshAstro(locationId) }
        loadWeather(locationId)
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
            candidates = emptyList()
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            // Pequeño respiro para no buscar en cada pulsación.
            delay(SEARCH_DEBOUNCE_MILLIS)
            candidates = runCatching { locationRepository.searchByName(query) }
                .getOrDefault(emptyList())
            _uiState.update {
                it.copy(searchResults = rank(candidates, it.searchNearby), isSearching = false)
            }
        }
    }

    /**
     * Activa o desactiva el orden por cercanía.
     *
     * No se vuelve a buscar: se reordenan los candidatos que ya están en
     * memoria, así que el check responde al instante.
     */
    fun setSearchNearby(enabled: Boolean) {
        if (enabled) viewModelScope.launch { resolveSearchOrigin() }
        _uiState.update { it.copy(searchNearby = enabled, searchResults = rank(candidates, enabled)) }
    }

    /**
     * Punto desde el que se mide la cercanía.
     *
     * Primero la posición real; si no hay permiso, o el GPS está apagado, o
     * tarda demasiado, se cae a la ciudad que se está viendo. Así el check
     * nunca se queda sin hacer nada, que es lo que pasaría atado solo al GPS.
     */
    private suspend fun resolveSearchOrigin() {
        val gps = runCatching { locationRepository.getCurrentPosition() }.getOrNull()
        if (gps != null) {
            searchOrigin = gps
        } else {
            val city = _uiState.value.currentLocation
            val lat = city?.lat
            val lon = city?.lon
            if (lat != null && lon != null) searchOrigin = Coordinates(lat, lon)
        }
        // La posición puede llegar después de haber marcado el check.
        _uiState.update { it.copy(searchResults = rank(candidates, it.searchNearby)) }
    }

    /**
     * Ordena y acota los candidatos.
     *
     * Sin cercanía se respeta el orden de Open-Meteo, que es por relevancia y
     * población. Con cercanía manda la distancia, que es lo que rescata al
     * pueblo homónimo de al lado frente a la capital del otro hemisferio.
     */
    private fun rank(candidates: List<SavedLocation>, nearby: Boolean): List<SearchResult> {
        val origin = searchOrigin
        if (!nearby || origin == null) {
            return candidates.take(MAX_SEARCH_RESULTS).map { SearchResult(it) }
        }

        return candidates
            .mapNotNull { location ->
                val lat = location.lat ?: return@mapNotNull null
                val lon = location.lon ?: return@mapNotNull null
                SearchResult(location, distanceKm(origin.lat, origin.lon, lat, lon))
            }
            .sortedBy { it.distanceKm }
            .take(MAX_SEARCH_RESULTS)
    }

    fun clearSearch() {
        searchJob?.cancel()
        candidates = emptyList()
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

    private fun updateCity(locationId: String, transform: (CityWeather) -> CityWeather) {
        _uiState.update { state ->
            val name = state.locations.firstOrNull { it.locationId == locationId }?.nombre
                ?: state.cities[locationId]?.name
                ?: "Desconocido"
            val current = state.cities[locationId]
                ?: CityWeather(locationId = locationId, name = name)
            state.copy(cities = state.cities + (locationId to transform(current).copy(name = name)))
        }
    }

    companion object {
        private const val SUN_PHASE_REFRESH_MILLIS = 60_000L
        private const val SEARCH_DEBOUNCE_MILLIS = 200L

        /**
         * Resultados que se muestran, de los 100 que se piden.
         *
         * Se piden muchos para poder ordenarlos por cercanía, pero enseñarlos
         * todos convertiría la lista en un catálogo.
         */
        private const val MAX_SEARCH_RESULTS = 25

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
