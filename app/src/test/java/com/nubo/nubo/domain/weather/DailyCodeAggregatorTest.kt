package com.nubo.nubo.domain.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Portado de `test/models/daily_forecast_test.dart` (grupo "código dominante
 * del día") del proyecto Flutter. Son la especificación del algoritmo: si estos
 * pasan, el icono de "Próximos días" se comporta igual que en la app Flutter.
 */
class DailyCodeAggregatorTest {

    private fun hours(vararg codes: Int, isDay: Boolean = true) =
        codes.map { HourSample(it, isDay) }

    private fun repeat(code: Int, times: Int, isDay: Boolean = true) =
        List(times) { HourSample(code, isDay) }

    @Test
    fun `una hora de nubes no convierte en nuboso un dia despejado`() {
        // Open-Meteo reportaría "2" (el fenómeno más significativo del día),
        // pero 23 de las 24 horas están despejadas.
        val result = DailyCodeAggregator.dominantCode(repeat(0, 23) + hours(2))
        assertEquals(0, result)
    }

    @Test
    fun `dos horas de lluvia no convierten en lluvioso un dia despejado`() {
        val result = DailyCodeAggregator.dominantCode(repeat(0, 22) + repeat(61, 2))
        assertEquals(0, result)
    }

    @Test
    fun `la lluvia sostenida si representa al dia`() {
        val result = DailyCodeAggregator.dominantCode(repeat(0, 18) + repeat(61, 6))
        assertEquals(61, result)
    }

    @Test
    fun `una tormenta breve sigue avisando pese al umbral menor`() {
        // Solo 2 horas, por debajo del umbral general de 3h, pero las tormentas
        // son relevantes aunque duren poco.
        val result = DailyCodeAggregator.dominantCode(repeat(0, 22) + repeat(95, 2))
        assertEquals(95, result)
    }

    @Test
    fun `gana el fenomeno mas severo entre los que superan su umbral`() {
        // Aunque la llovizna dura el doble, la lluvia supera su umbral y es
        // el fenómeno más severo del día.
        val result = DailyCodeAggregator.dominantCode(
            repeat(51, 12) + repeat(61, 6) + repeat(0, 6),
        )
        assertEquals(61, result)
    }

    @Test
    fun `conserva la intensidad concreta dentro de la familia ganadora`() {
        val result = DailyCodeAggregator.dominantCode(
            repeat(0, 16) + repeat(65, 5) + repeat(61, 3),
        )
        assertEquals(65, result)
    }

    @Test
    fun `la noche nublada no gana a un dia de luz despejado`() {
        val result = DailyCodeAggregator.dominantCode(
            repeat(3, 12, isDay = false) + repeat(0, 12, isDay = true),
        )
        assertEquals(0, result)
    }

    @Test
    fun `la lluvia nocturna sostenida si representa al dia`() {
        val result = DailyCodeAggregator.dominantCode(
            repeat(0, 20, isDay = true) + repeat(61, 4, isDay = false),
        )
        assertEquals(61, result)
    }

    @Test
    fun `un dia compuesto solo de fenomenos breves elige el mas duradero`() {
        val result = DailyCodeAggregator.dominantCode(hours(45, 45, 51, 61))
        assertEquals(45, result)
    }

    @Test
    fun `sin horas devuelve null para que se use el codigo de la API`() {
        assertNull(DailyCodeAggregator.dominantCode(emptyList()))
    }
}
