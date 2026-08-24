package com.nubo.nubo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tarjeta translúcida sobre el gradiente del cielo.
 *
 * Lleva un velo oscuro bajo el tinte claro para que **ocluya la lluvia** que
 * cae por detrás. En la app Flutter las tarjetas eran de un 5-10 % de opacidad
 * y las gotas se veían a través casi sin atenuar, con lo que parecía que la
 * lluvia pasaba por delante; el velo resuelve eso conservando que se siga
 * intuyendo el color del cielo debajo.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    tint: Color = Color.White.copy(alpha = 0.08f),
    scrim: Color = DefaultScrim,
    contentPadding: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(scrim)
            .background(tint)
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Velo por defecto.
 *
 * Un 42 % de un azul muy oscuro: suficiente para que una gota que pasa por
 * detrás quede claramente atenuada, y lo bastante transparente para que la
 * tarjeta siga tomando el color del cielo en cada momento del día.
 */
private val DefaultScrim = Color(0xFF0B1220).copy(alpha = 0.42f)
