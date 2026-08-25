package com.nubo.nubo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.nubo.nubo.domain.weather.WeatherCodeGroup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Estado del cielo que se pinta **detrás** de la precipitación.
 *
 * Va aparte de [WeatherEffect] porque las dos capas se combinan: cuando llueve
 * también hay nubes. Con un solo enum habría que enumerar cada pareja.
 *
 * No es un enum sino datos: así "poco nuboso de noche" es sencillamente unas
 * cuantas estrellas y un par de nubes, sin inventarle un nombre.
 */
data class SkyLayer(
    val cloudCount: Int = 0,
    /** Opacidad de cada nube. Por encima de 0,15 empieza a competir con el texto. */
    val cloudOpacity: Float = 0f,
    val starCount: Int = 0,
    val fogBands: Int = 0,
) {
    val isEmpty: Boolean get() = cloudCount == 0 && starCount == 0 && fogBands == 0

    companion object {
        val NONE = SkyLayer()

        /**
         * Deriva la capa del código WMO.
         *
         * El día despejado se queda **quieto** a propósito: es el estado más
         * frecuente, el gradiente ya cuenta que hace bueno, y así sigue sin
         * costar un solo fotograma. La noche despejada sí se anima, que es
         * donde las estrellas lucen.
         */
        fun fromSkyCode(code: String?): SkyLayer {
            val numeric = WeatherCodeGroup.numericValue(code) ?: return NONE
            val night = code?.endsWith("n") == true

            return when (WeatherCodeGroup.fromCode(code)) {
                WeatherCodeGroup.CLEAR ->
                    if (night) SkyLayer(starCount = STARS_CLEAR) else NONE

                WeatherCodeGroup.PARTLY_CLOUDY -> {
                    // 1 es "poco nuboso" y 2 "intervalos": el segundo tapa más
                    // cielo, así que lleva más nube y menos estrella.
                    val few = numeric <= 1
                    SkyLayer(
                        cloudCount = if (few) 3 else 5,
                        cloudOpacity = if (few) 0.07f else 0.10f,
                        starCount = if (!night) 0 else if (few) STARS_CLEAR / 2 else STARS_CLEAR / 4,
                    )
                }

                WeatherCodeGroup.CLOUDY -> SkyLayer(cloudCount = 8, cloudOpacity = 0.14f)

                WeatherCodeGroup.FOG -> SkyLayer(cloudCount = 4, cloudOpacity = 0.08f, fogBands = 4)

                // Si cae algo, el cielo está tapado.
                else -> SkyLayer(cloudCount = 8, cloudOpacity = 0.16f)
            }
        }

        private const val STARS_CLEAR = 70
    }
}

/** Una nube. Como las gotas, su posición es función pura del tiempo. */
private data class Cloud(
    val y: Float,
    val phase: Float,
    /** Cercanía aparente: afecta a tamaño, velocidad y opacidad. */
    val depth: Float,
    val radius: Float,
)

/** Una estrella, con su propio desfase para que el parpadeo no vaya a la vez. */
private data class Star(val x: Float, val y: Float, val phase: Float, val size: Float)

/**
 * Nubes, estrellas y niebla sobre el gradiente del cielo.
 *
 * Va en su propio Canvas y con su propio reloj, separado del de la
 * precipitación, por una razón concreta: estas capas se mueven tan despacio
 * que **no necesitan 60 fps**. Su reloj avanza a [SLOW_FPS], de modo que el
 * fondo de un día nublado redibuja la cuarta parte de lo que redibuja un
 * chaparrón. Compartiendo Canvas, el estado de la lluvia invalidaría también
 * este y el ahorro se perdería.
 */
@Composable
fun SkyLayerOverlay(
    layer: SkyLayer,
    /** Velocidad del viento en km/h; mueve las nubes tan deprisa como sopla. */
    windSpeed: Int?,
    /** Dirección de procedencia en grados meteorológicos (0 = norte). */
    windDegrees: Int?,
    modifier: Modifier = Modifier,
) {
    if (layer.isEmpty) {
        Box(modifier)
        return
    }

    // Semilla fija: el patrón debe ser estable entre recomposiciones para que
    // las nubes no salten de sitio al cambiar de página.
    val clouds = remember(layer.cloudCount) {
        val random = Random(11)
        List(layer.cloudCount) {
            Cloud(
                y = 0.05f + random.nextFloat() * 0.65f,
                phase = random.nextFloat(),
                depth = 0.5f + random.nextFloat() * 0.5f,
                radius = 0.18f + random.nextFloat() * 0.22f,
            )
        }
    }

    val stars = remember(layer.starCount) {
        val random = Random(29)
        List(layer.starCount) {
            Star(
                x = random.nextFloat(),
                // Se concentran arriba: abajo quedarían tras las tarjetas.
                y = random.nextFloat() * 0.55f,
                phase = random.nextFloat(),
                size = 0.6f + random.nextFloat() * 1.4f,
            )
        }
    }

    var elapsed by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(layer) {
        var previousFrame = 0L
        var sinceUpdate = 0f

        while (true) {
            val frame = withFrameNanos { it }
            val delta = if (previousFrame == 0L) 0f else (frame - previousFrame) / 1_000_000_000f
            previousFrame = frame

            // Solo se toca el estado —y por tanto solo se redibuja— cuando ha
            // pasado un fotograma "lento". A esta velocidad de deriva, entre
            // uno y otro una nube no se mueve ni un píxel.
            sinceUpdate += delta
            if (sinceUpdate >= 1f / SLOW_FPS) {
                elapsed = (elapsed + sinceUpdate) % CYCLE_SECONDS
                sinceUpdate = 0f
            }
        }
    }

    val drift = windDrift(windSpeed, windDegrees)

    Canvas(modifier = modifier.fillMaxSize()) {
        if (layer.fogBands > 0) {
            // Un velo de fondo además de las bandas: la niebla es sobre todo
            // pérdida de visibilidad, y solo con franjas se leía como calima.
            drawRect(color = FOG_VEIL)
            for (band in 0 until layer.fogBands) {
                drawFogBand(band, elapsed, layer.fogBands)
            }
        }

        for (star in stars) {
            // Parpadeo suave: nunca se apaga del todo, solo respira.
            val twinkle = 0.55f + 0.45f * sin((elapsed * 0.6f + star.phase * TWO_PI))
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * twinkle),
                radius = star.size * density,
                center = Offset(star.x * size.width, star.y * size.height),
            )
        }

        for (cloud in clouds) {
            drawCloud(cloud, elapsed, drift, layer.cloudOpacity)
        }
    }
}

/**
 * Desplazamiento por segundo, en anchos de pantalla.
 *
 * El grado meteorológico dice de **dónde** viene el viento, así que la nube va
 * en sentido contrario. Se acota por arriba porque con un temporal de 100 km/h
 * el fondo parecería una cinta transportadora.
 */
private fun windDrift(windSpeed: Int?, windDegrees: Int?): Float {
    val speed = (windSpeed ?: DEFAULT_WIND).coerceIn(0, MAX_WIND)
    val magnitude = BASE_DRIFT + (speed.toFloat() / MAX_WIND) * EXTRA_DRIFT

    // Sin dirección conocida, de izquierda a derecha.
    val degrees = windDegrees ?: return magnitude

    // 270º (viento del oeste) debe empujar hacia el este, o sea a la derecha.
    val radians = Math.toRadians(degrees.toDouble())
    val eastward = -sin(radians).toFloat()
    return magnitude * eastward
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(
    cloud: Cloud,
    elapsed: Float,
    drift: Float,
    opacity: Float,
) {
    // Recorrido normalizado que se repite. Se sale por un lado y entra por el
    // otro, con un margen para que no aparezca de golpe en el borde.
    val travel = (cloud.phase + elapsed * drift * cloud.depth).mod(1f)
    val x = (travel * (1f + 2 * MARGIN) - MARGIN) * size.width
    val y = cloud.y * size.height
    val radius = cloud.radius * cloud.depth * min(size.width, size.height)

    // Gradiente radial en vez de círculo: Compose no tiene desenfoque barato
    // por debajo de API 31 y nuestro mínimo es el 24. Un círculo sólido se ve
    // como un círculo; esto se lee como nube.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = opacity * cloud.depth),
                Color.White.copy(alpha = opacity * cloud.depth * 0.4f),
                Color.Transparent,
            ),
            center = Offset(x, y),
            radius = radius,
        ),
        radius = radius,
        center = Offset(x, y),
    )
}

/** Banda de niebla: una franja difusa que sube y baja muy despacio. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFogBand(
    index: Int,
    elapsed: Float,
    total: Int,
) {
    val slot = (index + 0.5f) / total
    val sway = sin(elapsed * 0.25f + index) * 0.03f
    val top = ((slot + sway) * size.height).coerceIn(0f, size.height)
    val height = size.height * 0.22f

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, FOG_COLOR, Color.Transparent),
            startY = top - height / 2,
            endY = top + height / 2,
        ),
        topLeft = Offset(0f, top - height / 2),
        size = Size(size.width, height),
    )
}

private val FOG_COLOR = Color(0xFFDDE4EC).copy(alpha = 0.20f)

/** Velo uniforme bajo las bandas; va detrás de las tarjetas, que lo tapan. */
private val FOG_VEIL = Color(0xFFDDE4EC).copy(alpha = 0.10f)

private const val TWO_PI = 2f * PI.toFloat()

/**
 * Fotogramas por segundo de esta capa.
 *
 * Quince bastan: una nube tarda minutos en cruzar la pantalla, así que a 60
 * se redibujaría cuatro veces para mover lo mismo.
 */
private const val SLOW_FPS = 15f

/** Periodo del ciclo del reloj lento. */
private const val CYCLE_SECONDS = 600f

/** Margen por el que la nube entra y sale, en anchos de pantalla. */
private const val MARGIN = 0.35f

/** Anchos de pantalla por segundo con viento en calma. */
private const val BASE_DRIFT = 0.004f

/** Lo que suma el viento al máximo. */
private const val EXTRA_DRIFT = 0.020f

private const val DEFAULT_WIND = 10
private const val MAX_WIND = 60
