package com.nubo.nubo.ui.weather

import com.nubo.nubo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.BuildConfig
import kotlinx.coroutines.launch

/**
 * Menú lateral: las ubicaciones guardadas o los ajustes.
 *
 * Son los dos contenidos del mismo cajón, no dos capas: los ajustes **sustituyen**
 * a la lista en su sitio en vez de abrirse encima, así que el menú nunca tiene
 * nada por delante y siempre se sale de él por donde se entró. El engranaje de la
 * cabecera se convierte en la flecha de volver, que es la misma esquina.
 *
 * Añadir una ubicación es el botón flotante de abajo a la derecha, donde Material
 * pone la acción principal de una pantalla; antes era una tarjeta más al final de
 * la lista, que con varias ciudades quedaba fuera de la vista.
 */
@Composable
fun AppDrawer(
    state: WeatherUiState,
    interval: BackgroundInterval,
    showSettings: Boolean,
    onToggleSettings: (Boolean) -> Unit,
    onSelectCity: (Int) -> Unit,
    onRemoveCity: (Int) -> Unit,
    onUndoRemove: () -> Unit,
    onMoveCity: (Int, Int) -> Unit,
    onAddLocation: () -> Unit,
    onIntervalChange: (BackgroundInterval) -> Unit,
    alertNotifications: Boolean,
    onAlertNotificationsChange: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onShowAbout: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Se resuelven durante la composición porque `stringResource` solo puede
    // llamarse aquí, no dentro de la corrutina que enseña el aviso.
    val removedGeneric = stringResource(R.string.location_removed_generic)
    val removedFormat = stringResource(R.string.location_removed, "%s")
    val undoLabel = stringResource(R.string.undo)

    ModalDrawerSheet(drawerContainerColor = Color(0xFF16213E)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp),
            ) {
                // Alto fijo: el título pierde su segunda línea en los ajustes y
                // sin esto el botón de la esquina daría un salto al cambiar.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (showSettings) stringResource(R.string.settings) else "Nubo",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        // La versión ya la da "Acerca de Nubo" dentro de los
                        // ajustes; repetirla en la cabecera sobra.
                        if (!showSettings) {
                            Text(
                                "v${BuildConfig.VERSION_NAME}",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                            )
                        }
                    }

                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onToggleSettings(!showSettings) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (showSettings) Icons.AutoMirrored.Filled.ArrowBack
                            else Icons.Outlined.Settings,
                            stringResource(
                                if (showSettings) R.string.back else R.string.settings,
                            ),
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Los ajustes entran por la derecha y salen por ella: el gesto
                // dice de dónde vienen y a dónde vuelven, que es lo único que
                // hay que aprender de un cajón con dos contenidos.
                AnimatedContent(
                    targetState = showSettings,
                    transitionSpec = {
                        val forward = targetState
                        val width = { w: Int -> if (forward) w else -w }
                        (slideInHorizontally(tween(240), width) + fadeIn(tween(240)))
                            .togetherWith(
                                slideOutHorizontally(tween(240)) { -width(it) } +
                                    fadeOut(tween(160)),
                            )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    label = "drawer",
                ) { settings ->
                    if (settings) {
                        SettingsPanel(
                            interval = interval,
                            onIntervalChange = onIntervalChange,
                            alertNotifications = alertNotifications,
                            onAlertNotificationsChange = onAlertNotificationsChange,
                            onCheckUpdates = onCheckUpdates,
                            onShowAbout = onShowAbout,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            // Los dos gestos de las tarjetas no se ven, así que se
                            // cuentan: sin esta línea, reordenar no lo descubre nadie.
                            Text(
                                stringResource(R.string.drawer_gestures),
                                color = Color.White.copy(alpha = 0.38f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(
                                    horizontal = 20.dp,
                                    vertical = 4.dp,
                                ),
                            )

                            LocationList(
                                state = state,
                                onSelect = onSelectCity,
                                onRemove = { index ->
                                    val name = state.locations.getOrNull(index)?.nombre
                                    onRemoveCity(index)
                                    scope.launch {
                                        // Solo se guarda una eliminación pendiente,
                                        // así que si llega otra el aviso anterior
                                        // sobra: su "Deshacer" ya no recuperaría nada.
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = name?.let { removedFormat.format(it) }
                                                ?: removedGeneric,
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onUndoRemove()
                                        }
                                    }
                                },
                                onMove = onMoveCity,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // El botón sube cuando aparece el aviso de borrado, que ocupa el ancho
            // del cajón y si no se lo comería justo cuando hay algo que deshacer.
            val snackbarShowing = snackbarHostState.currentSnackbarData != null
            androidx.compose.animation.AnimatedVisibility(
                visible = !showSettings,
                enter = scaleIn(tween(180)) + fadeIn(tween(180)),
                exit = scaleOut(tween(140)) + fadeOut(tween(140)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = if (snackbarShowing) 84.dp else 20.dp),
            ) {
                FloatingActionButton(
                    onClick = onAddLocation,
                    containerColor = Color(0xFF64B5F6),
                    contentColor = Color(0xFF0D1B2A),
                    // Sin sombra: sobre el azul plano del cajón no separa nada del
                    // fondo, solo ensucia el borde de abajo del botón.
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                    ),
                ) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_location))
                }
            }

            SnackbarHost(
                snackbarHostState,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            )
        }
    }
}
