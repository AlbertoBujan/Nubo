package com.nubo.nubo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.domain.model.DailyForecast
import com.nubo.nubo.domain.model.WeatherAlert
import com.nubo.nubo.domain.weather.WeatherCode
import java.time.LocalDate
import java.util.Locale

/** Predicción de los próximos días, una fila por día. */
@Composable
fun DailyView(
    forecasts: List<DailyForecast>,
    alerts: List<WeatherAlert>,
    /** Día de hoy en el sitio, que no tiene por qué ser el del teléfono. */
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    if (forecasts.isEmpty()) return

    // Rango global para que la barra de cada día sea comparable con las demás.
    val globalMin = remember(forecasts) { forecasts.mapNotNull { it.tempMin }.minOrNull() ?: 0 }
    val globalMax = remember(forecasts) { forecasts.mapNotNull { it.tempMax }.maxOrNull() ?: 1 }

    Column(modifier.fillMaxWidth()) {
        SectionTitle(
            icon = Icons.Outlined.CalendarMonth,
            text = "Próximos días",
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        forecasts.forEach { forecast ->
            DailyRow(
                forecast = forecast,
                alerts = alerts,
                globalMin = globalMin,
                globalMax = globalMax,
                today = today,
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
) {
    val isToday = forecast.date == today

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    contentDescription = forecast.skyDescription,
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

                Text(
                    forecast.tempMin?.let { "$it°" } ?: "--",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 15.sp,
                    modifier = Modifier.width(38.dp),
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

/** Diámetro del punto de aviso de la fila diaria. */
private val ALERT_DOT_SIZE = 9.dp

private fun dayLabel(date: LocalDate, today: LocalDate): String {
    return when (date) {
        today -> "Hoy"
        today.plusDays(1) -> "Mañana"
        else -> date.dayOfWeek
            .getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("es-ES"))
            .replaceFirstChar { it.uppercase() }
    }
}
