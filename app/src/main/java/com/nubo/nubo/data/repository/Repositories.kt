package com.nubo.nubo.data.repository

import com.nubo.nubo.data.location.LocationService
import com.nubo.nubo.data.remote.AlertService
import com.nubo.nubo.data.remote.Coordinates
import com.nubo.nubo.data.remote.MunicipioSearchService
import com.nubo.nubo.data.remote.OpenMeteoApi
import com.nubo.nubo.domain.model.DailyForecast
import com.nubo.nubo.domain.model.HourlyForecast
import com.nubo.nubo.domain.model.SavedLocation
import com.nubo.nubo.domain.model.WeatherAlert
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
)

class WeatherRepositoryException(message: String) : Exception(message)

// ── Localización ────────────────────────────────────────────────────────────

interface LocationRepository {
    suspend fun getCoordinates(municipioId: String): Coordinates?
    suspend fun findNearest(lat: Double, lon: Double): SavedLocation?
    suspend fun searchByName(query: String): List<SavedLocation>
    suspend fun getCurrentPosition(): Coordinates
}

class LocationRepositoryImpl(
    private val searchService: MunicipioSearchService,
    private val locationService: LocationService,
) : LocationRepository {
    override suspend fun getCoordinates(municipioId: String) =
        searchService.getCoordinates(municipioId)

    override suspend fun findNearest(lat: Double, lon: Double) =
        searchService.findNearestMunicipio(lat, lon)

    override suspend fun searchByName(query: String) = searchService.searchByName(query)

    override suspend fun getCurrentPosition() = locationService.getCurrentPosition()
}

// ── Tiempo ──────────────────────────────────────────────────────────────────

interface WeatherRepository {
    suspend fun getForecast(municipioId: String): WeatherForecastResult
}

class WeatherRepositoryImpl(
    private val locationRepository: LocationRepository,
    private val api: OpenMeteoApi = OpenMeteoApi(),
) : WeatherRepository {

    override suspend fun getForecast(municipioId: String): WeatherForecastResult {
        val coords = locationRepository.getCoordinates(municipioId)
            ?: throw WeatherRepositoryException(
                "No se encontraron coordenadas para la ubicación",
            )

        val rawJson = api.fetchForecast(coords.lat, coords.lon)
        return WeatherForecastResult(
            daily = DailyForecast.fromOpenMeteoJson(rawJson),
            hourly = HourlyForecast.fromOpenMeteoJson(rawJson),
            rawJson = rawJson,
        )
    }
}

// ── Avisos ──────────────────────────────────────────────────────────────────

interface AlertRepository {
    suspend fun getAlerts(municipioId: String): List<WeatherAlert>
}

class AlertRepositoryImpl(
    private val alertService: AlertService = AlertService(),
) : AlertRepository {
    override suspend fun getAlerts(municipioId: String) = alertService.fetchAlerts(municipioId)
}
