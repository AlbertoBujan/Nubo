package com.nubo.nubo.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Qué intervalo sale de lo que hay guardado.
 *
 * La preferencia se guardó durante varias versiones como la **posición** en la
 * lista, y la lista ha cambiado: al meter "cada 6 horas" en su sitio, la
 * posición 1 dejó de ser "cada 12 horas". Estas pruebas son el contrato de esa
 * mudanza, que es de las que fallan sin decir nada — el ajuste simplemente
 * aparece cambiado.
 */
class BackgroundIntervalTest {

    @Test
    fun `el nombre guardado manda`() {
        assertEquals(
            BackgroundInterval.EVERY_6H,
            intervalNamed(name = "EVERY_6H", legacyIndex = null),
        )
    }

    @Test
    fun `con nombre no se mira la posicion antigua`() {
        // Si llegasen los dos, el nombre es el que escribió esta versión.
        assertEquals(
            BackgroundInterval.EVERY_24H,
            intervalNamed(name = "EVERY_24H", legacyIndex = 1),
        )
    }

    @Test
    fun `las posiciones antiguas se leen con la lista de entonces`() {
        // Apagado, 12 h y 24 h, que es lo que significaban al escribirse.
        assertEquals(BackgroundInterval.OFF, intervalNamed(null, 0))
        assertEquals(BackgroundInterval.EVERY_12H, intervalNamed(null, 1))
        assertEquals(BackgroundInterval.EVERY_24H, intervalNamed(null, 2))
    }

    @Test
    fun `quien tenia cada 12 horas sigue teniendo cada 12 horas`() {
        // El caso concreto que rompería leer la posición con la lista nueva:
        // la posición 1 es ahora "cada 6 horas".
        assertEquals(BackgroundInterval.EVERY_6H, BackgroundInterval.entries[1])
        assertEquals(BackgroundInterval.EVERY_12H, intervalNamed(null, 1))
    }

    @Test
    fun `sin nada guardado queda apagado`() {
        assertEquals(BackgroundInterval.OFF, intervalNamed(null, null))
    }

    @Test
    fun `un nombre o una posicion que no existen quedan apagados`() {
        // Volver de una versión más nueva no debe encender nada por su cuenta.
        assertEquals(BackgroundInterval.OFF, intervalNamed("EVERY_3H", null))
        assertEquals(BackgroundInterval.OFF, intervalNamed(null, 7))
    }

    @Test
    fun `la lista va de mas frecuente a menos y solo apagado no tiene horas`() {
        val horas = BackgroundInterval.entries.map { it.hours }

        assertEquals(listOf(null, 6L, 12L, 24L), horas)
    }
}
