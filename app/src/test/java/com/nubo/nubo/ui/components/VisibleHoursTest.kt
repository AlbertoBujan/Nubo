package com.nubo.nubo.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cuántas horas caben en la tarjeta.
 *
 * Con la interfaz agrandada la pantalla no crece, así que el ancho de la
 * tarjeta **en dp** encoge: las seis de siempre dejarían de caber y las
 * columnas se apretarían hasta cortar "12 km/h". El número sale del ancho que
 * hay, no del ajuste, de modo que un móvil estrecho recibe el mismo trato sin
 * haber tocado nada.
 */
class VisibleHoursTest {

    /** Ancho de la tarjeta en un móvil de 360 dp con los márgenes actuales. */
    private val phone = 336.dp

    @Test
    fun `a tamano normal caben las seis de siempre`() {
        assertEquals(6, visibleHoursIn(phone))
        assertEquals(6, visibleHoursIn(465.dp))
    }

    @Test
    fun `al agrandar la interfaz caben menos`() {
        // Agrandar multiplica la densidad, así que el mismo ancho físico son
        // menos dp: 336 / 1,3 = 258.
        assertTrue(visibleHoursIn(phone / 1.15f) < 6)
        assertEquals(4, visibleHoursIn(phone / 1.3f))
        assertEquals(4, visibleHoursIn(phone / 1.45f))
    }

    @Test
    fun `nunca baja de tres ni pasa de seis`() {
        (40..900 step 7).forEach { ancho ->
            val horas = visibleHoursIn(ancho.dp)
            assertTrue("$ancho dp -> $horas", horas in 3..6)
        }
    }

    @Test
    fun `mas ancho nunca da menos columnas`() {
        (40..900 step 13).map { visibleHoursIn(it.dp) }.zipWithNext { menor, mayor ->
            assertTrue(mayor >= menor)
        }
    }

    @Test
    fun `la columna que sale nunca baja del minimo legible`() {
        listOf(200, 260, 336, 465).forEach { ancho ->
            val horas = visibleHoursIn(ancho.dp)
            // Salvo cuando ya se ha tocado el suelo de tres columnas, que es
            // preferible a enseñar una sola hora en pantalla.
            if (horas > 3) assertTrue("$ancho dp", ancho.toFloat() / horas >= 54f)
        }
    }
}
