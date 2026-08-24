package com.nubo.nubo.domain.weather

/** Clasificación del estado del cielo en 3 categorías para el fondo dinámico. */
enum class SkyCondition {
    /** Despejado o poco nuboso (WMO 0, 1). */
    CLEAR,

    /** Intervalos nubosos, cubierto, niebla (WMO 2, 3, 45, 48). */
    PARTLY_CLOUDY,

    /** Precipitación: lluvia, llovizna, nieve, tormenta, granizo (WMO 51+). */
    OVERCAST,
    ;

    companion object {
        /** Clasifica un código WMO (con posible sufijo 'n' de noche). */
        fun fromCode(code: String?): SkyCondition {
            val numeric = WeatherCodeGroup.numericValue(code) ?: return CLEAR
            return when {
                numeric <= 1 -> CLEAR
                numeric <= 3 || numeric == 45 || numeric == 48 -> PARTLY_CLOUDY
                else -> OVERCAST
            }
        }
    }
}

/** Fase solar del momento actual, que decide el gradiente de fondo. */
enum class SunPhase { NIGHT, SUNRISE, DAY, SUNSET }
