package com.nubo.nubo.domain.astro

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * El ocaso lunar tiene que caer **después** del orto.
 *
 * Buscándolo dentro del día natural se cogía el de la madrugada, que cierra la
 * noche anterior: una luna que sale a las 16:35 y se pone a la 01:53 del día
 * siguiente devolvía el intervalo al revés, y entonces ni se pintaba el
 * trayecto recorrido ni aparecía el punto de posición.
 */
class MoonOrderTest {

    private val sitios = listOf(
        Triple("Curtis", 43.14, -8.04) to ZoneId.of("Europe/Madrid"),
        Triple("Tokio", 35.69, 139.69) to ZoneId.of("Asia/Tokyo"),
        Triple("Nairobi", -1.29, 36.82) to ZoneId.of("Africa/Nairobi"),
    )

    @Test
    fun `el ocaso siempre va despues del orto, cualquier dia y sitio`() {
        // Un mes entero cubre el ciclo lunar completo, que es donde estaba el
        // caso malo: los días en que la luna sale por la tarde.
        for ((sitio, zone) in sitios) {
            val (nombre, lat, lon) = sitio
            for (dia in 1..31) {
                val fecha = LocalDateTime.of(2026, 8, dia, 12, 0)
                val data = MoonCalculator.calculate(fecha, lat, lon, zone)

                val rise = data.moonrise
                val set = data.moonset
                if (rise == null || set == null) continue

                assertTrue(
                    "$nombre el $dia: orto $rise, ocaso $set",
                    set.isAfter(rise),
                )
            }
        }
    }

    @Test
    fun `la luna no pasa mas de dieciseis horas sobre el horizonte`() {
        // Si el ocaso encontrado fuese el del ciclo siguiente, el intervalo se
        // acercaría a las 24 h; esto lo detecta.
        //
        // La cota sale de la geometría: a 43º de latitud y con la luna en su
        // declinación máxima (+28,6º), cos(H) = -tan(43,1º)·tan(28,6º), de
        // donde salen unas 16,1 h sobre el horizonte. Diecisiete deja margen
        // sin dejar pasar un ciclo entero.
        for ((sitio, zone) in sitios) {
            val (nombre, lat, lon) = sitio
            for (dia in 1..31) {
                val data = MoonCalculator.calculate(
                    LocalDateTime.of(2026, 8, dia, 12, 0), lat, lon, zone,
                )
                val rise = data.moonrise ?: continue
                val set = data.moonset ?: continue

                val horas = java.time.Duration.between(rise, set).toMinutes() / 60.0
                assertTrue("$nombre el $dia: $horas h sobre el horizonte", horas in 0.0..17.0)
            }
        }
    }
}
