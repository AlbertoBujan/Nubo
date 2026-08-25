package com.nubo.nubo.ui.weather

import com.nubo.nubo.domain.astro.SunTimes
import com.nubo.nubo.domain.model.DailyForecast
import com.nubo.nubo.domain.model.HourlyForecast
import com.nubo.nubo.domain.weather.SunPhase
import com.nubo.nubo.ui.components.WeatherEffect
import com.nubo.nubo.ui.components.progressBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SunPhaseTest {

    private val sunTimes = SunTimes(
        sunrise = LocalDateTime.of(2026, 8, 24, 7, 0),
        sunset = LocalDateTime.of(2026, 8, 24, 21, 0),
    )

    private fun phaseAt(hour: Int, minute: Int = 0) = WeatherViewModel.phaseFor(
        sunTimes,
        LocalDateTime.of(2026, 8, 24, hour, minute),
    )

    @Test
    fun `el mediodia es de dia y la madrugada de noche`() {
        assertEquals(SunPhase.DAY, phaseAt(12))
        assertEquals(SunPhase.NIGHT, phaseAt(3))
        assertEquals(SunPhase.NIGHT, phaseAt(23))
    }

    @Test
    fun `hay media hora de transicion a cada lado del amanecer`() {
        assertEquals(SunPhase.NIGHT, phaseAt(6, 29))
        assertEquals(SunPhase.SUNRISE, phaseAt(6, 31))
        assertEquals(SunPhase.SUNRISE, phaseAt(7, 0))
        assertEquals(SunPhase.SUNRISE, phaseAt(7, 29))
        assertEquals(SunPhase.DAY, phaseAt(7, 31))
    }

    @Test
    fun `y otra media hora alrededor del ocaso`() {
        assertEquals(SunPhase.DAY, phaseAt(20, 29))
        assertEquals(SunPhase.SUNSET, phaseAt(20, 31))
        assertEquals(SunPhase.SUNSET, phaseAt(21, 29))
        assertEquals(SunPhase.NIGHT, phaseAt(21, 31))
    }

    @Test
    fun `sin datos solares se asume de dia`() {
        assertEquals(SunPhase.DAY, WeatherViewModel.phaseFor(null))
    }
}

class WeatherEffectTest {

    @Test
    fun `el cielo despejado o nublado no dibuja nada`() {
        assertEquals(WeatherEffect.NONE, WeatherEffect.fromSkyCode("0"))
        assertEquals(WeatherEffect.NONE, WeatherEffect.fromSkyCode("3"))
        assertEquals(WeatherEffect.NONE, WeatherEffect.fromSkyCode("45"))
    }

    @Test
    fun `la nieve cae como nieve, no como lluvia`() {
        assertEquals(WeatherEffect.SNOW, WeatherEffect.fromSkyCode("73"))
        assertEquals(WeatherEffect.SNOW, WeatherEffect.fromSkyCode("86"))
        assertEquals(WeatherEffect.SNOW, WeatherEffect.fromSkyCode("77"))

        // Un copo es un punto que se balancea: ni deja estela ni cae al ritmo
        // de una gota, y esas dos cosas son las que lo diferencian.
        assertTrue(WeatherEffect.SNOW.isSnow)
        assertEquals(0f, WeatherEffect.SNOW.dropLength, 0f)
        assertTrue(WeatherEffect.SNOW.speed < WeatherEffect.DRIZZLE.speed)
        assertTrue(!WeatherEffect.SNOW.hasFlashes)
    }

    @Test
    fun `distingue llovizna, lluvia y lluvia fuerte`() {
        assertEquals(WeatherEffect.DRIZZLE, WeatherEffect.fromSkyCode("51"))
        assertEquals(WeatherEffect.RAIN, WeatherEffect.fromSkyCode("61"))
        assertEquals(WeatherEffect.RAIN, WeatherEffect.fromSkyCode("80"))
        assertEquals(WeatherEffect.HEAVY_RAIN, WeatherEffect.fromSkyCode("65"))
        assertEquals(WeatherEffect.HEAVY_RAIN, WeatherEffect.fromSkyCode("82"))
    }

    @Test
    fun `solo la tormenta trae destellos`() {
        assertEquals(WeatherEffect.THUNDER, WeatherEffect.fromSkyCode("95"))
        assertTrue(WeatherEffect.THUNDER.hasFlashes)
        assertTrue(!WeatherEffect.HEAVY_RAIN.hasFlashes)
    }

    @Test
    fun `ignora el sufijo nocturno y los codigos invalidos`() {
        assertEquals(WeatherEffect.RAIN, WeatherEffect.fromSkyCode("61n"))
        assertEquals(WeatherEffect.NONE, WeatherEffect.fromSkyCode(null))
        assertEquals(WeatherEffect.NONE, WeatherEffect.fromSkyCode("abc"))
    }

    @Test
    fun `a mas intensidad, mas gotas`() {
        assertTrue(WeatherEffect.DRIZZLE.dropCount < WeatherEffect.RAIN.dropCount)
        assertTrue(WeatherEffect.RAIN.dropCount < WeatherEffect.HEAVY_RAIN.dropCount)
        assertEquals(0, WeatherEffect.NONE.dropCount)
    }
}

class CityWeatherTest {

    private fun hour(offsetHours: Long, temp: Int, code: String = "0") = HourlyForecast(
        dateTime = LocalDateTime.now().plusHours(offsetHours),
        temperature = temp,
        skyStateCode = code,
        skyDescription = "",
        precipitationProbability = null,
        humidity = null,
        windSpeed = null,
        windDirection = null,
        windDirectionDegrees = null,
        dewPoint = null,
    )

    @Test
    fun `la temperatura actual sale de la hora mas proxima`() {
        val city = CityWeather(
            locationId = "28079",
            name = "Madrid",
            hourly = listOf(hour(-3, 10), hour(0, 22), hour(5, 30)),
        )
        assertEquals(22, city.currentTemperature)
    }

    @Test
    fun `sin datos horarios no hay temperatura`() {
        val city = CityWeather("28079", "Madrid")
        assertNull(city.currentTemperature)
        assertEquals("", city.skyCode)
    }

    @Test
    fun `el rango del dia usa hoy y si no el primer dia disponible`() {
        val today = LocalDate.now()
        val city = CityWeather(
            locationId = "28079",
            name = "Madrid",
            daily = listOf(
                DailyForecast(today.plusDays(1), 30, 20, "0", "", null),
                DailyForecast(today, 25, 15, "0", "", null),
            ),
        )
        assertEquals(25 to 15, city.todayRange)

        val sinHoy = city.copy(
            daily = listOf(DailyForecast(today.plusDays(2), 12, 4, "0", "", null)),
        )
        assertEquals(12 to 4, sinHoy.todayRange)
    }

    @Test
    fun `el texto de frescura se adapta a la antiguedad`() {
        val base = CityWeather("28079", "Madrid")
        assertEquals("", base.lastRefreshText)
        assertEquals(
            "Actualizado",
            base.copy(lastUpdated = LocalDateTime.now()).lastRefreshText,
        )
        assertEquals(
            "Hace 5 min",
            base.copy(lastUpdated = LocalDateTime.now().minusMinutes(5)).lastRefreshText,
        )
        assertEquals(
            "Hace 3 h",
            base.copy(lastUpdated = LocalDateTime.now().minusHours(3)).lastRefreshText,
        )
        assertEquals(
            "Hace 2 d",
            base.copy(lastUpdated = LocalDateTime.now().minusDays(2)).lastRefreshText,
        )
    }

    @Test
    fun `hasData exige horas y dias`() {
        val today = LocalDate.now()
        assertTrue(!CityWeather("1", "x", hourly = listOf(hour(0, 20))).hasData)
        assertTrue(
            CityWeather(
                "1",
                "x",
                hourly = listOf(hour(0, 20)),
                daily = listOf(DailyForecast(today, 20, 10, "0", "", null)),
            ).hasData,
        )
    }
}

class ArcProgressTest {

    @Test
    fun `el progreso del arco va de cero a uno`() {
        val start = LocalDateTime.of(2026, 8, 24, 8, 0)
        val end = LocalDateTime.of(2026, 8, 24, 20, 0)

        assertEquals(0f, progressBetween(start, end, start), 0.001f)
        assertEquals(0.5f, progressBetween(start, end, LocalDateTime.of(2026, 8, 24, 14, 0)), 0.001f)
        assertEquals(1f, progressBetween(start, end, end), 0.001f)
    }

    @Test
    fun `fuera del intervalo queda acotado`() {
        val start = LocalDateTime.of(2026, 8, 24, 8, 0)
        val end = LocalDateTime.of(2026, 8, 24, 20, 0)

        assertEquals(0f, progressBetween(start, end, start.minusHours(3)), 0.001f)
        assertEquals(1f, progressBetween(start, end, end.plusHours(3)), 0.001f)
    }

    @Test
    fun `un intervalo invertido no rompe el calculo`() {
        val start = LocalDateTime.of(2026, 8, 24, 20, 0)
        val end = LocalDateTime.of(2026, 8, 24, 8, 0)
        assertEquals(0f, progressBetween(start, end), 0.001f)
    }
}
