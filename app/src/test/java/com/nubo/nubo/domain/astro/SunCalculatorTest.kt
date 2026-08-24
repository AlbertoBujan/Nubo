package com.nubo.nubo.domain.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Verifica el port de los cálculos solares contra hechos físicos conocidos.
 *
 * Se comprueba la **duración del día** en vez de las horas absolutas: la
 * duración no depende de la zona horaria elegida, así que un fallo señala un
 * error real en las fórmulas y no un desajuste de husos en el test.
 */
class SunCalculatorTest {

    private val madridLat = 40.4168
    private val madridLng = -3.7038
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun dayLengthMinutes(times: SunTimes): Long =
        Duration.between(times.sunrise, times.sunset).toMinutes()

    @Test
    fun `el solsticio de verano en Madrid dura unas 15 horas`() {
        val times = SunCalculator.calculateTimes(
            LocalDateTime.of(2025, 6, 21, 12, 0), madridLat, madridLng, madrid,
        )
        assertNotNull(times)

        // Valor real en Madrid: 15 h 03 min. Se admite un margen de 15 min.
        val minutes = dayLengthMinutes(times!!)
        assertTrue("duración fue $minutes min", minutes in 888..918)
    }

    @Test
    fun `el solsticio de invierno en Madrid dura unas 9 horas y cuarto`() {
        val times = SunCalculator.calculateTimes(
            LocalDateTime.of(2025, 12, 21, 12, 0), madridLat, madridLng, madrid,
        )
        assertNotNull(times)

        // Valor real en Madrid: 9 h 17 min.
        val minutes = dayLengthMinutes(times!!)
        assertTrue("duración fue $minutes min", minutes in 542..572)
    }

    @Test
    fun `en el ecuador el dia dura unas 12 horas todo el año`() {
        for (month in 1..12) {
            val times = SunCalculator.calculateTimes(
                LocalDateTime.of(2025, month, 15, 12, 0), 0.0, 0.0, ZoneId.of("UTC"),
            )
            assertNotNull("mes $month", times)
            val minutes = dayLengthMinutes(times!!)
            assertTrue("mes $month duró $minutes min", minutes in 710..730)
        }
    }

    @Test
    fun `el amanecer siempre precede al ocaso`() {
        for (month in 1..12) {
            val times = SunCalculator.calculateTimes(
                LocalDateTime.of(2025, month, 10, 12, 0), madridLat, madridLng, madrid,
            )
            assertNotNull(times)
            assertTrue("mes $month", times!!.sunrise.isBefore(times.sunset))
        }
    }

    @Test
    fun `el amanecer y el ocaso caen en el dia solicitado`() {
        val times = SunCalculator.calculateTimes(
            LocalDateTime.of(2025, 3, 15, 12, 0), madridLat, madridLng, madrid,
        )
        assertNotNull(times)
        assertEquals(15, times!!.sunrise.dayOfMonth)
        assertEquals(15, times.sunset.dayOfMonth)
    }

    @Test
    fun `la noche polar no tiene amanecer`() {
        // Svalbard en pleno invierno: el sol no llega a salir.
        val times = SunCalculator.calculateTimes(
            LocalDateTime.of(2025, 12, 21, 12, 0), 78.2, 15.6, ZoneId.of("UTC"),
        )
        assertNull(times)
    }

    @Test
    fun `el sol de medianoche tampoco tiene ocaso`() {
        val times = SunCalculator.calculateTimes(
            LocalDateTime.of(2025, 6, 21, 12, 0), 78.2, 15.6, ZoneId.of("UTC"),
        )
        assertNull(times)
    }

    @Test
    fun `el hemisferio sur invierte las estaciones`() {
        val sydneyLat = -33.87
        val sydneyLng = 151.21
        val sydney = ZoneId.of("Australia/Sydney")

        val junio = SunCalculator.calculateTimes(
            LocalDateTime.of(2025, 6, 21, 12, 0), sydneyLat, sydneyLng, sydney,
        )
        val diciembre = SunCalculator.calculateTimes(
            LocalDateTime.of(2025, 12, 21, 12, 0), sydneyLat, sydneyLng, sydney,
        )

        // En junio es invierno austral, así que el día es más corto.
        assertTrue(dayLengthMinutes(junio!!) < dayLengthMinutes(diciembre!!))
    }
}
