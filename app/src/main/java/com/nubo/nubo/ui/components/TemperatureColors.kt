package com.nubo.nubo.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Escala de color por temperatura, del azul frío al rojo.
 *
 * Se usa tanto en la línea del gráfico horario como en la barra de rango de la
 * predicción diaria, para que un mismo valor se vea del mismo color en toda la
 * aplicación.
 */
object TemperatureColors {

    private val stops = listOf(
        0f to Color(0xFF2196F3),
        10f to Color(0xFF00BCD4),
        18f to Color(0xFF4CAF50),
        25f to Color(0xFFFFEB3B),
        32f to Color(0xFFFF9800),
        38f to Color(0xFFF44336),
    )

    fun forTemperature(temp: Float): Color {
        if (temp <= stops.first().first) return stops.first().second
        if (temp >= stops.last().first) return stops.last().second

        for (i in 0 until stops.size - 1) {
            val (lowTemp, lowColor) = stops[i]
            val (highTemp, highColor) = stops[i + 1]
            if (temp in lowTemp..highTemp) {
                val t = (temp - lowTemp) / (highTemp - lowTemp)
                return lerp(lowColor, highColor, t)
            }
        }
        return stops.last().second
    }
}
