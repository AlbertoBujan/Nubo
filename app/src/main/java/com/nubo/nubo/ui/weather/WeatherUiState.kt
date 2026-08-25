package com.nubo.nubo.ui.weather

import com.nubo.nubo.domain.astro.MoonData
import com.nubo.nubo.domain.astro.SunTimes
import com.nubo.nubo.domain.model.AirQualityForecast
import com.nubo.nubo.domain.model.DailyForecast
import com.nubo.nubo.domain.model.HourlyForecast
import com.nubo.nubo.domain.model.SavedLocation
import com.nubo.nubo.domain.model.zoneOf
import com.nubo.nubo.domain.model.WeatherAlert
import com.nubo.nubo.domain.weather.SkyCondition
import com.nubo.nubo.domain.weather.SunPhase
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Estado de una ciudad concreta. */
data class CityWeather(
    val locationId: String,
    val name: String,
    /**
     * Zona horaria del sitio.
     *
     * Open-Meteo devuelve las marcas de tiempo ya en la hora local del sitio,
     * así que compararlas con el reloj del teléfono solo funciona si ambos
     * coinciden. Mientras la app fue solo española casi colaba —salvo
     * Canarias, que va una hora por detrás—, pero con ciudades de todo el
     * mundo hay que comparar contra la hora de allí.
     */
    val timeZone: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val daily: List<DailyForecast> = emptyList(),
    val hourly: List<HourlyForecast> = emptyList(),
    val alerts: List<WeatherAlert> = emptyList(),
    /**
     * Serie horaria de calidad del aire. Vacía si su endpoint falló o si la
     * caché es anterior a la tarjeta de condiciones.
     */
    val airQuality: List<AirQualityForecast> = emptyList(),
    val sunTimes: SunTimes? = null,
    val moonData: MoonData? = null,
    /**
     * Trayectorias del sol y de la luna, normalizadas. Ver `SkyPath`.
     *
     * No se persisten: dependen del día y de la latitud, y recalcularlas al
     * arrancar cuesta menos que serializarlas.
     */
    val sunPath: List<Float> = emptyList(),
    val moonPath: List<Float> = emptyList(),
    val lastUpdated: LocalDateTime? = null,
) {
    /** Zona del sitio; la del dispositivo si aún no se conoce. */
    val zone: ZoneId get() = zoneOf(timeZone)

    /** Hora local del sitio ahora mismo. */
    val nowThere: LocalDateTime get() = LocalDateTime.now(zone)

    /** Hora de predicción más próxima al momento actual. */
    private val closestHour: HourlyForecast?
        get() {
            if (hourly.isEmpty()) return null
            val now = nowThere
            return hourly.minByOrNull {
                Duration.between(it.dateTime, now).abs().toMillis()
            }
        }

    val currentTemperature: Int? get() = closestHour?.temperature
    val skyCode: String get() = closestHour?.skyStateCode.orEmpty()
    val skyDescription: String get() = closestHour?.skyDescription.orEmpty()
    val skyCondition: SkyCondition get() = SkyCondition.fromCode(skyCode)

    /** Viento de la hora en curso; mueve las nubes del fondo. */
    val windSpeed: Int? get() = closestHour?.windSpeed
    val windDegrees: Int? get() = closestHour?.windDirectionDegrees

    // ── Condiciones de ahora mismo ──────────────────────────────────────────

    val humidity: Int? get() = closestHour?.humidity
    val apparentTemperature: Int? get() = closestHour?.apparentTemperature
    val uvIndex: Double? get() = closestHour?.uvIndex

    /**
     * Calidad del aire de la hora en curso.
     *
     * Se elige la muestra más próxima igual que con la predicción, y no el
     * primer elemento de la lista, porque al releer la caché unas horas
     * después el principio de la serie ya ha quedado atrás.
     */
    val airQualityIndex: Int?
        get() {
            if (airQuality.isEmpty()) return null
            val now = nowThere
            return airQuality.minByOrNull {
                Duration.between(it.dateTime, now).abs().toMillis()
            }?.europeanAqi
        }

    /** Si hay algo que enseñar en la tarjeta de condiciones. */
    val hasConditions: Boolean
        get() = airQualityIndex != null ||
            uvIndex != null ||
            humidity != null ||
            apparentTemperature != null

    val hasData: Boolean get() = daily.isNotEmpty() && hourly.isNotEmpty()

    /** Máxima y mínima de hoy; si hoy no está, se usa el primer día. */
    val todayRange: Pair<Int?, Int?>
        get() {
            if (daily.isEmpty()) return null to null
            val today = nowThere.toLocalDate()
            val day = daily.firstOrNull { it.date == today } ?: daily.first()
            return day.tempMax to day.tempMin
        }

    /** "Hace 5 min", "Hace 2 h"… para el indicador de frescura. */
    val lastRefreshText: String
        get() {
            val updated = lastUpdated ?: return ""
            val diff = Duration.between(updated, LocalDateTime.now())
            return when {
                diff.toDays() >= 1 -> "Hace ${diff.toDays()} d"
                diff.toHours() >= 1 -> "Hace ${diff.toHours()} h"
                diff.toMinutes() >= 1 -> "Hace ${diff.toMinutes()} min"
                else -> "Actualizado"
            }
        }
}

/** Cada cuánto se refresca el tiempo en segundo plano. */
enum class BackgroundInterval(val label: String, val hours: Long?) {
    OFF("Desactivado", null),
    EVERY_12H("Cada 12 horas", 12),
    EVERY_24H("Cada 24 horas", 24),
}

/**
 * Una ubicación borrada que todavía se puede recuperar con "Deshacer".
 *
 * Guarda su posición original para devolverla al mismo sitio, no al final.
 */
data class RemovedLocation(
    val index: Int,
    val location: SavedLocation,
    val weather: CityWeather?,
)

/**
 * Un resultado de búsqueda.
 *
 * La distancia no vive en [SavedLocation] porque es circunstancial —depende de
 * dónde estés al buscar— y esa sí se persiste.
 */
data class SearchResult(
    val location: SavedLocation,
    val distanceKm: Double? = null,
)

/** Estado completo de la pantalla principal. */
data class WeatherUiState(
    val locations: List<SavedLocation> = emptyList(),
    val cities: Map<String, CityWeather> = emptyMap(),
    val currentIndex: Int = 0,
    val sunPhase: SunPhase = SunPhase.DAY,
    val isRefreshing: Boolean = false,
    val isLocating: Boolean = false,
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    /** Si los resultados se ordenan por cercanía en vez de por relevancia. */
    val searchNearby: Boolean = false,
    val backgroundInterval: BackgroundInterval = BackgroundInterval.OFF,
    val isInitialized: Boolean = false,
) {
    val currentLocation: SavedLocation? get() = locations.getOrNull(currentIndex)

    val currentMunicipioId: String get() = currentLocation?.locationId.orEmpty()

    fun cityAt(index: Int): CityWeather? =
        locations.getOrNull(index)?.let { cities[it.locationId] }

    val currentCity: CityWeather? get() = cityAt(currentIndex)
}
