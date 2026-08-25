package com.nubo.nubo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.domain.weather.AirQualityBand
import com.nubo.nubo.domain.weather.UvBand
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Condiciones de la hora en curso, en cuatro casillas.
 *
 * Existe porque la pantalla no tenía sitio para los datos de contexto: la
 * cabecera es "qué temperatura hace" y el resto de tarjetas son evolución en
 * el tiempo. Una tarjeta entera para la calidad del aire sola habría sido
 * justo el exceso que se quería evitar; con cuatro datos es una sección con
 * sentido propio, y tres de los cuatro ya venían en la llamada que se hacía.
 *
 * Solo la calidad del aire y el UV llevan color: son los dos que avisan de
 * algo. La humedad y la sensación se leen en blanco para que el color signifique
 * "mira esto" y no decore.
 */
@Composable
fun ConditionsCard(
    airQualityIndex: Int?,
    uvIndex: Double?,
    humidity: Int?,
    apparentTemperature: Int?,
    /** Temperatura real, para decir cuánto se aparta de ella la sensación. */
    temperature: Int?,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(Modifier.padding(top = 16.dp, bottom = 18.dp)) {
            SectionTitle(
                icon = Icons.Outlined.Speed,
                text = "Condiciones actuales",
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(18.dp))

            Row(Modifier.padding(horizontal = 16.dp)) {
                val band = airQualityIndex?.let { AirQualityBand.forAqi(it) }
                Tile(
                    label = "Calidad del aire",
                    value = airQualityIndex?.toString(),
                    caption = band?.label,
                    captionColor = band?.toColor(),
                )
                Tile(
                    label = "Índice UV",
                    value = uvIndex?.roundToInt()?.toString(),
                    caption = uvIndex?.let { UvBand.forIndex(it).label },
                    captionColor = uvIndex?.let { UvBand.forIndex(it).toColor() },
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(Modifier.padding(horizontal = 16.dp)) {
                Tile(
                    label = "Humedad",
                    value = humidity?.let { "$it %" },
                    caption = null,
                )
                Tile(
                    label = "Sensación",
                    value = apparentTemperature?.let { "$it°" },
                    caption = apparentDifference(apparentTemperature, temperature),
                )
            }
        }
    }
}

/**
 * "3° menos", "2° más", o nada si coinciden.
 *
 * Sin esta comparación la sensación térmica es un número más que casi siempre
 * repite la temperatura de arriba; lo que informa es en qué se aparta.
 */
private fun apparentDifference(apparent: Int?, real: Int?): String? {
    if (apparent == null || real == null) return null
    val difference = apparent - real
    if (difference == 0) return "Como la real"
    return "${abs(difference)}° ${if (difference > 0) "más" else "menos"}"
}

/** Una casilla: rótulo arriba, valor grande, y matiz opcional debajo. */
@Composable
private fun RowScope.Tile(
    label: String,
    value: String?,
    caption: String?,
    captionColor: Color? = null,
) {
    // Centrada dentro de su mitad: alineada a la izquierda, cada casilla
    // dejaba vacía la mitad derecha de su columna y la tarjeta se leía
    // descolgada hacia un lado.
    Column(
        Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label.uppercase(),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            fontWeight = FontWeight.W600,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            // Un guion, y no ocultar la casilla: así la rejilla no se
            // descuadra cuando falla el endpoint del aire.
            value ?: "—",
            color = if (value != null) Color.White else Color.White.copy(alpha = 0.35f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
        )
        if (caption != null) {
            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (captionColor != null) {
                    Spacer(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(captionColor),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    caption,
                    color = captionColor ?: Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontWeight = if (captionColor != null) FontWeight.W600 else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
