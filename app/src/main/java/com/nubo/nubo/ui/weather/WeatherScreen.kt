package com.nubo.nubo.ui.weather

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.nubo.nubo.R
import com.nubo.nubo.domain.model.CityError
import com.nubo.nubo.domain.weather.SkyCondition
import com.nubo.nubo.domain.weather.SunPhase
import com.nubo.nubo.domain.weather.WeatherCode
import com.nubo.nubo.ui.components.AlertBox
import com.nubo.nubo.ui.components.DailyView
import com.nubo.nubo.ui.components.HourlyView
import com.nubo.nubo.ui.components.countUpTo
import java.time.LocalDateTime
import com.nubo.nubo.ui.components.SkyLayer
import com.nubo.nubo.ui.components.SkyLayerOverlay
import com.nubo.nubo.ui.components.SunMoonCard
import com.nubo.nubo.ui.components.WeatherEffect
import com.nubo.nubo.ui.components.WeatherEffectsOverlay
import com.nubo.nubo.ui.components.describe
import com.nubo.nubo.ui.components.labelRes
import com.nubo.nubo.ui.components.toImageVector
import com.nubo.nubo.ui.theme.SkyGradient
import com.nubo.nubo.ui.theme.SkyGradients
import kotlin.math.roundToInt

/**
 * Pantalla principal: una página por ciudad, con el fondo del cielo detrás.
 *
 * El gradiente se interpola con la posición fraccionaria del pager, así que
 * acompaña al dedo durante el arrastre. El ViewModel, en cambio, solo se entera
 * del cambio cuando el pager **se asienta**: en la app Flutter actualizar el
 * estado a mitad del gesto reconstruía el árbol en los últimos fotogramas y
 * producía un tirón al final del swipe.
 */
@Composable
fun WeatherScreen(
    state: WeatherUiState,
    onRefreshAll: () -> Unit,
    onRetry: (String) -> Unit,
    onPageSettled: (Int) -> Unit,
    onOpenMenu: () -> Unit,
    onRemoveLocation: (Int) -> Unit,
    onAddLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = state.currentIndex,
        pageCount = { state.locations.size },
    )


    // Si la lista cambia desde fuera (añadir/quitar ciudad) el pager la sigue.
    LaunchedEffect(state.currentIndex, state.locations.size) {
        if (state.locations.isNotEmpty() &&
            state.currentIndex in state.locations.indices &&
            pagerState.currentPage != state.currentIndex &&
            !pagerState.isScrollInProgress
        ) {
            pagerState.animateScrollToPage(state.currentIndex)
        }
    }

    val pullState = rememberPullToRefreshState()

    // Un desplazamiento vertical por ciudad, guardado aquí arriba porque lo
    // necesitan dos sitios: la página, que se desplaza, y la barra superior,
    // que recoge el nombre cuando la página baja. Se crea a mano en vez de con
    // `rememberScrollState` porque el número de páginas cambia y no se puede
    // llamar a `remember` un número variable de veces.
    val pageScrolls = remember { mutableStateMapOf<String, ScrollState>() }

    fun scrollOf(locationId: String): ScrollState =
        pageScrolls.getOrPut(locationId) { ScrollState(0) }

    /**
     * Generación de cada página. Cambiarla la reconstruye desde cero.
     *
     * Es cómo se devuelve una ciudad a su estado inicial al dejarla: en vez de
     * ir izando y reiniciando uno a uno el carrusel horario, el día
     * desplegado y lo que se añada mañana, se descarta la composición entera y
     * todos sus `remember` vuelven a su valor de partida. El desplazamiento
     * vertical sí se reinicia aparte, porque vive fuera de la página.
     */
    val pageGenerations = remember { mutableStateMapOf<String, Int>() }

    // Se lee dentro del efecto, que se creó una sola vez y de otro modo vería
    // la lista que hubiera al arrancar.
    val locations by rememberUpdatedState(state.locations)

    // settledPage solo cambia cuando la animación termina, que es justo el
    // momento en el que interesa hacer el trabajo pesado. Y es también cuando
    // la página que se abandona ya no se ve, así que reconstruirla ahí no se
    // nota.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            onPageSettled(settled)

            val settledId = locations.getOrNull(settled)?.locationId
            locations.forEach { location ->
                if (location.locationId == settledId) return@forEach
                pageGenerations[location.locationId] =
                    (pageGenerations[location.locationId] ?: 0) + 1
                pageScrolls[location.locationId]?.scrollTo(0)
            }
        }
    }

    /**
     * Cuánto ha colapsado la cabecera, de 0 a 1.
     *
     * Se mide sobre la página que se está viendo. Al cambiar de ciudad
     * arrastrando, el valor salta al de la nueva, que es lo correcto: cada
     * ciudad recuerda por dónde iba.
     */
    val collapsePx = with(LocalDensity.current) { COLLAPSE_DISTANCE.toPx() }
    val collapse by remember(state) {
        derivedStateOf {
            val id = state.locations.getOrNull(pagerState.currentPage)?.locationId
            val offset = id?.let { pageScrolls[it]?.value } ?: 0
            (offset / collapsePx).coerceIn(0f, 1f)
        }
    }

    // Posición absoluta del pager: continua durante todo el gesto, a
    // diferencia de `currentPage`, que salta al pasar la mitad.
    val position by remember(state) {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }

    val gradient by remember(state) {
        derivedStateOf { interpolatedGradient(state, position) }
    }

    // La ciudad cuyo cielo se pinta de fondo: la más cercana a asentarse.
    val backgroundCity by remember(state) {
        derivedStateOf {
            state.cityAt(position.roundToInt().coerceIn(0, maxOf(0, state.locations.lastIndex)))
        }
    }

    val effect by remember(state) {
        derivedStateOf { WeatherEffect.fromSkyCode(backgroundCity?.skyCode) }
    }

    // Los dos extremos del viaje del nombre, medidos en la pantalla en vez de
    // calculados: el de salida se mueve con el desplazamiento de la página y el
    // de llegada depende del ancho del menú, así que cualquier número escrito a
    // mano aquí quedaría desfasado al tocar cualquiera de los dos.
    // Dónde tiene su nombre **cada** ciudad, no solo la que se está viendo.
    //
    // Miden todas las páginas, incluidas las que asoman al deslizar, y por eso
    // en el relevo de mitad del gesto la nueva ya tiene sitio conocido. Cuando
    // solo medía la que se veía, la que entraba tardaba un fotograma en decir
    // dónde estaba y en ese hueco no se dibujaba ningún nombre: eso es lo que
    // se veía como que el nombre bailaba al cambiar de ciudad.
    //
    // La clave es el id porque una página que carga o que falló no dibuja el
    // nombre grande y por tanto no mide: sin ella se usaría el sitio que dejó
    // otra ciudad, y el nombre saldría dos veces —el de esta capa y el del
    // propio aviso de "Cargando…".
    val nameMetrics = remember { mutableStateMapOf<String, NameMetrics>() }

    // Dónde y cuánto ocupa el pager: de aquí sale el ancho de página con el que
    // se calcula por dónde va el nombre.
    var pagerBounds by remember { mutableStateOf<Rect?>(null) }
    var nameTarget by remember { mutableStateOf<Rect?>(null) }

    // Qué dato se animó ya en cada ciudad. Vive **fuera** del pager a
    // propósito: dentro moriría con el `key` que reinicia la página al salir de
    // ella, y volver a una ciudad ya cargada repetiría el recuento. Lo que la
    // animación cuenta es que el dato acaba de llegar, no que se esté mirando.
    val animatedStamps = remember { mutableStateMapOf<String, LocalDateTime>() }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    val effectAlpha by remember(state) {
        derivedStateOf {
            val span = state.pageSpan(position)
            sceneAlpha(
                span,
                WeatherEffect.fromSkyCode(state.cityAt(span.lower)?.skyCode) ==
                    WeatherEffect.fromSkyCode(state.cityAt(span.upper)?.skyCode),
            )
        }
    }

    val skyLayer by remember(state) {
        derivedStateOf { SkyLayer.fromSkyCode(backgroundCity?.skyCode) }
    }

    val skyLayerAlpha by remember(state) {
        derivedStateOf {
            val span = state.pageSpan(position)
            sceneAlpha(
                span,
                SkyLayer.fromSkyCode(state.cityAt(span.lower)?.skyCode) ==
                    SkyLayer.fromSkyCode(state.cityAt(span.upper)?.skyCode),
            )
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradient.colors)),
    ) {
        // Orden de capas: primero el cielo —nubes, estrellas, niebla— y encima
        // lo que cae. Las dos van entre el gradiente y el contenido, así que
        // las tarjetas las ocultan con su velo.
        SkyLayerOverlay(
            layer = skyLayer,
            windSpeed = backgroundCity?.windSpeed,
            windDegrees = backgroundCity?.windDegrees,
            alpha = skyLayerAlpha,
            modifier = Modifier.fillMaxSize(),
        )

        WeatherEffectsOverlay(effect, effectAlpha, Modifier.fillMaxSize())

        // El gradiente y la lluvia ocupan toda la pantalla; el contenido se
        // aparta de las barras del sistema para no quedar bajo el reloj.
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            if (state.locations.isNotEmpty()) {
                TopBar(
                    state = state,
                    pageCount = state.locations.size,
                    activeDot = pagerState.currentPage,
                    collapse = collapse,
                    onNameAnchor = { nameTarget = it },
                    onOpenMenu = onOpenMenu,
                    onRefreshAll = onRefreshAll,
                    onRemoveLocation = onRemoveLocation,
                )
            }

            if (state.locations.isEmpty()) {
                WelcomeState(
                    isInitialized = state.isInitialized,
                    isLocating = state.isLocating,
                    onAddLocation = onAddLocation,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Tirar hacia abajo recarga **todas** las ciudades, no solo la
                // visible, que es lo que hacía la app Flutter: el gesto es el
                // mismo que el botón de la barra superior, y ambos se apagan
                // con el mismo `isRefreshing`.
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefreshAll,
                    state = pullState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { pagerBounds = it.unclippedBounds() },
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            modifier = Modifier.align(Alignment.TopCenter),
                            isRefreshing = state.isRefreshing,
                            state = pullState,
                            // Los colores por defecto salen del esquema de
                            // Material y sobre el gradiente del cielo quedan
                            // desvaídos; estos son los que ya usaba Flutter.
                            containerColor = MaterialTheme.colorScheme.surface,
                            color = Color.White,
                        )
                    },
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val city = state.cityAt(page)
                        if (city != null) {
                            key(city.locationId, pageGenerations[city.locationId] ?: 0) {
                            CityPage(
                                city = city,
                                scrollState = scrollOf(city.locationId),
                                // Solo la página que se ve cede su nombre a la
                                // capa de encima; las de al lado dibujan el
                                // suyo, que es lo que se ve asomar al deslizar.
                                animateFrom = animationTrigger(
                                    city.lastUpdated,
                                    animatedStamps[city.locationId],
                                ),
                                onAnimated = {
                                    city.lastUpdated?.let {
                                        animatedStamps[city.locationId] = it
                                    }
                                },
                                // Solo se apunta con la página quieta arriba
                                // del todo: es su sitio de reposo, y a partir
                                // de él se calcula el resto sin volver a medir.
                                onNameBounds = { rect ->
                                    if (scrollOf(city.locationId).value == 0) {
                                        nameMetrics[city.locationId] =
                                            NameMetrics(rect.width, rect.height, rect.top)
                                    }
                                },
                                // El nombre grande solo se calla en la página
                                // que se ve, porque ahí lo dibuja la capa de
                                // encima; las de al lado conservan el suyo,
                                // que es lo que se ve asomar al deslizar.
                                // Hasta que la ciudad se ha medido una vez, la
                                // capa de encima no sabe dónde ponerla y el
                                // nombre lo pinta la página: es el primer
                                // fotograma tras arrancar, y sin esto no habría
                                // ningún nombre en pantalla. Cuando el relevo
                                // ocurre los dos están en el mismo sitio, así
                                // que no se nota.
                                hideName = page == pagerState.currentPage &&
                                    nameMetrics.containsKey(city.locationId),
                                collapse = if (page == pagerState.currentPage) collapse else 0f,
                                onRetry = { onRetry(city.locationId) },
                            )
                            }
                        }
                    }
                }
            }
        }

        // El nombre de la ciudad, una sola vez y por encima de todo.
        //
        // Vive aquí y no dentro de la página porque tiene que viajar hasta la
        // barra, y el contenedor que desplaza la página recorta por arriba: un
        // texto que suba más allá de su borde deja de dibujarse. Antes eran dos
        // textos, uno que se apagaba abajo y otro que se encendía arriba; se
        // leía como un relevo, no como un nombre que se mueve.
        //
        // Los dos extremos del viaje se **miden**: el de salida se desplaza con
        // la página y el de llegada depende de lo que ocupe el menú.
        // El nombre sale del **pager**, no de `state.currentLocation`.
        //
        // Son dos relojes distintos: el pager cambia de página a mitad del
        // gesto y el estado no se entera hasta que se asienta. Con el nombre
        // colgando del segundo, entre un momento y otro no había ninguno que
        // dibujar —el de la página nueva ya se había apagado por ser la que se
        // ve, y esta capa seguía preguntando por la anterior—, y esa espera se
        // veía como que el nombre tardaba en aparecer al cambiar de ciudad.
        val current = state.locations.getOrNull(pagerState.currentPage)
        val travellingName = current?.nombre.orEmpty()
        val metrics = current?.locationId?.let { nameMetrics[it] }
        val pager = pagerBounds
        val target = nameTarget
        if (travellingName.isNotEmpty() && metrics != null && pager != null && target != null) {
            val scroll = current?.locationId?.let { pageScrolls[it] }
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { overlayOrigin = it.unclippedBounds().topLeft },
            ) {
                Text(
                    travellingName,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.W500,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        // Sin acotar al ancho del padre: el nombre se dibuja
                        // siempre a tamaño grande y encoge con la escala, que
                        // se ve mejor que agrandar un texto medido en pequeño.
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .graphicsLayer {
                            // De dónde sale el nombre. **No se mide aquí**: se
                            // calcula del desplazamiento del pager y del scroll
                            // de la página, que se leen en este mismo fotograma.
                            // Medirlo llegaba siempre un fotograma tarde, y a la
                            // velocidad de un deslizamiento rápido eso son 25 px
                            // de vaivén respecto a la página: el nombre bailaba.
                            //
                            // El nombre va centrado en su página, así que basta
                            // el ancho de una y lo que ocupa el texto.
                            val originLeft = pager.left +
                                (pager.width - metrics.width) / 2f -
                                pagerState.currentPageOffsetFraction * pager.width
                            val originTop = metrics.restTop - (scroll?.value ?: 0)
                            val originMiddle = originTop + metrics.height / 2f

                            val travel = nameTravel(collapse)
                            val middle = lerp(originMiddle, target.center.y, travel)
                            translationX =
                                lerp(originLeft, target.left, travel) - overlayOrigin.x
                            translationY = middle - metrics.height / 2f - overlayOrigin.y
                            val shrink = nameScale(collapse)
                            scaleX = shrink
                            scaleY = shrink
                            // Encoge hacia su izquierda y sin subir ni bajar:
                            // el punto que se interpola es ese, no el centro.
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                )
            }
        }
    }
}

/**
 * Lo que hace falta saber del nombre de una ciudad para dibujarlo desde fuera
 * de su página: cuánto ocupa y a qué altura está cuando la página está arriba
 * del todo. Lo demás —dónde cae ahora mismo— se calcula.
 */
private data class NameMetrics(
    val width: Float,
    val height: Float,
    /** Su borde superior con la página arriba del todo. */
    val restTop: Float,
)

/**
 * Rectángulo que ocupa algo en la ventana, **sin recortar** por sus padres.
 *
 * `boundsInWindow()` devuelve lo que de verdad se ve, que aquí sería lo peor
 * posible: al desplazar la página el nombre acaba fuera del contenedor y sus
 * límites se vaciarían justo cuando hay que saber de dónde viene.
 */
private fun LayoutCoordinates.unclippedBounds(): Rect =
    Rect(positionInWindow(), size.toSize())

/**
 * Gradiente del cielo, mezclando el de las dos páginas entre las que se está.
 *
 * La fase solar sale de **cada ciudad**, no del estado global. Con una fase
 * única, deslizar de A Coruña a Toronto pintaba Toronto con el azul de
 * mediodía porque la fase seguía siendo la de A Coruña hasta que el pager se
 * asentaba.
 */
private fun interpolatedGradient(state: WeatherUiState, position: Float): SkyGradient {
    fun gradientFor(index: Int): SkyGradient {
        val city = state.cityAt(index)
        return SkyGradients.forPhase(
            city?.sunPhase ?: SunPhase.DAY,
            city?.skyCondition ?: SkyCondition.CLEAR,
        )
    }

    if (state.locations.isEmpty()) {
        return SkyGradients.forPhase(SunPhase.DAY, SkyCondition.CLEAR)
    }

    val span = state.pageSpan(position)
    if (span.lower == span.upper) return gradientFor(span.lower)

    return SkyGradients.lerp(gradientFor(span.lower), gradientFor(span.upper), span.blend)
}

/**
 * Las dos páginas entre las que se está y cuánto pesa la segunda.
 *
 * Se calcula sobre la posición absoluta del pager —página más desplazamiento—
 * porque `currentPage` salta a mitad de gesto y `currentPageOffsetFraction`
 * salta con él: encadenados dan un valor continuo, por separado no.
 */
internal data class PageSpan(val lower: Int, val upper: Int, val blend: Float)

internal fun pageSpanAt(position: Float, lastIndex: Int): PageSpan {
    val maxIndex = lastIndex.coerceAtLeast(0)
    val floor = kotlin.math.floor(position)
    return PageSpan(
        lower = floor.toInt().coerceIn(0, maxIndex),
        upper = kotlin.math.ceil(position).toInt().coerceIn(0, maxIndex),
        blend = (position - floor).coerceIn(0f, 1f),
    )
}

private fun WeatherUiState.pageSpan(position: Float): PageSpan =
    pageSpanAt(position, locations.lastIndex)

/**
 * Opacidad de una animación durante el arrastre entre dos páginas.
 *
 * Las animaciones no se pueden mezclar como el gradiente —no existe media
 * lluvia ni media estrella—, así que en vez de interpolarlas se **apagan y se
 * vuelven a encender siguiendo el dedo**: baja a cero justo en la mitad del
 * gesto, que es donde el efecto cambia, y sube otra vez al acercarse a la
 * página nueva. El cambio ocurre invisible.
 *
 * Antes el efecto saltaba de golpe al cruzar esa mitad, sin relación con lo
 * que hacía el dedo, y era el "cambian espontáneamente" que se veía.
 *
 * Si las dos páginas comparten animación no se apaga nada: desvanecer la
 * lluvia para volver a traer la misma lluvia sería peor que no hacer nada.
 */
internal fun sceneAlpha(span: PageSpan, sameOnBothSides: Boolean): Float {
    if (sameOnBothSides || span.lower == span.upper) return 1f
    return kotlin.math.abs(1f - 2f * span.blend)
}

@Composable
private fun TopBar(
    state: WeatherUiState,
    pageCount: Int,
    activeDot: Int,
    /** 0 con la página arriba del todo, 1 con la cabecera ya recogida. */
    collapse: Float,
    /** Dónde aterriza el nombre; lo dibuja la capa de encima, no esta fila. */
    onNameAnchor: (Rect) -> Unit,
    onOpenMenu: () -> Unit,
    onRefreshAll: () -> Unit,
    onRemoveLocation: (Int) -> Unit,
) {
    val dotsAlpha = paginationDotsAlpha(collapse)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                // Ocupa el hueco que deja el indicador de frescura, así que el
                // nombre nunca puede crecer por debajo de él.
                modifier = Modifier.weight(1f).height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Sin fondo, pero conservando los 36 dp: es la zona que se
                // pulsa, y el icono solo mide 20.
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenMenu),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Menu,
                        stringResource(R.string.menu),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                // La chincheta ocupa su sitio desde el principio aunque no se
                // vea: si apareciera creando hueco, empujaría al nombre justo
                // cuando acaba de posarse y el viaje terminaría con un salto.
                Icon(
                    Icons.Outlined.LocationOn,
                    null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { alpha = pinAlpha(collapse) },
                )

                Spacer(Modifier.width(5.dp))

                // Hueco vacío: aquí no se dibuja nada, solo se mide dónde tiene
                // que terminar el nombre que viaja desde la página.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { onNameAnchor(it.unclippedBounds()) },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(onClick = onRefreshAll),
            ) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Schedule,
                        null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        state.currentCity?.dataAge?.describe().orEmpty(),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W500,
                    )
                }
            }
        }

        // Puntos de paginación, centrados sobre la barra. Se apagan al
        // recogerse la cabecera para dejarle el sitio al nombre.
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .height(36.dp)
                .graphicsLayer { alpha = dotsAlpha },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                val active = index == activeDot
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .width(if (active) 18.dp else 6.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (active) Color.White else Color.White.copy(alpha = 0.3f),
                        )
                        // Invisible no se pulsa: si no, mantener el dedo
                        // sobre la nada borraría una ciudad.
                        .pointerInput(index, pageCount, dotsAlpha > 0f) {
                            if (dotsAlpha <= 0f) return@pointerInput
                            detectTapGestures(
                                onLongPress = {
                                    if (pageCount > 1) onRemoveLocation(index)
                                },
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun CityPage(
    city: CityWeather,
    scrollState: ScrollState,
    collapse: Float,
    animateFrom: LocalDateTime?,
    onAnimated: () -> Unit,
    onNameBounds: (Rect) -> Unit,
    hideName: Boolean,
    onRetry: () -> Unit,
) {
    when {
        city.isLoading && !city.hasData -> LoadingState(city.name)
        city.error != null && !city.hasData -> ErrorState(city.error, onRetry)
        !city.hasData -> NoDataState(city.name, onRetry)
        else -> CityContent(city, scrollState, animateFrom, onAnimated, onNameBounds, hideName)
    }
}

/**
 * Margen lateral común a todas las tarjetas de la página.
 *
 * Iba suelto en cada llamada y había derivado a dos valores distintos, con lo
 * que los avisos sobresalían cuatro puntos por cada lado respecto a las horas
 * y al sol. Una constante evita que se vuelva a separar.
 */
private val CARD_MARGIN = 20.dp

/**
 * Desplazamiento que recoge la cabecera del todo.
 *
 * Es algo más que el alto del nombre grande, para que el relevo termine justo
 * cuando ese nombre ya ha salido por arriba.
 */
private val COLLAPSE_DISTANCE = 56.dp

/** Punto del recorrido en el que los puntos ya se han apartado del camino. */
private const val NAME_HANDOVER = 0.45f

/**
 * Opacidad de los puntos de paginación.
 *
 * Se apagan en la primera mitad del recorrido, mucho antes de que el nombre
 * llegue a la altura de la barra, y de ahí sale la garantía de que no pueden
 * chocar: los puntos van centrados y su fila crece con cada ciudad guardada,
 * así que con unas cuantas se meterían justo donde aterriza el nombre. En vez
 * de medir anchos y confiar en que quepa, no coinciden nunca. Se van sin
 * pérdida, porque dicen en qué ciudad estás peor que el propio nombre, y
 * vuelven al subir.
 */
internal fun paginationDotsAlpha(collapse: Float): Float =
    (1f - collapse / NAME_HANDOVER).coerceIn(0f, 1f)

/**
 * Cuánto queda del nombre al llegar a la barra: 30 sp por 0,6 son los 18 sp
 * que llevaba el nombre pequeño de antes.
 */
private const val TRAVEL_SCALE = 0.6f

/**
 * Cuánto ha recorrido el nombre, de su sitio en la página a la barra.
 *
 * Es el propio desplazamiento, acotado: el nombre no adelanta ni se retrasa
 * respecto al dedo, que es lo que hace que se lea como que lo arrastras tú.
 */
internal fun nameTravel(collapse: Float): Float = collapse.coerceIn(0f, 1f)

/**
 * El dato que hay que estrenar con una animación, o nulo si no hay ninguno.
 *
 * Lo que se anima es que **el dato es nuevo**, no que se esté mirando la
 * ciudad: la primera carga y cada refresco traen una hora de actualización
 * distinta, y volver a una ciudad ya cargada trae la misma. Por eso el registro
 * de lo ya animado vive fuera del pager, donde no lo barre el reinicio de la
 * página al salir de ella.
 */
internal fun animationTrigger(
    stamp: LocalDateTime?,
    alreadyAnimated: LocalDateTime?,
): LocalDateTime? = stamp?.takeIf { it != alreadyAnimated }

/** Tramo final del viaje en el que asoma la chincheta. */
private const val PIN_APPEARS = 0.75f

/**
 * Opacidad de la chincheta que acompaña al nombre en la barra.
 *
 * Entra solo en el último tramo, cuando el nombre ya está aterrizando: si
 * apareciera antes se leería como un icono suelto en mitad de la pantalla, y no
 * como lo que es, la marca del sitio en el que se ha posado el nombre.
 */
internal fun pinAlpha(collapse: Float): Float =
    ((collapse - PIN_APPEARS) / (1f - PIN_APPEARS)).coerceIn(0f, 1f)

/** Tamaño del nombre a lo largo del viaje. */
internal fun nameScale(collapse: Float): Float =
    lerp(1f, TRAVEL_SCALE, nameTravel(collapse))

@Composable
private fun CityContent(
    city: CityWeather,
    scrollState: ScrollState,
    animateFrom: LocalDateTime?,
    onAnimated: () -> Unit,
    onNameBounds: (Rect) -> Unit,
    hideName: Boolean,
) {
    val (max, min) = city.todayRange

    // El disparo de las animaciones es **el dato**, no un sí/no: en cuanto
    // empieza se da por gastado y `animate` pasa a falso, pero al refrescar
    // llega una hora de actualización nueva y todo lo que cuelga de ella vuelve
    // a arrancar. Con un booleano, la segunda vez ya valía `true` y no cambiaba
    // nada, así que refrescar no animaba nada.
    val trigger = remember(city.lastUpdated) { animateFrom }
    LaunchedEffect(trigger) { if (trigger != null) onAnimated() }

    val degrees = countUpTo(city.currentTemperature, trigger)
    val high = countUpTo(max, trigger)
    val low = countUpTo(min, trigger)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Aquí el nombre solo ocupa su sitio. En la página que se está viendo
        // lo dibuja la capa de encima, porque tiene que salir de este contenedor
        // —que recorta— para llegar hasta la barra; en las de al lado se dibuja
        // aquí, que es lo que se ve entrar al deslizar entre ciudades.
        Text(
            city.name,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.W500,
            maxLines = 1,
            modifier = Modifier
                .graphicsLayer { alpha = if (hideName) 0f else 1f }
                .onGloballyPositioned { onNameBounds(it.unclippedBounds()) },
        )

        Text(
            degrees?.let { "$it°" } ?: "--",
            color = Color.White,
            fontSize = 92.sp,
            fontWeight = FontWeight.Thin,
        )

        Text(
            stringResource(city.skyDescription.labelRes),
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 22.sp,
        )

        if (max != null || min != null) {
            Text(
                stringResource(
                    R.string.high_low,
                    high?.let { "$it°" } ?: stringResource(R.string.no_value),
                    low?.let { "$it°" } ?: stringResource(R.string.no_value),
                ),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 15.sp,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (city.alerts.isNotEmpty()) {
            AlertBox(city.alerts, Modifier.padding(horizontal = CARD_MARGIN))
            Spacer(Modifier.height(8.dp))
        }

        HourlyView(
            forecasts = city.hourly,
            alerts = city.alerts,
            nowThere = city.nowThere,
            animateFrom = trigger,
            modifier = Modifier.padding(horizontal = CARD_MARGIN),
        )

        Spacer(Modifier.height(16.dp))

        DailyView(
            forecasts = city.daily,
            alerts = city.alerts,
            today = city.nowThere.toLocalDate(),
            airQualityByDay = city.airQualityByDay,
            animateFrom = trigger,
            modifier = Modifier.padding(horizontal = CARD_MARGIN),
        )

        Spacer(Modifier.height(12.dp))

        SunMoonCard(
            sunTimes = city.sunTimes,
            moonData = city.moonData,
            sunPath = city.sunPath,
            moonPath = city.moonPath,
            nowThere = city.nowThere,
            modifier = Modifier.padding(horizontal = CARD_MARGIN),
        )
    }
}

@Composable
private fun LoadingState(cityName: String) {
    CenteredMessage {
        CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.loading_city, cityName),
            color = Color.White,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun ErrorState(error: CityError, onRetry: () -> Unit) {
    CenteredMessage {
        Icon(
            Icons.Outlined.CloudOff,
            null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            error.describe(),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun NoDataState(cityName: String, onRetry: () -> Unit) {
    CenteredMessage {
        Icon(
            Icons.Outlined.CloudOff,
            null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.no_data_for, cityName),
            color = Color.White,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.download_data)) }
    }
}

@Composable
private fun WelcomeState(
    isInitialized: Boolean,
    isLocating: Boolean,
    onAddLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Mientras se lee el disco no se enseña la bienvenida, para no dar un
    // parpadeo de "no tienes ciudades" a quien sí las tiene guardadas.
    if (!isInitialized) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f))
        }
        return
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                WeatherCode.fromCode("1").icon.toImageVector(),
                null,
                tint = Color.White,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.welcome_title),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.welcome_body),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onAddLocation,
                enabled = !isLocating,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text(
                    stringResource(
                        if (isLocating) R.string.locating else R.string.add_location,
                    ),
                    fontSize = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}
