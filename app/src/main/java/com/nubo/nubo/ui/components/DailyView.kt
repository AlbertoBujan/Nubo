package com.nubo.nubo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.R
import com.nubo.nubo.domain.model.DailyForecast
import com.nubo.nubo.domain.model.WeatherAlert
import com.nubo.nubo.domain.weather.AirQualityBand
import com.nubo.nubo.domain.weather.DayAlert
import com.nubo.nubo.domain.weather.UvBand
import com.nubo.nubo.domain.weather.WeatherCode
import com.nubo.nubo.domain.weather.dayAlerts
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

    val haptics = LocalHapticFeedback.current



    // Rango global para que la barra de cada día sea comparable con las demás.
    val globalMin = remember(forecasts) { forecasts.mapNotNull { it.tempMin }.minOrNull() ?: 0 }
    val globalMax = remember(forecasts) { forecasts.mapNotNull { it.tempMax }.maxOrNull() ?: 1 }

    BoxWithConstraints(modifier.fillMaxWidth()) {
    // Con la interfaz agrandada —o en una pantalla estrecha— las columnas fijas
    // se comen el sitio de la barra, que es lo único de la fila que lleva un
    // dato. Se mide **aquí**, con lo que la fila tiene de verdad: la
    // configuración del sistema no sabe nada del ajuste de tamaño, así que
    // preguntarle a ella daba siempre el mismo ancho y la fila se rompía.
    val compact = maxWidth < COMPACT_WIDTH

    Column(Modifier.fillMaxWidth()) {
        SectionTitle(
            icon = Icons.Outlined.CalendarMonth,
            text = stringResource(R.string.next_days),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        forecasts.forEachIndexed { index, forecast ->
            DailyRow(
                forecast = forecast,
                compact = compact,
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
                onToggle = {
                    val next = toggledExpansion(expandedDate, forecast.date)
                    // Abrir y cerrar se notan distinto, que es lo que
                    // distinguen estos dos toques.
                    haptics.performHapticFeedback(
                        if (next == null) {
                            HapticFeedbackType.ToggleOff
                        } else {
                            HapticFeedbackType.ToggleOn
                        },
                    )
                    expandedDate = next
                },
            )
            Spacer(Modifier.height(6.dp))
        }
    }
    }
}

@Composable
private fun DailyRow(
    forecast: DailyForecast,
    /** Sin sitio: día abreviado y columnas justas. */
    compact: Boolean,
    animateFrom: Any?,
    animationDelay: Int,
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

    // Un aviso por fenómeno, con su peor nivel y lo más grave primero.
    val dayBadges = remember(forecast, alerts) { dayAlerts(alerts, forecast.date) }

    // Dónde cae cada disco y dónde empieza la tarjeta, para que los tramos de
    // la franja acaben justo entre un disco y el siguiente. Se miden en vez
    // de calcularse porque lo que hay que casar —el alto de la cabecera, el
    // de cada casilla, el aire entre discos— depende del tamaño de interfaz,
    // del ancho de la pantalla y del idioma. Repartir en tramos iguales deja
    // la franja cerca y nunca encima: con cuatro avisos el disco rojo caía ya
    // sobre el tramo naranja.
    //
    // Las dos medidas se toman respecto a la raíz y se restan: así la resta
    // sigue valiendo mientras la lista se desplaza, porque las dos se vuelven
    // a tomar en la misma pasada de colocación.
    var cardTopY by remember { mutableFloatStateOf(Float.NaN) }
    var cardHeight by remember { mutableFloatStateOf(0f) }
    val dotCenters = remember(dayBadges) { mutableStateMapOf<Int, Float>() }

    val barCuts = alertBarCuts(
        dotCenters = dayBadges.indices.map { dotCenters[it] },
        cardTopY = cardTopY,
        cardHeight = cardHeight,
    )

    // Los iconos van fuera de la GlassCard, no dentro: la tarjeta recorta su
    // contenido con las esquinas redondeadas, así que ahí dentro se verían
    // cortados justo donde tienen que verse enteros.
    Box(Modifier.fillMaxWidth()) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            tint = Color.White.copy(alpha = if (isToday) 0.12f else 0.06f),
            borderColor = Color.White.copy(alpha = 0.10f),
            contentPadding = 0.dp,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        cardTopY = it.positionInRoot().y
                        cardHeight = it.size.height.toFloat()
                    },
            ) {
            Column(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Los márgenes de esta fila están apretados a conciencia:
                    // todo lo que se recorta aquí se lo lleva la barra, que es
                    // lo único que crece con el dato y lo único que se compara
                    // de un día a otro.
                    .padding(
                        start = if (compact) 10.dp else 14.dp,
                        end = if (compact) 6.dp else 8.dp,
                        top = 12.dp,
                        bottom = 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dayLabel(forecast.date, today, compact),
                    color = if (isToday) Color.White else Color.White.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    fontWeight = if (isToday) FontWeight.W600 else FontWeight.Normal,
                    maxLines = 1,
                    // Da para el día más largo —"Wednesday", "Miércoles"— con
                    // un dedo de aire. Es fijo para que los iconos de todas las
                    // filas queden en la misma columna.
                    modifier = Modifier.width(if (compact) 62.dp else 88.dp),
                )

                Icon(
                    imageVector = WeatherCode.fromCode(forecast.skyStateCode).icon.toImageVector(),
                    contentDescription = stringResource(
                        WeatherCode.fromCode(forecast.skyStateCode).description.labelRes,
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 22.dp else 24.dp),
                )

                Spacer(Modifier.width(if (compact) 6.dp else 8.dp))

                Box(Modifier.width(if (compact) 30.dp else 38.dp)) {
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
                    modifier = Modifier.width(if (compact) 32.dp else 38.dp),
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
                        .padding(horizontal = if (compact) 4.dp else 5.dp),
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
                    modifier = Modifier.width(if (compact) 32.dp else 38.dp),
                )

                // Sin flecha, nadie descubre que la fila se abre.
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(
                        if (expanded) R.string.collapse else R.string.expand,
                    ),
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(if (compact) 18.dp else 20.dp)
                        .rotate(chevronTurn),
                )
            }

            AnimatedVisibility(expanded) {
                DayConditions(
                    forecast = forecast,
                    airQuality = airQuality,
                    alerts = dayBadges,
                    onDotCenter = { index, y -> dotCenters[index] = y },
                    onDotsGone = dotCenters::clear,
                )
            }
            }

            // La franja va **encima** del contenido y no antes que él: así no
            // le quita ni un píxel a la fila, que anda justa de ancho, y se
            // mete en el margen izquierdo que ya había. La recorta la propia
            // tarjeta, que es lo que le da la curva de la esquina.
            if (dayBadges.isNotEmpty()) {
                val barWidth = ALERT_BAR_WIDTH
                Canvas(Modifier.matchParentSize()) {
                    val width = barWidth.toPx()
                    val cuts = if (barCuts.size == dayBadges.size - 1) {
                        barCuts
                    } else {
                        // Cerrada no hay discos que medir, y a partes iguales
                        // es lo que la fila quiere decir ahí: cuántos avisos
                        // hay y de qué nivel.
                        List(dayBadges.size - 1) { (it + 1f) / dayBadges.size }
                    }
                    var top = 0f
                    cuts.forEachIndexed { index, cut ->
                        val bottom = (cut * size.height).coerceIn(top, size.height)
                        drawRect(
                            color = dayBadges[index].level.toColor(),
                            topLeft = Offset(0f, top),
                            size = Size(width, bottom - top),
                        )
                        top = bottom
                    }
                    drawRect(
                        color = dayBadges.last().level.toColor(),
                        topLeft = Offset(0f, top),
                        size = Size(width, size.height - top),
                    )
                }
            }
            }
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
 *
 * Las cifras salen **puestas**, sin contar. Lo que se estrena aquí es el
 * despliegue, y el despliegue ya se anima solo: encima de una fila que se abre,
 * cuatro números corriendo a la vez piden atención donde solo hacía falta
 * leerlos.
 */
@Composable
private fun DayConditions(
    forecast: DailyForecast,
    airQuality: Int?,
    alerts: List<DayAlert>,
    onDotCenter: (Int, Float) -> Unit = { _, _ -> },
    onDotsGone: () -> Unit = {},
) {
    val units = LocalUnits.current

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
        Spacer(Modifier.height(14.dp))

        // Los discos ocupan **una columna propia** a la izquierda de la
        // rejilla, no renglones encima de ella. Llevaban el nombre y el nivel
        // escritos al lado, y las dos cosas sobraban: el nivel ya lo dice el
        // color —el mismo de la franja— y el fenómeno ya lo dice el símbolo,
        // igual que lo decía en la esquina. Cuatro renglones de texto eran la
        // mitad de lo que crecía la tarjeta al abrirla, y lo que crecía
        // empujaba la rejilla fuera de donde estaba al tocarla.
        //
        // De paso separa lo que la franja funde: dos avisos amarillos son un
        // solo tramo de color, y aquí son dos discos.
        Row(Modifier.fillMaxWidth()) {
            // La columna se reserva **siempre**, con avisos o sin ellos. Si
            // apareciera solo cuando los hay, la rejilla se correría a la
            // derecha al abrir un día con avisos y volvería a su sitio al
            // abrir el siguiente sin ellos: los mismos cuatro números
            // saltando de posición según el día que toques. Es el criterio
            // que ya sigue la casilla de calidad del aire, que enseña un
            // guion en vez de desaparecer cuando el modelo no llega.
            Box(
                modifier = Modifier
                    .width(ALERT_COLUMN_WIDTH)
                    .align(Alignment.CenterVertically),
            ) {
                if (alerts.isNotEmpty()) {
                    AlertDots(alerts, onDotCenter = onDotCenter, onDotsGone = onDotsGone)
                }
            }

            // La rejilla se queda **en filas y no en columnas**: la casilla de
            // calidad del aire pierde su banda cuando el modelo no llega —del
            // quinto día en adelante— y la de UV no, así que apiladas por
            // columnas la segunda fila quedaría a dos alturas distintas.
            Column(Modifier.weight(1f)) {
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
                        value = forecast.humidityMean
                            ?.let { stringResource(R.string.percent, it) },
                        caption = null,
                    )
                    DayTile(
                        label = stringResource(R.string.apparent),
                        value = apparentRange(
                            forecast.apparentMin?.let(units::temperature),
                            forecast.apparentMax?.let(units::temperature),
                        ),
                        caption = null,
                    )
                }
            }
        }
    }
}

/**
 * La columna de discos: un aviso cada uno, lo más grave arriba.
 *
 * El grupo va **centrado** sobre la rejilla —lo centra quien la coloca, con
 * `align`— y no pegado arriba: con un solo aviso el disco cae a la altura de
 * los números en vez de flotar sobre la primera etiqueta, y con cuatro la
 * columna sigue leyéndose como un grupo.
 */
@Composable
private fun AlertDots(
    alerts: List<DayAlert>,
    onDotCenter: (Int, Float) -> Unit,
    onDotsGone: () -> Unit,
) {
    // Al cerrarse la fila los discos dejan de colocarse, así que nadie vuelve
    // a avisar de dónde están: sin esto la franja seguiría partida por las
    // alturas de la última vez que estuvo abierta.
    DisposableEffect(Unit) { onDispose(onDotsGone) }

    Column {
        alerts.forEachIndexed { index, aviso ->
            if (index > 0) Spacer(Modifier.height(ALERT_DOT_GAP))
            Box(
                modifier = Modifier
                    .size(ALERT_BADGE_SIZE)
                    .clip(CircleShape)
                    .background(aviso.level.toColor())
                    .onGloballyPositioned {
                        onDotCenter(index, it.positionInRoot().y + it.size.height / 2f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconForType(aviso.type),
                    // Sin el texto al lado, el icono es lo único que queda, y
                    // el nivel solo se ve en el color: un lector de pantalla
                    // no ve colores, así que los dice los dos.
                    contentDescription = stringResource(
                        R.string.alert_badge,
                        stringResource(aviso.type.labelRes),
                        stringResource(aviso.level.labelRes),
                    ),
                    tint = ALERT_GLYPH_COLOR,
                    modifier = Modifier.size(ALERT_GLYPH_SIZE),
                )
            }
        }
    }
}

/**
 * Dónde cambia de color la franja, en fracción de la altura de la tarjeta.
 *
 * Cada tramo tiene que acabar **a medio camino entre su disco y el
 * siguiente**, que es lo que hace que cada color quede a la altura del aviso
 * que representa. El primero arranca arriba del todo y el último llega abajo
 * aunque no haya más discos: la franja es el borde de la tarjeta entera, no
 * una leyenda que se acaba donde se acaba la lista.
 *
 * Devuelve la lista vacía si falta algo por medir —la fila está cerrada, o es
 * el primer fotograma—, y entonces quien dibuja reparte a partes iguales. No
 * se devuelve ese reparto aquí porque son dos cosas distintas: esto es lo
 * medido, y lo otro es lo que se enseña mientras no hay medida.
 *
 * Las posiciones llegan **respecto a la raíz** y se restan contra el borde de
 * la tarjeta, porque las dos se vuelven a tomar en la misma pasada de
 * colocación y así la resta no se descuadra al desplazar la lista.
 */
internal fun alertBarCuts(
    dotCenters: List<Float?>,
    cardTopY: Float,
    cardHeight: Float,
): List<Float> {
    if (dotCenters.size < 2 || cardHeight <= 0f || cardTopY.isNaN()) return emptyList()
    val centers = dotCenters.map { it ?: return emptyList() }
    return centers
        .zipWithNext { above, below -> ((above + below) / 2f - cardTopY) / cardHeight }
        .map { it.coerceIn(0f, 1f) }
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

/**
 * La franja de colores del borde izquierdo.
 *
 * Cerrada, la fila solo dice **cuántos** avisos hay y de qué nivel, repartidos
 * en tramos iguales de arriba abajo. El qué se cuenta al desplegarla, que es
 * donde hay sitio: ahí cada tramo tiene su disco y su nombre al lado. Los
 * discos sueltos sobre la esquina, que fue el intento anterior, competían con
 * el nombre del día por el mismo rincón.
 */
private val ALERT_BAR_WIDTH = 5.dp

/** El disco del desplegable, con el símbolo del fenómeno dentro. */
private val ALERT_BADGE_SIZE = 17.dp

/**
 * El símbolo, dentro del disco y con aire a los lados.
 *
 * Por debajo de esto el icono de viento —tres líneas finas— se emborrona, que
 * es el mismo motivo por el que la flecha del carrusel se redibujó sólida.
 */
private val ALERT_GLYPH_SIZE = 11.dp

/** Lo que separa un disco del siguiente en la columna del desplegable. */
private val ALERT_DOT_GAP = 10.dp

/**
 * El hueco que la columna de discos le quita a la rejilla.
 *
 * Lo ocupa un día sin avisos igual que uno con cuatro: lo que se gana
 * enseñando la rejilla un poco más ancha los días tranquilos no compensa que
 * los números cambien de sitio al pasar de un día a otro.
 */
private val ALERT_COLUMN_WIDTH = ALERT_BADGE_SIZE + 14.dp

/**
 * Oscuro y no blanco.
 *
 * El nivel más frecuente con diferencia es el amarillo, y blanco sobre ese
 * amarillo casi no se ve. Es el mismo negro translúcido con el que el panel de
 * avisos escribe "AMARILLO" dentro de su etiqueta de color, así que además
 * las dos marcas del mismo aviso se parecen entre sí.
 */
private val ALERT_GLYPH_COLOR = Color.Black.copy(alpha = 0.75f)

/** Lo justo para que dos discos no se toquen. */
private val ALERT_ICON_GAP = 3.dp

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate, compact: Boolean): String = when (date) {
    today -> stringResource(R.string.today)
    today.plusDays(1) -> stringResource(R.string.tomorrow)
    // Sin locale explícito: el nombre del día sale en el idioma del teléfono,
    // que es justo lo que hay que hacer ahora que la interfaz se traduce. La
    // forma corta la da el propio `java.time`, así que "Mié" y "Wed" salen bien
    // sin recortar letras a mano.
    else -> date.dayOfWeek
        .getDisplayName(
            if (compact) java.time.format.TextStyle.SHORT else java.time.format.TextStyle.FULL,
            Locale.getDefault(),
        )
        .replaceFirstChar { it.uppercase(Locale.getDefault()) }
        .removeSuffix(".")
}

/**
 * Por debajo de este ancho la fila de un día pasa a su forma corta.
 *
 * Sale de sumar lo que ocupan las columnas fijas dejando a la barra un tramo
 * que todavía diga algo. Está por debajo de lo que mide la fila en un móvil
 * estrecho a tamaño normal —336 dp— a propósito: ahí el nombre entero todavía
 * entra, y abreviarlo sin necesidad sería peor.
 */
private val COMPACT_WIDTH = 320.dp
