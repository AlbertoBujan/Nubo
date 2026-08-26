package com.nubo.nubo.ui.components

import com.nubo.nubo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.domain.model.HourlyForecast
import com.nubo.nubo.domain.model.AlertType
import com.nubo.nubo.domain.model.WeatherAlert
import com.nubo.nubo.domain.weather.WeatherCode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.tanh
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.drawscope.clipRect

/**
 * Horas que caben a la vez, y tamaño del bloque que avanza cada deslizamiento.
 *
 * El ancho de cada columna se **calcula** a partir del de la tarjeta en vez de
 * ser fijo: con una anchura fija de 65 dp la sexta hora se cortaba por la mitad
 * en la posición inicial, porque no había ninguna relación entre ese número y
 * el ancho real de la pantalla.
 */
private const val VISIBLE_HOURS = 6

private val CHART_HEIGHT = 110.dp

/** Lo que tarda la curva en trazarse de lado a lado. */
private const val CHART_DRAW_MILLIS = 1100

/** Lo que cada columna espera respecto a la anterior. */
private const val COLUMN_STAGGER_MILLIS = 55

private val DEW_COLOR = Color(0xFF80DEEA)

/**
 * Formatos de fecha **en el idioma del teléfono**.
 *
 * Antes forzaban `es-ES`, y con razón: sin eso los días salían en el idioma
 * del sistema dentro de una interfaz que solo hablaba español, y quedaba peor.
 * Ahora que la app se traduce, ese arreglo sería el fallo — pondría "lunes"
 * dentro de una pantalla en inglés.
 */
private val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE")
private val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Carrusel horario con el gráfico de temperaturas debajo. */
@Composable
fun HourlyView(
    forecasts: List<HourlyForecast>,
    alerts: List<WeatherAlert>,
    /** Hora local del sitio: las marcas de la predicción están en su huso. */
    nowThere: java.time.LocalDateTime,
    modifier: Modifier = Modifier,
    /**
     * Dato que se estrena, o nulo si no hay nada que estrenar.
     *
     * Cuando cambia, la gráfica se traza de izquierda a derecha en vez de
     * aparecer hecha. Es el dato y no un sí/no porque al refrescar hay que
     * poder distinguir una actualización de la siguiente.
     */
    animateFrom: Any? = null,
) {
    if (forecasts.isEmpty()) return

    // La lista se recorta a un múltiplo de [VISIBLE_HOURS] para que el último
    // bloque no se quede a medias, con media columna cortada a cada lado.
    // `HourlyForecast.MAX_HOURS` ya es múltiplo de seis, así que normalmente
    // esto no quita nada; hace falta cuando llegan menos horas de las
    // esperadas, por ejemplo al leer una caché ya muy consumida.
    val hours = remember(forecasts) {
        val whole = forecasts.size - forecasts.size % VISIBLE_HOURS
        if (whole == 0) forecasts else forecasts.take(whole)
    }

    val hasAnyRain = remember(hours) {
        hours.any { (it.precipitationProbability ?: 0) > 0 }
    }
    val scrollState = rememberScrollState()

    // De 0 a 1, lo que lleva trazado la curva. Sin nada que estrenar nace en 1
    // y el dibujo es el de siempre, sin recorte ni fotograma de más.
    val reveal = remember(animateFrom) { Animatable(if (animateFrom == null) 1f else 0f) }
    LaunchedEffect(animateFrom) {
        if (animateFrom != null) {
            reveal.animateTo(1f, tween(CHART_DRAW_MILLIS, easing = LinearEasing))
        }
    }

    // Índice de la hora más cercana al momento actual. Se calcula una sola vez
    // aquí y no dentro de cada columna: comparando por separado, dos horas
    // contiguas podían cumplir el criterio y ambas se rotulaban "Ahora".
    val nowIndex = remember(hours, nowThere) {
        hours.indices.minByOrNull {
            java.time.Duration.between(hours[it].dateTime, nowThere).abs().toMillis()
        } ?: -1
    }

    // El título va **fuera** de la tarjeta, como el de los próximos días.
    // `SectionTitle` solo se usa en esos dos sitios y son lo mismo —el
    // encabezado de una sección de la página—, así que tenerlo dentro en uno y
    // fuera en el otro los hacía parecer cosas distintas. Los rótulos de Sol y
    // Luna sí van dentro, pero no son encabezados: son la etiqueta de una
    // tarjetita, y de hecho ni siquiera usan este componente.
    Column(modifier.fillMaxWidth()) {
        SectionTitle(
            icon = Icons.Outlined.Schedule,
            text = stringResource(R.string.next_hours),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
        ) {
            Column(Modifier.padding(top = 14.dp, bottom = 8.dp)) {
            BoxWithConstraints {
                // El ancho de columna se redondea a **píxeles enteros**, que es
                // lo que hace el layout al medirla, y de ahí sale el bloque.
                // Calculándolo en `Dp` el bloque salía 1160 px mientras las seis
                // columnas medían 1158: dos píxeles de desfase por bloque que
                // desalineaban el carrusel a los pocos gestos.
                val density = LocalDensity.current
                val itemPx = columnWidthPx(with(density) { maxWidth.toPx() })
                val itemWidth = with(density) { itemPx.toDp() }
                val blockPx = itemPx * VISIBLE_HOURS
                val blockWidth = itemWidth * VISIBLE_HOURS

                // El gesto vive en toda la tarjeta y no solo en la fila de
                // columnas: el gráfico ocupa la mitad de abajo y está fuera
                // del área desplazable, así que arrastrando sobre él no
                // pasaba nada.
                // Resistencia en los topes: sin ella el gesto se quedaba
                // completamente muerto al llegar al principio o al final.
                val edge = remember { EdgeResistance() }
                edge.limit = with(density) { MAX_EDGE_PULL.toPx() }

                Column(
                    Modifier
                        // Justo un bloque de ancho. Con la holgura sobrante de
                        // la tarjeta, el tope del scroll caía a media columna
                        // del último bloque y no se llegaba a encajar.
                        .width(blockWidth)
                        .nestedScroll(remember(edge) { stayInsideCard(edge) })
                        .scrollable(
                            state = scrollState,
                            orientation = Orientation.Horizontal,
                            flingBehavior = rememberBlockFling(scrollState, blockPx),
                            // Arrastrar hacia la izquierda avanza en el tiempo,
                            // igual que hace `horizontalScroll` por dentro.
                            reverseDirection = true,
                        )
                        .graphicsLayer { translationX = edge.pull },
                ) {
                    // Desplaza el contenido pero **no** captura el gesto: de
                    // eso se encarga la columna entera. Con las dos activas,
                    // arrastrar sobre las horas y arrastrar sobre el gráfico
                    // serían dos gestos distintos sobre el mismo estado.
                    Row(Modifier.horizontalScroll(scrollState, enabled = false)) {
                        hours.forEachIndexed { index, forecast ->
                            HourColumn(
                                forecast = forecast,
                                alerts = alerts,
                                hasAnyRain = hasAnyRain,
                                isNow = index == nowIndex,
                                today = nowThere.toLocalDate(),
                                animateFrom = animateFrom,
                                // Escalonado: la fila se enciende al ritmo al
                                // que la curva de debajo la va alcanzando.
                                animationDelay = index * COLUMN_STAGGER_MILLIS,
                                modifier = Modifier.width(itemWidth),
                            )
                        }
                    }

                    // El gráfico no va dentro del scroll: ocupa el ancho de la
                    // tarjeta y se dibuja desplazado a mano. Así la curva no
                    // arrastra consigo el trabajo de medir todas las columnas.
                    TemperatureChart(
                        forecasts = hours,
                        itemWidth = itemWidth,
                        scroll = scrollState.value,
                        reveal = reveal.value,
                        modifier = Modifier
                            .width(blockWidth)
                            .height(CHART_HEIGHT),
                    )
                }
            }
            }
        }
    }
}

/**
 * Se queda con todo el desplazamiento horizontal que sobra dentro de la
 * tarjeta, sin dejarlo subir al carrusel de ciudades.
 *
 * Sin esto, al llegar al principio o al final de las horas el resto del gesto
 * pasaba al `HorizontalPager` y **cambiaba de ciudad**: los dos deslizamientos
 * son el mismo movimiento del dedo y colisionan justo en los extremos, que es
 * donde uno esperaría que simplemente no pasara nada.
 *
 * Solo se consume la componente horizontal. La vertical se deja pasar entera
 * porque es la que hace correr la página.
 */
private fun stayInsideCard(edge: EdgeResistance) = object : NestedScrollConnection {

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        // Lo que sobra es justo lo que el carrusel no ha podido recorrer: se
        // aprovecha para empujar la tarjeta antes de quedárselo.
        if (available.x != 0f) edge.push(available.x)
        return Offset(available.x, 0f)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // Se llama al soltar, haya habido inercia o no, así que sirve de aviso
        // de que el dedo ya no está.
        edge.release()
        return Velocity(available.x, 0f)
    }
}

/**
 * Empuje elástico de la tarjeta contra sus topes.
 *
 * El efecto de rebote de Android no sirve aquí, y no por capricho: el sobrante
 * se despacha a los padres —donde [stayInsideCard] tiene que quedárselo para
 * que no cambie de ciudad— **antes** de que el efecto llegue a verlo. Dejarlo
 * pasar para que el efecto funcione es exactamente devolverle el gesto al
 * carrusel de ciudades.
 *
 * Así que el empuje se lleva desde el mismo sitio donde ya se intercepta ese
 * sobrante. El desplazamiento crece con **rendimiento decreciente**: un
 * arrastre grande apenas mueve más que uno mediano, que es lo que se siente
 * como un tope elástico y no como algo roto.
 */
@Stable
private class EdgeResistance {

    /** Cuánto se puede llegar a mover, en píxeles. */
    var limit: Float = 0f

    /** Desplazamiento a pintar. */
    var pull by mutableFloatStateOf(0f)
        private set

    /** Arrastre acumulado sin amortiguar, del que sale [pull]. */
    private var raw = 0f

    fun push(delta: Float) {
        if (limit <= 0f) return
        raw += delta
        pull = edgePull(raw, limit)
    }

    suspend fun release() {
        if (pull == 0f) {
            raw = 0f
            return
        }
        animate(
            initialValue = pull,
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        ) { value, _ -> pull = value }
        raw = 0f
    }
}

/** Lo que llega a moverse la tarjeta cuando ya no hay más horas. */
private val MAX_EDGE_PULL = 28.dp

/**
 * Desplazamiento visible para un arrastre acumulado de [raw] contra el tope.
 *
 * La tangente hiperbólica da el rendimiento decreciente: los primeros píxeles
 * se notan casi enteros y a partir de ahí cuesta cada vez más, sin llegar
 * nunca a [limit]. Es lo que se siente como un tope elástico en vez de como
 * algo que se ha soltado.
 */
internal fun edgePull(raw: Float, limit: Float): Float =
    if (limit <= 0f) 0f else limit * tanh(raw / limit)

/**
 * Inercia que asienta el carrusel en bloques de [VISIBLE_HOURS] horas.
 *
 * El destino se limita a los dos bloques contiguos, así que **un gesto avanza
 * un bloque** por rápido que sea: dejar que la inercia decidiera haría que un
 * mismo movimiento saltara dos o tres según la fuerza, y la predicción por
 * horas se lee de seis en seis, no a ojo.
 *
 * Al soltar sin apenas velocidad el destino es el bloque más cercano, que es
 * lo que devuelve la columna a su sitio si el arrastre se queda a medias.
 */
@Composable
private fun rememberBlockFling(scrollState: ScrollState, blockWidth: Float): FlingBehavior {
    val decay = remember { exponentialDecay<Float>() }
    return remember(scrollState, blockWidth, decay) {
        BlockFlingBehavior(scrollState, blockWidth, decay)
    }
}

private class BlockFlingBehavior(
    private val scrollState: ScrollState,
    private val blockWidth: Float,
    private val decay: DecayAnimationSpec<Float>,
) : FlingBehavior {

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (blockWidth <= 0f) return initialVelocity

        val current = scrollState.value.toFloat()
        val target = blockTarget(
            current = current,
            projected = decay.calculateTargetValue(current, initialVelocity),
            blockWidth = blockWidth,
            maxScroll = scrollState.maxValue.toFloat(),
        )

        // Ya está donde tiene que estar: no hay nada que animar. Y sobre todo,
        // no se le puede pasar la velocidad del dedo a un muelle que no tiene
        // recorrido. En los extremos, ese muelle se lanzaba más allá del tope,
        // el `scrollBy` se comía el tramo imposible, y al volver sí movía: un
        // gesto contra el principio de la lista dejaba el carrusel casi una
        // columna adentro.
        if (target == current) return 0f

        var last = current
        AnimationState(initialValue = current, initialVelocity = initialVelocity).animateTo(
            targetValue = target,
            // Muelle sin rebote: recoge la velocidad del dedo, así que el
            // frenado no da un tirón al empezar la animación.
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) {
            val delta = value - last
            last = value
            // Si el muelle se pasa de largo y el scroll no puede seguirle, se
            // corta aquí en vez de dejar que la vuelta arrastre el contenido.
            if (abs(scrollBy(delta) - delta) > 0.5f) cancelAnimation()
        }
        return 0f
    }
}

@Composable
private fun HourColumn(
    forecast: HourlyForecast,
    alerts: List<WeatherAlert>,
    hasAnyRain: Boolean,
    isNow: Boolean,
    today: LocalDate,
    /** Dato que se estrena, o nulo; ver `countUpTo`. */
    animateFrom: Any?,
    /** Retardo de esta columna, para que la fila se anime de izquierda a derecha. */
    animationDelay: Int,
    modifier: Modifier = Modifier,
) {

    val dayLabel = when (forecast.dateTime.toLocalDate()) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
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
            .distinctBy { AlertType.of(it.event) }
            .sortedByDescending { it.severity }
            .take(2)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(dayLabel, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
        Text(
            if (isNow) stringResource(R.string.now) else forecast.dateTime.format(HOUR),
            color = if (isNow) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = if (isNow) FontWeight.W600 else FontWeight.Normal,
        )

        Spacer(Modifier.height(6.dp))

        Icon(
            imageVector = WeatherCode.fromCode(forecast.skyStateCode).icon.toImageVector(),
            contentDescription = stringResource(
                WeatherCode.fromCode(forecast.skyStateCode).description.labelRes,
            ),
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )

        // La fila de probabilidad solo ocupa sitio si alguna hora tiene lluvia,
        // para que el carrusel no quede con un hueco vacío en días secos.
        if (hasAnyRain) {
            Spacer(Modifier.height(4.dp))
            // La casilla se ocupa según el dato **final**: si dependiese del
            // que va contando, el texto aparecería a media animación en vez de
            // contar desde cero.
            val chance = forecast.precipitationProbability ?: 0
            val counted = countUpTo(chance, animateFrom, animationDelay) ?: 0
            Text(
                if (chance > 0) "$counted%" else "",
                color = Color(0xFF64B5F6),
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            forecast.windDirectionDegrees?.let { degrees ->
                WindArrow(
                    // El dato dice de dónde viene el viento; la punta enseña
                    // hacia dónde va, así que la media vuelta va aquí y lo que
                    // se anima es ya el rumbo de la punta.
                    tipDegrees = sweepTo(
                        (degrees + 180).toFloat(),
                        animateFrom,
                        animationDelay,
                    ),
                    color = windColor(forecast.windSpeed),
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
            }
            val wind = countUpTo(forecast.windSpeed, animateFrom, animationDelay)
            Text(
                wind?.let { "$it km/h" }.orEmpty(),
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
                        imageVector = iconForType(AlertType.of(alert.event)),
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
    itemWidth: Dp,
    /** Desplazamiento del carrusel, en píxeles. */
    scroll: Int,
    /**
     * Cuánto se enseña, de 0 a 1, medido **en el ancho de la tarjeta**.
     *
     * No es un porcentaje del recorrido de la curva: la curva son 24 horas y
     * solo seis están a la vista, así que trazarla por su longitud llenaría lo
     * que se ve en el primer cuarto del tiempo y el resto pasaría fuera de la
     * pantalla, sin que se viera nada.
     */
    reveal: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()

    // Se dibujan las 48 horas y el recorte se encarga del resto: decidir por
    // dónde cortar la curva sale más caro que dejar que la GPU la recorte.
    Canvas(modifier.clipToBounds()) {
        val temps = forecasts.mapNotNull { it.temperature?.toFloat() }
        if (temps.isEmpty()) return@Canvas

        // Hasta dónde ha llegado el trazo. Los puntos y las etiquetas se
        // comparan contra esto para asomar cuando la línea los alcanza, en vez
        // de quedar cortados por la mitad como los cortaría un recorte.
        val revealX = if (reveal >= 1f) Float.MAX_VALUE else size.width * reveal

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
        val itemPx = itemWidth.toPx()

        fun yFor(value: Float) = paddingTop + chartHeight * (1f - (value - minT) / (maxT - minT))

        // Cada punto va bajo el centro de su columna, y se le restan los
        // píxeles desplazados para que siga al carrusel.
        val points = forecasts.mapIndexed { i, forecast ->
            Offset(
                (i + 0.5f) * itemPx - scroll,
                yFor(forecast.temperature?.toFloat() ?: minT),
            )
        }

        val guide = Color.White.copy(alpha = 0.1f)
        drawLine(guide, Offset(0f, paddingTop), Offset(size.width, paddingTop), 1f)
        drawLine(
            guide,
            Offset(0f, size.height - paddingBottom),
            Offset(size.width, size.height - paddingBottom),
            1f,
        )

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
            drawDewCurve(measurer, forecasts, dewPoints, points, ::yFor, minT, revealX)
        }

        drawTemperatureCurve(measurer, forecasts, points, paddingTop, revealX)
    }
}

/**
 * Dibuja [block] recortado a lo que la animación lleve trazado.
 *
 * Con el trazo terminado no se recorta nada: `clipRect` es una capa de más en
 * cada fotograma del desplazamiento, que es cuando el gráfico se redibuja sin
 * parar y no hay ninguna animación en curso.
 */
private fun DrawScope.revealed(revealX: Float, block: DrawScope.() -> Unit) {
    if (revealX >= size.width) block() else clipRect(right = revealX) { block() }
}

/**
 * Si el punto cae dentro de lo que se ve, con un margen holgado.
 *
 * Solo se consulta antes de medir y pintar texto: con 48 horas cargadas y seis
 * a la vista, rotularlas todas en cada fotograma del desplazamiento es trabajo
 * tirado. Los trazos sí se dibujan enteros y los recorta el lienzo.
 */
private fun DrawScope.isLabelVisible(x: Float): Boolean {
    val margin = 40.dp.toPx()
    return x > -margin && x < size.width + margin
}

/**
 * Prolonga una serie una columna por cada lado, siguiendo su propia pendiente.
 *
 * El primer punto cae a media columna del borde, así que sin esto la curva
 * empieza en seco con un hueco delante, y lo mismo por detrás en el último
 * bloque. Los puntos añadidos solo alimentan el trazo: no llevan círculo ni
 * etiqueta, porque no son horas, son la continuación de la línea hasta el
 * borde de la tarjeta.
 *
 * Se extrapola en línea recta en vez de prolongar en horizontal para que no
 * aparezca un codo justo en la primera hora. Al tratarse de una columna, lo
 * que se ve nunca pasa de media, así que el desvío está acotado a la mitad de
 * lo que sube o baja esa primera hora.
 */
internal fun extendToEdges(points: List<Offset>): List<Offset> {
    if (points.size < 2) return points
    val head = points[0] * 2f - points[1]
    val tail = points[points.lastIndex] * 2f - points[points.lastIndex - 1]
    return buildList(points.size + 2) {
        add(head)
        addAll(points)
        add(tail)
    }
}

private fun DrawScope.drawDewCurve(
    measurer: TextMeasurer,
    forecasts: List<HourlyForecast>,
    dewValues: List<Float?>,
    tempPoints: List<Offset>,
    yFor: (Float) -> Float,
    minT: Float,
    revealX: Float,
) {
    val dewPoints = dewValues.mapIndexed { i, value ->
        Offset(tempPoints[i].x, yFor(value ?: minT))
    }

    // Línea discontinua trazada a mano: recorre cada segmento alternando
    // tramo pintado y hueco, que es más barato que extraer sub-paths.
    val dash = 6.dp.toPx()
    val gap = 4.dp.toPx()
    val color = DEW_COLOR.copy(alpha = 0.7f)

    val stroked = extendToEdges(dewPoints)
    fun strokeDashes() {
    for (i in 0 until stroked.size - 1) {
        val from = stroked[i]
        val to = stroked[i + 1]
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
    }

    revealed(revealX) { strokeDashes() }

    dewPoints.forEachIndexed { i, point ->
        if (point.x > revealX) return@forEachIndexed
        if (dewValues[i] != null) drawCircle(DEW_COLOR, 1.5.dp.toPx(), point)
    }

    // Etiquetas en índices impares, intercaladas con las de temperatura.
    val style = TextStyle(color = DEW_COLOR, fontSize = 10.sp, fontWeight = FontWeight.W500)
    for (i in 1 until dewPoints.size step 2) {
        if (dewPoints[i].x > revealX) continue
        if (!isLabelVisible(dewPoints[i].x)) continue
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
    revealX: Float,
) {
    // Curva suave: cada tramo es una cúbica con los tiradores en el punto medio,
    // que evita los picos angulosos de unir los puntos con rectas.
    val stroked = extendToEdges(points)
    val path = Path().apply {
        moveTo(stroked.first().x, stroked.first().y)
        for (i in 0 until stroked.size - 1) {
            val p0 = stroked[i]
            val p1 = stroked[i + 1]
            val midX = (p0.x + p1.x) / 2
            cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
        }
    }

    // El degradado se ancla a las horas reales; los tramos prolongados quedan
    // fuera del rango y `horizontalGradient` los pinta con el color del
    // extremo, que es justo lo que se quiere.
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
        lineTo(stroked.last().x, size.height)
        lineTo(stroked.first().x, size.height)
        close()
    }
    revealed(revealX) {
        drawPath(
            fill,
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                startY = paddingTop,
                endY = size.height,
            ),
        )

        drawPath(path, brush, style = Stroke(width = 2.5.dp.toPx()))
    }

    val style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    points.forEachIndexed { i, point ->
        if (point.x > revealX) return@forEachIndexed
        val temp = forecasts[i].temperature ?: return@forEachIndexed
        if (i % 2 == 0 && isLabelVisible(point.x)) {
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
fun WindArrow(tipDegrees: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        // Rumbo **de la punta**, no la dirección meteorológica: la media vuelta
        // que las separa se aplica en quien llama, porque es lo que hay que
        // animar cuando la flecha sale del norte.
        val radians = Math.toRadians(tipDegrees.toDouble())
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
    /** Cuánto del tramo se ha llenado, de 0 a 1. */
    reveal: Float = 1f,
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
        // El tramo se llena desde su principio; el color no se estira con él,
        // porque el degradado va de la mínima a la máxima del día y moverlo
        // haría que la barra cambiase de color mientras crece.
        val right = left + (size.width * endFraction - left) * reveal.coerceIn(0f, 1f)
        if (right <= left) return@Canvas

        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(
                    TemperatureColors.forTemperature(min.toFloat()),
                    TemperatureColors.forTemperature(max.toFloat()),
                ),
                startX = left,
                endX = size.width * endFraction,
            ),
            topLeft = Offset(left, 0f),
            size = androidx.compose.ui.geometry.Size(right - left, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
        )
    }
}

/**
 * Dónde debe asentarse el carrusel tras un gesto.
 *
 * [projected] es donde pararía la inercia por sí sola. El destino se limita a
 * los dos bloques que rodean la posición actual, y ahí está el fondo del
 * asunto: sin ese recorte un gesto fuerte salta tres bloques y uno flojo
 * ninguno, cuando lo que se espera es que cada gesto pase de bloque.
 *
 * Como durante el arrastre la posición ya es fraccionaria, el bloque de salida
 * y el de llegada nunca coinciden y el gesto siempre avanza. Al soltar sin
 * velocidad el proyectado es la posición actual y gana el bloque más cercano,
 * que es lo que recoloca un arrastre a medias.
 */
internal fun blockTarget(
    current: Float,
    projected: Float,
    blockWidth: Float,
    maxScroll: Float,
): Float {
    if (blockWidth <= 0f) return current
    val block = (projected / blockWidth)
        .roundToInt()
        .coerceIn(
            floor(current / blockWidth).toInt(),
            ceil(current / blockWidth).toInt(),
        )
    return (block * blockWidth).coerceIn(0f, maxScroll)
}

/**
 * Ancho de una columna, en píxeles enteros, para una tarjeta de [viewportPx].
 *
 * Se trunca a propósito. El layout mide cada columna redondeando su `Dp` a
 * píxeles, así que si el bloque se calcula aparte en `Dp` los dos números no
 * coinciden: con una tarjeta de 1160 px salían columnas de 193 y un bloque de
 * 1160, cuando seis columnas miden 1158. Dos píxeles por bloque que a los
 * pocos gestos dejaban el carrusel a media columna.
 *
 * La ventana visible se fija después a seis de estas columnas, no al ancho de
 * la tarjeta, para que el recorrido total sea un número entero de bloques y el
 * último se pueda alcanzar.
 */
internal fun columnWidthPx(viewportPx: Float, visibleHours: Int = VISIBLE_HOURS): Float =
    floor(viewportPx / visibleHours).coerceAtLeast(1f)
