package com.nubo.nubo.domain.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * La intensidad tiene que llegar al icono.
 *
 * Es el fallo que había: el texto distinguía "Lluvia débil" de "Lluvia fuerte"
 * pero las dos se pintaban igual, y en las filas de días y en el carrusel de
 * horas el icono es lo único que hay.
 */
class WeatherCodeIntensityTest {

    private fun iconOf(code: String) = WeatherCode.fromCode(code).icon

    @Test
    fun `los tres escalones de lluvia se pintan distinto`() {
        val light = iconOf("61")
        val moderate = iconOf("63")
        val heavy = iconOf("65")

        assertEquals(3, setOf(light, moderate, heavy).size)
    }

    @Test
    fun `los chubascos siguen la misma escala que la lluvia`() {
        assertEquals(iconOf("61"), iconOf("80"))
        assertEquals(iconOf("63"), iconOf("81"))
        assertEquals(iconOf("65"), iconOf("82"))
    }

    @Test
    fun `los tres escalones de nieve se pintan distinto`() {
        assertEquals(3, setOf(iconOf("71"), iconOf("73"), iconOf("75")).size)
    }

    @Test
    fun `la lluvia debil no se confunde con la llovizna`() {
        assertNotEquals(iconOf("51"), iconOf("61"))
    }

    @Test
    fun `la noche no cambia la intensidad`() {
        listOf("61", "63", "65", "71", "73", "75", "80", "82").forEach { code ->
            assertEquals(iconOf(code), iconOf("${code}n"))
        }
    }
}
