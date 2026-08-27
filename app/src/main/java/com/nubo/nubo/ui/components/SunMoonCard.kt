package com.nubo.nubo.ui.components

import com.nubo.nubo.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Brightness1
import androidx.compose.material.icons.outlined.Brightness1
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nubo.nubo.domain.astro.MoonPhase
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import com.nubo.nubo.domain.astro.DayLength
import kotlin.math.abs

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
    /** Duración del día y su cambio desde ayer, para el reverso del sol. */
    dayLength: DayLength?,
    moonData: MoonData?,
    /** Próxima llena y próxima nueva, para el detalle que se abre al tocar. */
    nextFullMoon: LocalDateTime?,
    nextNewMoon: LocalDateTime?,
    /** Al sur del ecuador la luna se ve del revés. */
    southernSky: Boolean,
    /** Trayectorias reales; ver `SkyPath`. Vacías, se cae a una curva suave. */
    sunPath: List<Float>,
    moonPath: List<Float>,
    /** Hora local del sitio, contra la que se mide el trayecto recorrido. */
    nowThere: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    if (sunTimes == null) return

    val formatter = DateTimeFormatter.ofPattern("HH:mm")

    // IntrinsicSize.Min mide la más alta y estira la otra hasta igualarla, de
    // modo que las dos tarjetas quedan siempre simétricas aunque el nombre de
    // la fase lunar ocupe más que el título "Sol".
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var showDayLength by remember(sunTimes) { mutableStateOf(false) }

        ArcCard(
            title = stringResource(R.string.sun),
            icon = Icons.Outlined.WbSunny,
            iconTint = Color(0xFFFFC107),
            start = sunTimes.sunrise,
            end = sunTimes.sunset,
            startLabel = sunTimes.sunrise.format(formatter),
            endLabel = sunTimes.sunset.format(formatter),
            arcColor = Color(0xFFFFB300),
            now = nowThere,
            path = sunPath,
            onClick = dayLength?.let { { showDayLength = !showDayLength } },
            flipped = showDayLength,
            back = dayLength?.let { { DayLengthFacts(it) } },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )

        if (moonData?.moonrise != null && moonData.moonset != null) {
            // Se olvida al cambiar de día lunar: la tarjeta vuelve a enseñar
            // dónde está la luna, que es lo que se mira primero.
            var showPhases by remember(moonData) { mutableStateOf(false) }

            ArcCard(
                title = stringResource(moonData.phase.labelRes),
                // La luna se dibuja como está esta noche, no con un creciente
                // de catálogo: es el único icono de la app que sale del dato.
                iconContent = {
                    MoonPhaseIcon(
                        illumination = moonData.illumination,
                        waxing = moonData.cycle < 0.5,
                        mirrored = southernSky,
                        tint = Color(0xFFECEFF1),
                        modifier = Modifier.size(16.dp),
                    )
                },
                start = moonData.moonrise,
                end = moonData.moonset,
                startLabel = moonData.moonrise.format(formatter),
                endLabel = moonData.moonset.format(formatter),
                arcColor = Color(0xFF90A4AE),
                now = nowThere,
                path = moonPath,
                // Solo la luna se da la vuelta: del sol ya está todo en la
                // tarjeta, y de la luna lo que falta —cuándo tocan la llena y
                // la nueva— no cabe sin tapar el arco.
                onClick = { showPhases = !showPhases },
                flipped = showPhases,
                back = {
                    MoonMilestones(
                        nextFullMoon = nextFullMoon,
                        nextNewMoon = nextNewMoon,
                    )
                },
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
    icon: ImageVector? = null,
    iconTint: Color = Color.White,
    start: LocalDateTime,
    end: LocalDateTime,
    startLabel: String,
    endLabel: String,
    arcColor: Color,
    now: LocalDateTime,
    path: List<Float>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** Enseña [back] en lugar del arco. */
    flipped: Boolean = false,
    back: (@Composable () -> Unit)? = null,
    /** Dibujo propio en lugar de [icon]; lo usa la luna para su fase. */
    iconContent: (@Composable () -> Unit)? = null,
) {
    val progress = progressBetween(start, end, now)

    // Un reflejo cruza la tarjeta cada vez que cambia de cara. Empieza en 1
    // —o sea, terminado— para que no barra al entrar en pantalla: lo que tiene
    // que acompañar es el giro, no la aparición de la tarjeta.
    val sheen = remember { Animatable(1f) }
    var firstFace by remember { mutableStateOf(true) }
    LaunchedEffect(flipped) {
        if (firstFace) {
            firstFace = false
        } else {
            sheen.snapTo(0f)
            sheen.animateTo(1f, tween(SHEEN_MILLIS, easing = LinearEasing))
        }
    }

    GlassCard(
        // Sin realce de pulsación: el que pone el tema por defecto lo dibuja el
        // sistema **fuera** del recorte de Compose, así que salía cuadrado y
        // tapaba las esquinas — se veía como si la tarjeta perdiera el radio
        // mientras se mantiene el dedo. Recortar por delante no lo arregla,
        // porque ese dibujo no pasa por el recorte. Y no hace falta ninguno:
        // la tarjeta se da la vuelta al soltar, que es respuesta de sobra.
        modifier = if (onClick == null) {
            modifier
        } else {
            modifier
                // El recorte va delante del reflejo para que la banda no se
                // salga por las esquinas. Esto sí se puede recortar: lo dibuja
                // Compose dentro de la cadena, a diferencia del realce del
                // sistema que hubo que quitar.
                .clip(RoundedCornerShape(CARD_CORNER))
                .drawWithContent {
                    drawContent()
                    drawSheen(sheen.value)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
        },
        cornerRadius = CARD_CORNER,
        contentPadding = 12.dp,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconContent != null) {
                    iconContent()
                } else if (icon != null) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
                }
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

            // Las dos caras ocupan lo mismo, y por eso el alto va fijo: si el
            // reverso midiera distinto, la tarjeta crecería al darle la vuelta
            // y arrastraría a la del sol, que iguala su altura con esta.
            Crossfade(
                targetState = flipped && back != null,
                animationSpec = tween(FLIP_MILLIS),
                label = "moon",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FACE_HEIGHT),
            ) { showBack ->
                if (showBack) {
                    back?.invoke()
                } else {
                    Column {
                        Arc(
                            progress = progress,
                            color = arcColor,
                            path = path,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                startLabel,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                            )
                            Text(
                                endLabel,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
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

/**
 * El reverso de la tarjeta de la luna: cuándo tocan la próxima llena y la
 * próxima nueva.
 *
 * Es la información que la tarjeta no puede enseñar a la vez que el arco, y la
 * que de verdad se pregunta uno mirando la luna: no en qué fase está —eso lo
 * dice el título, que no se va— sino cuándo llega la siguiente que se reconoce
 * a simple vista. Las dos horas van en la zona de la ciudad, como todo lo demás.
 */
@Composable
private fun MoonMilestones(nextFullMoon: LocalDateTime?, nextNewMoon: LocalDateTime?) {
    Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxHeight()) {
        MoonMilestone(
            label = stringResource(R.string.next_full_moon),
            moment = nextFullMoon,
            icon = Icons.Filled.Brightness1,
            iconTint = Color(0xFFECEFF1),
        )

        Spacer(Modifier.height(12.dp))

        MoonMilestone(
            label = stringResource(R.string.next_new_moon),
            moment = nextNewMoon,
            icon = Icons.Outlined.Brightness1,
            iconTint = Color(0xFF78909C),
        )
    }
}

/**
 * El reverso de la tarjeta del sol: cuánto dura hoy el día y cuánto ha
 * cambiado desde ayer.
 *
 * La comparación con ayer es lo único de la tarjeta que no se puede deducir
 * mirando el arco, y es lo que de verdad se nota al pasar las semanas.
 */
@Composable
private fun DayLengthFacts(dayLength: DayLength) {
    Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxHeight()) {
        CardFact(
            label = stringResource(R.string.day_length),
            value = stringResource(
                R.string.duration_hours_minutes,
                dayLength.today.toHours(),
                dayLength.today.toMinutesPart(),
            ),
            icon = Icons.Outlined.HourglassEmpty,
            iconTint = Color(0xFFFFC107),
        )

        Spacer(Modifier.height(12.dp))

        val change = dayLength.sinceYesterday
        val minutes = change?.toMinutes()?.let { abs(it) }
        CardFact(
            label = stringResource(R.string.versus_yesterday),
            value = when {
                minutes == null -> stringResource(R.string.no_value)
                // Menos de un minuto no es "igual", pero decir "0 min más" es
                // peor que decir que la diferencia no se nota.
                minutes == 0L -> stringResource(R.string.same_as_yesterday)
                // En plural, porque "1 minuto menos" pasa varios días
                // seguidos cerca de los solsticios.
                change.isNegative ->
                    pluralStringResource(R.plurals.minutes_less, minutes.toInt(), minutes)

                else -> pluralStringResource(R.plurals.minutes_more, minutes.toInt(), minutes)
            },
            icon = if (change != null && change.isNegative) {
                Icons.Outlined.TrendingDown
            } else {
                Icons.Outlined.TrendingUp
            },
            iconTint = Color(0xFFFFC107).copy(alpha = 0.8f),
        )
    }
}

/** Una de las dos fases, con su fecha y su hora. */
@Composable
private fun MoonMilestone(
    label: String,
    moment: LocalDateTime?,
    icon: ImageVector,
    iconTint: Color,
) = CardFact(
    label = label,
    // Sin dato no se esconde la fila: que falte una de las dos y la otra no,
    // dice más que un guion en su sitio.
    value = moment?.format(MOON_MOMENT)?.replaceFirstChar { it.uppercase() }
        ?: stringResource(R.string.no_value),
    icon = icon,
    iconTint = iconTint,
)

/** Rótulo pequeño y valor, la forma que tienen los dos reversos. */
@Composable
private fun CardFact(
    label: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                label.uppercase(),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.sp,
                fontWeight = FontWeight.W600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                value,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * "Vie 28 ago · 04:18". El formato sale del idioma del teléfono, como todas las
 * fechas de la app.
 */
private val MOON_MOMENT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM · HH:mm")

/**
 * Banda de luz que cruza la tarjeta de derecha a izquierda.
 *
 * Va inclinada y es ancha y tenue: un reflejo vertical y opaco se lee como una
 * cortina que tapa, y lo que se busca es que **parezca vidrio**, del mismo modo
 * que la tarjeta ya finge serlo con su velo. En los extremos no se dibuja nada,
 * así que ni aparece ni desaparece de golpe: entra y sale por fuera del borde.
 */
private fun DrawScope.drawSheen(progress: Float) {
    if (progress >= 1f) return

    val band = size.width * 0.45f
    // El centro va de fuera por la derecha a fuera por la izquierda.
    val centre = size.width + band - progress * (size.width + 2 * band)

    drawRect(
        brush = Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to Color.White.copy(alpha = SHEEN_ALPHA),
            1f to Color.Transparent,
            // La diagonal es suave —la banda baja mientras cruza— porque una
            // vertical perfecta delata que es un rectángulo moviéndose.
            start = Offset(centre - band, size.height),
            end = Offset(centre + band, 0f),
        ),
    )
}

/** Lo que tarda el reflejo en cruzar: un golpe de luz, no un barrido. */
private const val SHEEN_MILLIS = 200

/** Lo más claro que llega a ponerse la banda: se intuye más que se ve. */
private const val SHEEN_ALPHA = 0.02f

/** Radio de las dos tarjetas; lo comparten el recorte y el realce del toque. */
private val CARD_CORNER = 20.dp

/** Lo que mide el cuerpo de la tarjeta, sea cual sea la cara que enseñe. */
private val FACE_HEIGHT = 78.dp

/** Lo que tarda una cara en dar paso a la otra. */
private const val FLIP_MILLIS = 260
