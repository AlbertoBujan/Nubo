package com.nubo.nubo.ui.weather

import com.nubo.nubo.domain.astro.DayLength
import com.nubo.nubo.domain.astro.MoonData
import com.nubo.nubo.domain.astro.SunTimes
import com.nubo.nubo.domain.model.AirQualityForecast
import com.nubo.nubo.domain.model.CityError
import com.nubo.nubo.domain.model.DailyForecast
import com.nubo.nubo.domain.model.HourlyForecast
import com.nubo.nubo.domain.model.SavedLocation
import com.nubo.nubo.domain.model.zoneOf
import com.nubo.nubo.domain.model.WeatherAlert
import com.nubo.nubo.domain.weather.SkyCondition
import com.nubo.nubo.domain.weather.WeatherCode
import com.nubo.nubo.domain.weather.WeatherDescription
import com.nubo.nubo.domain.weather.SunPhase
import com.nubo.nubo.domain.weather.sunPhaseAt
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import com.nubo.nubo.domain.model.Units

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
    val error: CityError? = null,
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
    /** Cuánto dura el día de hoy y cuánto ha cambiado desde ayer. */
    val dayLength: DayLength? = null,
    /** Próxima luna llena en la zona de la ciudad, o nulo si no se ha calculado. */
    val nextFullMoon: LocalDateTime? = null,
    /** Próxima luna nueva, igual. */
    val nextNewMoon: LocalDateTime? = null,
    /**
     * Si la ciudad está al sur del ecuador.
     *
     * Desde allí la luna se ve del revés, y el icono de la fase se dibuja
     * espejado. Es un dato del sitio, no del tiempo, pero viaja con lo lunar
     * porque es lo único que lo usa.
     */
    val southernSky: Boolean = false,
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
    /** Fenómeno de la hora en curso; su texto lo resuelve la interfaz. */
    val skyDescription: WeatherDescription
        get() = WeatherCode.fromCode(skyCode).description
    val skyCondition: SkyCondition get() = SkyCondition.fromCode(skyCode)

    /**
     * Fase solar **de este sitio**, con su propio reloj.
     *
     * No hay una fase solar de la aplicación. Cuando la había, deslizar de A
     * Coruña a Toronto pintaba Toronto con el gradiente de mediodía y las
     * estrellas encima: el cielo salía del estado global —la fase de la ciudad
     * activa— mientras las estrellas salían del código horario de Toronto, que
     * sí es por sitio. Dos fuentes distintas para lo mismo.
     */
    val sunPhase: SunPhase get() = sunPhaseAt(sunTimes, nowThere)

    /** Viento de la hora en curso; mueve las nubes del fondo. */
    val windSpeed: Int? get() = closestHour?.windSpeed
    val windDegrees: Int? get() = closestHour?.windDirectionDegrees

    // ── Condiciones por día ─────────────────────────────────────────────────

    /**
     * Peor índice de calidad del aire de cada día.
     *
     * Se agrega aquí porque su API no tiene bloque diario, solo la serie
     * horaria. Se toma el **máximo** y no la media: de un día lo que importa
     * es lo malo que llegó a ponerse, que es además como se publica
     * habitualmente el índice europeo.
     *
     * Los días que el modelo no alcanza —a partir del quinto, más o menos—
     * simplemente no aparecen en el mapa, y su casilla enseña un guion.
     */
    val airQualityByDay: Map<LocalDate, Int>
        get() = airQuality
            .groupBy { it.dateTime.toLocalDate() }
            .mapValues { (_, samples) -> samples.maxOf { it.europeanAqi } }

    val hasData: Boolean get() = daily.isNotEmpty() && hourly.isNotEmpty()

    /** Máxima y mínima de hoy; si hoy no está, se usa el primer día. */
    val todayRange: Pair<Int?, Int?>
        get() {
            if (daily.isEmpty()) return null to null
            val today = nowThere.toLocalDate()
            val day = daily.firstOrNull { it.date == today } ?: daily.first()
            return day.tempMax to day.tempMin
        }

    /**
     * Antigüedad de los datos, para el indicador de frescura.
     *
     * Devuelve la magnitud y su unidad, no la frase: "hace 5 min" y "5 min
     * ago" no se diferencian solo en las palabras, también en el orden, así
     * que armarla aquí impediría traducirla. `null` si nunca se ha cargado.
     */
    val dataAge: DataAge?
        get() {
            val updated = lastUpdated ?: return null
            val diff = Duration.between(updated, LocalDateTime.now())
            return when {
                diff.toDays() >= 1 -> DataAge(AgeUnit.DAYS, diff.toDays())
                diff.toHours() >= 1 -> DataAge(AgeUnit.HOURS, diff.toHours())
                diff.toMinutes() >= 1 -> DataAge(AgeUnit.MINUTES, diff.toMinutes())
                else -> DataAge(AgeUnit.JUST_NOW, 0)
            }
        }
}

/** Antigüedad de los datos: cuánto y de qué. */
data class DataAge(val unit: AgeUnit, val amount: Long)

enum class AgeUnit { JUST_NOW, MINUTES, HOURS, DAYS }

/** Cada cuánto se refresca el tiempo en segundo plano. */
enum class BackgroundInterval(val hours: Long?) {
    OFF(null),
    EVERY_12H(12),
    EVERY_24H(24),
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
    /**
     * Contador que avanza cada minuto.
     *
     * No lo lee nadie: existe para que el estado se vuelva a emitir y la
     * interfaz relea las fases solares, que dependen de la hora y no de
     * ningún dato guardado. Antes aquí había una `sunPhase` global, que es
     * justo lo que no puede haber con ciudades en husos distintos.
     */
    val clockTick: Int = 0,
    val isRefreshing: Boolean = false,
    val isLocating: Boolean = false,
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    /** Si los resultados se ordenan por cercanía en vez de por relevancia. */
    val searchNearby: Boolean = false,
    val backgroundInterval: BackgroundInterval = BackgroundInterval.OFF,
    /** Avisar por notificación de los avisos nuevos. Solo España. */
    val alertNotifications: Boolean = false,
    /** Unidades con las que se pintan temperaturas y viento. */
    val units: Units = Units(),
    val isInitialized: Boolean = false,
) {
    val currentLocation: SavedLocation? get() = locations.getOrNull(currentIndex)

    val currentMunicipioId: String get() = currentLocation?.locationId.orEmpty()

    fun cityAt(index: Int): CityWeather? =
        locations.getOrNull(index)?.let { cities[it.locationId] }

    val currentCity: CityWeather? get() = cityAt(currentIndex)
}
