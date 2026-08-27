package com.nubo.nubo.domain.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Se valida contra hechos físicos del ciclo lunar y contra un método
 * independiente —la lunación media desde una luna nueva conocida—, no contra
 * los valores que devuelva el propio buscador.
 */
class MoonPhasesTest {

    private val zone: ZoneId = ZoneId.of("Europe/Madrid")

    /** Duración del mes sinódico. */
    private val synodicDays = 29.530588853

    private fun horas(a: LocalDateTime, b: LocalDateTime) =
        abs(Duration.between(a, b).toMinutes()) / 60.0

    @Test
    fun `la proxima llena cae dentro del ciclo`() {
        val from = LocalDateTime.of(2026, 3, 10, 9, 0)
        val full = MoonPhases.nextFullMoon(from, zone)

        assertNotNull(full)
        val dias = Duration.between(from, full).toMinutes() / 1440.0
        assertTrue("cae en $full", dias > 0 && dias <= synodicDays)
    }

    @Test
    fun `en la llena la luna esta iluminada del todo`() {
        val full = MoonPhases.nextFullMoon(LocalDateTime.of(2026, 3, 10, 9, 0), zone)!!
        val fraccion = SunCalc.moonIllumination(full.atZone(zone).toInstant()).fraction

        assertTrue("iluminación $fraccion", fraccion > 0.995)
    }

    @Test
    fun `en la nueva no se ve nada`() {
        val nueva = MoonPhases.nextNewMoon(LocalDateTime.of(2026, 3, 10, 9, 0), zone)!!
        val fraccion = SunCalc.moonIllumination(nueva.atZone(zone).toInstant()).fraction

        assertTrue("iluminación $fraccion", fraccion < 0.005)
    }

    @Test
    fun `entre una nueva y la siguiente pasa un mes sinodico`() {
        val primera = MoonPhases.nextNewMoon(LocalDateTime.of(2026, 1, 5, 0, 0), zone)!!
        val segunda = MoonPhases.nextNewMoon(primera.plusDays(1), zone)!!

        val dias = Duration.between(primera, segunda).toMinutes() / 1440.0
        assertTrue("$dias días entre $primera y $segunda", abs(dias - synodicDays) < 0.6)
    }

    @Test
    fun `la llena cae a mitad de camino entre dos nuevas`() {
        val nueva = MoonPhases.nextNewMoon(LocalDateTime.of(2026, 6, 1, 0, 0), zone)!!
        val llena = MoonPhases.nextFullMoon(nueva.plusHours(1), zone)!!

        val dias = Duration.between(nueva, llena).toMinutes() / 1440.0
        assertTrue("$dias días", abs(dias - synodicDays / 2) < 0.8)
    }

    @Test
    fun `coincide con la lunacion media, que es un metodo aparte`() {
        // Luna nueva de referencia: 6 de enero de 2000 a las 18:14 UTC.
        val referencia = LocalDateTime.of(2000, 1, 6, 18, 14).atZone(ZoneId.of("UTC"))
        val desde = LocalDateTime.of(2026, 4, 2, 0, 0)
        val nueva = MoonPhases.nextNewMoon(desde, zone)!!

        val diasDesdeReferencia =
            Duration.between(referencia.toInstant(), nueva.atZone(zone).toInstant())
                .toMinutes() / 1440.0
        val lunaciones = diasDesdeReferencia / synodicDays
        val desviacion = abs(lunaciones - Math.round(lunaciones)) * synodicDays * 24

        // La lunación media no clava cada mes: las órbitas son elípticas y el
        // instante real oscila más de medio día alrededor de ella.
        assertTrue("se aparta $desviacion h de la lunación media", desviacion < 14)
    }

    @Test
    fun `la llena se llama llena el dia que cae, no el de antes`() {
        val llena = MoonPhases.nextFullMoon(LocalDateTime.of(2026, 3, 1, 0, 0), zone)!!
        val dia = llena.toLocalDate()

        assertEquals(MoonPhase.FULL, MoonPhases.phaseOn(dia, zone, 0.5))
        // La víspera está iluminada casi igual, pero no es el día.
        assertNotEquals(MoonPhase.FULL, MoonPhases.phaseOn(dia.minusDays(1), zone, 0.47))
        assertNotEquals(MoonPhase.FULL, MoonPhases.phaseOn(dia.plusDays(1), zone, 0.53))
    }

    @Test
    fun `la nueva tambien tiene su dia`() {
        val nueva = MoonPhases.nextNewMoon(LocalDateTime.of(2026, 3, 1, 0, 0), zone)!!

        assertEquals(MoonPhase.NEW, MoonPhases.phaseOn(nueva.toLocalDate(), zone, 0.0))
    }

    @Test
    fun `cada lunacion tiene sus cuatro dias senalados y ni uno mas`() {
        val nueva = MoonPhases.nextNewMoon(LocalDateTime.of(2026, 5, 1, 0, 0), zone)!!
        val dias = (0..28).map { nueva.toLocalDate().plusDays(it.toLong()) }

        val nombres = dias.map { dia ->
            val medio = dia.atTime(12, 0).atZone(zone).toInstant()
            MoonPhases.phaseOn(dia, zone, SunCalc.moonIllumination(medio).phase)
        }

        listOf(
            MoonPhase.NEW,
            MoonPhase.FIRST_QUARTER,
            MoonPhase.FULL,
            MoonPhase.LAST_QUARTER,
        ).forEach { fase ->
            assertEquals("$fase en $nombres", 1, nombres.count { it == fase })
        }
    }

    @Test
    fun `los dias sin fase senalada describen el tramo`() {
        val nueva = MoonPhases.nextNewMoon(LocalDateTime.of(2026, 5, 1, 0, 0), zone)!!
        val tresDiasDespues = nueva.toLocalDate().plusDays(3)
        val medio = tresDiasDespues.atTime(12, 0).atZone(zone).toInstant()

        assertEquals(
            MoonPhase.WAXING_CRESCENT,
            MoonPhases.phaseOn(tresDiasDespues, zone, SunCalc.moonIllumination(medio).phase),
        )
    }

    @Test
    fun `lo que acaba de pasar no cuenta como proximo`() {
        val llena = MoonPhases.nextFullMoon(LocalDateTime.of(2026, 3, 10, 9, 0), zone)!!
        val siguiente = MoonPhases.nextFullMoon(llena.plusHours(1), zone)!!

        assertTrue("$siguiente", horas(llena, siguiente) > 24 * 25)
    }
}
