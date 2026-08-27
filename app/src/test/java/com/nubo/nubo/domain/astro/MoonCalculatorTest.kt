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
            assertTrue("ciclo ${moon.cycle} en $date", moon.cycle in 0.0..1.0)
            assertTrue("iluminación ${moon.illumination} en $date", moon.illumination in 0.0..1.0)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `el ciclo lunar completo dura un mes sinodico`() {
        // Se cuentan las lunas nuevas de un año: la fase cruza de ~1 a ~0.
        var previous = MoonCalculator
            .calculate(LocalDateTime.of(2025, 1, 1, 12, 0), madridLat, madridLng, utc).cycle
        var newMoons = 0

        var date = LocalDateTime.of(2025, 1, 2, 12, 0)
        repeat(364) {
            val phase = MoonCalculator.calculate(date, madridLat, madridLng, utc).cycle
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
            val expected = (1 - kotlin.math.cos(2 * Math.PI * moon.cycle)) / 2
            assertTrue(
                "ciclo ${moon.cycle} vs iluminación ${moon.illumination} en $date",
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
    fun `de madrugada se dibuja el trayecto que ya esta en curso`() {
        // Tokio, Dakota del Norte, a las 03:11: la luna lleva arriba desde la
        // tarde anterior y el orto de hoy es a las 20:04. El arco tiene que ser
        // el paso en curso, o la tarjeta enseña un trayecto que no ha empezado
        // y se queda sin punto con la luna en el cielo.
        val zone = ZoneId.of("America/Chicago")
        val lat = 47.92472
        val lon = -98.81622
        val now = LocalDateTime.of(2026, 8, 27, 3, 11)

        val data = MoonCalculator.calculate(now, lat, lon, zone)

        assertTrue(
            "orto ${data.moonrise} debería ser anterior a ahora",
            data.moonrise!!.isBefore(now),
        )
        assertTrue(
            "ocaso ${data.moonset} debería ser posterior a ahora",
            data.moonset!!.isAfter(now),
        )
    }

    @Test
    fun `con la luna bajo el horizonte se dibuja el trayecto de hoy`() {
        // A Coruña a media mañana: la luna se puso a las 08:06 y todavía no ha
        // vuelto a salir, así que lo que toca enseñar es el paso de esta noche.
        val zone = ZoneId.of("Europe/Madrid")
        val now = LocalDateTime.of(2026, 8, 27, 10, 0)

        val data = MoonCalculator.calculate(now, 43.3713, -8.396, zone)

        assertTrue("orto ${data.moonrise}", data.moonrise!!.isAfter(now))
    }
}
