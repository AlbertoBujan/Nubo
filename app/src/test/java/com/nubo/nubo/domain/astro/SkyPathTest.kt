package com.nubo.nubo.domain.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Se valida contra hechos físicos, no contra valores copiados de la propia
 * implementación: la curva debe cambiar con la latitud y con la estación, que
 * es justo lo que una parábola fija no hacía.
 */
class SkyPathTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val curtisLat = 43.14
    private val curtisLon = -8.04

    private fun pathFor(date: LocalDateTime, lat: Double, lon: Double): List<Float> {
        val (rise, set) = SunCalculator.calculateTimes(date, lat, lon, madrid)
            ?.let { it.sunrise to it.sunset }
            ?: error("sin orto ni ocaso")
        return SkyPath.sun(rise, set, lat, lon, madrid)
    }

    @Test
    fun `arranca y acaba en el horizonte, con la cima en medio`() {
        val path = pathFor(LocalDateTime.of(2026, 6, 21, 12, 0), curtisLat, curtisLon)

        assertEquals(SkyPath.SAMPLES, path.size)
        assertEquals(0f, path.first(), 0.02f)
        assertEquals(0f, path.last(), 0.02f)
        // El máximo cae en el mediodía solar, en el centro del recorrido.
        val peak = path.indexOf(path.max())
        assertTrue("La cima estaba en $peak de ${path.size}", peak in 22..25)
    }

    @Test
    fun `es simetrica respecto al mediodia solar`() {
        val path = pathFor(LocalDateTime.of(2026, 3, 20, 12, 0), curtisLat, curtisLon)

        for (i in path.indices) {
            assertEquals(path[i], path[path.size - 1 - i], 0.02f)
        }
    }

    @Test
    fun `el arco de verano es mucho mas alto que el de invierno`() {
        // Esta es la diferencia que una curva fija no puede representar. En
        // Galicia el sol culmina a unos 70º en junio y a unos 24º en
        // diciembre, y el dibujo tiene que notarlo.
        val verano = pathFor(LocalDateTime.of(2026, 6, 21, 12, 0), curtisLat, curtisLon)
        val invierno = pathFor(LocalDateTime.of(2026, 12, 21, 12, 0), curtisLat, curtisLon)

        assertEquals(70.4f / 90f, verano.max(), 0.03f)
        assertEquals(23.6f / 90f, invierno.max(), 0.03f)
    }

    @Test
    fun `el arco es mas alto cuanto mas cerca del ecuador`() {
        // En el equinoccio la altura máxima es 90º menos la latitud, así que
        // en Nairobi casi roza el cenit y en Tromsø se queda a ras.
        val nairobi = pathFor(LocalDateTime.of(2026, 3, 20, 12, 0), -1.29, 36.82)
        val tromso = pathFor(LocalDateTime.of(2026, 3, 20, 12, 0), 69.65, 18.95)

        assertEquals(88.7f / 90f, nairobi.max(), 0.03f)
        assertEquals(20.3f / 90f, tromso.max(), 0.03f)
    }

    @Test
    fun `la altura interpolada respeta las muestras`() {
        val path = listOf(0f, 0.5f, 1f)

        assertEquals(0f, SkyPath.heightAt(path, 0f), 0.0001f)
        assertEquals(0.5f, SkyPath.heightAt(path, 0.5f), 0.0001f)
        assertEquals(1f, SkyPath.heightAt(path, 1f), 0.0001f)
        // Y a mitad de camino entre dos muestras, la media.
        assertEquals(0.25f, SkyPath.heightAt(path, 0.25f), 0.0001f)
    }

    @Test
    fun `una trayectoria vacia no rompe la interpolacion`() {
        assertEquals(0f, SkyPath.heightAt(emptyList(), 0.5f), 0.0001f)
    }

    @Test
    fun `un recorrido invertido o nulo no devuelve nada`() {
        val momento = LocalDateTime.of(2026, 6, 21, 12, 0)

        assertTrue(SkyPath.sun(momento, momento, curtisLat, curtisLon, madrid).isEmpty())
        assertTrue(
            SkyPath.sun(momento, momento.minusHours(2), curtisLat, curtisLon, madrid).isEmpty(),
        )
    }
}
