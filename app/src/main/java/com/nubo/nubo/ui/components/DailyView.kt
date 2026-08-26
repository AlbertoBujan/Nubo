package com.nubo.nubo.ui.components

import com.nubo.nubo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.domain.model.DailyForecast
import com.nubo.nubo.domain.model.WeatherAlert
import com.nubo.nubo.domain.weather.AirQualityBand
import com.nubo.nubo.domain.weather.UvBand
import com.nubo.nubo.domain.weather.WeatherCode
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Predicción de los próximos días, una fila por día. */
@Composable
fun DailyView(
    forecasts: List<DailyForecast>,
    alerts: List<WeatherAlert>,
    /** Día de hoy en el sitio, que no tiene por qué ser el del teléfono. */
    today: LocalDate,
    /** Peor índice de calidad del aire de cada día; falta donde el modelo no llega. */
    airQualityByDay: Map<LocalDate, Int> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    if (forecasts.isEmpty()) return

    // Un solo día abierto a la vez. El estado vive aquí, en la lista, y no
    // dentro de cada fila: con un abierto por fila habría que ir cerrando los
    // demás a mano, y bastaría olvidarse de uno para tener dos desplegados.
    var expandedDate by remember { mutableStateOf<LocalDate?>(null) }

    // Rango global para que la barra de cada día sea comparable con las demás.
    val globalMin = remember(forecasts) { forecasts.mapNotNull { it.tempMin }.minOrNull() ?: 0 }
    val globalMax = remember(forecasts) { forecasts.mapNotNull { it.tempMax }.maxOrNull() ?: 1 }

    Column(modifier.fillMaxWidth()) {
        SectionTitle(
            icon = Icons.Outlined.CalendarMonth,
            text = stringResource(R.string.next_days),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        forecasts.forEach { forecast ->
            DailyRow(
                forecast = forecast,
                alerts = alerts,
                globalMin = globalMin,
                globalMax = globalMax,
                today = today,
                airQuality = airQualityByDay[forecast.date],
                expanded = expandedDate == forecast.date,
                onToggle = { expandedDate = toggledExpansion(expandedDate, forecast.date) },
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun DailyRow(
    forecast: DailyForecast,
    alerts: List<WeatherAlert>,
    globalMin: Int,
    globalMax: Int,
    today: LocalDate,
    airQuality: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val isToday = forecast.date == today
    // Cerrada apunta a la derecha y abierta hacia abajo, girando 90°. La
    // flecha va **siguiendo al contenido**: cerrada señala hacia donde está
    // recogido, abierta señala lo que acaba de aparecer debajo. Con el par
    // abajo/arriba, la posición abierta apunta en dirección contraria a lo que
    // ha desplegado, y con siete filas idénticas siete flechas hacia abajo se
    // leen más como "sigue bajando" que como "esto se abre".
    val chevronTurn by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")

    // Color del aviso más grave que solapa con este día, si lo hay.
    val alertColor = remember(forecast, alerts) {
        val startOfDay = forecast.date.atStartOfDay()
        val endOfDay = startOfDay.plusDays(1)
        alerts.filter { it.overlaps(startOfDay, endOfDay) }
            .maxByOrNull { it.severity }
            ?.level
            ?.toColor()
    }

    // El punto va fuera de la GlassCard, no dentro: la tarjeta recorta su
    // contenido con las esquinas redondeadas, así que un punto interior se
    // vería cortado justo donde tiene que verse entero.
    Box(Modifier.fillMaxWidth()) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            tint = Color.White.copy(alpha = if (isToday) 0.12f else 0.06f),
            borderColor = Color.White.copy(alpha = 0.10f),
            contentPadding = 0.dp,
        ) {
            Column(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dayLabel(forecast.date, today),
                    color = if (isToday) Color.White else Color.White.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    fontWeight = if (isToday) FontWeight.W600 else FontWeight.Normal,
                    modifier = Modifier.width(92.dp),
                )

                Icon(
                    imageVector = WeatherCode.fromCode(forecast.skyStateCode).icon.toImageVector(),
                    contentDescription = stringResource(
                        WeatherCode.fromCode(forecast.skyStateCode).description.labelRes,
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )

                Spacer(Modifier.width(10.dp))

                Box(Modifier.width(44.dp)) {
                    val probability = forecast.precipitationProbability ?: 0
                    if (probability > 0) {
                        Text(
                            "$probability%",
                            color = Color(0xFF64B5F6),
                            fontSize = 13.sp,
                        )
                    }
                }

                // Pegada a la barra por la derecha, como la maxima lo esta por la
                // izquierda: las dos son los extremos del rango que dibuja la barra.
                Text(
                    forecast.tempMin?.let { "$it°" } ?: "--",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )

                TemperatureRangeBar(
                    min = forecast.tempMin ?: globalMin,
                    max = forecast.tempMax ?: globalMax,
                    globalMin = globalMin,
                    globalMax = globalMax,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                )

                Text(
                    forecast.tempMax?.let { "$it°" } ?: "--",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    modifier = Modifier.width(40.dp),
                )

                // Sin flecha, nadie descubre que la fila se abre.
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(
                        if (expanded) R.string.collapse else R.string.expand,
                    ),
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronTurn),
                )
            }

            AnimatedVisibility(expanded) {
                DayConditions(forecast = forecast, airQuality = airQuality)
            }
            }
        }

        // Punto del nivel más grave, montado sobre la esquina redondeada:
        // ahí destaca sin robarle sitio a ningún dato de la fila.
        if (alertColor != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .size(ALERT_DOT_SIZE)
                    .clip(CircleShape)
                    .background(alertColor),
            )
        }
    }
}

/**
 * Condiciones resumidas del día, dentro de la fila desplegada.
 *
 * Son valores **del día entero**, no del momento: UV máximo, sensación mínima
 * y máxima, humedad media y el peor índice de calidad del aire. Hoy se lee
 * igual que el resto de días a propósito — con dos significados distintos en
 * tarjetas idénticas habría que explicar cuál es cuál.
 */
@Composable
private fun DayConditions(forecast: DailyForecast, airQuality: Int?) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
        Spacer(Modifier.height(14.dp))

        Row {
            val band = airQuality?.let { AirQualityBand.forAqi(it) }
            DayTile(
                label = stringResource(R.string.air_quality),
                value = airQuality?.toString(),
                caption = band?.let { stringResource(it.labelRes) },
                captionColor = band?.toColor(),
            )
            val uv = forecast.uvIndexMax
            DayTile(
                label = stringResource(R.string.uv_max),
                value = uv?.roundToInt()?.toString(),
                caption = uv?.let { stringResource(UvBand.forIndex(it).labelRes) },
                captionColor = uv?.let { UvBand.forIndex(it).toColor() },
            )
        }

        Spacer(Modifier.height(16.dp))

        Row {
            DayTile(
                label = stringResource(R.string.humidity_mean),
                value = forecast.humidityMean?.let { stringResource(R.string.percent, it) },
                caption = null,
            )
            DayTile(
                label = stringResource(R.string.apparent),
                value = apparentRange(forecast),
                caption = null,
            )
        }
    }
}

/**
 * Qué día queda abierto al tocar uno.
 *
 * Tocar el que ya está abierto lo cierra; tocar cualquier otro cierra el
 * anterior y abre ese. Nunca hay dos desplegados: no es que se cierren en
 * cadena, es que solo se guarda un día.
 */
internal fun toggledExpansion(current: LocalDate?, tapped: LocalDate): LocalDate? =
    if (current == tapped) null else tapped

/** "14° a 20°", o solo el valor que haya, o nada. */
internal fun apparentRange(forecast: DailyForecast): String? {
    val min = forecast.apparentMin
    val max = forecast.apparentMax
    return when {
        min != null && max != null -> "$min° a $max°"
        max != null -> "$max°"
        min != null -> "$min°"
        else -> null
    }
}

/** Una casilla del desplegable: rótulo, valor y matiz opcional. */
@Composable
private fun RowScope.DayTile(
    label: String,
    value: String?,
    caption: String?,
    captionColor: Color? = null,
) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label.uppercase(),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            fontWeight = FontWeight.W600,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            // Un guion, y no ocultar la casilla: la calidad del aire se acaba
            // hacia el quinto día y la rejilla no debe cambiar de forma según
            // hasta dónde llegue el modelo.
            value ?: "—",
            color = if (value != null) Color.White else Color.White.copy(alpha = 0.35f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
        )
        if (caption != null) {
            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (captionColor != null) {
                    Spacer(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(captionColor),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    caption,
                    color = captionColor ?: Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Diámetro del punto de aviso de la fila diaria. */
private val ALERT_DOT_SIZE = 9.dp

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.today)
    today.plusDays(1) -> stringResource(R.string.tomorrow)
    // Sin locale explícito: el nombre del día sale en el idioma del teléfono,
    // que es justo lo que hay que hacer ahora que la interfaz se traduce.
    else -> date.dayOfWeek
        .getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { it.uppercase(Locale.getDefault()) }
}
