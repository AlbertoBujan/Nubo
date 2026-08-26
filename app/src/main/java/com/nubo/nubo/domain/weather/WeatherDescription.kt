package com.nubo.nubo.domain.weather

/**
 * Fenómeno con el que se describe el cielo.
 *
 * Es un enum del dominio y no el texto ya escrito, por la misma razón que
 * [WeatherIcon]: aquí no puede entrar Android, y traducir exige recursos. El
 * texto de cada valor se resuelve en `ui/WeatherLabels.kt`.
 */
enum class WeatherDescription {
    SNOW_SHOWERS_LIGHT,
    SNOW_SHOWERS_HEAVY,
    SHOWERS_LIGHT,
    SHOWERS_HEAVY,
    SHOWERS,
    OVERCAST,
    UNKNOWN,
    CLEAR,
    SNOW_GRAINS,
    PARTLY_CLOUDY,
    DRIZZLE_DENSE,
    FREEZING_DRIZZLE_DENSE,
    FREEZING_DRIZZLE_LIGHT,
    DRIZZLE_LIGHT,
    DRIZZLE,
    RAIN_LIGHT,
    RAIN_HEAVY,
    FREEZING_RAIN_LIGHT,
    FREEZING_RAIN_HEAVY,
    RAIN,
    FOG,
    RIME_FOG,
    SNOW_LIGHT,
    SNOW_HEAVY,
    SNOW,
    MOSTLY_CLEAR,
    THUNDERSTORM,
    THUNDERSTORM_HAIL,
    THUNDERSTORM_HAIL_HEAVY,
}
