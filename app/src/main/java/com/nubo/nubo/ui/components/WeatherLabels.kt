package com.nubo.nubo.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nubo.nubo.R
import com.nubo.nubo.domain.astro.MoonPhase
import com.nubo.nubo.domain.model.AlertLevel
import com.nubo.nubo.domain.model.AlertType
import com.nubo.nubo.domain.model.CityError
import com.nubo.nubo.domain.model.ErrorReason
import com.nubo.nubo.domain.weather.AirQualityBand
import com.nubo.nubo.domain.weather.UvBand
import com.nubo.nubo.domain.weather.WeatherDescription
import com.nubo.nubo.ui.weather.AgeUnit
import com.nubo.nubo.ui.weather.BackgroundInterval
import com.nubo.nubo.ui.weather.DataAge
import com.nubo.nubo.domain.model.DistanceUnit
import com.nubo.nubo.domain.model.SpeedUnit
import com.nubo.nubo.domain.model.TemperatureUnit

/**
 * Texto de cada valor del dominio.
 *
 * El dominio no depende de Android —es lo que permite probarlo sin
 * instrumentación— así que no puede alcanzar los recursos. Devuelve enums y
 * aquí se les pone nombre, igual que `WeatherIcons.kt` les pone dibujo. Ese
 * reparto es además lo que hace posible traducir la app sin tocar la lógica.
 */
@get:StringRes
val WeatherDescription.labelRes: Int
    get() = when (this) {
        WeatherDescription.CLEAR -> R.string.sky_clear
        WeatherDescription.MOSTLY_CLEAR -> R.string.sky_mostly_clear
        WeatherDescription.PARTLY_CLOUDY -> R.string.sky_partly_cloudy
        WeatherDescription.OVERCAST -> R.string.sky_overcast
        WeatherDescription.FOG -> R.string.sky_fog
        WeatherDescription.RIME_FOG -> R.string.sky_rime_fog
        WeatherDescription.DRIZZLE_LIGHT -> R.string.sky_drizzle_light
        WeatherDescription.DRIZZLE -> R.string.sky_drizzle
        WeatherDescription.DRIZZLE_DENSE -> R.string.sky_drizzle_dense
        WeatherDescription.FREEZING_DRIZZLE_LIGHT -> R.string.sky_freezing_drizzle_light
        WeatherDescription.FREEZING_DRIZZLE_DENSE -> R.string.sky_freezing_drizzle_dense
        WeatherDescription.RAIN_LIGHT -> R.string.sky_rain_light
        WeatherDescription.RAIN -> R.string.sky_rain
        WeatherDescription.RAIN_HEAVY -> R.string.sky_rain_heavy
        WeatherDescription.FREEZING_RAIN_LIGHT -> R.string.sky_freezing_rain_light
        WeatherDescription.FREEZING_RAIN_HEAVY -> R.string.sky_freezing_rain_heavy
        WeatherDescription.SNOW_LIGHT -> R.string.sky_snow_light
        WeatherDescription.SNOW -> R.string.sky_snow
        WeatherDescription.SNOW_HEAVY -> R.string.sky_snow_heavy
        WeatherDescription.SNOW_GRAINS -> R.string.sky_snow_grains
        WeatherDescription.SHOWERS_LIGHT -> R.string.sky_showers_light
        WeatherDescription.SHOWERS -> R.string.sky_showers
        WeatherDescription.SHOWERS_HEAVY -> R.string.sky_showers_heavy
        WeatherDescription.SNOW_SHOWERS_LIGHT -> R.string.sky_snow_showers_light
        WeatherDescription.SNOW_SHOWERS_HEAVY -> R.string.sky_snow_showers_heavy
        WeatherDescription.THUNDERSTORM -> R.string.sky_thunderstorm
        WeatherDescription.THUNDERSTORM_HAIL -> R.string.sky_thunderstorm_hail
        WeatherDescription.THUNDERSTORM_HAIL_HEAVY -> R.string.sky_thunderstorm_hail_heavy
        WeatherDescription.UNKNOWN -> R.string.sky_unknown
    }

@get:StringRes
val MoonPhase.labelRes: Int
    get() = when (this) {
        MoonPhase.NEW -> R.string.moon_new
        MoonPhase.WAXING_CRESCENT -> R.string.moon_waxing_crescent
        MoonPhase.FIRST_QUARTER -> R.string.moon_first_quarter
        MoonPhase.WAXING_GIBBOUS -> R.string.moon_waxing_gibbous
        MoonPhase.FULL -> R.string.moon_full
        MoonPhase.WANING_GIBBOUS -> R.string.moon_waning_gibbous
        MoonPhase.LAST_QUARTER -> R.string.moon_last_quarter
        MoonPhase.WANING_CRESCENT -> R.string.moon_waning_crescent
    }

@get:StringRes
val AirQualityBand.labelRes: Int
    get() = when (this) {
        AirQualityBand.GOOD -> R.string.aqi_good
        AirQualityBand.FAIR -> R.string.aqi_fair
        AirQualityBand.MODERATE -> R.string.aqi_moderate
        AirQualityBand.POOR -> R.string.aqi_poor
        AirQualityBand.VERY_POOR -> R.string.aqi_very_poor
        AirQualityBand.EXTREME -> R.string.aqi_extreme
    }

@get:StringRes
val UvBand.labelRes: Int
    get() = when (this) {
        UvBand.LOW -> R.string.uv_low
        UvBand.MODERATE -> R.string.uv_moderate
        UvBand.HIGH -> R.string.uv_high
        UvBand.VERY_HIGH -> R.string.uv_very_high
        UvBand.EXTREME -> R.string.uv_extreme
    }

@get:StringRes
val AlertLevel.labelRes: Int
    get() = when (this) {
        AlertLevel.YELLOW -> R.string.level_yellow
        AlertLevel.ORANGE -> R.string.level_orange
        AlertLevel.RED -> R.string.level_red
    }

@get:StringRes
val BackgroundInterval.labelRes: Int
    get() = when (this) {
        BackgroundInterval.OFF -> R.string.interval_off
        BackgroundInterval.EVERY_12H -> R.string.interval_12h
        BackgroundInterval.EVERY_24H -> R.string.interval_24h
    }

/**
 * Frase que se le enseña al usuario para un error de carga.
 *
 * El motivo viaja desde las capas de datos como un enum, y es aquí —la única
 * capa que alcanza los recursos— donde se convierte en algo legible. El código
 * de estado se pega al texto porque un número no necesita traducción y ayuda a
 * saber qué pasó.
 */
@Composable
fun CityError.describe(): String = when (reason) {
    ErrorReason.NETWORK -> stringResource(R.string.error_network)
    ErrorReason.SERVER -> stringResource(R.string.error_server, statusCode ?: 0)
    ErrorReason.UNREADABLE -> stringResource(R.string.error_unreadable)
    ErrorReason.NO_COORDINATES -> stringResource(R.string.error_no_coordinates)
    ErrorReason.LOCATION_DISABLED -> stringResource(R.string.error_location)
    ErrorReason.LOCATION_PERMISSION -> stringResource(R.string.error_location_permission)
    ErrorReason.LOCATION_TIMEOUT -> stringResource(R.string.error_location_timeout)
    ErrorReason.LOCATION_UNKNOWN -> stringResource(R.string.error_location)
    ErrorReason.UNKNOWN -> stringResource(R.string.error_unknown)
}

@get:StringRes
val AlertType.labelRes: Int
    get() = when (this) {
        AlertType.WIND -> R.string.alert_wind
        AlertType.COASTAL -> R.string.alert_coastal
        AlertType.RAIN -> R.string.alert_rain
        AlertType.SNOW -> R.string.alert_snow
        AlertType.THUNDERSTORM -> R.string.alert_thunderstorm
        AlertType.TEMPERATURE -> R.string.alert_temperature
        AlertType.FOG -> R.string.alert_fog
        AlertType.DUST -> R.string.alert_dust
        AlertType.AVALANCHE -> R.string.alert_avalanche
        AlertType.THAW -> R.string.alert_thaw
        AlertType.GENERIC -> R.string.warnings
    }

/** "Hace 5 min", "5 min ago"… según el idioma. */
@Composable
fun DataAge.describe(): String = when (unit) {
    AgeUnit.JUST_NOW -> stringResource(R.string.updated)
    AgeUnit.MINUTES -> stringResource(R.string.updated_minutes_ago, amount.toInt())
    AgeUnit.HOURS -> stringResource(R.string.updated_hours_ago, amount.toInt())
    AgeUnit.DAYS -> stringResource(R.string.updated_days_ago, amount.toInt())
}

/** Cómo se escribe una velocidad en la unidad elegida: "20 km/h", "12 mph". */
val SpeedUnit.labelRes: Int
    @StringRes get() = when (this) {
        SpeedUnit.KMH -> R.string.speed_kmh
        SpeedUnit.MPH -> R.string.speed_mph
    }

/** Nombre de la unidad de velocidad, para elegirla en los ajustes. */
val SpeedUnit.nameRes: Int
    @StringRes get() = when (this) {
        SpeedUnit.KMH -> R.string.unit_kmh
        SpeedUnit.MPH -> R.string.unit_mph
    }

/** Nombre de la escala de temperatura, para elegirla en los ajustes. */
val TemperatureUnit.nameRes: Int
    @StringRes get() = when (this) {
        TemperatureUnit.CELSIUS -> R.string.unit_celsius
        TemperatureUnit.FAHRENHEIT -> R.string.unit_fahrenheit
    }

/** Abreviatura con la que se enseña cuál está elegida. */
val TemperatureUnit.shortRes: Int
    @StringRes get() = when (this) {
        TemperatureUnit.CELSIUS -> R.string.unit_celsius_short
        TemperatureUnit.FAHRENHEIT -> R.string.unit_fahrenheit_short
    }

val SpeedUnit.shortRes: Int
    @StringRes get() = when (this) {
        SpeedUnit.KMH -> R.string.unit_kmh_short
        SpeedUnit.MPH -> R.string.unit_mph_short
    }

/** Nombre de la unidad de distancia, para elegirla en los ajustes. */
val DistanceUnit.nameRes: Int
    @StringRes get() = when (this) {
        DistanceUnit.KILOMETRES -> R.string.unit_kilometres
        DistanceUnit.MILES -> R.string.unit_miles
    }

val DistanceUnit.shortRes: Int
    @StringRes get() = when (this) {
        DistanceUnit.KILOMETRES -> R.string.unit_km_short
        DistanceUnit.MILES -> R.string.unit_miles_short
    }
