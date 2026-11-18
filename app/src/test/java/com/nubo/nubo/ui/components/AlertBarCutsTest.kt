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

/**
 * Qué se pinta **mientras la fila se abre o se cierra**.
 *
 * El despliegue mete el contenido desplazado y la tarjeta crece a la vez, así
 * que a medio camino lo medido todavía no describe dónde van a quedar los
 * discos: la franja entera se pintaba del color del último aviso hasta que la
 * animación terminaba. Lo que se promete aquí es que no hay ningún instante
 * con un solo color, y que al llegar se llega exactamente a lo medido.
 */
class AlertBarAnimationTest {

    @Test
    fun `cerrada reparte a partes iguales`() {
        val cuts = alertBarCutsAt(listOf(0.7f, 0.8f), count = 3, progress = 0f)

        assertEquals(listOf(1f / 3f, 2f / 3f), cuts)
    }

    @Test
    fun `abierta del todo es exactamente lo medido`() {
        val medido = listOf(0.7f, 0.8f)

        assertEquals(medido, alertBarCutsAt(medido, count = 3, progress = 1f))
    }

    @Test
    fun `a medio camino cada tramo sigue teniendo alto`() {
        // Lo que rompía: a mitad de la animación salía un solo color.
        val medido = listOf(0.72f, 0.81f, 0.9f)

        for (step in 0..20) {
            val cuts = alertBarCutsAt(medido, count = 4, progress = step / 20f)
            val bordes = listOf(0f) + cuts + listOf(1f)
            bordes.zipWithNext { arriba, abajo ->
                assertTrue(
                    "tramo sin alto con progress=${step / 20f}: $cuts",
                    abajo - arriba > 0.01f,
                )
            }
        }
    }

    @Test
    fun `los tramos van en orden y dentro de la tarjeta`() {
        val medido = listOf(0.72f, 0.81f, 0.9f)

        for (step in 0..20) {
            val cuts = alertBarCutsAt(medido, count = 4, progress = step / 20f)
            cuts.zipWithNext { arriba, abajo -> assertTrue(abajo > arriba) }
            cuts.forEach { assertTrue(it > 0f && it < 1f) }
        }
    }

    @Test
    fun `sin medida se reparte a partes iguales aunque esté abierta`() {
        // La fila cerrada no tiene discos que medir, y el primer fotograma de
        // la abierta tampoco: ahí manda el reparto, no una lista a medias.
        assertEquals(listOf(0.5f), alertBarCutsAt(emptyList(), count = 2, progress = 1f))
        assertEquals(listOf(0.5f), alertBarCutsAt(listOf(0.4f, 0.6f), count = 2, progress = 1f))
    }

    @Test
    fun `un solo aviso no tiene fronteras`() {
        assertTrue(alertBarCutsAt(emptyList(), count = 1, progress = 1f).isEmpty())
    }

    @Test
    fun `lo abierta que está sale del alto de la tarjeta`() {
        // Cabecera de 100 y contenido de 300: la tarjeta va de 100 a 400.
        assertEquals(0f, alertBarProgress(100f, 100f, 300f), 0.001f)
        assertEquals(0.5f, alertBarProgress(250f, 100f, 300f), 0.001f)
        assertEquals(1f, alertBarProgress(400f, 100f, 300f), 0.001f)
    }

    @Test
    fun `sin medir nada la fila cuenta como cerrada`() {
        assertEquals(0f, alertBarProgress(0f, 0f, 0f), 0.001f)
        assertEquals(0f, alertBarProgress(400f, 100f, 0f), 0.001f)
    }

    @Test
    fun `un alto fuera de rango no se sale de la franja`() {
        // Entre pasadas de colocación puede llegar un alto de más.
        assertEquals(1f, alertBarProgress(900f, 100f, 300f), 0.001f)
        assertEquals(0f, alertBarProgress(50f, 100f, 300f), 0.001f)
    }
}
