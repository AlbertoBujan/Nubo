package com.nubo.nubo.ui.weather

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
import androidx.compose.material.icons.outlined.Settings
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
 * Menú lateral: solo las ubicaciones guardadas.
 *
 * Todo lo demás —el intervalo de segundo plano, buscar actualizaciones y la
 * información de la app— se ha ido a [SettingsSheet], detrás del engranaje de
 * la esquina. Aquí solo quedan las tarjetas y el botón de añadir.
 */
@Composable
fun AppDrawer(
    state: WeatherUiState,
    onSelectCity: (Int) -> Unit,
    onRemoveCity: (Int) -> Unit,
    onUndoRemove: () -> Unit,
    onMoveCity: (Int, Int) -> Unit,
    onAddLocation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ModalDrawerSheet(drawerContainerColor = Color(0xFF16213E)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Nubo",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "v${BuildConfig.VERSION_NAME}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                        )
                    }

                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            "Ajustes",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Los dos gestos de las tarjetas no se ven, así que se cuentan:
                // sin esta línea, reordenar no lo descubre nadie.
                Text(
                    "Desliza para eliminar · Mantén pulsado para reordenar",
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )

                LocationList(
                    state = state,
                    onSelect = onSelectCity,
                    onRemove = { index ->
                        val name = state.locations.getOrNull(index)?.nombre
                        onRemoveCity(index)
                        scope.launch {
                            // Solo se guarda una eliminación pendiente, así que
                            // si llega otra el aviso anterior sobra: su
                            // "Deshacer" ya no recuperaría nada.
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                message = name?.let { "$it eliminada" } ?: "Ubicación eliminada",
                                actionLabel = "Deshacer",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) onUndoRemove()
                        }
                    },
                    onMove = onMoveCity,
                    onAddLocation = onAddLocation,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
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
