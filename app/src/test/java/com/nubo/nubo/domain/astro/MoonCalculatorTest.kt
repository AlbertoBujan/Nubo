package com.nubo.nubo.domain.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

class MoonCalculatorTest {

    private val madridLat = 40.4168
    private val madridLng = -3.7038
    private val utc: ZoneId = ZoneId.of("UTC")

    @Test
    fun `fase e iluminacion se mantienen dentro de rango todo un año`() {
        var date = LocalDateTime.of(2025, 1, 1, 0, 0)
        repeat(365) {
            val moon = MoonCalculator.calculate(date, madridLat, madridLng, utc)
            assertTrue("fase ${moon.phase} en $date", moon.phase in 0.0..1.0)
            assertTrue("iluminación ${moon.illumination} en $date", moon.illumination in 0.0..1.0)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `el ciclo lunar completo dura un mes sinodico`() {
        // Se cuentan las lunas nuevas de un año: la fase cruza de ~1 a ~0.
        var previous = MoonCalculator
            .calculate(LocalDateTime.of(2025, 1, 1, 12, 0), madridLat, madridLng, utc).phase
        var newMoons = 0

        var date = LocalDateTime.of(2025, 1, 2, 12, 0)
        repeat(364) {
            val phase = MoonCalculator.calculate(date, madridLat, madridLng, utc).phase
            if (phase < previous - 0.5) newMoons++
            previous = phase
            date = date.plusDays(1)
        }

        // 365 / 29.53 = 12,36 ciclos.
        assertTrue("se contaron $newMoons lunas nuevas", newMoons in 11..13)
    }

    @Test
    fun `la luna llena ilumina el disco entero y la nueva no`() {
        var maxIllumination = 0.0
        var minIllumination = 1.0

        var date = LocalDateTime.of(2025, 1, 1, 12, 0)
        repeat(60) {
            val moon = MoonCalculator.calculate(date, madridLat, madridLng, utc)
            if (moon.illumination > maxIllumination) maxIllumination = moon.illumination
            if (moon.illumination < minIllumination) minIllumination = moon.illumination
            date = date.plusDays(1)
        }

        // En dos meses tiene que haber al menos una llena y una nueva.
        assertTrue("máxima iluminación fue $maxIllumination", maxIllumination > 0.97)
        assertTrue("mínima iluminación fue $minIllumination", minIllumination < 0.03)
    }

    @Test
    fun `la iluminacion concuerda con la fase`() {
        var date = LocalDateTime.of(2025, 3, 1, 12, 0)
        repeat(60) {
            val moon = MoonCalculator.calculate(date, madridLat, madridLng, utc)
            // La fracción iluminada es máxima en la llena (fase 0,5) y mínima
            // en la nueva (fase 0 o 1); el error admitido cubre la excentricidad.
            val expected = (1 - kotlin.math.cos(2 * Math.PI * moon.phase)) / 2
            assertTrue(
                "fase ${moon.phase} vs iluminación ${moon.illumination} en $date",
                abs(expected - moon.illumination) < 0.06,
            )
            date = date.plusDays(1)
        }
    }

    @Test
    fun `hay orto y ocaso lunar en un dia normal`() {
        val moon = MoonCalculator.calculate(
            LocalDateTime.of(2025, 5, 15, 12, 0), madridLat, madridLng, utc,
        )
        assertTrue("moonrise nulo", moon.moonrise != null)
        assertTrue("moonset nulo", moon.moonset != null)
    }

    @Test
    fun `los nombres de fase cubren el ciclo`() {
        assertEquals("Luna nueva", MoonCalculator.phaseName(0.0))
        assertEquals("Luna nueva", MoonCalculator.phaseName(0.99))
        assertEquals("Creciente cóncava", MoonCalculator.phaseName(0.10))
        assertEquals("Cuarto creciente", MoonCalculator.phaseName(0.25))
        assertEquals("Creciente convexa", MoonCalculator.phaseName(0.35))
        assertEquals("Luna llena", MoonCalculator.phaseName(0.50))
        assertEquals("Menguante convexa", MoonCalculator.phaseName(0.60))
        assertEquals("Cuarto menguante", MoonCalculator.phaseName(0.75))
        assertEquals("Menguante cóncava", MoonCalculator.phaseName(0.85))
    }
}
