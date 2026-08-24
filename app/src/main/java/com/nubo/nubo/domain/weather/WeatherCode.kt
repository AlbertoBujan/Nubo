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
    CLOUD_RAIN,
    CLOUD_SNOW,
    CLOUD_LIGHTNING,
    CLOUD_FOG,
    UNKNOWN,
}

/**
 * Descripción e icono de cada código WMO que devuelve Open-Meteo.
 *
 * El sufijo 'n' marca la variante nocturna, que solo cambia en los códigos
 * donde se ve el astro (despejado y poco nuboso).
 */
data class WeatherCode(val description: String, val icon: WeatherIcon) {

    companion object {
        private val UNKNOWN = WeatherCode("Desconocido", WeatherIcon.UNKNOWN)

        val codes: Map<String, WeatherCode> = buildMap {
            // 0: Despejado
            put("0", WeatherCode("Despejado", WeatherIcon.SUN))
            put("0n", WeatherCode("Despejado", WeatherIcon.MOON))

            // 1: Principalmente despejado
            put("1", WeatherCode("Poco nuboso", WeatherIcon.CLOUD_SUN))
            put("1n", WeatherCode("Poco nuboso", WeatherIcon.CLOUD_MOON))

            // 2: Parcialmente nublado
            put("2", WeatherCode("Intervalos nubosos", WeatherIcon.CLOUD_SUN))
            put("2n", WeatherCode("Intervalos nubosos", WeatherIcon.CLOUD_MOON))

            // 3: Cubierto
            put("3", WeatherCode("Cubierto", WeatherIcon.CLOUD))
            put("3n", WeatherCode("Cubierto", WeatherIcon.CLOUD))

            // 45, 48: Niebla
            put("45", WeatherCode("Niebla", WeatherIcon.CLOUD_FOG))
            put("45n", WeatherCode("Niebla", WeatherIcon.CLOUD_FOG))
            put("48", WeatherCode("Niebla escarchada", WeatherIcon.CLOUD_FOG))
            put("48n", WeatherCode("Niebla escarchada", WeatherIcon.CLOUD_FOG))

            // 51, 53, 55: Llovizna
            put("51", WeatherCode("Llovizna ligera", WeatherIcon.CLOUD_DRIZZLE))
            put("51n", WeatherCode("Llovizna ligera", WeatherIcon.CLOUD_DRIZZLE))
            put("53", WeatherCode("Llovizna moderada", WeatherIcon.CLOUD_DRIZZLE))
            put("53n", WeatherCode("Llovizna moderada", WeatherIcon.CLOUD_DRIZZLE))
            put("55", WeatherCode("Llovizna densa", WeatherIcon.CLOUD_DRIZZLE))
            put("55n", WeatherCode("Llovizna densa", WeatherIcon.CLOUD_DRIZZLE))

            // 56, 57: Llovizna helada
            put("56", WeatherCode("Llovizna helada ligera", WeatherIcon.CLOUD_DRIZZLE))
            put("56n", WeatherCode("Llovizna helada ligera", WeatherIcon.CLOUD_DRIZZLE))
            put("57", WeatherCode("Llovizna helada densa", WeatherIcon.CLOUD_DRIZZLE))
            put("57n", WeatherCode("Llovizna helada densa", WeatherIcon.CLOUD_DRIZZLE))

            // 61, 63, 65: Lluvia
            put("61", WeatherCode("Lluvia débil", WeatherIcon.CLOUD_RAIN))
            put("61n", WeatherCode("Lluvia débil", WeatherIcon.CLOUD_RAIN))
            put("63", WeatherCode("Lluvia moderada", WeatherIcon.CLOUD_RAIN))
            put("63n", WeatherCode("Lluvia moderada", WeatherIcon.CLOUD_RAIN))
            put("65", WeatherCode("Lluvia fuerte", WeatherIcon.CLOUD_RAIN))
            put("65n", WeatherCode("Lluvia fuerte", WeatherIcon.CLOUD_RAIN))

            // 66, 67: Lluvia helada
            put("66", WeatherCode("Lluvia helada débil", WeatherIcon.CLOUD_RAIN))
            put("66n", WeatherCode("Lluvia helada débil", WeatherIcon.CLOUD_RAIN))
            put("67", WeatherCode("Lluvia helada fuerte", WeatherIcon.CLOUD_RAIN))
            put("67n", WeatherCode("Lluvia helada fuerte", WeatherIcon.CLOUD_RAIN))

            // 71, 73, 75: Nieve
            put("71", WeatherCode("Nieve débil", WeatherIcon.CLOUD_SNOW))
            put("71n", WeatherCode("Nieve débil", WeatherIcon.CLOUD_SNOW))
            put("73", WeatherCode("Nieve moderada", WeatherIcon.CLOUD_SNOW))
            put("73n", WeatherCode("Nieve moderada", WeatherIcon.CLOUD_SNOW))
            put("75", WeatherCode("Nieve fuerte", WeatherIcon.CLOUD_SNOW))
            put("75n", WeatherCode("Nieve fuerte", WeatherIcon.CLOUD_SNOW))

            // 77: Granizo menudo
            put("77", WeatherCode("Granizo menudo", WeatherIcon.CLOUD_SNOW))
            put("77n", WeatherCode("Granizo menudo", WeatherIcon.CLOUD_SNOW))

            // 80, 81, 82: Chubascos de lluvia
            put("80", WeatherCode("Chubascos débiles", WeatherIcon.CLOUD_RAIN))
            put("80n", WeatherCode("Chubascos débiles", WeatherIcon.CLOUD_RAIN))
            put("81", WeatherCode("Chubascos moderados", WeatherIcon.CLOUD_RAIN))
            put("81n", WeatherCode("Chubascos moderados", WeatherIcon.CLOUD_RAIN))
            put("82", WeatherCode("Chubascos fuertes", WeatherIcon.CLOUD_RAIN))
            put("82n", WeatherCode("Chubascos fuertes", WeatherIcon.CLOUD_RAIN))

            // 85, 86: Chubascos de nieve
            put("85", WeatherCode("Chubascos de nieve débiles", WeatherIcon.CLOUD_SNOW))
            put("85n", WeatherCode("Chubascos de nieve débiles", WeatherIcon.CLOUD_SNOW))
            put("86", WeatherCode("Chubascos de nieve fuertes", WeatherIcon.CLOUD_SNOW))
            put("86n", WeatherCode("Chubascos de nieve fuertes", WeatherIcon.CLOUD_SNOW))

            // 95: Tormenta
            put("95", WeatherCode("Tormenta", WeatherIcon.CLOUD_LIGHTNING))
            put("95n", WeatherCode("Tormenta", WeatherIcon.CLOUD_LIGHTNING))

            // 96, 99: Tormenta con granizo
            put("96", WeatherCode("Tormenta con granizo", WeatherIcon.CLOUD_LIGHTNING))
            put("96n", WeatherCode("Tormenta con granizo", WeatherIcon.CLOUD_LIGHTNING))
            put("99", WeatherCode("Tormenta con granizo fuerte", WeatherIcon.CLOUD_LIGHTNING))
            put("99n", WeatherCode("Tormenta con granizo fuerte", WeatherIcon.CLOUD_LIGHTNING))
        }

        /** Busca un código; devuelve "Desconocido" si no está mapeado. */
        fun fromCode(code: String?): WeatherCode {
            if (code.isNullOrEmpty()) return UNKNOWN
            return codes[code] ?: UNKNOWN
        }
    }
}
