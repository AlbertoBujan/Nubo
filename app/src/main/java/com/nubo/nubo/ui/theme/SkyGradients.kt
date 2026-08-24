package com.nubo.nubo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.nubo.nubo.domain.weather.SkyCondition
import com.nubo.nubo.domain.weather.SunPhase

/**
 * Paleta del fondo dinámico: cuatro colores por combinación de fase solar y
 * estado del cielo.
 *
 * Se guardan como lista de 4 colores en vez de como Brush para poder
 * interpolar entre dos paletas durante el swipe entre ciudades; el Brush se
 * construye en el momento de pintar, cuando ya se conoce el tamaño.
 */
data class SkyGradient(val colors: List<Color>) {
    init {
        require(colors.size == STOPS) { "Un gradiente de cielo necesita $STOPS colores" }
    }

    companion object {
        const val STOPS = 4
        val POSITIONS = floatArrayOf(0f, 0.33f, 0.67f, 1f)
    }
}

object SkyGradients {

    /** Paleta correspondiente a una fase solar y un estado del cielo. */
    fun forPhase(phase: SunPhase, sky: SkyCondition): SkyGradient = when (phase) {
        SunPhase.DAY -> day(sky)
        SunPhase.SUNRISE -> sunrise(sky)
        SunPhase.SUNSET -> sunset(sky)
        SunPhase.NIGHT -> night(sky)
    }

    /** Interpola dos paletas color a color. */
    fun lerp(a: SkyGradient, b: SkyGradient, t: Float): SkyGradient =
        SkyGradient(a.colors.zip(b.colors) { ca, cb -> lerp(ca, cb, t) })

    private fun day(sky: SkyCondition) = when (sky) {
        SkyCondition.CLEAR -> SkyGradient(
            listOf(Color(0xFF0F5298), Color(0xFF1F73BA), Color(0xFF3C99DC), Color(0xFF4DA8E8)),
        )
        SkyCondition.PARTLY_CLOUDY -> SkyGradient(
            listOf(Color(0xFF4A6B8A), Color(0xFF627D96), Color(0xFF7A8FA0), Color(0xFF9EAAB6)),
        )
        SkyCondition.OVERCAST -> SkyGradient(
            listOf(Color(0xFF4B5563), Color(0xFF5A6370), Color(0xFF6B7280), Color(0xFF555E69)),
        )
    }

    private fun sunrise(sky: SkyCondition) = when (sky) {
        SkyCondition.CLEAR -> SkyGradient(
            listOf(Color(0xFF141E30), Color(0xFF243B55), Color(0xFFCC2B5E), Color(0xFF753A88)),
        )
        SkyCondition.PARTLY_CLOUDY -> SkyGradient(
            listOf(Color(0xFF2C3E50), Color(0xFF5D6D7E), Color(0xFF9B6B8A), Color(0xFF8E99A4)),
        )
        SkyCondition.OVERCAST -> SkyGradient(
            listOf(Color(0xFF3D3D3D), Color(0xFF4B4646), Color(0xFF5A5050), Color(0xFF6B6360)),
        )
    }

    private fun sunset(sky: SkyCondition) = when (sky) {
        SkyCondition.CLEAR -> SkyGradient(
            listOf(Color(0xFF3E1E68), Color(0xFF82306B), Color(0xFFC6426E), Color(0xFFF9A825)),
        )
        SkyCondition.PARTLY_CLOUDY -> SkyGradient(
            listOf(Color(0xFF3D3456), Color(0xFF634760), Color(0xFF8A5A6A), Color(0xFF7A6E65)),
        )
        SkyCondition.OVERCAST -> SkyGradient(
            listOf(Color(0xFF3D3D3D), Color(0xFF4C4444), Color(0xFF5A4A4A), Color(0xFF4A4545)),
        )
    }

    private fun night(sky: SkyCondition) = when (sky) {
        SkyCondition.CLEAR -> SkyGradient(
            listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460), Color(0xFF0A0A12)),
        )
        SkyCondition.PARTLY_CLOUDY -> SkyGradient(
            listOf(Color(0xFF1C2333), Color(0xFF232938), Color(0xFF2A2F3D), Color(0xFF252830)),
        )
        SkyCondition.OVERCAST -> SkyGradient(
            listOf(Color(0xFF1A1A1E), Color(0xFF202023), Color(0xFF252528), Color(0xFF1E1E20)),
        )
    }
}
