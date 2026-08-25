package com.nubo.nubo.data.repository

import com.nubo.nubo.data.location.LocationService
import com.nubo.nubo.data.remote.AlertService
import com.nubo.nubo.data.remote.AemetZoneService
import com.nubo.nubo.data.remote.Coordinates
import com.nubo.nubo.data.remote.GeocodingApi
import com.nubo.nubo.data.remote.AirQualityApi
import com.nubo.nubo.data.remote.OpenMeteoApi
import com.nubo.nubo.data.remote.ReverseGeocodingApi
import com.nubo.nubo.domain.model.AirQualityForecast
import com.nubo.nubo.domain.model.DailyForecast
import com.nubo.nubo.domain.model.HourlyForecast
import com.nubo.nubo.domain.model.SavedLocation
import com.nubo.nubo.domain.model.WeatherAlert
import com.nubo.nubo.domain.model.zoneOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * Predicción resuelta.
 *
 * Incluye el JSON crudo porque la caché lo persiste tal cual, sin reparsear.
 */
data class WeatherForecastResult(
    val daily: List<DailyForecast>,
    val hourly: List<HourlyForecast>,
    val rawJson: JSONObject,
    /** Zona horaria IANA del sitio, tal y como la devuelve Open-Meteo. */
    val timeZone: String? = null,
    /** Serie horaria de calidad del aire; vacía si su endpoint falló. */
    val airQuality: List<AirQualityForecast> = emptyList(),
    /** JSON crudo de la calidad del aire, para la caché. `null` si falló. */
    val airQualityRawJson: JSONObject? = null,
)

class WeatherRepositoryException(message: String) : Exception(message)

// ── Localización ────────────────────────────────────────────────────────────

interface LocationRepository {
    /** Localizaciones cuyo nombre casa con [query], en todo el mundo. */
    suspend fun searchByName(query: String): List<SavedLocation>

    /** Sitio habitado más cercano a unas coordenadas. */
    suspend fun findNearest(lat: Double, lon: Double): SavedLocation?

    suspend fun getCurrentPosition(): Coordinates

    /**
     * Zona de aviso de AEMET de una localización española, o `null` si está
     * fuera de España. Ver [AemetZoneService].
     */
    suspend fun zonaAvisoFor(location: SavedLocation): String?

    /**
     * Completa una localización guardada en el formato anterior, que solo
     * tenía el código INE, con sus coordenadas y su zona horaria.
     */
    suspend fun resolveLegacy(location: SavedLocation): SavedLocation?
}

class LocationRepositoryImpl(
    private val geocoding: GeocodingApi,
    private val reverseGeocoding: ReverseGeocodingApi,
    private val zoneService: AemetZoneService,
    private val locationService: LocationService,
) : LocationRepository {

    override suspend fun searchByName(query: String) = geocoding.search(query)

    override suspend fun findNearest(lat: Double, lon: Double) =
        reverseGeocoding.findPlace(lat, lon)

    override suspend fun getCurrentPosition() = locationService.getCurrentPosition()

    override suspend fun zonaAvisoFor(location: SavedLocation): String? {
        if (!location.isInSpain) return null

        // Las guardadas de siempre llevan el código INE, que da la zona
        // exacta; las que vienen de Open-Meteo hay que resolverlas por
        // cercanía, que es lo más fino que se puede hacer sin ese código.
        if (location.isLegacyIne) return zoneService.zonaForIne(location.locationId)

        val lat = location.lat ?: return null
        val lon = location.lon ?: return null
        return zoneService.zonaNear(lat, lon)
    }

    override suspend fun resolveLegacy(location: SavedLocation): SavedLocation? {
        if (!location.isLegacyIne) return null

        val coords = zoneService.coordinatesForIne(location.locationId) ?: return null
        return location.copy(
            lat = coords.lat,
            lon = coords.lon,
            countryCode = SPAIN,
            timeZone = spanishZoneFor(location.locationId),
        )
    }

    private companion object {
        const val SPAIN = "ES"

        /** Provincias INE de Las Palmas y Santa Cruz de Tenerife. */
        val CANARY_PROVINCES = setOf("35", "38")

        /**
         * España tiene dos husos, y la app llevaba desde siempre asumiendo
         * uno: quien mirase Canarias desde la península veía las horas
         * corridas sesenta minutos.
         */
        fun spanishZoneFor(ineId: String): String =
            if (ineId.take(2) in CANARY_PROVINCES) "Atlantic/Canary" else "Europe/Madrid"
    }
}

// ── Tiempo ──────────────────────────────────────────────────────────────────

interface WeatherRepository {
    suspend fun getForecast(location: SavedLocation): WeatherForecastResult
}

class WeatherRepositoryImpl(
    private val api: OpenMeteoApi = OpenMeteoApi(),
    private val airQualityApi: AirQualityApi = AirQualityApi(),
) : WeatherRepository {

    override suspend fun getForecast(location: SavedLocation): WeatherForecastResult =
        coroutineScope {
            val lat = location.lat
            val lon = location.lon
            if (lat == null || lon == null) {
                throw WeatherRepositoryException(
                    "No se encontraron coordenadas para la ubicación",
                )
            }

            // Las dos peticiones salen a la vez: encadenarlas duplicaría el
            // tiempo de refresco de cada ciudad por un dato secundario.
            val forecastJob = async { api.fetchForecast(lat, lon) }
            val airJob = async { runCatching { airQualityApi.fetchAirQuality(lat, lon) } }

            val rawJson = forecastJob.await()
            // La calidad del aire es prescindible: si su endpoint cae, la
            // predicción sigue llegando y la casilla queda vacía.
            val airJson = airJob.await().getOrNull()

            // La zona horaria de la respuesta manda sobre la del buscador: es
            // la que corresponde a las marcas de tiempo que vienen en este JSON.
            val timeZone = rawJson.optString("timezone").takeIf { it.isNotBlank() }

            WeatherForecastResult(
                daily = DailyForecast.fromOpenMeteoJson(rawJson),
                hourly = HourlyForecast.fromOpenMeteoJson(rawJson),
                rawJson = rawJson,
                timeZone = timeZone,
                airQuality = airJson?.let { AirQualityForecast.fromOpenMeteoJson(it) }.orEmpty(),
                airQualityRawJson = airJson,
            )
        }
}

// ── Avisos ──────────────────────────────────────────────────────────────────

interface AlertRepository {
    suspend fun getAlerts(location: SavedLocation): List<WeatherAlert>
}

/**
 * Avisos de la localización, si los hay para su país.
 *
 * Hoy la única fuente es AEMET, así que fuera de España no hay avisos y la
 * lista viene vacía. Ahí es donde entrarían MeteoAlarm para Europa o el NWS
 * para Estados Unidos.
 */
class AlertRepositoryImpl(
    private val locationRepository: LocationRepository,
    private val alertService: AlertService = AlertService(),
) : AlertRepository {
    override suspend fun getAlerts(location: SavedLocation): List<WeatherAlert> {
        val zona = locationRepository.zonaAvisoFor(location) ?: return emptyList()
        return alertService.fetchAlerts(zona, zoneOf(location.timeZone))
    }
}
