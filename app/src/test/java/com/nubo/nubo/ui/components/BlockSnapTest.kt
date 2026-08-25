package com.nubo.nubo.ui.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asentamiento por bloques del carrusel horario.
 *
 * Es la parte del gesto que puede fallar en silencio: un gesto que salta dos
 * bloques o uno que no se mueve se notan al usarlo, pero no al leer el código.
 */
class BlockSnapTest {

    private val block = 600f
    private val max = 4200f // siete bloques de margen

    @Test
    fun `un impulso fuerte avanza un bloque, no tres`() {
        // Apenas 30 px arrastrados, pero con inercia para irse muy lejos.
        assertEquals(600f, blockTarget(30f, 3_000f, block, max))
        assertEquals(600f, blockTarget(30f, 30_000f, block, max))
    }

    @Test
    fun `y hacia atras tampoco se salta ninguno`() {
        assertEquals(600f, blockTarget(1170f, -3_000f, block, max))
        assertEquals(600f, blockTarget(1170f, -30_000f, block, max))
    }

    @Test
    fun `un impulso que no llega a medio bloque devuelve la columna a su sitio`() {
        // Un toque flojo tras mover el carrusel dos dedos: la inercia se
        // agota en un tercio de bloque, así que no cuenta como pasar página.
        assertEquals(0f, blockTarget(30f, 200f, block, max))
    }

    @Test
    fun `soltar sin velocidad cae en el bloque mas cercano`() {
        assertEquals(600f, blockTarget(780f, 780f, block, max))
        assertEquals(1200f, blockTarget(1020f, 1020f, block, max))
    }

    @Test
    fun `no se pasa de los extremos`() {
        assertEquals(0f, blockTarget(0f, -5000f, block, max))
        assertEquals(max, blockTarget(max, 9000f, block, max))
    }

    @Test
    fun `parado en un bloque exacto no se mueve solo`() {
        assertEquals(1200f, blockTarget(1200f, 1200f, block, max))
    }

    @Test
    fun `sin medir todavia se queda donde esta`() {
        assertEquals(450f, blockTarget(450f, 9000f, 0f, max))
    }
}

/**
 * Prolongación de las curvas hasta el borde de la tarjeta.
 *
 * El primer punto cae a media columna del borde, así que sin prolongar la
 * curva empieza en seco. Un signo cambiado aquí dobla la línea hacia dentro en
 * vez de sacarla, y como pasa fuera de la zona de puntos es fácil que cuele.
 */
class ExtendToEdgesTest {

    @Test
    fun `añade un punto a cada lado siguiendo la pendiente`() {
        val extended = extendToEdges(listOf(Offset(10f, 20f), Offset(20f, 30f)))

        assertEquals(4, extended.size)
        // Mismo paso y misma pendiente hacia fuera por los dos extremos.
        assertEquals(Offset(0f, 10f), extended.first())
        assertEquals(Offset(30f, 40f), extended.last())
    }

    @Test
    fun `los puntos originales quedan intactos y en orden`() {
        val points = listOf(Offset(0f, 5f), Offset(10f, 9f), Offset(20f, 4f))
        val extended = extendToEdges(points)

        assertEquals(points, extended.subList(1, extended.size - 1))
    }

    @Test
    fun `una curva plana se prolonga plana`() {
        val extended = extendToEdges(listOf(Offset(10f, 50f), Offset(20f, 50f)))

        assertEquals(50f, extended.first().y)
        assertEquals(50f, extended.last().y)
    }

    @Test
    fun `con menos de dos puntos no hay pendiente que seguir`() {
        val single = listOf(Offset(3f, 4f))
        assertEquals(single, extendToEdges(single))
        assertEquals(emptyList<Offset>(), extendToEdges(emptyList()))
    }
}
