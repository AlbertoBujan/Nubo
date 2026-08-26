package com.nubo.nubo.ui.components

import com.nubo.nubo.domain.model.DailyForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Desplegable de los días.
 *
 * Solo puede haber uno abierto: abrir otro cierra el que estuviera. Se
 * garantiza guardando **un solo día**, no cerrando los demás en cadena, que
 * es donde bastaría olvidarse de uno.
 */
class DailyExpansionTest {

    private val lunes = LocalDate.of(2026, 8, 24)
    private val martes = LocalDate.of(2026, 8, 25)

    @Test
    fun `tocar un dia cerrado lo abre`() {
        assertEquals(lunes, toggledExpansion(current = null, tapped = lunes))
    }

    @Test
    fun `tocar el que ya esta abierto lo cierra`() {
        assertNull(toggledExpansion(current = lunes, tapped = lunes))
    }

    @Test
    fun `abrir otro deja abierto solo el nuevo`() {
        assertEquals(martes, toggledExpansion(current = lunes, tapped = martes))
    }
}

/** Formato del rango de sensación térmica del día. */
class ApparentRangeTest {

    private fun day(min: Int?, max: Int?) = DailyForecast(
        date = LocalDate.of(2026, 8, 25),
        tempMax = 20,
        tempMin = 14,
        skyStateCode = "3",
        precipitationProbability = 0,
        apparentMin = min,
        apparentMax = max,
    )

    @Test
    fun `con los dos extremos se escribe el rango`() {
        assertEquals("14° a 20°", apparentRange(day(min = 14, max = 20)))
    }

    @Test
    fun `con uno solo se escribe ese`() {
        assertEquals("20°", apparentRange(day(min = null, max = 20)))
        assertEquals("14°", apparentRange(day(min = 14, max = null)))
    }

    @Test
    fun `sin datos no se inventa nada`() {
        assertNull(apparentRange(day(min = null, max = null)))
    }
}
