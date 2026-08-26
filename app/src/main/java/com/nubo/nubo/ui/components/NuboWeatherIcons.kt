package com.nubo.nubo.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Iconos de precipitación dibujados a mano.
 *
 * `material-icons-extended` no trae ninguna nube con precipitación —no existe
 * `Rainy`, y `Shower` es una ducha de baño—, así que lo más parecido eran la
 * gota suelta (`WaterDrop`) y el copo suelto (`AcUnit`). No es el estándar: el
 * resto de apps del tiempo, y el propio `Thunderstorm` que ya usamos para las
 * tormentas, dibujan la nube con lo que cae debajo.
 *
 * La nube de aquí es **literalmente la de `Icons.Outlined.Thunderstorm`**, con
 * el rayo sustituido por lo que caiga en cada caso. Reutilizarla, en vez de
 * dibujar otra, es lo que hace que lluvia, llovizna, nieve y tormenta se lean
 * como la misma familia y ocupen el mismo hueco óptico dentro de la fila.
 */
object NuboWeatherIcons {

    val CloudRainLight: ImageVector by lazy { buildCloudIcon("CloudRainLight", LIGHT_RAIN_STREAKS) }

    val CloudRain: ImageVector by lazy { buildCloudIcon("CloudRain", RAIN_STREAKS) }

    val CloudRainHeavy: ImageVector by lazy {
        buildCloudIcon("CloudRainHeavy", HEAVY_RAIN_STREAKS, strokeWidth = 2.4f)
    }

    val CloudDrizzle: ImageVector by lazy { buildCloudIcon("CloudDrizzle", DRIZZLE_STREAKS) }

    val CloudSnowLight: ImageVector by lazy {
        buildCloudIcon("CloudSnowLight", streaks = emptyList(), flakes = LIGHT_SNOW_FLAKES)
    }

    val CloudSnow: ImageVector by lazy {
        buildCloudIcon("CloudSnow", streaks = emptyList(), flakes = SNOW_FLAKES)
    }

    val CloudSnowHeavy: ImageVector by lazy {
        buildCloudIcon("CloudSnowHeavy", streaks = emptyList(), flakes = HEAVY_SNOW_FLAKES)
    }
}

/** Trazo de precipitación: (x superior, y superior, x inferior, y inferior). */
private data class Streak(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

/**
 * Tres trazos largos e inclinados. La inclinación es la misma que llevan los
 * rayos de `Thunderstorm`, para que las dos siluetas rimen.
 */
private val RAIN_STREAKS = listOf(
    Streak(8.2f, 17.6f, 6.9f, 21.4f),
    Streak(12.6f, 17.6f, 11.3f, 21.4f),
    Streak(17.0f, 17.6f, 15.7f, 21.4f),
)

/**
 * Dos trazos largos: la lluvia débil se separa de la llovizna por el **largo**
 * del trazo, no por cuántos hay, y de la normal por cuántos hay, no por el
 * largo. Cambiar las dos cosas a la vez haría que los tres escalones se
 * parecieran entre sí en lugar de leerse como una escala.
 */
private val LIGHT_RAIN_STREAKS = listOf(
    Streak(10.4f, 17.6f, 9.1f, 21.4f),
    Streak(14.8f, 17.6f, 13.5f, 21.4f),
)

/**
 * Cuatro trazos, más largos y más gruesos.
 *
 * A 24 dp un trazo más no basta —de tres a cuatro casi no se cuenta—, así que
 * el escalón lo llevan las tres cosas juntas: número, largo y grosor.
 */
private val HEAVY_RAIN_STREAKS = listOf(
    Streak(7.0f, 17.4f, 5.8f, 21.8f),
    Streak(10.4f, 17.4f, 9.2f, 21.8f),
    Streak(13.8f, 17.4f, 12.6f, 21.8f),
    Streak(17.2f, 17.4f, 16.0f, 21.8f),
)

/** Dos trazos cortos: misma nube, precipitación visiblemente menor. */
private val DRIZZLE_STREAKS = listOf(
    Streak(10.4f, 17.8f, 9.6f, 20.1f),
    Streak(14.8f, 17.8f, 14.0f, 20.1f),
)

/** Centro de un copo, dibujado como asterisco de seis puntas. */
private data class Flake(val cx: Float, val cy: Float, val radius: Float)

/**
 * Dos copos, colocados donde la llovizna pone sus trazos.
 *
 * Un asterisco se reconoce como nieve a simple vista; los puntos que usan
 * otros sets se confunden con la llovizna al tamaño al que se pinta esto.
 */
private val SNOW_FLAKES = listOf(
    Flake(8.9f, 19.8f, 1.9f),
    Flake(15.5f, 19.8f, 1.9f),
)

/** Un solo copo, centrado: la nevada débil. */
private val LIGHT_SNOW_FLAKES = listOf(
    Flake(12.2f, 19.8f, 1.9f),
)

/**
 * Tres copos en dos alturas.
 *
 * Puestos en fila no cabían sin encogerlos hasta perder las puntas, que es lo
 * único que hace que un asterisco se lea como nieve.
 */
private val HEAVY_SNOW_FLAKES = listOf(
    Flake(8.2f, 18.9f, 1.75f),
    Flake(15.8f, 18.9f, 1.75f),
    Flake(12.0f, 21.4f, 1.75f),
)

private fun buildCloudIcon(
    name: String,
    streaks: List<Streak>,
    flakes: List<Flake> = emptyList(),
    strokeWidth: Float = 2f,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // EvenOdd garantiza el hueco interior de la nube sin depender del
        // sentido en que esté trazado cada contorno.
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            cloudOutline()
        }
        streaks.forEach { s ->
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(s.x1, s.y1)
                lineTo(s.x2, s.y2)
            }
        }
        flakes.forEach { f ->
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.15f,
                strokeLineCap = StrokeCap.Round,
            ) {
                // Tres diámetros a 60º entre sí: las seis puntas del copo.
                for (degrees in intArrayOf(90, 30, 150)) {
                    val radians = degrees * PI.toFloat() / 180f
                    val dx = cos(radians) * f.radius
                    val dy = sin(radians) * f.radius
                    moveTo(f.cx - dx, f.cy - dy)
                    lineTo(f.cx + dx, f.cy + dy)
                }
            }
        }
    }.build()

/** Contorno de nube de `Icons.Outlined.Thunderstorm`, copiado sin tocar. */
private fun PathBuilder.cloudOutline() {
    moveTo(17.92f, 7.02f)
    curveTo(17.45f, 4.18f, 14.97f, 2.0f, 12.0f, 2.0f)
    curveTo(9.82f, 2.0f, 7.83f, 3.18f, 6.78f, 5.06f)
    curveTo(4.09f, 5.41f, 2.0f, 7.74f, 2.0f, 10.5f)
    curveTo(2.0f, 13.53f, 4.47f, 16.0f, 7.5f, 16.0f)
    horizontalLineToRelative(10.0f)
    curveToRelative(2.48f, 0.0f, 4.5f, -2.02f, 4.5f, -4.5f)
    curveTo(22.0f, 9.16f, 20.21f, 7.23f, 17.92f, 7.02f)
    close()
    moveTo(17.5f, 14.0f)
    horizontalLineToRelative(-10.0f)
    curveTo(5.57f, 14.0f, 4.0f, 12.43f, 4.0f, 10.5f)
    curveToRelative(0.0f, -1.74f, 1.31f, -3.23f, 3.04f, -3.46f)
    lineToRelative(0.99f, -0.13f)
    lineToRelative(0.49f, -0.87f)
    curveTo(9.23f, 4.78f, 10.56f, 4.0f, 12.0f, 4.0f)
    curveToRelative(1.94f, 0.0f, 3.63f, 1.44f, 3.95f, 3.35f)
    lineToRelative(0.25f, 1.52f)
    lineToRelative(1.54f, 0.14f)
    curveTo(19.01f, 9.13f, 20.0f, 10.22f, 20.0f, 11.5f)
    curveTo(20.0f, 12.88f, 18.88f, 14.0f, 17.5f, 14.0f)
    close()
}
