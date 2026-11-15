package com.nubo.nubo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dónde parte cada color de la franja de avisos.
 *
 * Lo que se comprueba no son números sacados de una ejecución, sino la
 * promesa: cada frontera cae **a medio camino entre dos discos**, que es lo
 * que pone cada color a la altura de su aviso. Repartir a partes iguales
 * dejaba el disco rojo sobre el tramo naranja en cuanto había cuatro avisos.
 */
class AlertBarCutsTest {

    @Test
    fun `parte a medio camino entre dos discos`() {
        // Tarjeta de 0 a 200. Discos en 60 y 140: la frontera va en 100.
        val cuts = alertBarCuts(listOf(60f, 140f), cardTopY = 0f, cardHeight = 200f)

        assertEquals(listOf(0.5f), cuts)
    }

    @Test
    fun `descuenta dónde empieza la tarjeta`() {
        // La misma tarjeta, pero desplazada 1000 px al bajar la lista: las
        // fronteras no se mueven, porque lo que vale es la diferencia.
        val quieta = alertBarCuts(listOf(60f, 140f), cardTopY = 0f, cardHeight = 200f)
        val bajada = alertBarCuts(listOf(1060f, 1140f), cardTopY = 1000f, cardHeight = 200f)

        assertEquals(quieta, bajada)
    }

    @Test
    fun `cada disco cae dentro de su tramo`() {
        // Tres discos agrupados en la mitad de abajo, que es lo que pasa de
        // verdad: la cabecera de la fila va arriba y la rejilla debajo.
        val centers = listOf(120f, 150f, 180f)
        val height = 260f
        val cuts = alertBarCuts(centers, cardTopY = 0f, cardHeight = height)

        val bordes = listOf(0f) + cuts + listOf(1f)
        centers.forEachIndexed { index, center ->
            val fraccion = center / height
            assertTrue(
                "el disco $index cae fuera de su tramo",
                fraccion > bordes[index] && fraccion < bordes[index + 1],
            )
        }
    }

    @Test
    fun `las fronteras van en orden`() {
        val cuts = alertBarCuts(listOf(50f, 90f, 130f, 170f), cardTopY = 0f, cardHeight = 300f)

        assertEquals(3, cuts.size)
        assertEquals(cuts.sorted(), cuts)
    }

    @Test
    fun `un solo aviso no parte nada`() {
        assertEquals(emptyList<Float>(), alertBarCuts(listOf(80f), cardTopY = 0f, cardHeight = 200f))
    }

    @Test
    fun `sin medir alguno de los discos no dice nada`() {
        // Media medida es peor que ninguna: quien dibuja reparte a partes
        // iguales mientras tanto, y eso es lo correcto hasta tenerlas todas.
        assertEquals(
            emptyList<Float>(),
            alertBarCuts(listOf(60f, null, 180f), cardTopY = 0f, cardHeight = 200f),
        )
    }

    @Test
    fun `sin alto de tarjeta no dice nada`() {
        assertEquals(
            emptyList<Float>(),
            alertBarCuts(listOf(60f, 140f), cardTopY = 0f, cardHeight = 0f),
        )
        assertEquals(
            emptyList<Float>(),
            alertBarCuts(listOf(60f, 140f), cardTopY = Float.NaN, cardHeight = 200f),
        )
    }

    @Test
    fun `una frontera nunca se sale de la tarjeta`() {
        // Durante la animación de cierre los discos pueden quedar medidos por
        // debajo del borde de una tarjeta que ya se ha encogido.
        val cuts = alertBarCuts(listOf(400f, 600f), cardTopY = 0f, cardHeight = 100f)

        assertEquals(listOf(1f), cuts)
        assertTrue(cuts.all { it in 0f..1f })
    }
}
