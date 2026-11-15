package com.nubo.nubo.domain.weather

import com.nubo.nubo.domain.model.AlertLevel
import com.nubo.nubo.domain.model.AlertType
import com.nubo.nubo.domain.model.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Qué iconos le tocan a la fila de un día.
 *
 * Lo que se fija aquí es que la esquina no mienta ni se sobrecargue: un icono
 * por fenómeno, con el peor nivel que ese fenómeno alcanza en el día, y lo más
 * grave primero.
 */
class DayAlertsTest {

    private val dia = LocalDate.of(2026, 11, 15)

    private fun aviso(
        tipo: String,
        nivel: String,
        desde: LocalDateTime = dia.atTime(9, 0),
        hasta: LocalDateTime = dia.atTime(20, 0),
    ) = WeatherAlert(
        nivel = nivel,
        event = "Aviso de $tipo de nivel $nivel",
        headline = "",
        description = "",
        instruction = "",
        areaDescription = "",
        onset = desde,
        expires = hasta,
        probability = "",
    )

    @Test
    fun `un aviso da su fenomeno y su nivel`() {
        val resultado = dayAlerts(listOf(aviso("vientos", "amarillo")), dia)

        assertEquals(listOf(DayAlert(AlertType.WIND, AlertLevel.YELLOW)), resultado)
    }

    @Test
    fun `el mismo fenomeno dos veces en el dia sale una sola vez`() {
        // AEMET parte el viento en dos avisos cuando sube de nivel a mediodía.
        // Pintar los dos sería repetir el mismo icono en la misma esquina.
        val resultado = dayAlerts(
            listOf(
                aviso("vientos", "amarillo", dia.atTime(0, 0), dia.atTime(12, 0)),
                aviso("vientos", "naranja", dia.atTime(12, 0), dia.atTime(23, 59)),
            ),
            dia,
        )

        assertEquals(1, resultado.size)
        assertEquals(DayAlert(AlertType.WIND, AlertLevel.ORANGE), resultado.single())
    }

    @Test
    fun `manda el nivel mas grave aunque llegue primero el flojo`() {
        val resultado = dayAlerts(
            listOf(aviso("lluvias", "amarillo"), aviso("lluvias", "rojo")),
            dia,
        )

        assertEquals(AlertLevel.RED, resultado.single().level)
    }

    @Test
    fun `lo mas grave va primero`() {
        val resultado = dayAlerts(
            listOf(aviso("vientos", "amarillo"), aviso("lluvias", "naranja")),
            dia,
        )

        assertEquals(
            listOf(
                DayAlert(AlertType.RAIN, AlertLevel.ORANGE),
                DayAlert(AlertType.WIND, AlertLevel.YELLOW),
            ),
            resultado,
        )
    }

    @Test
    fun `con el mismo nivel el orden es siempre el mismo`() {
        // Si el desempate fuera el orden de llegada, dos avisos iguales de
        // nivel podrían intercambiarse de un refresco a otro sin que hubiera
        // cambiado nada en el cielo.
        val unOrden = dayAlerts(
            listOf(aviso("tormentas", "amarillo"), aviso("vientos", "amarillo")),
            dia,
        )
        val elOtro = dayAlerts(
            listOf(aviso("vientos", "amarillo"), aviso("tormentas", "amarillo")),
            dia,
        )

        assertEquals(unOrden, elOtro)
        assertEquals(AlertType.WIND, unOrden.first().type)
    }

    @Test
    fun `un dia puede juntar cuatro fenomenos`() {
        // El caso real de Galicia: lluvia, tormenta, costeros y viento.
        val resultado = dayAlerts(
            listOf(
                aviso("lluvias", "amarillo"),
                aviso("tormentas", "amarillo"),
                aviso("costeros", "naranja"),
                aviso("vientos", "amarillo"),
            ),
            dia,
        )

        assertEquals(4, resultado.size)
        assertEquals(AlertType.COASTAL, resultado.first().type)
        assertTrue(resultado.map { it.type }.distinct().size == 4)
    }

    @Test
    fun `los avisos de otro dia no cuentan`() {
        val resultado = dayAlerts(
            listOf(
                aviso("vientos", "amarillo", dia.plusDays(2).atTime(0, 0), dia.plusDays(2).atTime(23, 0)),
            ),
            dia,
        )

        assertTrue(resultado.isEmpty())
    }

    @Test
    fun `un aviso que cruza la medianoche cuenta en los dos dias`() {
        val cruzado = aviso("nieve", "naranja", dia.atTime(22, 0), dia.plusDays(1).atTime(6, 0))

        assertEquals(AlertType.SNOW, dayAlerts(listOf(cruzado), dia).single().type)
        assertEquals(AlertType.SNOW, dayAlerts(listOf(cruzado), dia.plusDays(1)).single().type)
    }

    @Test
    fun `sin avisos no hay iconos`() {
        assertTrue(dayAlerts(emptyList(), dia).isEmpty())
    }
}
