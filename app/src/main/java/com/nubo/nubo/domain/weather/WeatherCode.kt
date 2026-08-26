package com.nubo.nubo.domain.weather

/**
 * Icono con el que se representa un fenómeno.
 *
 * Es un enum del dominio y no un icono de Compose a propósito: así los modelos
 * y sus tests no dependen de la capa de UI. El mapeo a un vector concreto se
 * hace en `ui/WeatherIcons.kt`.
 */
enum class WeatherIcon {
    SUN,
    MOON,
    CLOUD_SUN,
    CLOUD_MOON,
    CLOUD,
    CLOUD_DRIZZLE,
    CLOUD_RAIN_LIGHT,
    CLOUD_RAIN,
    CLOUD_RAIN_HEAVY,
    CLOUD_SNOW_LIGHT,
    CLOUD_SNOW,
    CLOUD_SNOW_HEAVY,
    CLOUD_LIGHTNING,
    CLOUD_FOG,
    UNKNOWN,
}

/**
 * Descripción e icono de cada código WMO que devuelve Open-Meteo.
 *
 * El sufijo 'n' marca la variante nocturna, que solo cambia en los códigos
 * donde se ve el astro (despejado y poco nuboso).
 *
 * La intensidad **sí** llega al icono: 61/63/65 y 80/81/82 se pintan distinto,
 * igual que 71/73/75. Antes compartían dibujo y el texto era el único sitio
 * donde se distinguía una llovizna de un diluvio — un texto que solo aparece
 * en la cabecera de la ciudad, mientras que las filas de días y el carrusel de
 * horas enseñan el icono a secas.
 */
data class WeatherCode(val description: WeatherDescription, val icon: WeatherIcon) {

    companion object {
        private val UNKNOWN = WeatherCode(WeatherDescription.UNKNOWN, WeatherIcon.UNKNOWN)

        val codes: Map<String, WeatherCode> = buildMap {
            // 0: Despejado
            put("0", WeatherCode(WeatherDescription.CLEAR, WeatherIcon.SUN))
            put("0n", WeatherCode(WeatherDescription.CLEAR, WeatherIcon.MOON))

            // 1: Principalmente despejado
            put("1", WeatherCode(WeatherDescription.MOSTLY_CLEAR, WeatherIcon.CLOUD_SUN))
            put("1n", WeatherCode(WeatherDescription.MOSTLY_CLEAR, WeatherIcon.CLOUD_MOON))

            // 2: Parcialmente nublado
            put("2", WeatherCode(WeatherDescription.PARTLY_CLOUDY, WeatherIcon.CLOUD_SUN))
            put("2n", WeatherCode(WeatherDescription.PARTLY_CLOUDY, WeatherIcon.CLOUD_MOON))

            // 3: Cubierto
            put("3", WeatherCode(WeatherDescription.OVERCAST, WeatherIcon.CLOUD))
            put("3n", WeatherCode(WeatherDescription.OVERCAST, WeatherIcon.CLOUD))

            // 45, 48: Niebla
            put("45", WeatherCode(WeatherDescription.FOG, WeatherIcon.CLOUD_FOG))
            put("45n", WeatherCode(WeatherDescription.FOG, WeatherIcon.CLOUD_FOG))
            put("48", WeatherCode(WeatherDescription.RIME_FOG, WeatherIcon.CLOUD_FOG))
            put("48n", WeatherCode(WeatherDescription.RIME_FOG, WeatherIcon.CLOUD_FOG))

            // 51, 53, 55: Llovizna
            put("51", WeatherCode(WeatherDescription.DRIZZLE_LIGHT, WeatherIcon.CLOUD_DRIZZLE))
            put("51n", WeatherCode(WeatherDescription.DRIZZLE_LIGHT, WeatherIcon.CLOUD_DRIZZLE))
            put("53", WeatherCode(WeatherDescription.DRIZZLE, WeatherIcon.CLOUD_DRIZZLE))
            put("53n", WeatherCode(WeatherDescription.DRIZZLE, WeatherIcon.CLOUD_DRIZZLE))
            put("55", WeatherCode(WeatherDescription.DRIZZLE_DENSE, WeatherIcon.CLOUD_DRIZZLE))
            put("55n", WeatherCode(WeatherDescription.DRIZZLE_DENSE, WeatherIcon.CLOUD_DRIZZLE))

            // 56, 57: Llovizna helada
            put("56", WeatherCode(WeatherDescription.FREEZING_DRIZZLE_LIGHT, WeatherIcon.CLOUD_DRIZZLE))
            put("56n", WeatherCode(WeatherDescription.FREEZING_DRIZZLE_LIGHT, WeatherIcon.CLOUD_DRIZZLE))
            put("57", WeatherCode(WeatherDescription.FREEZING_DRIZZLE_DENSE, WeatherIcon.CLOUD_DRIZZLE))
            put("57n", WeatherCode(WeatherDescription.FREEZING_DRIZZLE_DENSE, WeatherIcon.CLOUD_DRIZZLE))

            // 61, 63, 65: Lluvia
            put("61", WeatherCode(WeatherDescription.RAIN_LIGHT, WeatherIcon.CLOUD_RAIN_LIGHT))
            put("61n", WeatherCode(WeatherDescription.RAIN_LIGHT, WeatherIcon.CLOUD_RAIN_LIGHT))
            put("63", WeatherCode(WeatherDescription.RAIN, WeatherIcon.CLOUD_RAIN))
            put("63n", WeatherCode(WeatherDescription.RAIN, WeatherIcon.CLOUD_RAIN))
            put("65", WeatherCode(WeatherDescription.RAIN_HEAVY, WeatherIcon.CLOUD_RAIN_HEAVY))
            put("65n", WeatherCode(WeatherDescription.RAIN_HEAVY, WeatherIcon.CLOUD_RAIN_HEAVY))

            // 66, 67: Lluvia helada
            put("66", WeatherCode(WeatherDescription.FREEZING_RAIN_LIGHT, WeatherIcon.CLOUD_RAIN_LIGHT))
            put("66n", WeatherCode(WeatherDescription.FREEZING_RAIN_LIGHT, WeatherIcon.CLOUD_RAIN_LIGHT))
            put("67", WeatherCode(WeatherDescription.FREEZING_RAIN_HEAVY, WeatherIcon.CLOUD_RAIN_HEAVY))
            put("67n", WeatherCode(WeatherDescription.FREEZING_RAIN_HEAVY, WeatherIcon.CLOUD_RAIN_HEAVY))

            // 71, 73, 75: Nieve
            put("71", WeatherCode(WeatherDescription.SNOW_LIGHT, WeatherIcon.CLOUD_SNOW_LIGHT))
            put("71n", WeatherCode(WeatherDescription.SNOW_LIGHT, WeatherIcon.CLOUD_SNOW_LIGHT))
            put("73", WeatherCode(WeatherDescription.SNOW, WeatherIcon.CLOUD_SNOW))
            put("73n", WeatherCode(WeatherDescription.SNOW, WeatherIcon.CLOUD_SNOW))
            put("75", WeatherCode(WeatherDescription.SNOW_HEAVY, WeatherIcon.CLOUD_SNOW_HEAVY))
            put("75n", WeatherCode(WeatherDescription.SNOW_HEAVY, WeatherIcon.CLOUD_SNOW_HEAVY))

            // 77: Granizo menudo
            put("77", WeatherCode(WeatherDescription.SNOW_GRAINS, WeatherIcon.CLOUD_SNOW_LIGHT))
            put("77n", WeatherCode(WeatherDescription.SNOW_GRAINS, WeatherIcon.CLOUD_SNOW_LIGHT))

            // 80, 81, 82: Chubascos de lluvia
            put("80", WeatherCode(WeatherDescription.SHOWERS_LIGHT, WeatherIcon.CLOUD_RAIN_LIGHT))
            put("80n", WeatherCode(WeatherDescription.SHOWERS_LIGHT, WeatherIcon.CLOUD_RAIN_LIGHT))
            put("81", WeatherCode(WeatherDescription.SHOWERS, WeatherIcon.CLOUD_RAIN))
            put("81n", WeatherCode(WeatherDescription.SHOWERS, WeatherIcon.CLOUD_RAIN))
            put("82", WeatherCode(WeatherDescription.SHOWERS_HEAVY, WeatherIcon.CLOUD_RAIN_HEAVY))
            put("82n", WeatherCode(WeatherDescription.SHOWERS_HEAVY, WeatherIcon.CLOUD_RAIN_HEAVY))

            // 85, 86: Chubascos de nieve
            put("85", WeatherCode(WeatherDescription.SNOW_SHOWERS_LIGHT, WeatherIcon.CLOUD_SNOW_LIGHT))
            put("85n", WeatherCode(WeatherDescription.SNOW_SHOWERS_LIGHT, WeatherIcon.CLOUD_SNOW_LIGHT))
            put("86", WeatherCode(WeatherDescription.SNOW_SHOWERS_HEAVY, WeatherIcon.CLOUD_SNOW_HEAVY))
            put("86n", WeatherCode(WeatherDescription.SNOW_SHOWERS_HEAVY, WeatherIcon.CLOUD_SNOW_HEAVY))

            // 95: Tormenta
            put("95", WeatherCode(WeatherDescription.THUNDERSTORM, WeatherIcon.CLOUD_LIGHTNING))
            put("95n", WeatherCode(WeatherDescription.THUNDERSTORM, WeatherIcon.CLOUD_LIGHTNING))

            // 96, 99: Tormenta con granizo
            put("96", WeatherCode(WeatherDescription.THUNDERSTORM_HAIL, WeatherIcon.CLOUD_LIGHTNING))
            put("96n", WeatherCode(WeatherDescription.THUNDERSTORM_HAIL, WeatherIcon.CLOUD_LIGHTNING))
            put("99", WeatherCode(WeatherDescription.THUNDERSTORM_HAIL_HEAVY, WeatherIcon.CLOUD_LIGHTNING))
            put("99n", WeatherCode(WeatherDescription.THUNDERSTORM_HAIL_HEAVY, WeatherIcon.CLOUD_LIGHTNING))
        }

        /** Busca un código; devuelve "Desconocido" si no está mapeado. */
        fun fromCode(code: String?): WeatherCode {
            if (code.isNullOrEmpty()) return UNKNOWN
            return codes[code] ?: UNKNOWN
        }
    }
}
