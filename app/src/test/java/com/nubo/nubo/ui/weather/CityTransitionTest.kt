package com.nubo.nubo.ui.weather

import com.nubo.nubo.domain.astro.SunTimes
import com.nubo.nubo.domain.weather.SunPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Aritmética del arrastre entre ciudades.
 *
 * El fondo se mezclaba bien pero las animaciones cambiaban de golpe al cruzar
 * la mitad del gesto, sin relación con el dedo. La causa era usar
 * `currentPage` y `currentPageOffsetFraction` por separado: los dos saltan a
 * la vez a mitad de gesto, y solo encadenados dan un valor continuo.
 */
class PageSpanTest {

    @Test
    fun `la posicion entera cae en una sola pagina`() {
        val span = pageSpanAt(2f, lastIndex = 4)
        assertEquals(2, span.lower)
        assertEquals(2, span.upper)
        assertEquals(0f, span.blend, 0f)
    }

    @Test
    fun `a media pagina pesan las dos por igual`() {
        val span = pageSpanAt(1.5f, lastIndex = 4)
        assertEquals(1, span.lower)
        assertEquals(2, span.upper)
        assertEquals(0.5f, span.blend, 0.001f)
    }

    @Test
    fun `el peso avanza sin saltos a lo largo del gesto`() {
        // Recorre el arrastre completo de la página 0 a la 1 y comprueba que
        // el peso nunca retrocede ni pega un tirón.
        var previous = 0f
        (0..100).forEach { step ->
            val span = pageSpanAt(step / 100f, lastIndex = 3)
            if (span.lower == 0 && span.upper == 1) {
                assertTrue("retrocede en $step", span.blend >= previous - 0.001f)
                assertTrue("salto en $step", span.blend - previous < 0.05f)
                previous = span.blend
            }
        }
    }

    @Test
    fun `no se sale de la lista por rebote en los extremos`() {
        // El pager admite desplazamiento fuera de rango al llegar al tope.
        val start = pageSpanAt(-0.3f, lastIndex = 2)
        assertEquals(0, start.lower)
        assertEquals(0, start.upper)

        val end = pageSpanAt(2.4f, lastIndex = 2)
        assertEquals(2, end.lower)
        assertEquals(2, end.upper)
    }

    @Test
    fun `con una sola ciudad todo apunta a la misma pagina`() {
        val span = pageSpanAt(0.5f, lastIndex = 0)
        assertEquals(0, span.lower)
        assertEquals(0, span.upper)
    }
}

class SceneAlphaTest {

    private fun spanAt(blend: Float) = PageSpan(lower = 0, upper = 1, blend = blend)

    @Test
    fun `el cambio de animacion ocurre a opacidad cero`() {
        // Justo en la mitad, que es donde la animación se releva.
        assertEquals(0f, sceneAlpha(spanAt(0.5f), sameOnBothSides = false), 0.001f)
    }

    @Test
    fun `en los extremos la animacion esta entera`() {
        assertEquals(1f, sceneAlpha(spanAt(0f), sameOnBothSides = false), 0.001f)
        assertEquals(1f, sceneAlpha(spanAt(1f), sameOnBothSides = false), 0.001f)
    }

    @Test
    fun `si las dos ciudades comparten animacion no se apaga nada`() {
        // Desvanecer la lluvia para volver a traer la misma lluvia sería peor
        // que no hacer nada.
        (0..10).forEach { step ->
            assertEquals(
                1f,
                sceneAlpha(spanAt(step / 10f), sameOnBothSides = true),
                0f,
            )
        }
    }

    @Test
    fun `la opacidad nunca se sale de rango y es continua`() {
        var previous = sceneAlpha(spanAt(0f), sameOnBothSides = false)
        (1..100).forEach { step ->
            val alpha = sceneAlpha(spanAt(step / 100f), sameOnBothSides = false)
            assertTrue("fuera de rango en $step: $alpha", alpha in 0f..1f)
            assertTrue("salto en $step", kotlin.math.abs(alpha - previous) < 0.05f)
            previous = alpha
        }
    }

    @Test
    fun `quieto en una pagina no hay desvanecido`() {
        val settled = PageSpan(lower = 1, upper = 1, blend = 0f)
        assertEquals(1f, sceneAlpha(settled, sameOnBothSides = false), 0f)
    }
}

/**
 * Fase solar por ciudad.
 *
 * Había una sola fase para toda la app, la de la ciudad activa, y comparada
 * además contra el reloj del teléfono. Deslizando de A Coruña a Toronto salía
 * el cielo azul de mediodía con las estrellas de Toronto encima: el gradiente
 * venía del estado global y las estrellas del código horario del sitio.
 */
class CitySunPhaseTest {

    /**
     * Zonas repartidas por el mundo. Al menos una está a varias horas de
     * cualquier reloj donde se ejecuten los tests, que es lo que hace que
     * estos tests detecten el fallo en vez de pasar por casualidad.
     */
    private val zones = listOf(
        "America/Toronto",
        "Asia/Tokyo",
        "Europe/Madrid",
        "Pacific/Auckland",
        "Pacific/Honolulu",
    )

    private fun cityAt(zone: String, sunset: Long, sunrise: Long): CityWeather {
        val nowThere = LocalDateTime.now(ZoneId.of(zone))
        return CityWeather(
            locationId = "test",
            name = "Prueba",
            timeZone = zone,
            sunTimes = SunTimes(
                sunrise = nowThere.plusHours(sunrise),
                sunset = nowThere.plusHours(sunset),
            ),
        )
    }

    @Test
    fun `cada ciudad usa su propio reloj, no el del telefono`() {
        // El sol se puso hace tres horas *allí* y sale dentro de seis: es de
        // noche en ese sitio, mida lo que mida el reloj del dispositivo.
        zones.forEach { zone ->
            assertEquals(
                "en $zone",
                SunPhase.NIGHT,
                cityAt(zone, sunset = -3, sunrise = 6).sunPhase,
            )
        }
    }

    @Test
    fun `y lo mismo en pleno dia`() {
        zones.forEach { zone ->
            assertEquals(
                "en $zone",
                SunPhase.DAY,
                cityAt(zone, sunset = 5, sunrise = -5).sunPhase,
            )
        }
    }

    @Test
    fun `sin datos solares se asume de dia`() {
        val city = CityWeather(locationId = "x", name = "X", timeZone = "America/Toronto")
        assertEquals(SunPhase.DAY, city.sunPhase)
    }
}

/**
 * Resumen diario de la calidad del aire.
 *
 * Su API no tiene bloque diario, solo la serie horaria, así que la agregación
 * se hace aquí. Y el modelo CAMS se agota hacia el quinto día: los días sin
 * dato tienen que **faltar** del mapa, no aparecer con un cero, que se leería
 * como aire inmejorable.
 */
class AirQualityByDayTest {

    private fun city(vararg samples: Pair<String, Int>) = CityWeather(
        locationId = "x",
        name = "X",
        timeZone = "Europe/Madrid",
        airQuality = samples.map { (time, aqi) ->
            com.nubo.nubo.domain.model.AirQualityForecast(
                dateTime = LocalDateTime.parse(time),
                europeanAqi = aqi,
            )
        },
    )

    @Test
    fun `de cada dia se guarda el peor momento, no la media`() {
        // Un día con una punta de contaminación es un día con contaminación.
        val byDay = city(
            "2026-08-25T06:00" to 12,
            "2026-08-25T14:00" to 58,
            "2026-08-25T22:00" to 20,
        ).airQualityByDay

        assertEquals(58, byDay[LocalDate.of(2026, 8, 25)])
    }

    @Test
    fun `los dias se separan bien`() {
        val byDay = city(
            "2026-08-25T23:00" to 30,
            "2026-08-26T00:00" to 70,
        ).airQualityByDay

        assertEquals(30, byDay[LocalDate.of(2026, 8, 25)])
        assertEquals(70, byDay[LocalDate.of(2026, 8, 26)])
    }

    @Test
    fun `un dia sin muestras no aparece, en vez de salir a cero`() {
        val byDay = city("2026-08-25T06:00" to 12).airQualityByDay

        assertNull(byDay[LocalDate.of(2026, 8, 30)])
        assertEquals(1, byDay.size)
    }

    @Test
    fun `sin serie no hay resumen`() {
        assertTrue(city().airQualityByDay.isEmpty())
    }
}
