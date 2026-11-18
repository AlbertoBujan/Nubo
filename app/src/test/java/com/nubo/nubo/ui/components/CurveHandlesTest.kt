package com.nubo.nubo.ui.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cómo fluye la curva de temperatura entre una hora y la siguiente.
 *
 * La temperatura llega redondeada a grados, así que la serie es una escalera.
 * Lo que se comprueba aquí no son números de una ejecución, sino que la línea
 * **no dibuja esa escalera**: ni rellanos en cada hora ni escalones entre dos,
 * pasando aun así por todos los puntos, que es donde van los círculos y las
 * etiquetas.
 */
class CurveHandlesTest {

    private fun bezier(from: Offset, first: Offset, second: Offset, to: Offset, t: Float): Offset {
        val u = 1f - t
        return from * (u * u * u) +
            first * (3f * u * u * t) +
            second * (3f * u * t * t) +
            to * (t * t * t)
    }

    /** La curva entera, muestreada tramo a tramo. */
    private fun sampled(points: List<Offset>, steps: Int = 40): List<Offset> {
        val handles = curveHandles(points)
        return handles.flatMapIndexed { i, (first, second) ->
            (0..steps).map { step ->
                bezier(points[i], first, second, points[i + 1], step / steps.toFloat())
            }
        }
    }

    private fun stair(vararg temps: Float) =
        temps.mapIndexed { i, t -> Offset(i * 100f, t) }

    @Test
    fun `la curva pasa por cada hora`() {
        val points = stair(10f, 10f, 0f, 0f, 5f)

        curveHandles(points).forEachIndexed { i, (first, second) ->
            assertEquals(points[i], bezier(points[i], first, second, points[i + 1], 0f))
            assertEquals(points[i + 1], bezier(points[i], first, second, points[i + 1], 1f))
        }
    }

    @Test
    fun `no se aplana en cada hora`() {
        // Esto era el fallo: con los tiradores a la altura de su punto, la
        // tangente era horizontal en cada hora y cada punto tenía su rellano.
        val points = stair(30f, 20f, 10f)

        curveHandles(points).forEach { (first, second) ->
            assertTrue("tirador de salida plano", first.y != points[0].y)
            assertTrue("tirador de entrada plano", second.y != points[1].y)
        }
    }

    @Test
    fun `entra y sale de cada hora con la misma pendiente`() {
        // Lo que hace que sea una sola curva y no tramos pegados: el tirador
        // que llega a una hora y el que sale de ella son simétricos respecto
        // de ella.
        val points = stair(10f, 10f, 11f, 13f, 12f)
        val handles = curveHandles(points)

        for (i in 1 until points.size - 1) {
            val llega = handles[i - 1].second
            val sale = handles[i].first
            assertEquals(points[i].x - llega.x, sale.x - points[i].x, 0.001f)
            assertEquals(points[i].y - llega.y, sale.y - points[i].y, 0.001f)
        }
    }

    @Test
    fun `el llano se curva antes del escalón`() {
        // Tres horas a 16° y luego 17°: la subida no puede empezar en el
        // punto, porque entonces es un escalón. El tramo anterior tiene que
        // llegar ya subiendo, y el precio es hundirse un poco antes, que es
        // lo que reparte la subida. Ese hundimiento es un 7 % del escalón:
        // con un grado de salto y la escala de la tarjeta, dos o tres
        // píxeles.
        val points = stair(0f, 0f, 0f, -10f)
        val (first, second) = curveHandles(points)[1]
        val tramo = (0..40).map { bezier(points[1], first, second, points[2], it / 40f).y }

        assertTrue("el llano llega al escalón todavía plano", tramo.last() < tramo[39])
        val escalon = 10f
        assertTrue("se hunde demasiado antes de subir: ${tramo.max()}", tramo.max() < 0.1f * escalon)
    }

    @Test
    fun `horas iguales siguen planas`() {
        // Fluir no es inventarse una onda: si la temperatura no cambia, la
        // línea no se mueve.
        val points = stair(7f, 7f, 7f, 7f)

        sampled(points).forEach { assertEquals(7f, it.y, 0.001f) }
    }

    @Test
    fun `no se sale del rango de las horas que une`() {
        // Un Catmull-Rom se pasa un poco al entrar y salir de un escalón.
        // Con un grado de salto eso son unos pocos píxeles, y lo que no puede
        // es acercarse al grado entero: la curva pasaría por encima de su
        // propia etiqueta.
        val points = stair(10f, 10f, 9f, 9f, 8f, 8f, 9f, 10f)
        val alturas = sampled(points).map { it.y }
        val salto = 1f

        assertTrue("se pasa por arriba: ${alturas.min()}", alturas.min() > 8f - 0.25f * salto)
        assertTrue("se pasa por abajo: ${alturas.max()}", alturas.max() < 10f + 0.25f * salto)
    }

    @Test
    fun `sin dos puntos no hay tramo`() {
        assertTrue(curveHandles(emptyList()).isEmpty())
        assertTrue(curveHandles(listOf(Offset(0f, 0f))).isEmpty())
        assertEquals(1, curveHandles(listOf(Offset(0f, 0f), Offset(10f, 10f))).size)
    }
}

/**
 * El troceado de la curva para la línea de guiones del rocío.
 */
class SmoothPolylineTest {

    private fun stair(vararg temps: Float) =
        temps.mapIndexed { i, t -> Offset(i * 100f, t) }

    @Test
    fun `pasa por todas las horas`() {
        val points = stair(10f, 10f, 7f, 8f)
        val linea = smoothPolyline(points, perHour = 6)

        points.forEachIndexed { i, point ->
            assertEquals(point.x, linea[i * 6].x, 0.01f)
            assertEquals(point.y, linea[i * 6].y, 0.01f)
        }
    }

    @Test
    fun `trocea cada hora en partes iguales`() {
        val linea = smoothPolyline(stair(0f, 1f, 2f), perHour = 6)

        assertEquals(13, linea.size)
        linea.zipWithNext { a, b -> assertTrue(b.x > a.x) }
    }

    @Test
    fun `horas iguales siguen planas`() {
        smoothPolyline(stair(4f, 4f, 4f), perHour = 6).forEach {
            assertEquals(4f, it.y, 0.001f)
        }
    }

    @Test
    fun `sin dos puntos se devuelve lo que llega`() {
        val uno = listOf(Offset(1f, 2f))

        assertEquals(uno, smoothPolyline(uno))
        assertEquals(emptyList<Offset>(), smoothPolyline(emptyList()))
    }
}
