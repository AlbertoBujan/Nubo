package com.nubo.nubo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.domain.astro.MoonData
import com.nubo.nubo.domain.astro.SkyPath
import com.nubo.nubo.domain.astro.SunTimes
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.PI
import java.time.format.DateTimeFormatter

/**
 * Tarjetas gemelas del ciclo solar y lunar.
 *
 * Recibe los datos ya resueltos en vez del ViewModel para que la tarjeta
 * pertenezca a la página que la muestra: leyendo el estado global cambiaría de
 * ciudad a la vez que el índice activo, que es justo el tirón que se corrigió
 * en la app Flutter.
 */
@Composable
fun SunMoonCard(
    sunTimes: SunTimes?,
    moonData: MoonData?,
    /** Trayectorias reales; ver `SkyPath`. Vacías, se cae a una curva suave. */
    sunPath: List<Float>,
    moonPath: List<Float>,
    /** Hora local del sitio, contra la que se mide el trayecto recorrido. */
    nowThere: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    if (sunTimes == null) return

    val formatter = DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.forLanguageTag("es-ES"))

    // IntrinsicSize.Min mide la más alta y estira la otra hasta igualarla, de
    // modo que las dos tarjetas quedan siempre simétricas aunque el nombre de
    // la fase lunar ocupe más que el título "Sol".
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArcCard(
            title = "Sol",
            icon = Icons.Outlined.WbSunny,
            iconTint = Color(0xFFFFC107),
            start = sunTimes.sunrise,
            end = sunTimes.sunset,
            startLabel = sunTimes.sunrise.format(formatter),
            endLabel = sunTimes.sunset.format(formatter),
            arcColor = Color(0xFFFFB300),
            now = nowThere,
            path = sunPath,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )

        if (moonData?.moonrise != null && moonData.moonset != null) {
            ArcCard(
                title = moonData.phaseName,
                icon = Icons.Outlined.DarkMode,
                iconTint = Color(0xFFB0BEC5),
                start = moonData.moonrise,
                end = moonData.moonset,
                startLabel = moonData.moonrise.format(formatter),
                endLabel = moonData.moonset.format(formatter),
                arcColor = Color(0xFF90A4AE),
                now = nowThere,
                path = moonPath,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ArcCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    start: LocalDateTime,
    end: LocalDateTime,
    startLabel: String,
    endLabel: String,
    arcColor: Color,
    now: LocalDateTime,
    path: List<Float>,
    modifier: Modifier = Modifier,
) {
    val progress = progressBetween(start, end, now)

    GlassCard(modifier = modifier, cornerRadius = 20.dp, contentPadding = 12.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))

            Arc(
                progress = progress,
                color = arcColor,
                path = path,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )

            Spacer(Modifier.height(6.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(startLabel, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                Text(endLabel, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            }
        }
    }
}

/**
 * Trayectoria del astro con el tramo recorrido resaltado y un punto en la
 * posición actual, al estilo de Breezy Weather.
 *
 * La curva es la **altura real sobre el horizonte** hora a hora, muestreada en
 * `SkyPath`. Antes era primero un semicírculo y luego una parábola, y ninguna
 * de las dos es lo que hace el sol: la forma verdadera depende de la latitud y
 * de la época del año, y por eso no puede ser una fórmula fija.
 *
 * El eje horizontal es el tiempo, así que el punto a mitad de la tarjeta es
 * el mediodía solar y su altura es la que de verdad tiene el astro entonces.
 */
@Composable
private fun Arc(
    progress: Float,
    color: Color,
    path: List<Float>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val stroke = 2.5.dp.toPx()
        val baseline = size.height - stroke
        val left = stroke
        val right = size.width - stroke
        val apex = baseline - stroke

        // Sin trayectoria —día polar, o datos aún sin calcular— se cae a una
        // curva suave para no dejar la tarjeta vacía.
        val heights = path.ifEmpty { FALLBACK_CURVE }

        fun pointAt(fraction: Float): Offset {
            val x = left + (right - left) * fraction.coerceIn(0f, 1f)
            val y = baseline - apex * SkyPath.heightAt(heights, fraction)
            return Offset(x, y)
        }

        fun buildPath(from: Float, to: Float): Path {
            val result = Path()
            val first = pointAt(from)
            result.moveTo(first.x, first.y)

            // Se recorre por muestras y no por el ancho en píxeles: son las
            // muestras las que llevan la forma.
            val steps = heights.size
            for (i in 0..steps) {
                val fraction = from + (to - from) * i / steps
                val point = pointAt(fraction)
                result.lineTo(point.x, point.y)
            }
            return result
        }

        // Trayecto completo, punteado y tenue.
        drawPath(
            path = buildPath(0f, 1f),
            color = color.copy(alpha = 0.28f),
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
            ),
        )

        if (progress <= 0f) return@Canvas

        drawPath(
            path = buildPath(0f, progress.coerceIn(0f, 1f)),
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        if (progress >= 1f) return@Canvas

        val position = pointAt(progress)
        drawCircle(color, 4.dp.toPx(), position)
        drawCircle(Color.White, 1.5.dp.toPx(), position)
    }
}

/**
 * Curva de reserva: media onda de seno.
 *
 * Solo se usa cuando no hay trayectoria que dibujar, y se parece bastante a
 * la de un día de equinoccio en latitudes medias.
 */
private val FALLBACK_CURVE: List<Float> = List(SkyPath.SAMPLES) { i ->
    kotlin.math.sin(PI * i / (SkyPath.SAMPLES - 1)).toFloat()
}

/** Fracción transcurrida entre dos instantes, acotada a 0..1. */
internal fun progressBetween(
    start: LocalDateTime,
    end: LocalDateTime,
    now: LocalDateTime = LocalDateTime.now(),
): Float {
    val total = Duration.between(start, end).toMillis()
    if (total <= 0) return 0f
    val elapsed = Duration.between(start, now).toMillis()
    return (elapsed.toFloat() / total).coerceIn(0f, 1f)
}
