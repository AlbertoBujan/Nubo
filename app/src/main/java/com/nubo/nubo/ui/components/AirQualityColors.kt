package com.nubo.nubo.ui.components

import androidx.compose.ui.graphics.Color
import com.nubo.nubo.domain.weather.AirQualityBand
import com.nubo.nubo.domain.weather.UvBand

/**
 * Colores del índice europeo de calidad del aire.
 *
 * Son los oficiales de la EAQI salvo las dos últimas bandas: el granate
 * `#960032` y el morado `#7D2181` están pensados para fondo blanco y sobre la
 * tarjeta translúcida, que ya es oscura, quedan ilegibles. Se conservan sus
 * tonos y se les sube la luminosidad, que es lo mínimo para que el código de
 * color siga reconociéndose.
 */
fun AirQualityBand.toColor(): Color = when (this) {
    AirQualityBand.GOOD -> Color(0xFF50F0E6)
    AirQualityBand.FAIR -> Color(0xFF50CCAA)
    AirQualityBand.MODERATE -> Color(0xFFF0E641)
    AirQualityBand.POOR -> Color(0xFFFF7B6B)
    AirQualityBand.VERY_POOR -> Color(0xFFE85E86)
    AirQualityBand.EXTREME -> Color(0xFFC77DD0)
}

/** Colores de la escala ultravioleta de la OMS, aclarados igual que arriba. */
fun UvBand.toColor(): Color = when (this) {
    UvBand.LOW -> Color(0xFF7CC576)
    UvBand.MODERATE -> Color(0xFFF5E14D)
    UvBand.HIGH -> Color(0xFFFFA24C)
    UvBand.VERY_HIGH -> Color(0xFFFF6B5B)
    UvBand.EXTREME -> Color(0xFFC77DD0)
}
