package com.nubo.nubo.domain.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Portado de `test/enums/weather_enums_test.dart` del proyecto Flutter. */
class WeatherCodeGroupTest {

    @Test
    fun `clasifica cada familia por su codigo WMO`() {
        assertEquals(WeatherCodeGroup.CLEAR, WeatherCodeGroup.fromCode("0"))
        assertEquals(WeatherCodeGroup.PARTLY_CLOUDY, WeatherCodeGroup.fromCode("1"))
        assertEquals(WeatherCodeGroup.PARTLY_CLOUDY, WeatherCodeGroup.fromCode("2"))
        assertEquals(WeatherCodeGroup.CLOUDY, WeatherCodeGroup.fromCode("3"))
        assertEquals(WeatherCodeGroup.FOG, WeatherCodeGroup.fromCode("45"))
        assertEquals(WeatherCodeGroup.FOG, WeatherCodeGroup.fromCode("48"))
        assertEquals(WeatherCodeGroup.DRIZZLE, WeatherCodeGroup.fromCode("53"))
        assertEquals(WeatherCodeGroup.RAIN, WeatherCodeGroup.fromCode("63"))
        assertEquals(WeatherCodeGroup.SNOW, WeatherCodeGroup.fromCode("73"))
        assertEquals(WeatherCodeGroup.SNOW, WeatherCodeGroup.fromCode("77"))
        assertEquals(WeatherCodeGroup.RAIN, WeatherCodeGroup.fromCode("81"))
        assertEquals(WeatherCodeGroup.SNOW, WeatherCodeGroup.fromCode("86"))
        assertEquals(WeatherCodeGroup.THUNDER, WeatherCodeGroup.fromCode("95"))
        assertEquals(WeatherCodeGroup.THUNDER, WeatherCodeGroup.fromCode("99"))
    }

    @Test
    fun `ignora el sufijo nocturno`() {
        assertEquals(WeatherCodeGroup.RAIN, WeatherCodeGroup.fromCode("61n"))
        assertEquals(WeatherCodeGroup.CLEAR, WeatherCodeGroup.fromCode("0n"))
    }

    @Test
    fun `codigo nulo o invalido cae en clear`() {
        assertEquals(WeatherCodeGroup.CLEAR, WeatherCodeGroup.fromCode(null))
        assertEquals(WeatherCodeGroup.CLEAR, WeatherCodeGroup.fromCode(""))
        assertEquals(WeatherCodeGroup.CLEAR, WeatherCodeGroup.fromCode("abc"))
        assertNull(WeatherCodeGroup.numericValue("abc"))
    }

    @Test
    fun `la severidad crece con la relevancia del fenomeno`() {
        assertTrue(WeatherCodeGroup.CLEAR.severity < WeatherCodeGroup.RAIN.severity)
        assertTrue(WeatherCodeGroup.RAIN.severity < WeatherCodeGroup.THUNDER.severity)
    }

    @Test
    fun `solo los fenomenos concretos son significativos`() {
        assertFalse(WeatherCodeGroup.CLEAR.isSignificant)
        assertFalse(WeatherCodeGroup.PARTLY_CLOUDY.isSignificant)
        assertFalse(WeatherCodeGroup.CLOUDY.isSignificant)
        assertTrue(WeatherCodeGroup.FOG.isSignificant)
        assertTrue(WeatherCodeGroup.THUNDER.isSignificant)
    }

    @Test
    fun `la tormenta exige menos horas que el resto`() {
        assertTrue(WeatherCodeGroup.THUNDER.minHours < WeatherCodeGroup.RAIN.minHours)
        assertEquals(0, WeatherCodeGroup.CLEAR.minHours)
    }

    @Test
    fun `hasRain y hasThunder identifican la precipitacion liquida`() {
        assertTrue(WeatherCodeGroup.DRIZZLE.hasRain)
        assertTrue(WeatherCodeGroup.RAIN.hasRain)
        assertTrue(WeatherCodeGroup.THUNDER.hasRain)
        assertFalse(WeatherCodeGroup.SNOW.hasRain)
        assertFalse(WeatherCodeGroup.CLOUDY.hasRain)

        assertTrue(WeatherCodeGroup.THUNDER.hasThunder)
        assertFalse(WeatherCodeGroup.RAIN.hasThunder)
    }
}
