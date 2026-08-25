package com.nubo.nubo.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Aritmética de reordenar las tarjetas del menú.
 *
 * Vive fuera del composable porque es lo único del gesto que puede estar mal
 * sin que se note: un desplazamiento de más deja huecos duplicados y un
 * redondeo al revés hace que la tarjeta caiga una posición corrida.
 */
class ReorderTargetTest {

    private val pitch = 100f

    @Test
    fun `el hueco se abre al pasar media tarjeta`() {
        assertEquals(1, reorderTarget(from = 1, offset = 49f, pitch = pitch, lastIndex = 4))
        assertEquals(2, reorderTarget(from = 1, offset = 51f, pitch = pitch, lastIndex = 4))
        assertEquals(0, reorderTarget(from = 1, offset = -51f, pitch = pitch, lastIndex = 4))
    }

    @Test
    fun `no se sale de la lista por arriba ni por abajo`() {
        assertEquals(4, reorderTarget(from = 1, offset = 900f, pitch = pitch, lastIndex = 4))
        assertEquals(0, reorderTarget(from = 1, offset = -900f, pitch = pitch, lastIndex = 4))
    }

    @Test
    fun `una lista vacia o sin medir deja la tarjeta donde estaba`() {
        assertEquals(2, reorderTarget(from = 2, offset = 500f, pitch = 0f, lastIndex = 4))
        assertEquals(0, reorderTarget(from = 0, offset = 500f, pitch = pitch, lastIndex = -1))
    }
}

class ReorderShiftTest {

    private val pitch = 100f

    @Test
    fun `bajando la tarjeta, las que adelanta suben un paso`() {
        // Se arrastra la 1 hasta la 3: la 2 y la 3 suben, la 4 no se entera.
        assertEquals(0f, reorderShift(0, from = 1, to = 3, pitch = pitch))
        assertEquals(0f, reorderShift(1, from = 1, to = 3, pitch = pitch))
        assertEquals(-pitch, reorderShift(2, from = 1, to = 3, pitch = pitch))
        assertEquals(-pitch, reorderShift(3, from = 1, to = 3, pitch = pitch))
        assertEquals(0f, reorderShift(4, from = 1, to = 3, pitch = pitch))
    }

    @Test
    fun `subiendo la tarjeta, las que adelanta bajan un paso`() {
        assertEquals(0f, reorderShift(0, from = 3, to = 1, pitch = pitch))
        assertEquals(pitch, reorderShift(1, from = 3, to = 1, pitch = pitch))
        assertEquals(pitch, reorderShift(2, from = 3, to = 1, pitch = pitch))
        assertEquals(0f, reorderShift(3, from = 3, to = 1, pitch = pitch))
    }

    @Test
    fun `sin arrastre nadie se mueve`() {
        repeat(5) { index ->
            assertEquals(0f, reorderShift(index, from = -1, to = -1, pitch = pitch))
        }
    }

    @Test
    fun `volver al origen deja la lista quieta`() {
        repeat(5) { index ->
            assertEquals(0f, reorderShift(index, from = 2, to = 2, pitch = pitch))
        }
    }
}
