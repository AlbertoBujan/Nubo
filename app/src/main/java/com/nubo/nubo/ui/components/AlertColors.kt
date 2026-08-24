package com.nubo.nubo.ui.components

import androidx.compose.ui.graphics.Color
import com.nubo.nubo.domain.model.AlertLevel

/** Color de marca de cada nivel de aviso, el mismo que usa AEMET. */
fun AlertLevel.toColor(): Color = when (this) {
    AlertLevel.RED -> Color(0xFFD32F2F)
    AlertLevel.ORANGE -> Color(0xFFFF8F00)
    AlertLevel.YELLOW -> Color(0xFFFBC02D)
}

/** Relleno tenue para la tarjeta del aviso. */
fun AlertLevel.toBackgroundColor(): Color = toColor().copy(alpha = 0.12f)
