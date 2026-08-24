package com.nubo.nubo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.domain.model.HourlyForecast
import com.nubo.nubo.domain.model.WeatherAlert
import com.nubo.nubo.domain.weather.WeatherCode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ITEM_WIDTH = 65.dp
private val CHART_PADDING_LEFT = 32.dp
private val CHART_HEIGHT = 110.dp

private val DEW_COLOR = Color(0xFF80DEEA)

private val SPANISH: java.util.Locale = java.util.Locale.forLanguageTag("es-ES")
private val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", SPANISH)
private val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", SPANISH)

/** Carrusel horario con el gráfico de temperaturas debajo. */
@Composable
fun HourlyView(
    forecasts: List<HourlyForecast>,
    alerts: List<WeatherAlert>,
    modifier: Modifier = Modifier,
) {
    if (forecasts.isEmpty()) return

    val hasAnyRain = remember(forecasts) {
        forecasts.any { (it.precipitationProbability ?: 0) > 0 }
    }
    val scrollState = rememberScrollState()

    // Índice de la hora más cercana al momento actual. Se calcula una sola vez
    // aquí y no dentro de cada columna: comparando por separado, dos horas
    // contiguas podían cumplir el criterio y ambas se rotulaban "Ahora".
    val nowIndex = remember(forecasts) {
        val now = java.time.LocalDateTime.now()
        forecasts.indices.minByOrNull {
            java.time.Duration.between(forecasts[it].dateTime, now).abs().toMillis()
        } ?: -1
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
    ) {
        Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            SectionTitle(
                icon = Icons.Outlined.Schedule,
                text = "Predicción por horas",
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(16.dp))

            Row(Modifier.horizontalScroll(scrollState)) {
                Column {
                    Row(Modifier.padding(start = CHART_PADDING_LEFT)) {
                        forecasts.forEachIndexed { index, forecast ->
                            HourColumn(
                                forecast = forecast,
                                alerts = alerts,
                                hasAnyRain = hasAnyRain,
                                isNow = index == nowIndex,
                                modifier = Modifier.width(ITEM_WIDTH),
                            )
                        }
                    }
                    TemperatureChart(
                        forecasts = forecasts,
                        modifier = Modifier
                            .width(CHART_PADDING_LEFT + ITEM_WIDTH * forecasts.size + 16.dp)
                            .height(CHART_HEIGHT),
                    )
                }
            }
        }
    }
}

@Composable
private fun HourColumn(
    forecast: HourlyForecast,
    alerts: List<WeatherAlert>,
    hasAnyRain: Boolean,
    isNow: Boolean,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()

    val dayLabel = when (forecast.dateTime.toLocalDate()) {
        today -> "Hoy"
        today.plusDays(1) -> "Mañana"
        // El locale va explícito: sin él se usa el del dispositivo y los días
        // salían en inglés ("Wed") en un teléfono configurado en otro idioma.
        else -> forecast.dateTime.format(SHORT_DAY)
            .replaceFirstChar { it.uppercase() }
            .removeSuffix(".")
    }

    val activeAlerts = remember(forecast, alerts) {
        val from = forecast.dateTime
        val to = from.plusHours(1)
        alerts.filter { it.overlaps(from, to) }
            .distinctBy { normalizeType(it.event) }
            .sortedByDescending { it.severity }
            .take(2)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(dayLabel, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
        Text(
            if (isNow) "Ahora" else forecast.dateTime.format(HOUR),
            color = if (isNow) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = if (isNow) FontWeight.W600 else FontWeight.Normal,
        )

        Spacer(Modifier.height(6.dp))

        Icon(
            imageVector = WeatherCode.fromCode(forecast.skyStateCode).icon.toImageVector(),
            contentDescription = forecast.skyDescription,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )

        // La fila de probabilidad solo ocupa sitio si alguna hora tiene lluvia,
        // para que el carrusel no quede con un hueco vacío en días secos.
        if (hasAnyRain) {
            Spacer(Modifier.height(4.dp))
            val probability = forecast.precipitationProbability ?: 0
            Text(
                if (probability > 0) "$probability%" else "",
                color = Color(0xFF64B5F6),
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            forecast.windDirectionDegrees?.let { degrees ->
                WindArrow(
                    degrees = degrees,
                    color = windColor(forecast.windSpeed),
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                forecast.windSpeed?.let { "$it km/h" }.orEmpty(),
                color = windColor(forecast.windSpeed),
                fontSize = 10.sp,
            )
        }

        if (activeAlerts.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row {
                activeAlerts.forEach { alert ->
                    Icon(
                        // El icono del fenómeno concreto (viento, costeros,
                        // nieve…) dice mucho más que un triángulo genérico.
                        imageVector = iconForType(normalizeType(alert.event)),
                        contentDescription = alert.event,
                        tint = alert.level.toColor(),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

private fun windColor(speed: Int?): Color = when {
    speed == null -> Color.White.copy(alpha = 0.7f)
    speed >= 70 -> Color(0xFFEF5350)
    speed >= 40 -> Color(0xFFFFB74D)
    else -> Color.White.copy(alpha = 0.7f)
}

/**
 * Gráfico de temperatura y punto de rocío.
 *
 * La línea de temperatura va con un degradado según el valor de cada hora, y
 * las etiquetas se alternan —temperatura en índices pares, rocío en impares—
 * para que no se solapen cuando ambas curvas se juntan.
 */
@Composable
private fun TemperatureChart(
    forecasts: List<HourlyForecast>,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()

    Canvas(modifier) {
        val temps = forecasts.mapNotNull { it.temperature?.toFloat() }
        if (temps.isEmpty()) return@Canvas

        val dewPoints = forecasts.map { it.dewPoint?.toFloat() }
        val hasDew = dewPoints.any { it != null }

        var maxT = temps.max()
        var minT = temps.min()
        dewPoints.filterNotNull().forEach {
            if (it > maxT) maxT = it
            if (it < minT) minT = it
        }
        if (maxT == minT) {
            maxT += 1f
            minT -= 1f
        }
        // Margen para que las curvas no se peguen a los bordes.
        val span = maxT - minT
        maxT += span * 0.05f
        minT -= span * 0.05f

        val paddingTop = 10.dp.toPx()
        val paddingBottom = 25.dp.toPx()
        val chartHeight = size.height - paddingTop - paddingBottom
        val itemWidth = ITEM_WIDTH.toPx()
        val paddingLeft = CHART_PADDING_LEFT.toPx()

        fun yFor(value: Float) = paddingTop + chartHeight * (1f - (value - minT) / (maxT - minT))

        drawLegend(measurer, maxT, minT, paddingTop, paddingBottom)

        val guide = Color.White.copy(alpha = 0.1f)
        drawLine(guide, Offset(paddingLeft, paddingTop), Offset(size.width, paddingTop), 1f)
        drawLine(
            guide,
            Offset(paddingLeft, size.height - paddingBottom),
            Offset(size.width, size.height - paddingBottom),
            1f,
        )

        val points = forecasts.mapIndexed { i, forecast ->
            Offset(
                paddingLeft + (i + 0.5f) * itemWidth,
                yFor(forecast.temperature?.toFloat() ?: minT),
            )
        }

        // Separadores verticales por hora.
        points.forEach { point ->
            drawLine(
                Color.White.copy(alpha = 0.03f),
                Offset(point.x, 0f),
                Offset(point.x, size.height),
                1f,
            )
        }

        if (hasDew) {
            drawDewCurve(measurer, forecasts, dewPoints, points, ::yFor, minT)
        }

        drawTemperatureCurve(measurer, forecasts, points, paddingTop)
    }
}

private fun DrawScope.drawLegend(
    measurer: TextMeasurer,
    maxT: Float,
    minT: Float,
    paddingTop: Float,
    paddingBottom: Float,
) {
    val style = TextStyle(color = Color.White.copy(alpha = 0.54f), fontSize = 10.sp)

    val maxText = measurer.measure("${maxT.toInt()}°", style)
    drawText(maxText, topLeft = Offset(8.dp.toPx(), paddingTop - maxText.size.height / 2))

    val minText = measurer.measure("${minT.toInt()}°", style)
    drawText(
        minText,
        topLeft = Offset(8.dp.toPx(), size.height - paddingBottom - minText.size.height / 2),
    )
}

private fun DrawScope.drawDewCurve(
    measurer: TextMeasurer,
    forecasts: List<HourlyForecast>,
    dewValues: List<Float?>,
    tempPoints: List<Offset>,
    yFor: (Float) -> Float,
    minT: Float,
) {
    val dewPoints = dewValues.mapIndexed { i, value ->
        Offset(tempPoints[i].x, yFor(value ?: minT))
    }

    // Línea discontinua trazada a mano: recorre cada segmento alternando
    // tramo pintado y hueco, que es más barato que extraer sub-paths.
    val dash = 6.dp.toPx()
    val gap = 4.dp.toPx()
    val color = DEW_COLOR.copy(alpha = 0.7f)

    for (i in 0 until dewPoints.size - 1) {
        val from = dewPoints[i]
        val to = dewPoints[i + 1]
        val delta = to - from
        val length = kotlin.math.hypot(delta.x, delta.y)
        if (length == 0f) continue

        val unit = Offset(delta.x / length, delta.y / length)
        var walked = 0f
        var drawing = true
        while (walked < length) {
            val step = if (drawing) dash else gap
            val end = minOf(walked + step, length)
            if (drawing) {
                drawLine(
                    color,
                    from + unit * walked,
                    from + unit * end,
                    1.5f,
                    cap = StrokeCap.Round,
                )
            }
            walked += step
            drawing = !drawing
        }
    }

    dewPoints.forEachIndexed { i, point ->
        if (dewValues[i] != null) drawCircle(DEW_COLOR, 1.5.dp.toPx(), point)
    }

    // Etiquetas en índices impares, intercaladas con las de temperatura.
    val style = TextStyle(color = DEW_COLOR, fontSize = 10.sp, fontWeight = FontWeight.W500)
    for (i in 1 until dewPoints.size step 2) {
        val value = forecasts[i].dewPoint ?: continue
        val text = measurer.measure("$value°", style)
        drawText(
            text,
            topLeft = Offset(
                dewPoints[i].x - text.size.width / 2,
                dewPoints[i].y + 6.dp.toPx(),
            ),
        )
    }
}

private fun DrawScope.drawTemperatureCurve(
    measurer: TextMeasurer,
    forecasts: List<HourlyForecast>,
    points: List<Offset>,
    paddingTop: Float,
) {
    // Curva suave: cada tramo es una cúbica con los tiradores en el punto medio,
    // que evita los picos angulosos de unir los puntos con rectas.
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val midX = (p0.x + p1.x) / 2
            cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
        }
    }

    val colors = forecasts.map {
        TemperatureColors.forTemperature(it.temperature?.toFloat() ?: 0f)
    }
    val brush = if (colors.size >= 2) {
        Brush.horizontalGradient(colors, startX = points.first().x, endX = points.last().x)
    } else {
        Brush.horizontalGradient(listOf(colors.first(), colors.first()))
    }

    // Relleno tenue bajo la curva.
    val fill = Path().apply {
        addPath(path)
        lineTo(points.last().x, size.height)
        lineTo(points.first().x, size.height)
        close()
    }
    drawPath(
        fill,
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
            startY = paddingTop,
            endY = size.height,
        ),
    )

    drawPath(path, brush, style = Stroke(width = 2.5.dp.toPx()))

    val style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    points.forEachIndexed { i, point ->
        val temp = forecasts[i].temperature ?: return@forEachIndexed
        if (i % 2 == 0) {
            val text = measurer.measure("$temp°", style)
            drawText(
                text,
                topLeft = Offset(point.x - text.size.width / 2, point.y + 8.dp.toPx()),
            )
        }
        drawCircle(Color.White, 2.5.dp.toPx(), point)
    }
}

/** Flecha que apunta en la dirección desde la que sopla el viento. */
@Composable
fun WindArrow(degrees: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        // El dato meteorológico indica de dónde viene el viento, así que se
        // suman 180° para que la punta muestre hacia dónde va.
        val radians = Math.toRadians((degrees + 180).toDouble())
        val direction = Offset(
            kotlin.math.sin(radians).toFloat(),
            -kotlin.math.cos(radians).toFloat(),
        )
        val tip = center + direction * radius
        val tail = center - direction * radius

        drawLine(color, tail, tip, 1.5f, cap = StrokeCap.Round)

        // Punta de flecha: dos trazos cortos girados respecto a la dirección.
        listOf(140.0, -140.0).forEach { angle ->
            val a = Math.toRadians(angle)
            val rotated = Offset(
                (direction.x * kotlin.math.cos(a) - direction.y * kotlin.math.sin(a)).toFloat(),
                (direction.x * kotlin.math.sin(a) + direction.y * kotlin.math.cos(a)).toFloat(),
            )
            drawLine(color, tip, tip + rotated * (radius * 0.7f), 1.5f, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun SectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.W600)
    }
}

/** Barra de rango de temperaturas al estilo iOS. */
@Composable
fun TemperatureRangeBar(
    min: Int,
    max: Int,
    globalMin: Int,
    globalMax: Int,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val span = (globalMax - globalMin).coerceAtLeast(1).toFloat()
    val startFraction = ((min - globalMin) / span).coerceIn(0f, 1f)
    val endFraction = ((max - globalMin) / span).coerceIn(0f, 1f)

    Canvas(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Color.White.copy(alpha = 0.12f)),
    ) {
        val left = size.width * startFraction
        val right = size.width * endFraction
        if (right <= left) return@Canvas

        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(
                    TemperatureColors.forTemperature(min.toFloat()),
                    TemperatureColors.forTemperature(max.toFloat()),
                ),
                startX = left,
                endX = right,
            ),
            topLeft = Offset(left, 0f),
            size = androidx.compose.ui.geometry.Size(right - left, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
        )
    }
}
