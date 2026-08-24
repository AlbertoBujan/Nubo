package com.nubo.nubo.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.BuildConfig

/** Menú lateral: ciudades guardadas y ajustes. */
@Composable
fun AppDrawer(
    state: WeatherUiState,
    onSelectCity: (Int) -> Unit,
    onRemoveCity: (Int) -> Unit,
    onAddLocation: () -> Unit,
    onRefreshAll: () -> Unit,
    onIntervalChange: (BackgroundInterval) -> Unit,
    onCheckUpdates: () -> Unit,
    onShowAbout: () -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = Color(0xFF16213E)) {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            Text(
                "Nubo",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                "v${BuildConfig.VERSION_NAME}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(20.dp))
            DrawerSectionLabel("Mis ubicaciones")

            state.locations.forEachIndexed { index, location ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCity(index) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        null,
                        tint = if (index == state.currentIndex) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        location.nombre,
                        color = if (index == state.currentIndex) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.75f)
                        },
                        fontSize = 16.sp,
                        fontWeight = if (index == state.currentIndex) {
                            FontWeight.W600
                        } else {
                            FontWeight.Normal
                        },
                        modifier = Modifier.weight(1f),
                    )
                    // La última ciudad no se puede borrar: dejaría la app vacía
                    // de golpe justo cuando el usuario solo quería ordenar.
                    if (state.locations.size > 1) {
                        Icon(
                            Icons.Outlined.Delete,
                            "Eliminar ${location.nombre}",
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onRemoveCity(index) },
                        )
                    }
                }
            }

            DrawerItem(Icons.Outlined.Add, "Añadir ubicación", onAddLocation)

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(8.dp))

            DrawerSectionLabel("Actualización")
            DrawerItem(Icons.Outlined.Refresh, "Actualizar ahora", onRefreshAll)

            DrawerSectionLabel("En segundo plano")
            BackgroundInterval.entries.forEach { interval ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onIntervalChange(interval) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Sync,
                        null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        interval.label,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.backgroundInterval == interval) {
                        Icon(
                            Icons.Filled.Check,
                            null,
                            tint = Color(0xFF64B5F6),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(8.dp))

            DrawerItem(Icons.Outlined.SystemUpdate, "Buscar actualizaciones", onCheckUpdates)
            DrawerItem(Icons.Outlined.Info, "Acerca de Nubo", onShowAbout)
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
    }
}
