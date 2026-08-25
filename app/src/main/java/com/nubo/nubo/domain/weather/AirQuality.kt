package com.nubo.nubo.domain.weather

import kotlin.math.roundToInt

/**
 * Bandas del índice europeo de calidad del aire (EAQI).
 *
 * Los cortes son los de la Agencia Europea de Medio Ambiente: 20, 40, 60, 80 y
 * 100, sin techo por arriba. Open-Meteo calcula el índice en todo el mundo
 * —incluidos sitios donde no hay estaciones— a partir del modelo CAMS, así que
 * esta escala vale igual en Curtis que en Nairobi.
 *
 * Las etiquetas son versiones cortas de las oficiales, que no caben en una
 * casilla de media tarjeta: la EAQI dice "razonablemente buena", "desfavorable"
 * y "extremadamente desfavorable" donde aquí pone "aceptable", "mala" y
 * "extrema".
 */
enum class AirQualityBand(val label: String, private val upperBound: Int) {
    GOOD("Buena", 20),
    FAIR("Aceptable", 40),
    MODERATE("Regular", 60),
    POOR("Mala", 80),
    VERY_POOR("Muy mala", 100),
    EXTREME("Extrema", Int.MAX_VALUE),
    ;

    /** Si conviene reducir la exposición al aire libre. */
    val isUnhealthy: Boolean get() = ordinal >= MODERATE.ordinal

    companion object {
        /**
         * Banda a la que pertenece un índice.
         *
         * El corte de cada tramo es **inclusivo**, y eso lo fija la propia
         * definición de la última banda: "extremadamente desfavorable" es
         * `> 100`, luego un 100 clavado todavía es "muy mala". El resto de
         * tramos se leen igual por coherencia, así que un 20 es "buena".
         */
        fun forAqi(aqi: Int): AirQualityBand =
            entries.first { aqi <= it.upperBound }
    }
}

/**
 * Bandas del índice ultravioleta, según la escala de la OMS.
 *
 * Open-Meteo lo da con decimales; se redondea antes de clasificar porque los
 * tramos oficiales están definidos sobre enteros.
 */
enum class UvBand(val label: String, private val upperBound: Int) {
    LOW("Bajo", 2),
    MODERATE("Moderado", 5),
    HIGH("Alto", 7),
    VERY_HIGH("Muy alto", 10),
    EXTREME("Extremo", Int.MAX_VALUE),
    ;

    /** A partir de aquí la OMS recomienda protección. */
    val needsProtection: Boolean get() = ordinal >= MODERATE.ordinal

    companion object {
        /**
         * Se **redondea**, no se trunca, porque es lo que dice la OMS y porque
         * la casilla enseña el índice también redondeado: truncando aquí, un
         * 2,6 se leería como "3" con la etiqueta "Bajo".
         */
        fun forIndex(index: Double): UvBand {
            val rounded = index.coerceAtLeast(0.0).roundToInt()
            return entries.first { rounded <= it.upperBound }
        }
    }
}
