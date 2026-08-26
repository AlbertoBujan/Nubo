package com.nubo.nubo.ui.weather

import com.nubo.nubo.ui.components.labelRes
import com.nubo.nubo.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
import com.nubo.nubo.work.BackgroundUpdateWorker

/**
 * Ajustes de la app.
 *
 * Viven **dentro del menú lateral**, en lugar de las ubicaciones, no en una
 * capa nueva encima: son el otro contenido del mismo cajón, y por eso se
 * entra y se sale de ellos con la misma flecha en la misma esquina.
 *
 * No hay "Actualizar ahora": tirar hacia abajo en la pantalla principal hace
 * exactamente lo mismo y está más a mano.
 */
@Composable
fun SettingsPanel(
    interval: BackgroundInterval,
    onIntervalChange: (BackgroundInterval) -> Unit,
    alertNotifications: Boolean,
    onAlertNotificationsChange: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onShowAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        SectionLabel(stringResource(R.string.background_updates))
        Text(
            stringResource(R.string.background_updates_hint),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(4.dp))

        BackgroundInterval.entries.forEach { entry ->
            SettingsRow(
                icon = Icons.Outlined.Sync,
                label = stringResource(entry.labelRes),
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

        SectionLabel(stringResource(R.string.notifications))
        SettingsRow(
            icon = Icons.Outlined.Warning,
            label = stringResource(R.string.alert_notifications),
            onClick = { onAlertNotificationsChange(!alertNotifications) },
            subtitle = stringResource(R.string.alert_notifications_hint),
            trailing = {
                Switch(
                    checked = alertNotifications,
                    onCheckedChange = onAlertNotificationsChange,
                )
            },
        )
        // Los avisos solo se descubren cuando corre la tarea de fondo. Si está
        // apagada se programa igualmente para esto, y decirlo aquí es lo que
        // separa "se programa igual" de "te hemos cambiado un ajuste".
        if (alertNotifications && interval == BackgroundInterval.OFF) {
            Text(
                stringResource(
                    R.string.alert_notifications_schedule,
                    BackgroundUpdateWorker.ALERTS_ONLY_HOURS,
                ),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Spacer(Modifier.height(12.dp))

        SectionLabel(stringResource(R.string.application))
        SettingsRow(
            Icons.Outlined.SystemUpdate,
            stringResource(R.string.check_updates),
            onCheckUpdates,
        )
        SettingsRow(
            Icons.Outlined.Info,
            stringResource(R.string.about_nubo),
            onShowAbout,
            subtitle = stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
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
