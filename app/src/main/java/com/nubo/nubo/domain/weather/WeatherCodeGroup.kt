package com.nubo.nubo.domain.weather

/**
 * Agrupación de códigos WMO por familia de fenómeno, ordenada por severidad.
 *
 * Se usa para decidir qué icono representa mejor un día completo a partir de
 * sus horas (ver [DailyCodeAggregator]): permite contar horas por familia en
 * vez de por código exacto, y comparar cuál es el fenómeno más relevante.
 */
enum class WeatherCodeGroup(
    /** Orden relativo de severidad; a mayor valor, fenómeno más relevante. */
    val severity: Int,
) {
    /** Despejado (WMO 0). */
    CLEAR(0),

    /** Poco nuboso / intervalos nubosos (WMO 1, 2). */
    PARTLY_CLOUDY(1),

    /** Cubierto (WMO 3). */
    CLOUDY(2),

    /** Niebla o niebla escarchada (WMO 45, 48). */
    FOG(3),

    /** Llovizna, incluida la helada (WMO 51-57). */
    DRIZZLE(4),

    /** Lluvia y chubascos, incluida la helada (WMO 61-67, 80-82). */
    RAIN(5),

    /** Nieve, granizo menudo y chubascos de nieve (WMO 71-77, 85, 86). */
    SNOW(6),

    /** Tormenta, con o sin granizo (WMO 95, 96, 99). */
    THUNDER(7),
    ;

    /**
     * Familias que describen un fenómeno concreto, no solo nubosidad.
     *
     * Solo estas pueden adueñarse del icono del día, y únicamente si duran las
     * horas mínimas que exige [minHours].
     */
    val isSignificant: Boolean get() = severity >= FOG.severity

    /**
     * Horas mínimas que debe durar el fenómeno para representar al día entero.
     *
     * Las tormentas bajan el umbral porque son relevantes aunque sean breves.
     */
    val minHours: Int
        get() = when {
            this == THUNDER -> 2
            isSignificant -> 3
            else -> 0
        }

    /** Familias en las que cae agua, y que por tanto pintan gotas en el fondo. */
    val hasRain: Boolean get() = this == DRIZZLE || this == RAIN || this == THUNDER

    /** Familias que además pintan destellos de rayo. */
    val hasThunder: Boolean get() = this == THUNDER

    companion object {
        /** Clasifica un código WMO (con posible sufijo 'n' de noche) en su familia. */
        fun fromCode(code: String?): WeatherCodeGroup {
            val numeric = numericValue(code) ?: return CLEAR
            return when {
                numeric == 0 -> CLEAR
                numeric <= 2 -> PARTLY_CLOUDY
                numeric == 3 -> CLOUDY
                numeric == 45 || numeric == 48 -> FOG
                numeric in 51..57 -> DRIZZLE
                numeric in 61..67 -> RAIN
                numeric in 71..77 -> SNOW
                numeric in 80..82 -> RAIN
                numeric == 85 || numeric == 86 -> SNOW
                numeric >= 95 -> THUNDER
                else -> CLEAR
            }
        }

        /** Extrae la parte numérica de un código WMO, ignorando el sufijo 'n'. */
        fun numericValue(code: String?): Int? {
            if (code.isNullOrEmpty()) return null
            return code.replace("n", "").toIntOrNull()
        }
    }
}
