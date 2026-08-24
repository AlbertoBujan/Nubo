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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import com.nubo.nubo.domain.astro.SunTimes
import java.time.Duration
import java.time.LocalDateTime
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
    modifier: Modifier = Modifier,
) {
    val progress = progressBetween(start, end)

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
 * Semicírculo con el trayecto recorrido resaltado y un punto en la posición
 * actual, al estilo de Breezy Weather.
 */
@Composable
private fun Arc(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 2.5.dp.toPx()
        val radius = minOf(size.width / 2, size.height) - stroke
        val center = Offset(size.width / 2, size.height)
        val rect = Rect(
            offset = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
        )

        // Trayecto completo, punteado y tenue.
        drawArc(
            color = color.copy(alpha = 0.28f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
            ),
        )

        if (progress <= 0f) return@Canvas

        // Tramo ya recorrido.
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        if (progress >= 1f) return@Canvas

        val angle = Math.toRadians((180f + 180f * progress).toDouble())
        val position = Offset(
            center.x + radius * kotlin.math.cos(angle).toFloat(),
            center.y + radius * kotlin.math.sin(angle).toFloat(),
        )
        drawCircle(color, 4.dp.toPx(), position)
        drawCircle(Color.White, 1.5.dp.toPx(), position)
    }
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
