package com.nubo.nubo.domain.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirQualityBandTest {

    @Test
    fun `los cortes oficiales caen donde deben`() {
        assertEquals(AirQualityBand.GOOD, AirQualityBand.forAqi(0))
        assertEquals(AirQualityBand.GOOD, AirQualityBand.forAqi(19))
        assertEquals(AirQualityBand.FAIR, AirQualityBand.forAqi(21))
        assertEquals(AirQualityBand.MODERATE, AirQualityBand.forAqi(41))
        assertEquals(AirQualityBand.POOR, AirQualityBand.forAqi(61))
        assertEquals(AirQualityBand.VERY_POOR, AirQualityBand.forAqi(81))
    }

    @Test
    fun `un cien clavado todavia no es extrema`() {
        // La banda extrema está definida como "> 100", no ">= 100".
        assertEquals(AirQualityBand.VERY_POOR, AirQualityBand.forAqi(100))
        assertEquals(AirQualityBand.EXTREME, AirQualityBand.forAqi(101))
    }

    @Test
    fun `el limite pertenece a la banda de abajo`() {
        assertEquals(AirQualityBand.GOOD, AirQualityBand.forAqi(20))
        assertEquals(AirQualityBand.FAIR, AirQualityBand.forAqi(40))
        assertEquals(AirQualityBand.MODERATE, AirQualityBand.forAqi(60))
        assertEquals(AirQualityBand.POOR, AirQualityBand.forAqi(80))
    }

    @Test
    fun `un indice desbocado no se sale de la escala`() {
        assertEquals(AirQualityBand.EXTREME, AirQualityBand.forAqi(Int.MAX_VALUE))
    }

    @Test
    fun `la advertencia empieza en regular`() {
        assertFalse(AirQualityBand.GOOD.isUnhealthy)
        assertFalse(AirQualityBand.FAIR.isUnhealthy)
        assertTrue(AirQualityBand.MODERATE.isUnhealthy)
        assertTrue(AirQualityBand.EXTREME.isUnhealthy)
    }
}

class UvBandTest {

    @Test
    fun `los tramos de la OMS`() {
        assertEquals(UvBand.LOW, UvBand.forIndex(0.0))
        assertEquals(UvBand.LOW, UvBand.forIndex(2.0))
        assertEquals(UvBand.MODERATE, UvBand.forIndex(3.0))
        assertEquals(UvBand.MODERATE, UvBand.forIndex(5.0))
        assertEquals(UvBand.HIGH, UvBand.forIndex(6.0))
        assertEquals(UvBand.VERY_HIGH, UvBand.forIndex(8.0))
        assertEquals(UvBand.EXTREME, UvBand.forIndex(11.0))
    }

    @Test
    fun `redondea igual que el numero que se enseña`() {
        // La casilla pinta `roundToInt`: si aquí se truncara, un 2,6 saldría
        // como "3" con la etiqueta "Bajo".
        assertEquals(UvBand.MODERATE, UvBand.forIndex(2.6))
        assertEquals(UvBand.LOW, UvBand.forIndex(2.4))
        assertEquals(UvBand.EXTREME, UvBand.forIndex(10.5))
    }

    @Test
    fun `un indice negativo se trata como cero`() {
        assertEquals(UvBand.LOW, UvBand.forIndex(-3.0))
    }

    @Test
    fun `la proteccion se recomienda desde moderado`() {
        assertFalse(UvBand.LOW.needsProtection)
        assertTrue(UvBand.MODERATE.needsProtection)
    }
}
