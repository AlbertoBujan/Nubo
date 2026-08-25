package com.nubo.nubo.ui.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.BuildConfig

/**
 * Ajustes de la app.
 *
 * Vive en una hoja inferior, como el buscador de ubicaciones, para no meter
 * navegación en una app que no la tiene: el menú lateral y esta hoja son las
 * dos únicas capas por encima de la pantalla del tiempo.
 *
 * No hay "Actualizar ahora": tirar hacia abajo en la pantalla principal hace
 * exactamente lo mismo y está más a mano.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    interval: BackgroundInterval,
    onIntervalChange: (BackgroundInterval) -> Unit,
    onCheckUpdates: () -> Unit,
    onShowAbout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E2A3A),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Ajustes",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.W600,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(12.dp))
            SectionLabel("Actualización en segundo plano")
            Text(
                "Cada cuánto descarga Nubo la predicción sin abrir la app.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(4.dp))

            BackgroundInterval.entries.forEach { entry ->
                SettingsRow(
                    icon = Icons.Outlined.Sync,
                    label = entry.label,
                    onClick = { onIntervalChange(entry) },
                    trailing = {
                        if (entry == interval) {
                            Icon(
                                Icons.Filled.Check,
                                null,
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(12.dp))

            SectionLabel("Aplicación")
            SettingsRow(Icons.Outlined.SystemUpdate, "Buscar actualizaciones", onCheckUpdates)
            SettingsRow(
                Icons.Outlined.Info,
                "Acerca de Nubo",
                onShowAbout,
                subtitle = "Versión ${BuildConfig.VERSION_NAME}",
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
            }
        }
        trailing()
    }
}
