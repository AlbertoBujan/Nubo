package com.nubo.nubo.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Viaje del nombre de la ubicación desde la página hasta la barra superior.
 *
 * El nombre ya no se apaga en un sitio para encenderse en otro: es uno solo que
 * se mueve, así que está visible durante todo el recorrido. Eso quita la vieja
 * defensa contra que choque con los puntos de paginación —que era, justamente,
 * que nunca coincidieran— y la sustituye por esta: los puntos se han ido del
 * todo cuando el nombre lleva menos de medio camino, o sea mucho antes de que
 * llegue a la altura de la barra, que es donde ellos están.
 */
class HeaderCollapseTest {

    /** Todo el recorrido en pasos de un 1 %, más los bordes. */
    private val sweep = (0..100).map { it / 100f }

    @Test
    fun `arriba del todo el nombre esta entero y en su sitio`() {
        assertEquals(0f, nameTravel(0f), 0f)
        assertEquals(1f, nameScale(0f), 0f)
        assertEquals(1f, paginationDotsAlpha(0f), 0f)
    }

    @Test
    fun `recogida del todo el nombre esta en la barra y a su tamano`() {
        assertEquals(1f, nameTravel(1f), 0f)
        assertEquals(0.6f, nameScale(1f), 1e-6f)
        assertEquals(0f, paginationDotsAlpha(1f), 0f)
    }

    @Test
    fun `los puntos se van antes de que el nombre se acerque a la barra`() {
        val gone = sweep.first { paginationDotsAlpha(it) == 0f }

        assertTrue("los puntos aguantan hasta $gone del viaje", nameTravel(gone) < 0.5f)
    }

    @Test
    fun `el nombre no da saltos ni se sale de escala`() {
        sweep.zipWithNext { previous, next ->
            assertTrue(nameTravel(next) >= nameTravel(previous))
            assertTrue(nameScale(next) <= nameScale(previous))
            // Un salto de más de un 2 % con un paso del 1 % sería un tirón.
            assertTrue(nameTravel(next) - nameTravel(previous) <= 0.02f)
        }
        sweep.forEach {
            assertTrue(nameTravel(it) in 0f..1f)
            assertTrue(nameScale(it) in 0.6f..1f)
        }
    }

    @Test
    fun `la chincheta solo asoma al final del viaje`() {
        assertEquals(0f, pinAlpha(0f), 0f)
        assertEquals(1f, pinAlpha(1f), 0f)

        // Mientras el nombre está de camino no hay chincheta: suelta en mitad
        // de la pantalla no marcaría nada.
        sweep.filter { nameTravel(it) < 0.7f }.forEach {
            assertEquals("chincheta visible en $it", 0f, pinAlpha(it), 0f)
        }
        sweep.zipWithNext { previous, next ->
            assertTrue(pinAlpha(next) >= pinAlpha(previous))
        }
    }

    @Test
    fun `un recorrido fuera de rango no descuadra nada`() {
        assertEquals(0f, nameTravel(-0.5f), 0f)
        assertEquals(1f, nameTravel(3f), 0f)
        assertEquals(1f, paginationDotsAlpha(-0.5f), 0f)
        assertEquals(0f, paginationDotsAlpha(3f), 0f)
        assertEquals(0f, pinAlpha(-0.5f), 0f)
        assertEquals(1f, pinAlpha(3f), 0f)
    }
}
