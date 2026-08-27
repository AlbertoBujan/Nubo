package com.nubo.nubo.domain.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Se valida contra hechos del calendario —el día mengua tras el solsticio de
 * verano y crece tras el de invierno— y no contra lo que devuelva la fórmula.
 */
class DayLengthTest {

    private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
    private val coruna = 43.3713 to -8.396

    private fun dia(fecha: LocalDateTime, zone: ZoneId = madrid, lugar: Pair<Double, Double> = coruna) =
        SunCalculator.calculateTimes(fecha, lugar.first, lugar.second, zone)!!

    private fun medida(fecha: LocalDateTime, zone: ZoneId = madrid) =
        dayLengthOf(dia(fecha, zone), dia(fecha.minusDays(1), zone), zone)

    @Test
    fun `en el solsticio de verano el dia es el mas largo del ano`() {
        val junio = medida(LocalDateTime.of(2026, 6, 21, 12, 0)).today
        val diciembre = medida(LocalDateTime.of(2026, 12, 21, 12, 0)).today

        assertTrue("$junio", junio.toHours() >= 15)
        assertTrue("$diciembre", diciembre.toHours() <= 9)
    }

    @Test
    fun `despues del solsticio de verano el dia mengua`() {
        val cambio = medida(LocalDateTime.of(2026, 8, 27, 12, 0)).sinceYesterday!!

        assertTrue("cambió $cambio", cambio.isNegative)
        // A finales de agosto en Galicia se pierden unos tres minutos al día.
        assertTrue("cambió $cambio", abs(cambio.toMinutes()) in 2..5)
    }

    @Test
    fun `despues del solsticio de invierno el dia crece`() {
        val cambio = medida(LocalDateTime.of(2026, 1, 20, 12, 0)).sinceYesterday!!

        assertTrue("cambió $cambio", !cambio.isNegative && cambio.toMinutes() >= 1)
    }

    @Test
    fun `en el ecuador el dia apenas se mueve`() {
        val quito = -0.1807 to -78.4678
        val zone = ZoneId.of("America/Guayaquil")
        val fecha = LocalDateTime.of(2026, 8, 27, 12, 0)
        val medida = dayLengthOf(dia(fecha, zone, quito), dia(fecha.minusDays(1), zone, quito), zone)

        assertEquals(12L, medida.today.toHours())
        assertTrue("${medida.sinceYesterday}", abs(medida.sinceYesterday!!.toSeconds()) < 60)
    }

    @Test
    fun `el dia del cambio de hora no inventa una hora`() {
        // Último domingo de octubre: el reloj atrasa una hora. Restando fechas
        // locales el día saldría una hora más largo de lo que es.
        val cambio = LocalDateTime.of(2026, 10, 25, 12, 0)
        val medida = medida(cambio)

        assertTrue("duró ${medida.today}", medida.today.toHours() in 9..11)
        assertTrue("cambió ${medida.sinceYesterday}", abs(medida.sinceYesterday!!.toMinutes()) < 10)
    }

    @Test
    fun `sin dato de ayer no se inventa la comparacion`() {
        val hoy = dia(LocalDateTime.of(2026, 8, 27, 12, 0))

        assertNull(dayLengthOf(hoy, null, madrid).sinceYesterday)
    }
}
