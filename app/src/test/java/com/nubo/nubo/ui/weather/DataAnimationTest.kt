package com.nubo.nubo.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * Cuándo se animan los números y el trazado de la gráfica.
 *
 * La regla es "cuando el dato es nuevo", no "cuando se mira la ciudad": si
 * fuese lo segundo, ir y volver entre dos ciudades pondría a bailar cifras que
 * llevaban ahí un rato y la animación dejaría de significar nada.
 */
class DataAnimationTest {

    private val ayer: LocalDateTime = LocalDateTime.of(2026, 8, 25, 19, 0)
    private val ahora: LocalDateTime = LocalDateTime.of(2026, 8, 26, 13, 0)

    @Test
    fun `un dato que nunca se ha animado se anima`() {
        assertEquals(ahora, animationTrigger(ahora, alreadyAnimated = null))
    }

    @Test
    fun `el mismo dato no se anima dos veces`() {
        assertNull(animationTrigger(ahora, alreadyAnimated = ahora))
    }

    @Test
    fun `refrescar trae un dato nuevo y vuelve a animar`() {
        assertEquals(ahora, animationTrigger(ahora, alreadyAnimated = ayer))
    }

    @Test
    fun `sin dato no hay nada que animar`() {
        assertNull(animationTrigger(null, alreadyAnimated = null))
        assertNull(animationTrigger(null, alreadyAnimated = ayer))
    }
}
