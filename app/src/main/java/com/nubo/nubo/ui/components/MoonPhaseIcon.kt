package com.nubo.nubo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation

/**
 * La luna tal y como se ve ahora mismo: el disco entero apagado y encima la
 * parte iluminada.
 *
 * Es el único icono de la app que se dibuja a partir del dato en vez de
 * elegirse de una tabla, y la razón es que el icono de Material era siempre el
 * mismo creciente: con luna llena enseñaba un cuarto igualmente, y encima
 * abierto hacia el lado contrario al que se ve desde el hemisferio norte.
 *
 * La parte iluminada se arma como **un solo camino** y se pinta de una vez:
 * la mitad del disco que da la luz, y de ahí se resta la elipse del terminador
 * si es creciente —lo que deja la hoz— o se le suma si es convexa, que la
 * desborda hacia el lado oscuro. En la mitad exacta la elipse no tiene ancho y
 * queda el medio disco limpio.
 *
 * Restar es la única forma que funciona: pintar la elipse encima con el color
 * apagado no recorta nada, porque ese color es translúcido a propósito —la
 * tarjeta tiene que dejar ver el cielo— y lo único que hacía era desteñir la
 * mitad iluminada. Con eso, un creciente del 12 % se dibujaba como media luna.
 */
@Composable
fun MoonPhaseIcon(
    /** Fracción del disco iluminada, 0..1. */
    illumination: Double,
    /** Creciendo hacia la llena; si no, menguando. */
    waxing: Boolean,
    /**
     * Desde el hemisferio sur la luna se ve al revés: el creciente abre hacia
     * el otro lado. La app enseña ciudades de todo el mundo, así que el lado
     * iluminado no puede darse por sabido.
     */
    mirrored: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        val lit = illumination.coerceIn(0.0, 1.0).toFloat()

        val dark = tint.copy(alpha = 0.22f)
        drawCircle(dark, radius, centre)
        // Un filo tenue: con luna nueva el disco apagado se confunde con el
        // fondo y la tarjeta se quedaría sin icono.
        drawCircle(tint.copy(alpha = 0.45f), radius, centre, style = Stroke(width = density))

        if (lit <= 0.01f) return@Canvas
        if (lit >= 0.99f) {
            drawCircle(tint, radius, centre)
            return@Canvas
        }

        // Con la luna creciendo, la luz viene por la derecha; menguando, por la
        // izquierda. Al otro lado del ecuador, al revés.
        val litOnRight = waxing != mirrored
        val disc = Rect(centre.x - radius, centre.y - radius, centre.x + radius, centre.y + radius)

        val half = Path().apply {
            moveTo(centre.x, centre.y - radius)
            arcTo(disc, -90f, if (litOnRight) 180f else -180f, false)
            close()
        }

        val terminator = radius * abs(1f - 2f * lit)
        val ellipse = Path().apply {
            addOval(
                Rect(
                    centre.x - terminator,
                    centre.y - radius,
                    centre.x + terminator,
                    centre.y + radius,
                ),
            )
        }

        val shape = Path()
        shape.op(
            half,
            ellipse,
            if (lit < 0.5f) PathOperation.Difference else PathOperation.Union,
        )

        drawPath(shape, tint)
    }
}
