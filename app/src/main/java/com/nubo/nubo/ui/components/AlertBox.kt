package com.nubo.nubo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.domain.model.WeatherAlert
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Avisos de AEMET, agrupados por tipo de fenómeno. */
@Composable
fun AlertBox(
    alerts: List<WeatherAlert>,
    modifier: Modifier = Modifier,
) {
    if (alerts.isEmpty()) return

    val grouped = remember(alerts) { alerts.groupBy { normalizeType(it.event) } }

    Column(modifier.fillMaxWidth()) {
        grouped.forEach { (title, group) ->
            AlertGroupTile(title = title, alerts = group)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AlertGroupTile(title: String, alerts: List<WeatherAlert>) {
    var expanded by remember { mutableStateOf(false) }

    // El grupo se pinta con el color del aviso más grave que contiene.
    val worst = remember(alerts) { alerts.maxByOrNull { it.severity } }
    val color = worst?.level?.toColor() ?: Color(0xFFFBC02D)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        tint = color.copy(alpha = 0.12f),
        borderColor = color,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(iconForType(title), null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    modifier = Modifier.weight(1f),
                )

                Text(
                    worst?.nivelDisplay?.uppercase().orEmpty(),
                    color = Color.Black.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(color)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )

                Spacer(Modifier.width(8.dp))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Contraer" else "Desplegar",
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    alerts.forEach { AlertDetail(it) }
                }
            }
        }
    }
}

@Composable
private fun AlertDetail(alert: WeatherAlert) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("EEE d, HH:mm", Locale.forLanguageTag("es-ES"))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Warning,
                null,
                tint = alert.level.toColor(),
                modifier = Modifier.size(10.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                alert.areaDescription,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.W600,
            )
        }

        if (alert.onset != null || alert.expires != null) {
            Spacer(Modifier.height(4.dp))
            val from = alert.onset?.format(formatter).orEmpty()
            val to = alert.expires?.format(formatter).orEmpty()
            Text(
                listOf(from, to).filter { it.isNotBlank() }.joinToString(" → "),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
        }

        if (alert.probability.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Probabilidad: ${alert.probability}",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
        }

        if (alert.description.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(alert.description, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }

    }
}

/** Agrupa los eventos de AEMET en familias legibles. */
internal fun normalizeType(event: String): String {
    val lower = event.lowercase()
    return when {
        lower.contains("viento") -> "Viento"
        lower.contains("costero") -> "Costeros"
        lower.contains("lluvia") || lower.contains("precipita") -> "Lluvia"
        lower.contains("nieve") || lower.contains("nevada") -> "Nieve"
        lower.contains("tormenta") -> "Tormenta"
        lower.contains("temperatura") -> "Temperaturas"
        lower.contains("niebla") -> "Niebla"
        lower.contains("polvo") -> "Polvo en suspensión"
        lower.contains("alud") -> "Aludes"
        lower.contains("deshielo") -> "Deshielos"
        else -> "Avisos meteorológicos"
    }
}

/**
 * Icono del fenómeno.
 *
 * Se comparte con el carrusel horario para que un aviso de viento se vea igual
 * en la tarjeta desplegable y en la hora concreta a la que afecta.
 */
internal fun iconForType(title: String): ImageVector = when (title) {
    "Viento" -> Icons.Outlined.Air
    "Costeros" -> Icons.Outlined.Waves
    "Lluvia" -> Icons.Outlined.WaterDrop
    "Nieve" -> Icons.Outlined.AcUnit
    "Tormenta" -> Icons.Outlined.Bolt
    "Temperaturas" -> Icons.Outlined.Thermostat
    "Niebla" -> Icons.Outlined.CloudQueue
    else -> Icons.Outlined.Warning
}
