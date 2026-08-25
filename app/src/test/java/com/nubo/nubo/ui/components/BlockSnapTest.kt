package com.nubo.nubo.ui.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

/**
 * Rejilla de columnas del carrusel.
 *
 * El carrusel se desalineaba a los pocos gestos porque el bloque se calculaba
 * en `Dp` y las columnas se medían en píxeles enteros: dos píxeles de desfase
 * por bloque que se acumulaban. Estos tests fijan que las dos cuentas salgan
 * del mismo número.
 */
class ColumnGridTest {

    /** Anchos de tarjeta plausibles: móvil estrecho, normal, ancho, tablet. */
    private val viewports = listOf(900f, 1000f, 1080f, 1160f, 1234f, 1440f, 2000f)

    @Test
    fun `la columna mide un numero entero de pixeles`() {
        viewports.forEach { viewport ->
            val column = columnWidthPx(viewport)
            assertEquals(column, kotlin.math.floor(column), 0f)
        }
    }

    @Test
    fun `seis columnas nunca se salen de la tarjeta`() {
        // Este es el fallo original: el bloque medía 1160 y las seis columnas
        // 1158, así que el bloque prometía un ancho que el layout no daba.
        viewports.forEach { viewport ->
            val block = columnWidthPx(viewport) * 6
            assertTrue("bloque $block > tarjeta $viewport", block <= viewport)
        }
    }

    @Test
    fun `el recorrido total es un numero entero de bloques`() {
        viewports.forEach { viewport ->
            val column = columnWidthPx(viewport)
            val block = column * 6
            // La ventana visible es un bloque, no la tarjeta entera.
            val maxScroll = 48 * column - block
            assertEquals(
                "con tarjeta de $viewport quedan bloques a medias",
                0f,
                maxScroll % block,
                0.001f,
            )
        }
    }

    @Test
    fun `una tarjeta ridiculamente estrecha no da columnas de cero`() {
        assertEquals(1f, columnWidthPx(3f), 0f)
    }
}

/**
 * Ritmo e intensidad de los relámpagos.
 *
 * Se bajaron porque un fogonazo cada pocos segundos cansa y a quien tenga
 * sensibilidad a los destellos le molesta. Estos tests fijan los dos límites
 * para que no vuelvan a subir sin querer.
 */
class LightningTest {

    @Test
    fun `entre destello y destello pasan al menos nueve segundos`() {
        val times = flashTimes()
        assertTrue("apenas hay destellos: $times", times.size >= 2)

        times.zipWithNext { previous, next ->
            val gap = next - previous
            assertTrue("destellos a $gap s de distancia", gap >= MIN_FLASH_GAP_SECONDS)
            assertTrue(
                "destellos a $gap s: hueco demasiado largo",
                gap <= MIN_FLASH_GAP_SECONDS + FLASH_GAP_SPREAD,
            )
        }
    }

    @Test
    fun `abrir una ciudad con tormenta no recibe con un fogonazo`() {
        assertTrue(flashTimes().first() >= FIRST_FLASH_SECONDS)
    }

    @Test
    fun `el velo nunca blanquea la pantalla`() {
        val times = flashTimes()
        // Se recorre el destello completo en pasos de 10 ms.
        val peak = (0..40).maxOf { step ->
            flashAlphaAt(times.first() + step * 0.01f, times)
        }
        assertTrue("pico de $peak fuera de rango", peak in 0.9f..1f)
        assertTrue("velo al ${peak * MAX_FLASH_ALPHA}", peak * MAX_FLASH_ALPHA <= 0.3f)
    }

    @Test
    fun `fuera del destello no hay velo`() {
        val times = flashTimes()
        assertEquals(0f, flashAlphaAt(times.first() - 0.5f, times), 0f)
        assertEquals(0f, flashAlphaAt(times.first() + 1f, times), 0f)
    }
}
