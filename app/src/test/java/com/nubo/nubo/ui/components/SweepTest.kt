package com.nubo.nubo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Giro de las flechas de viento, que salen del norte hacia su dirección.
 *
 * Lo que se fija es que vayan por el camino corto: sin esto, un viento del
 * noroeste daría casi una vuelta entera para acabar a diez grados de donde
 * empezó, y el giro contaría algo que el viento no hace.
 */
class SweepTest {

    @Test
    fun `el norte no gira`() {
        assertEquals(0f, shortestSweep(0f), 0f)
        assertEquals(0f, shortestSweep(360f), 0f)
    }

    @Test
    fun `casi la vuelta entera se hace al reves`() {
        assertEquals(-10f, shortestSweep(350f), 0.001f)
        assertEquals(-90f, shortestSweep(270f), 0.001f)
    }

    @Test
    fun `lo que ya es corto no se toca`() {
        assertEquals(45f, shortestSweep(45f), 0.001f)
        assertEquals(179f, shortestSweep(179f), 0.001f)
    }

    @Test
    fun `media vuelta gira siempre al mismo lado`() {
        assertEquals(180f, shortestSweep(180f), 0f)
        assertEquals(180f, shortestSweep(-180f), 0f)
    }

    @Test
    fun `ningun giro pasa de media vuelta`() {
        (-720..720 step 7).forEach { degrees ->
            val sweep = shortestSweep(degrees.toFloat())
            assertTrue("$degrees gira $sweep", sweep > -180f && sweep <= 180f)
        }
    }
}
