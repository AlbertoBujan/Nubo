package com.nubo.nubo.ui.weather

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nubo.nubo.domain.weather.WeatherCode
import com.nubo.nubo.ui.components.toImageVector
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Lista de ubicaciones guardadas del menú lateral.
 *
 * Cada sitio es una tarjeta con su icono del cielo, su nombre y su temperatura.
 * Sobre ella hay dos gestos:
 *
 * - **Deslizar a la derecha** la elimina. Se arma al pasar [DELETE_THRESHOLD]
 *   del ancho: a partir de ahí el fondo rojo se enciende, el texto cambia a
 *   "Suelta para eliminar" y el teléfono vibra. Soltar antes la devuelve a su
 *   sitio, así que el gesto se puede deshacer sin levantar el dedo.
 * - **Mantener pulsado y arrastrar** la reordena. El orden de esta lista es el
 *   de las páginas de la pantalla principal.
 *
 * Los dos gestos conviven porque el de reordenar exige pulsación larga y
 * consume los eventos en cuanto arranca, con lo que el detector horizontal
 * cancela su propio umbral. Sin ese consumo, arrastrar en diagonal dispararía
 * los dos a la vez.
 */
private val CARD_HEIGHT = 68.dp
private val CARD_GAP = 10.dp

/** Fracción del ancho a partir de la cual soltar borra. */
private const val DELETE_THRESHOLD = 0.4f

private val CARD_IDLE = Color(0xFF242E4A)
private val CARD_SELECTED = Color(0xFF3B445D)

@Composable
fun LocationList(
    state: WeatherUiState,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAddLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val pitchPx = with(LocalDensity.current) { (CARD_HEIGHT + CARD_GAP).toPx() }

    // -1 = no se está arrastrando nada. El movimiento no se aplica hasta que
    // se suelta: mientras dura el gesto la lista real no cambia, solo se
    // desplazan las tarjetas, y así las claves de `LazyColumn` quedan quietas.
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragTo by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun resetDrag() {
        dragFrom = -1
        dragTo = -1
        dragOffset = 0f
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
        // Arrastrar una tarjeta no debe además desplazar la lista.
        userScrollEnabled = dragFrom < 0,
    ) {
        itemsIndexed(
            state.locations,
            key = { _, location -> location.locationId },
        ) { index, location ->
            val dragging = index == dragFrom

            val animatedShift by animateFloatAsState(
                reorderShift(index, dragFrom, dragTo, pitchPx),
                label = "shift",
            )

            LocationCard(
                name = location.nombre,
                city = state.cities[location.locationId],
                selected = index == state.currentIndex,
                canRemove = state.locations.size > 1 && dragFrom < 0,
                onClick = { onSelect(index) },
                onRemove = { onRemove(index) },
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else animatedShift
                        val lift = if (dragging) 1.03f else 1f
                        scaleX = lift
                        scaleY = lift
                    }
                    .pointerInput(index, state.locations.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragFrom = index
                                dragTo = index
                                dragOffset = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val target = reorderTarget(
                                    from = index,
                                    offset = dragOffset,
                                    pitch = pitchPx,
                                    lastIndex = state.locations.lastIndex,
                                )
                                if (target != dragTo) {
                                    dragTo = target
                                    haptics.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove,
                                    )
                                }
                            },
                            onDragEnd = {
                                val from = dragFrom
                                val to = dragTo
                                resetDrag()
                                if (from >= 0 && to >= 0) onMove(from, to)
                            },
                            onDragCancel = { resetDrag() },
                        )
                    },
            )
        }

        item(key = "add") {
            AddLocationCard(onAddLocation)
        }
    }
}

/**
 * Una ubicación, con el fondo de borrado detrás.
 *
 * El desplazamiento horizontal vive aquí y no en la lista porque cada tarjeta
 * tiene el suyo: la clave de `LazyColumn` hace que sobreviva a un reordenado.
 */
@Composable
private fun LocationCard(
    name: String,
    city: CityWeather?,
    selected: Boolean,
    canRemove: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    // Se recuerda para vibrar solo al cruzar el umbral, no en cada fotograma.
    var armed by remember { mutableStateOf(false) }

    val threshold = widthPx * DELETE_THRESHOLD

    Box(
        modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .onSizeChanged { widthPx = it.width.toFloat() },
    ) {
        if (offsetX.value > 0f) {
            DeleteBackground(
                progress = (offsetX.value / (threshold.takeIf { it > 0f } ?: 1f))
                    .coerceIn(0f, 1f),
                armed = armed,
            )
        }

        Row(
            Modifier
                .graphicsLayer { translationX = offsetX.value }
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                // Opacos a propósito: son el blanco del menú al 16 % y al 6 %
                // ya mezclado con su fondo. Si se dejan translúcidos, el rojo
                // de borrado y su texto se ven **a través** de la tarjeta y se
                // solapan con el nombre del sitio.
                .background(if (selected) CARD_SELECTED else CARD_IDLE)
                .then(
                    if (selected) {
                        Modifier.border(
                            1.dp,
                            Color.White.copy(alpha = 0.28f),
                            RoundedCornerShape(16.dp),
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick)
                .pointerInput(canRemove) {
                    if (!canRemove) return@pointerInput
                    // El bloque de `pointerInput` no se reinicia al
                    // recomponer, así que el umbral y el ancho se leen aquí
                    // dentro en cada evento: capturarlos fuera dejaría los
                    // valores de la primera composición, cuando el ancho aún
                    // era cero y nada llegaba nunca a borrarse.
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            // Solo hacia la derecha; hacia la izquierda no hay
                            // ninguna acción y arrastrar en vacío confunde.
                            val next = (offsetX.value + amount).coerceAtLeast(0f)
                            scope.launch { offsetX.snapTo(next) }

                            val nowArmed = next >= widthPx * DELETE_THRESHOLD
                            if (nowArmed != armed) {
                                armed = nowArmed
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDragEnd = {
                            val delete = armed
                            armed = false
                            scope.launch {
                                if (delete) {
                                    // Sale de la pantalla antes de avisar al
                                    // ViewModel: si no, la tarjeta desaparece
                                    // de golpe a media pantalla.
                                    offsetX.animateTo(widthPx, tween(180))
                                    onRemove()
                                } else {
                                    offsetX.animateTo(0f, tween(200))
                                }
                            }
                        },
                        onDragCancel = {
                            armed = false
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                        },
                    )
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = city?.skyCode
                ?.takeIf { it.isNotEmpty() }
                ?.let { WeatherCode.fromCode(it).icon.toImageVector() }
                ?: Icons.Outlined.LocationOn

            Icon(
                icon,
                null,
                tint = Color.White.copy(alpha = if (selected) 0.95f else 0.7f),
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                name,
                color = Color.White.copy(alpha = if (selected) 1f else 0.8f),
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.W600 else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                city?.currentTemperature?.let { "$it°" } ?: "--",
                color = Color.White.copy(alpha = if (selected) 0.95f else 0.7f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

/** Fondo rojo que asoma al deslizar; se enciende al llegar al umbral. */
@Composable
private fun DeleteBackground(progress: Float, armed: Boolean) {
    val tint by animateFloatAsState(if (armed) 1f else 0f, label = "armed")

    Row(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Color(0xFFE53935).copy(alpha = 0.25f + 0.6f * tint * progress),
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Delete,
            null,
            tint = Color.White.copy(alpha = 0.6f + 0.4f * tint),
            modifier = Modifier.size(if (armed) 26.dp else 22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            if (armed) "Suelta para eliminar" else "Desliza para eliminar",
            color = Color.White.copy(alpha = 0.55f + 0.45f * tint),
            fontSize = 13.sp,
            fontWeight = if (armed) FontWeight.W600 else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** Tarjeta punteada del final: añadir una ubicación nueva. */
@Composable
private fun AddLocationCard(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Add,
            null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            "Añadir ubicación",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp,
        )
    }
}

/**
 * Posición a la que iría la tarjeta arrastrada desde [from] tras recorrer
 * [offset] píxeles.
 *
 * Todas las tarjetas miden lo mismo, así que basta dividir por el paso en vez
 * de medir dónde ha quedado cada una. El redondeo hace que el hueco se abra al
 * pasar media tarjeta, no al pasarla entera.
 */
internal fun reorderTarget(from: Int, offset: Float, pitch: Float, lastIndex: Int): Int {
    if (pitch <= 0f || lastIndex < 0) return from
    return (from + (offset / pitch).roundToInt()).coerceIn(0, lastIndex)
}

/**
 * Cuánto se aparta verticalmente la tarjeta [index] mientras se arrastra la
 * [from] hacia [to].
 *
 * Solo se mueven las que quedan entre las dos posiciones, y siempre un paso
 * entero: hacia abajo si la arrastrada sube, hacia arriba si baja.
 */
internal fun reorderShift(index: Int, from: Int, to: Int, pitch: Float): Float = when {
    from < 0 || to < 0 || index == from -> 0f
    index in to until from -> pitch
    index in (from + 1)..to -> -pitch
    else -> 0f
}
