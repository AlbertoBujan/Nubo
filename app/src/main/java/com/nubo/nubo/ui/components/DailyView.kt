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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
    /** Dato que se estrena, o nulo si no hay nada que estrenar. */
    animateFrom: Any? = null,
    modifier: Modifier = Modifier,
) {
    if (forecasts.isEmpty()) return

    // Un solo día abierto a la vez. El estado vive aquí, en la lista, y no
    // dentro de cada fila: con un abierto por fila habría que ir cerrando los
    // demás a mano, y bastaría olvidarse de uno para tener dos desplegados.
    var expandedDate by remember { mutableStateOf<LocalDate?>(null) }

    // Qué días han estrenado ya sus casillas. Abrir, cerrar y volver a abrir la
    // misma tarjeta no vuelve a contar: la animación cuenta que esas cifras se
    // ven por primera vez, y a la tercera vez ya solo estorba. Se olvida con
    // cada dato nuevo, que es cuando vuelve a haber algo que estrenar.
    val animatedDays = remember(animateFrom) { mutableStateMapOf<LocalDate, Boolean>() }

    // Rango global para que la barra de cada día sea comparable con las demás.
    val globalMin = remember(forecasts) { forecasts.mapNotNull { it.tempMin }.minOrNull() ?: 0 }
    val globalMax = remember(forecasts) { forecasts.mapNotNull { it.tempMax }.maxOrNull() ?: 1 }

    Column(modifier.fillMaxWidth()) {
        SectionTitle(
            icon = Icons.Outlined.CalendarMonth,
            text = stringResource(R.string.next_days),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        forecasts.forEachIndexed { index, forecast ->
            DailyRow(
                forecast = forecast,
                animateFrom = animateFrom,
                // Escalonadas como las horas: la lista se llena de arriba
                // abajo en vez de encenderse entera de golpe.
                animationDelay = index * ROW_STAGGER_MILLIS,
                alerts = alerts,
                globalMin = globalMin,
                globalMax = globalMax,
                today = today,
                airQuality = airQualityByDay[forecast.date],
                expanded = expandedDate == forecast.date,
                tilesAnimateFrom = forecast.date.takeIf { animatedDays[it] != true },
                onTilesAnimated = { animatedDays[forecast.date] = true },
                onToggle = { expandedDate = toggledExpansion(expandedDate, forecast.date) },
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun DailyRow(
    forecast: DailyForecast,
    animateFrom: Any?,
    animationDelay: Int,
    alerts: List<WeatherAlert>,
    globalMin: Int,
    globalMax: Int,
    today: LocalDate,
    airQuality: Int?,
    expanded: Boolean,
    /** Fecha si sus casillas aún no se han estrenado, o nulo si ya lo hicieron. */
    tilesAnimateFrom: LocalDate?,
    onTilesAnimated: () -> Unit,
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
                    // Los márgenes de esta fila están apretados a conciencia:
                    // todo lo que se recorta aquí se lo lleva la barra, que es
                    // lo único que crece con el dato y lo único que se compara
                    // de un día a otro.
                    .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dayLabel(forecast.date, today),
                    color = if (isToday) Color.White else Color.White.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    fontWeight = if (isToday) FontWeight.W600 else FontWeight.Normal,
                    maxLines = 1,
                    // Da para el día más largo —"Wednesday", "Miércoles"— con
                    // un dedo de aire. Es fijo para que los iconos de todas las
                    // filas queden en la misma columna.
                    modifier = Modifier.width(88.dp),
                )

                Icon(
                    imageVector = WeatherCode.fromCode(forecast.skyStateCode).icon.toImageVector(),
                    contentDescription = stringResource(
                        WeatherCode.fromCode(forecast.skyStateCode).description.labelRes,
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )

                Spacer(Modifier.width(8.dp))

                Box(Modifier.width(38.dp)) {
                    val chance = forecast.precipitationProbability ?: 0
                    val counted = countUpTo(chance, animateFrom, animationDelay) ?: 0
                    if (chance > 0) {
                        Text(
                            "$counted%",
                            color = Color(0xFF64B5F6),
                            fontSize = 13.sp,
                        )
                    }
                }

                // Pegada a la barra por la derecha, como la maxima lo esta por la
                // izquierda: las dos son los extremos del rango que dibuja la barra.
                val units = LocalUnits.current
                val low = countUpTo(
                    forecast.tempMin?.let(units::temperature),
                    animateFrom,
                    animationDelay,
                )
                Text(
                    low?.let { "$it°" } ?: "--",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(38.dp),
                )

                TemperatureRangeBar(
                    min = forecast.tempMin ?: globalMin,
                    max = forecast.tempMax ?: globalMax,
                    globalMin = globalMin,
                    globalMax = globalMax,
                    reveal = fillProgress(animateFrom, animationDelay),
                    height = 8.dp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 5.dp),
                )

                val high = countUpTo(
                    forecast.tempMax?.let(units::temperature),
                    animateFrom,
                    animationDelay,
                )
                Text(
                    high?.let { "$it°" } ?: "--",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    modifier = Modifier.width(38.dp),
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
                DayConditions(
                    forecast = forecast,
                    airQuality = airQuality,
                    animateFrom = tilesAnimateFrom,
                    onAnimated = onTilesAnimated,
                )
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
private fun DayConditions(
    forecast: DailyForecast,
    airQuality: Int?,
    animateFrom: LocalDate?,
    onAnimated: () -> Unit,
) {
    // Aquí lo que se estrena es el propio despliegue: estas cifras no estaban
    // en pantalla hasta que se ha tocado la fila. Se captura al abrirse y se da
    // por gastado en el acto, así que la segunda vez que se abre este mismo día
    // ya llega nulo y los números salen puestos.
    val trigger = remember(forecast.date) { animateFrom }
    val units = LocalUnits.current
    LaunchedEffect(trigger) { if (trigger != null) onAnimated() }

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
        Spacer(Modifier.height(14.dp))

        Row {
            val band = airQuality?.let { AirQualityBand.forAqi(it) }
            DayTile(
                label = stringResource(R.string.air_quality),
                value = countUpTo(airQuality, trigger)?.toString(),
                caption = band?.let { stringResource(it.labelRes) },
                captionColor = band?.toColor(),
            )
            val uv = forecast.uvIndexMax
            DayTile(
                label = stringResource(R.string.uv_max),
                value = countUpTo(uv?.roundToInt(), trigger)?.toString(),
                caption = uv?.let { stringResource(UvBand.forIndex(it).labelRes) },
                captionColor = uv?.let { UvBand.forIndex(it).toColor() },
            )
        }

        Spacer(Modifier.height(16.dp))

        Row {
            DayTile(
                label = stringResource(R.string.humidity_mean),
                value = countUpTo(forecast.humidityMean, trigger)
                    ?.let { stringResource(R.string.percent, it) },
                caption = null,
            )
            DayTile(
                label = stringResource(R.string.apparent),
                value = apparentRange(
                    countUpTo(forecast.apparentMin?.let(units::temperature), trigger),
                    countUpTo(forecast.apparentMax?.let(units::temperature), trigger),
                ),
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
internal fun apparentRange(forecast: DailyForecast): String? =
    apparentRange(forecast.apparentMin, forecast.apparentMax)

internal fun apparentRange(min: Int?, max: Int?): String? {
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
/** Lo que cada fila espera respecto a la de encima. */
private const val ROW_STAGGER_MILLIS = 45

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
