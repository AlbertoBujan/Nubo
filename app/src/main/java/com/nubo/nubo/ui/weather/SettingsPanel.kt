package com.nubo.nubo.ui.weather

import com.nubo.nubo.ui.components.labelRes
import com.nubo.nubo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
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
import com.nubo.nubo.domain.model.DistanceUnit
import com.nubo.nubo.domain.model.SpeedUnit
import com.nubo.nubo.domain.model.TemperatureUnit
import com.nubo.nubo.domain.model.Units
import com.nubo.nubo.ui.components.labelRes
import com.nubo.nubo.ui.components.nameRes
import com.nubo.nubo.ui.components.shortRes
import com.nubo.nubo.work.BackgroundUpdateWorker
import kotlin.math.roundToInt

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
    uiScale: UiScale,
    onUiScaleChange: (UiScale) -> Unit,
    units: Units,
    onUnitsChange: (Units) -> Unit,
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
        SectionLabel(stringResource(R.string.accessibility))

        Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.FormatSize,
                null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.interface_size),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                )
                Text(
                    stringResource(uiScale.labelRes),
                    color = Color(0xFF64B5F6),
                    fontSize = 13.sp,
                )
            }
        }

        // Cuatro pasos, y el primero es el tamaño de siempre: quien no toque
        // nada no nota ningún cambio.
        Slider(
            value = uiScale.ordinal.toFloat(),
            onValueChange = { onUiScaleChange(UiScale.entries[it.roundToInt()]) },
            valueRange = 0f..(UiScale.entries.size - 1).toFloat(),
            steps = UiScale.entries.size - 2,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        // Las dos letras enseñan a dónde lleva cada extremo mejor que un
        // rótulo, y de paso se ven ya al tamaño del que hablan.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("A", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            Text("A", color = Color.White.copy(alpha = 0.75f), fontSize = 20.sp)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Spacer(Modifier.height(12.dp))

        SectionLabel(stringResource(R.string.units))

        // Cada magnitud tiene dos opciones y nada más, así que la fila enseña
        // cuál está puesta y tocarla cambia a la otra: dos filas de radio por
        // magnitud dirían lo mismo ocupando el doble.
        SettingsRow(
            icon = Icons.Outlined.Thermostat,
            label = stringResource(R.string.temperature),
            onClick = {
                onUnitsChange(units.copy(temperature = units.temperature.next()))
            },
            subtitle = stringResource(units.temperature.nameRes),
            trailing = { UnitTag(stringResource(units.temperature.shortRes)) },
        )
        SettingsRow(
            icon = Icons.Outlined.Air,
            label = stringResource(R.string.wind_speed),
            onClick = { onUnitsChange(units.copy(speed = units.speed.next())) },
            subtitle = stringResource(units.speed.nameRes),
            trailing = { UnitTag(stringResource(units.speed.shortRes)) },
        )
        SettingsRow(
            icon = Icons.Outlined.Straighten,
            label = stringResource(R.string.distance),
            onClick = { onUnitsChange(units.copy(distance = units.distance.next())) },
            subtitle = stringResource(units.distance.nameRes),
            trailing = { UnitTag(stringResource(units.distance.shortRes)) },
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Spacer(Modifier.height(12.dp))

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

/** La otra opción; con dos, "la siguiente" y "la otra" son lo mismo. */
private fun TemperatureUnit.next(): TemperatureUnit =
    if (this == TemperatureUnit.CELSIUS) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS

private fun SpeedUnit.next(): SpeedUnit =
    if (this == SpeedUnit.KMH) SpeedUnit.MPH else SpeedUnit.KMH

private fun DistanceUnit.next(): DistanceUnit =
    if (this == DistanceUnit.KILOMETRES) DistanceUnit.MILES else DistanceUnit.KILOMETRES

/** La unidad puesta, a la derecha de su fila. */
@Composable
private fun UnitTag(text: String) {
    Text(
        text,
        color = Color(0xFF64B5F6),
        fontSize = 15.sp,
        fontWeight = FontWeight.W600,
    )
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
