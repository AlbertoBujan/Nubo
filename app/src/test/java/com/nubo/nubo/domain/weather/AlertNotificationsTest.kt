package com.nubo.nubo.domain.weather

import com.nubo.nubo.domain.model.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AlertNotificationsTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 15, 12, 0)

    private fun alert(
        nivel: String = "naranja",
        event: String = "Aviso de nevadas",
        onset: LocalDateTime? = now.minusHours(1),
        expires: LocalDateTime? = now.plusHours(6),
    ) = WeatherAlert(
        nivel = nivel,
        event = event,
        headline = "",
        description = "",
        instruction = "",
        areaDescription = "A Coruña litoral",
        onset = onset,
        expires = expires,
        probability = "",
    )

    @Test
    fun `el mismo aviso descargado dos veces solo se anuncia una`() {
        val a = alert()
        assertEquals(1, AlertNotifications.pending("om:1", listOf(a), emptySet(), now).size)

        val notified = setOf(AlertNotifications.keyOf("om:1", a))
        assertTrue(AlertNotifications.pending("om:1", listOf(a), notified, now).isEmpty())
    }

    @Test
    fun `prorrogar un aviso lo convierte en uno nuevo`() {
        val original = alert()
        val extended = alert(expires = now.plusHours(30))
        val notified = setOf(AlertNotifications.keyOf("om:1", original))

        assertEquals(
            listOf(extended),
            AlertNotifications.pending("om:1", listOf(extended), notified, now),
        )
    }

    @Test
    fun `subir de nivel es un aviso nuevo`() {
        val yellow = alert(nivel = "amarillo")
        val red = alert(nivel = "rojo")
        val notified = setOf(AlertNotifications.keyOf("om:1", yellow))

        assertEquals(
            listOf(red),
            AlertNotifications.pending("om:1", listOf(red), notified, now),
        )
    }

    @Test
    fun `el mismo aviso en dos ciudades se anuncia en las dos`() {
        val a = alert()
        val notified = setOf(AlertNotifications.keyOf("om:1", a))

        assertEquals(1, AlertNotifications.pending("om:2", listOf(a), notified, now).size)
    }

    @Test
    fun `un aviso ya caducado no se anuncia aunque sea la primera vez`() {
        val old = alert(onset = now.minusDays(2), expires = now.minusDays(1))

        assertTrue(AlertNotifications.pending("om:1", listOf(old), emptySet(), now).isEmpty())
    }

    @Test
    fun `la memoria olvida las claves de los avisos que ya no estan`() {
        val gone = "om:1|amarillo|viento|zona||"
        val alive = "om:1|rojo|nieve|zona||"

        assertEquals(
            setOf(alive),
            AlertNotifications.prune(setOf(gone, alive), setOf(alive)),
        )
    }
}
