package com.nubo.nubo.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.domain.weather.SkyCondition
import com.nubo.nubo.ui.components.AlertBox
import com.nubo.nubo.ui.components.ConditionsCard
import com.nubo.nubo.ui.components.DailyView
import com.nubo.nubo.ui.components.HourlyView
import com.nubo.nubo.ui.components.SunMoonCard
import com.nubo.nubo.ui.components.SkyLayer
import com.nubo.nubo.ui.components.SkyLayerOverlay
import com.nubo.nubo.ui.components.WeatherEffect
import com.nubo.nubo.ui.components.WeatherEffectsOverlay
import com.nubo.nubo.ui.components.toImageVector
import com.nubo.nubo.ui.theme.SkyGradient
import com.nubo.nubo.ui.theme.SkyGradients
import com.nubo.nubo.domain.weather.WeatherCode
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

    // settledPage solo cambia cuando la animación termina, que es justo el
    // momento en el que interesa hacer el trabajo pesado.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { onPageSettled(it) }
    }

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

    val gradient by remember(state) {
        derivedStateOf {
            interpolatedGradient(
                state = state,
                page = pagerState.currentPage,
                offset = pagerState.currentPageOffsetFraction,
            )
        }
    }

    // La ciudad cuyo cielo se pinta de fondo: la que está más cerca de quedar
    // asentada, para que el fondo cambie a la vez que el gradiente.
    val backgroundCity by remember(state) {
        derivedStateOf {
            val index = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .roundToInt()
                .coerceIn(0, maxOf(0, state.locations.lastIndex))
            state.cityAt(index)
        }
    }

    val effect by remember(state) {
        derivedStateOf { WeatherEffect.fromSkyCode(backgroundCity?.skyCode) }
    }

    val skyLayer by remember(state) {
        derivedStateOf { SkyLayer.fromSkyCode(backgroundCity?.skyCode) }
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
            modifier = Modifier.fillMaxSize(),
        )

        WeatherEffectsOverlay(effect, Modifier.fillMaxSize())

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
                    modifier = Modifier.fillMaxSize(),
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
                            CityPage(
                                city = city,
                                scrollState = scrollOf(city.locationId),
                                // El nombre grande solo se desvanece en la
                                // página que se ve; las de al lado conservan
                                // el suyo entero mientras asoman al deslizar.
                                collapse = if (page == pagerState.currentPage) collapse else 0f,
                                onRetry = { onRetry(city.locationId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Gradiente del cielo, mezclando el de la página actual con el de la siguiente. */
private fun interpolatedGradient(
    state: WeatherUiState,
    page: Int,
    offset: Float,
): SkyGradient {
    fun gradientFor(index: Int): SkyGradient {
        val sky = state.cityAt(index)?.skyCondition ?: SkyCondition.CLEAR
        return SkyGradients.forPhase(state.sunPhase, sky)
    }

    if (state.locations.isEmpty()) {
        return SkyGradients.forPhase(state.sunPhase, SkyCondition.CLEAR)
    }

    val maxIndex = state.locations.lastIndex
    val current = page.coerceIn(0, maxIndex)
    // El desplazamiento es negativo cuando se arrastra hacia la página previa.
    val next = (if (offset >= 0) current + 1 else current - 1).coerceIn(0, maxIndex)
    if (next == current || offset == 0f) return gradientFor(current)

    return SkyGradients.lerp(gradientFor(current), gradientFor(next), kotlin.math.abs(offset))
}

@Composable
private fun TopBar(
    state: WeatherUiState,
    pageCount: Int,
    activeDot: Int,
    /** 0 con la página arriba del todo, 1 con la cabecera ya recogida. */
    collapse: Float,
    onOpenMenu: () -> Unit,
    onRefreshAll: () -> Unit,
    onRemoveLocation: (Int) -> Unit,
) {
    val nameAlpha = topBarNameAlpha(collapse)

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
                        "Menú",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }

                if (nameAlpha > 0f) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        state.currentLocation?.nombre.orEmpty(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W600,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            alpha = nameAlpha
                            // Sube los últimos puntos mientras aparece, para
                            // que se lea como que llega desde abajo.
                            translationY = (1f - nameAlpha) * NAME_RISE.toPx()
                        },
                    )
                }
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
                        state.currentCity?.lastRefreshText.orEmpty(),
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
    onRetry: () -> Unit,
) {
    when {
        city.isLoading && !city.hasData -> LoadingState(city.name)
        city.error != null && !city.hasData -> ErrorState(city.error, onRetry)
        !city.hasData -> NoDataState(city.name, onRetry)
        else -> CityContent(city, scrollState, collapse)
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

/** Punto del recorrido en el que la barra empieza a recoger el nombre. */
private const val NAME_HANDOVER = 0.45f

/**
 * Opacidad del nombre en la barra superior.
 *
 * Entra en la segunda mitad del recorrido, cuando el nombre grande ya se ha
 * ido: solapados un instante se leerían como dos nombres, no como uno que
 * viaja.
 */
internal fun topBarNameAlpha(collapse: Float): Float =
    ((collapse - NAME_HANDOVER) / (1f - NAME_HANDOVER)).coerceIn(0f, 1f)

/**
 * Opacidad de los puntos de paginación.
 *
 * Se apagan **justo** cuando el nombre empieza a entrar, y de ahí sale la
 * garantía de que no pueden chocar: con muchas ciudades la fila de puntos es
 * ancha y el nombre corre hacia ella, pero nunca se ven los dos a la vez. Se
 * van sin pérdida, porque dicen en qué ciudad estás peor que el propio nombre,
 * y vuelven al subir.
 */
internal fun paginationDotsAlpha(collapse: Float): Float =
    (1f - collapse / NAME_HANDOVER).coerceIn(0f, 1f)

/** Cuánto encoge el nombre grande al irse. */
private const val NAME_SHRINK = 0.4f

/** Lo que el nombre grande se adelanta al desplazamiento. */
private val NAME_LIFT = 10.dp

/** Desde dónde asoma el nombre pequeño al entrar en la barra. */
private val NAME_RISE = 10.dp

@Composable
private fun CityContent(city: CityWeather, scrollState: ScrollState, collapse: Float) {
    val (max, min) = city.todayRange

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // El nombre grande se apaga y encoge mientras sube con el propio
        // desplazamiento, y la barra lo recoge arriba. No es el mismo texto
        // viajando —son dos, en árboles distintos—, pero al irse uno donde
        // llega el otro se lee como uno solo.
        Text(
            city.name,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.W500,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - collapse
                val shrink = 1f - collapse * NAME_SHRINK
                scaleX = shrink
                scaleY = shrink
                // Se adelanta un poco al desplazamiento: converge hacia la
                // barra en vez de limitarse a subir con la página.
                translationY = -collapse * NAME_LIFT.toPx()
            },
        )

        Text(
            city.currentTemperature?.let { "$it°" } ?: "--",
            color = Color.White,
            fontSize = 92.sp,
            fontWeight = FontWeight.Thin,
        )

        Text(
            city.skyDescription,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 22.sp,
        )

        if (max != null || min != null) {
            Text(
                "Máx: ${max?.let { "$it°" } ?: "--"}   Mín: ${min?.let { "$it°" } ?: "--"}",
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
            modifier = Modifier.padding(horizontal = CARD_MARGIN),
        )

        Spacer(Modifier.height(16.dp))

        DailyView(
            forecasts = city.daily,
            alerts = city.alerts,
            today = city.nowThere.toLocalDate(),
            modifier = Modifier.padding(horizontal = CARD_MARGIN),
        )

        // Antes del sol y la luna: las dos son la zona de datos consultables,
        // por debajo del recorrido principal de temperatura, horas y días.
        if (city.hasConditions) {
            Spacer(Modifier.height(12.dp))
            ConditionsCard(
                airQualityIndex = city.airQualityIndex,
                uvIndex = city.uvIndex,
                humidity = city.humidity,
                apparentTemperature = city.apparentTemperature,
                temperature = city.currentTemperature,
                modifier = Modifier.padding(horizontal = CARD_MARGIN),
            )
        }

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
        Text("Cargando $cityName…", color = Color.White, fontSize = 18.sp)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    CenteredMessage {
        Icon(
            Icons.Outlined.CloudOff,
            null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
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
        Text("Sin datos para $cityName", color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text("Descargar datos") }
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
                "¡Bienvenido a Nubo!",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Para empezar, añade tu primera ubicación.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onAddLocation,
                enabled = !isLocating,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text(if (isLocating) "Localizando…" else "Añadir ubicación", fontSize = 17.sp)
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
