package com.nubo.nubo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.nubo.nubo.domain.weather.WeatherCodeGroup
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Intensidad del efecto que se pinta sobre el fondo. */
enum class WeatherEffect(
    val dropCount: Int,
    /** Alturas de pantalla que recorre una partícula por segundo. */
    val speed: Float,
    /** Largo de la gota como fracción de la altura de pantalla. */
    val dropLength: Float,
    val opacity: Float,
) {
    NONE(0, 0f, 0f, 0f),
    DRIZZLE(28, 0.55f, 0.018f, 0.26f),
    RAIN(55, 0.95f, 0.032f, 0.34f),
    HEAVY_RAIN(80, 1.35f, 0.05f, 0.42f),
    THUNDER(80, 1.35f, 0.05f, 0.42f),

    // La nieve cae a una fracción de la velocidad de la lluvia y no deja
    // estela, así que `dropLength` no la usa: el copo es un punto.
    SNOW(70, 0.12f, 0f, 0.72f),
    ;

    val hasFlashes: Boolean get() = this == THUNDER

    /** Si las partículas son copos y no gotas, que se dibujan distinto. */
    val isSnow: Boolean get() = this == SNOW

    companion object {
        /** Códigos de lluvia intensa o chubasco fuerte. */
        private val HEAVY = setOf(65, 67, 82)

        /** Deriva el efecto del código WMO del cielo. */
        fun fromSkyCode(code: String?): WeatherEffect {
            val group = WeatherCodeGroup.fromCode(code)
            if (group.hasSnow) return SNOW
            if (!group.hasRain) return NONE
            if (group.hasThunder) return THUNDER
            if (group == WeatherCodeGroup.DRIZZLE) return DRIZZLE

            val numeric = WeatherCodeGroup.numericValue(code) ?: 0
            return if (numeric in HEAVY) HEAVY_RAIN else RAIN
        }
    }
}

/** Una gota. Su posición se deriva del tiempo, así que no acumula deriva. */
private data class Drop(
    val x: Float,
    val phase: Float,
    /** Cercanía aparente: afecta a velocidad, largo y opacidad. */
    val depth: Float,
    /** Inclinación de la caída. */
    val drift: Float,
)

/**
 * Lluvia, nieve y relámpagos sobre el fondo del cielo.
 *
 * Sigue el planteamiento de Breezy Weather: partículas dibujadas a mano sobre
 * un canvas en vez de una animación empaquetada, para poder ajustar densidad y
 * velocidad al fenómeno real. El destello tampoco dibuja un rayo: es un velo a
 * pantalla completa cuya opacidad sigue una curva corta de doble pico, que es
 * lo que de verdad se percibe de una descarga.
 *
 * El ticker solo corre mientras hay algo que pintar.
 */
@Composable
fun WeatherEffectsOverlay(
    effect: WeatherEffect,
    modifier: Modifier = Modifier,
) {
    // Semilla fija: el patrón debe ser estable entre recomposiciones para que
    // las gotas no salten de sitio al cambiar de página.
    val drops = remember(effect) {
        val random = Random(7)
        List(effect.dropCount) {
            Drop(
                x = random.nextFloat(),
                phase = random.nextFloat(),
                depth = 0.55f + random.nextFloat() * 0.45f,
                drift = -0.06f + random.nextFloat() * 0.03f,
            )
        }
    }

    val flashes = remember {
        val random = Random(23)
        buildList {
            var t = 2f + random.nextFloat() * 4f
            while (t < CYCLE_SECONDS - 1) {
                add(t)
                t += 3f + random.nextFloat() * 5f // un destello cada 3-8 s
            }
        }
    }

    var elapsed by remember { mutableFloatStateOf(0f) }
    var intensity by remember { mutableFloatStateOf(if (effect == WeatherEffect.NONE) 0f else 1f) }

    // El bucle se relanza al cambiar de efecto y termina solo cuando ya no
    // queda nada que dibujar, de modo que con buen tiempo no gasta frames.
    LaunchedEffect(effect) {
        val target = if (effect == WeatherEffect.NONE) 0f else 1f
        var previousFrame = 0L

        while (true) {
            val frame = withFrameNanos { it }
            val delta = if (previousFrame == 0L) {
                0f
            } else {
                (frame - previousFrame) / 1_000_000_000f
            }
            previousFrame = frame

            elapsed = (elapsed + delta) % CYCLE_SECONDS
            intensity = if (intensity < target) {
                min(target, intensity + delta / FADE_SECONDS)
            } else {
                maxOf(target, intensity - delta / FADE_SECONDS)
            }

            if (intensity <= 0f && target == 0f) break
        }
    }

    if (intensity <= 0f) {
        Box(modifier)
        return
    }

    val flashAlpha = if (effect.hasFlashes) flashAlphaAt(elapsed, flashes) * intensity else 0f

    Canvas(modifier = modifier.fillMaxSize()) {
        if (flashAlpha > 0f) {
            drawRect(color = Color(0xFFEAF2FF).copy(alpha = flashAlpha * 0.5f))
        }

        for (drop in drops) {
            // Recorrido normalizado que se repite: función pura del tiempo.
            val travel = (drop.phase + elapsed * effect.speed * drop.depth) % 1f
            val alpha = effect.opacity * drop.depth * intensity

            if (effect.isSnow) {
                // El copo no cae recto: se balancea. Sin ese vaivén los puntos
                // bajan en columnas y se leen como una cortina de lluvia lenta,
                // no como nieve.
                val sway = sin(travel * SWAY_TURNS + drop.phase * TWO_PI) * SWAY_WIDTH
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = (1.1f + drop.depth * 2.1f) * density,
                    center = Offset((drop.x + sway) * size.width, travel * size.height),
                )
                continue
            }

            val startX = (drop.x + drop.drift * travel) * size.width
            val startY = travel * size.height
            val length = effect.dropLength * drop.depth * size.height

            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = Offset(startX, startY),
                end = Offset(startX + drop.drift * size.width * 0.12f, startY + length),
                strokeWidth = 1f + drop.depth * 0.8f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Opacidad del destello en un instante.
 *
 * Cada relámpago dura 300 ms y describe un doble pico —brilla, cae, vuelve a
 * brillar más fuerte y se apaga—, que es como se percibe una descarga real.
 */
private fun flashAlphaAt(elapsed: Float, flashes: List<Float>): Float {
    for (start in flashes) {
        val t = elapsed - start
        if (t < 0f || t > 0.3f) continue

        return when {
            t < 0.05f -> (t / 0.05f) * 0.55f
            t < 0.10f -> 0.55f - ((t - 0.05f) / 0.05f) * 0.35f
            t < 0.16f -> 0.20f + ((t - 0.10f) / 0.06f) * 0.80f
            else -> 1f - ((t - 0.16f) / 0.14f)
        }
    }
    return 0f
}

private const val TWO_PI = 2f * PI.toFloat()

/** Vaivenes completos que da un copo en toda su caída. */
private const val SWAY_TURNS = 3f * TWO_PI

/** Amplitud del vaivén, en fracción del ancho de pantalla. */
private const val SWAY_WIDTH = 0.02f

/** Periodo del ciclo del ticker; los destellos se reparten dentro. */
private const val CYCLE_SECONDS = 60f

/** Lo que tarda un cambio de fenómeno en entrar o salir del todo. */
private const val FADE_SECONDS = 1.2f
