package com.nubo.nubo.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Relevo del nombre de la ubicación entre la página y la barra superior.
 *
 * Lo que se fija aquí es que el nombre pequeño y los puntos de paginación
 * **nunca** estén visibles a la vez. Es la única defensa contra que choquen:
 * los puntos van centrados en la barra y su fila crece con cada ciudad
 * añadida, así que con unas cuantas guardadas se meterían justo donde aterriza
 * el nombre. En vez de medir anchos y confiar en que quepa, se garantiza que
 * no coincidan.
 */
class HeaderCollapseTest {

    /** Todo el recorrido en pasos de un 1 %, más los bordes. */
    private val sweep = (0..100).map { it / 100f }

    @Test
    fun `arriba del todo se ven los puntos y no el nombre`() {
        assertEquals(1f, paginationDotsAlpha(0f), 0f)
        assertEquals(0f, topBarNameAlpha(0f), 0f)
    }

    @Test
    fun `recogida del todo se ve el nombre y no los puntos`() {
        assertEquals(0f, paginationDotsAlpha(1f), 0f)
        assertEquals(1f, topBarNameAlpha(1f), 0f)
    }

    @Test
    fun `nombre y puntos nunca se ven a la vez`() {
        sweep.forEach { collapse ->
            val name = topBarNameAlpha(collapse)
            val dots = paginationDotsAlpha(collapse)
            assertTrue(
                "en $collapse coinciden nombre=$name y puntos=$dots",
                name == 0f || dots == 0f,
            )
        }
    }

    @Test
    fun `las dos opacidades son monotonas y acotadas`() {
        sweep.zipWithNext { previous, next ->
            assertTrue(topBarNameAlpha(next) >= topBarNameAlpha(previous))
            assertTrue(paginationDotsAlpha(next) <= paginationDotsAlpha(previous))
        }
        sweep.forEach {
            assertTrue(topBarNameAlpha(it) in 0f..1f)
            assertTrue(paginationDotsAlpha(it) in 0f..1f)
        }
    }

    @Test
    fun `un recorrido fuera de rango no descuadra nada`() {
        assertEquals(1f, paginationDotsAlpha(-0.5f), 0f)
        assertEquals(0f, topBarNameAlpha(-0.5f), 0f)
        assertEquals(0f, paginationDotsAlpha(3f), 0f)
        assertEquals(1f, topBarNameAlpha(3f), 0f)
    }
}
