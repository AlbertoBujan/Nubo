package com.nubo.nubo.ui.weather

import com.nubo.nubo.domain.astro.MoonData
import com.nubo.nubo.domain.astro.SunTimes
import com.nubo.nubo.domain.model.DailyForecast
import com.nubo.nubo.domain.model.HourlyForecast
import com.nubo.nubo.domain.model.SavedLocation
import com.nubo.nubo.domain.model.WeatherAlert
import com.nubo.nubo.domain.weather.SkyCondition
import com.nubo.nubo.domain.weather.SunPhase
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Estado de una ciudad concreta. */
data class CityWeather(
    val municipioId: String,
    val name: String,
    val isLoading: Boolean = false,
    val error: String? = null,
    val daily: List<DailyForecast> = emptyList(),
    val hourly: List<HourlyForecast> = emptyList(),
    val alerts: List<WeatherAlert> = emptyList(),
    val sunTimes: SunTimes? = null,
    val moonData: MoonData? = null,
    val lastUpdated: LocalDateTime? = null,
) {
    /** Hora de predicción más próxima al momento actual. */
    private val closestHour: HourlyForecast?
        get() {
            if (hourly.isEmpty()) return null
            val now = LocalDateTime.now()
            return hourly.minByOrNull {
                Duration.between(it.dateTime, now).abs().toMillis()
            }
        }

    val currentTemperature: Int? get() = closestHour?.temperature
    val skyCode: String get() = closestHour?.skyStateCode.orEmpty()
    val skyDescription: String get() = closestHour?.skyDescription.orEmpty()
    val skyCondition: SkyCondition get() = SkyCondition.fromCode(skyCode)

    val hasData: Boolean get() = daily.isNotEmpty() && hourly.isNotEmpty()

    /** Máxima y mínima de hoy; si hoy no está, se usa el primer día. */
    val todayRange: Pair<Int?, Int?>
        get() {
            if (daily.isEmpty()) return null to null
            val today = LocalDate.now()
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

/** Estado completo de la pantalla principal. */
data class WeatherUiState(
    val locations: List<SavedLocation> = emptyList(),
    val cities: Map<String, CityWeather> = emptyMap(),
    val currentIndex: Int = 0,
    val sunPhase: SunPhase = SunPhase.DAY,
    val isRefreshing: Boolean = false,
    val isLocating: Boolean = false,
    val searchResults: List<SavedLocation> = emptyList(),
    val isSearching: Boolean = false,
    val backgroundInterval: BackgroundInterval = BackgroundInterval.OFF,
    val isInitialized: Boolean = false,
) {
    val currentLocation: SavedLocation? get() = locations.getOrNull(currentIndex)

    val currentMunicipioId: String get() = currentLocation?.municipioId.orEmpty()

    fun cityAt(index: Int): CityWeather? =
        locations.getOrNull(index)?.let { cities[it.municipioId] }

    val currentCity: CityWeather? get() = cityAt(currentIndex)
}
