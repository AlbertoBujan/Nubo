package com.nubo.nubo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Forma de la curva con la que los números llegan a su valor.
 *
 * Lo que se fija no son los cuatro números de la bézier —esos se pueden
 * afinar— sino las dos propiedades que se buscaban, y que la primera versión
 * cumplía solo a medias: que **frene** y que **siga contando hasta el final**.
 * Frenaba tanto que el número llegaba a su valor a mitad de la animación y el
 * resto del tiempo no cambiaba nada, con lo que se leía como que acababa de
 * golpe.
 */
class SettleEasingTest {

    /** En qué instante (de 0 a 1) cambia por última vez un recuento de [n]. */
    private fun ultimoCambio(n: Int): Float {
        val pasos = (0..1000).map { it / 1000f }
        var ultimo = 0f
        var previo = 0
        pasos.forEach { t ->
            val valor = (SETTLE.transform(t) * n).roundToInt()
            if (valor != previo) {
                previo = valor
                ultimo = t
            }
        }
        return ultimo
    }

    @Test
    fun `empieza y acaba donde debe`() {
        assertEquals(0f, SETTLE.transform(0f), 1e-4f)
        assertEquals(1f, SETTLE.transform(1f), 1e-4f)
    }

    @Test
    fun `frena`() {
        val primero = SETTLE.transform(0.1f) - SETTLE.transform(0f)
        val ultimo = SETTLE.transform(1f) - SETTLE.transform(0.9f)

        assertTrue("primero=$primero ultimo=$ultimo", ultimo < primero / 2f)
    }

    @Test
    fun `sigue contando hasta el final`() {
        // 20 grados es un recuento cualquiera de los que enseña la app.
        val instante = ultimoCambio(20)

        assertTrue("el último dígito cae en $instante", instante > 0.75f)
    }

    @Test
    fun `hasta un recuento corto llega vivo al final`() {
        assertTrue(ultimoCambio(5) > 0.6f)
    }

    @Test
    fun `nunca retrocede`() {
        (0..100).map { it / 100f }.zipWithNext { previous, next ->
            assertTrue(SETTLE.transform(next) >= SETTLE.transform(previous))
        }
    }
}
