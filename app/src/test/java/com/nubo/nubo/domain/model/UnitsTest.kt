package com.nubo.nubo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Conversión de unidades, validada contra equivalencias conocidas y no contra
 * lo que devuelva la propia fórmula.
 */
class UnitsTest {

    @Test
    fun `los puntos fijos del agua`() {
        assertEquals(32, TemperatureUnit.FAHRENHEIT.fromCelsius(0))
        assertEquals(212, TemperatureUnit.FAHRENHEIT.fromCelsius(100))
    }

    @Test
    fun `cuarenta bajo cero es el mismo numero en las dos escalas`() {
        assertEquals(-40, TemperatureUnit.FAHRENHEIT.fromCelsius(-40))
    }

    @Test
    fun `una temperatura de un dia cualquiera`() {
        assertEquals(68, TemperatureUnit.FAHRENHEIT.fromCelsius(20))
        assertEquals(5, TemperatureUnit.FAHRENHEIT.fromCelsius(-15))
    }

    @Test
    fun `en celsius no se toca nada`() {
        listOf(-20, 0, 17, 45).forEach {
            assertEquals(it, TemperatureUnit.CELSIUS.fromCelsius(it))
        }
    }

    @Test
    fun `las millas por hora`() {
        // 100 km/h son 62,1 mph; 60 mph son 96,6 km/h.
        assertEquals(62, SpeedUnit.MPH.fromKmh(100))
        assertEquals(60, SpeedUnit.MPH.fromKmh(97))
        assertEquals(0, SpeedUnit.MPH.fromKmh(0))
    }

    @Test
    fun `en kilometros por hora no se toca nada`() {
        listOf(0, 12, 130).forEach { assertEquals(it, SpeedUnit.KMH.fromKmh(it)) }
    }

    @Test
    fun `las unidades por defecto son las metricas`() {
        val units = Units()

        assertEquals(20, units.temperature(20))
        assertEquals(30, units.speed(30))
    }
}
