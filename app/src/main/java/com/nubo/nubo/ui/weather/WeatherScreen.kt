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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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
                            CityPage(city = city, onRetry = { onRetry(city.locationId) })
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
    onOpenMenu: () -> Unit,
    onRefreshAll: () -> Unit,
    onRemoveLocation: (Int) -> Unit,
) {
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
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(onClick = onOpenMenu),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Menu,
                    "Menú",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
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
                        state.currentCity?.lastRefreshText.orEmpty(),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W500,
                    )
                }
            }
        }

        // Puntos de paginación, centrados sobre la barra.
        Row(
            Modifier.align(Alignment.TopCenter).height(36.dp),
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
                        .pointerInput(index, pageCount) {
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
private fun CityPage(city: CityWeather, onRetry: () -> Unit) {
    when {
        city.isLoading && !city.hasData -> LoadingState(city.name)
        city.error != null && !city.hasData -> ErrorState(city.error, onRetry)
        !city.hasData -> NoDataState(city.name, onRetry)
        else -> CityContent(city)
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

@Composable
private fun CityContent(city: CityWeather) {
    val (max, min) = city.todayRange

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(city.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.W500)

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
