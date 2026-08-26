package com.nubo.nubo.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.nubo.nubo.domain.model.DistanceUnit
import java.util.Locale

/**
 * Se valida contra distancias reales conocidas, no contra valores copiados de
 * otra implementación: así el test detecta un error de fórmula y no solo una
 * regresión respecto a sí mismo.
 */
class DistanceTest {

    private val madrid = 40.4165 to -3.7026
    private val barcelona = 41.3874 to 2.1686
    private val tokio = 35.6895 to 139.6917

    private fun between(a: Pair<Double, Double>, b: Pair<Double, Double>) =
        distanceKm(a.first, a.second, b.first, b.second)

    @Test
    fun `Madrid-Barcelona son unos 505 kilometros`() {
        assertEquals(505.0, between(madrid, barcelona), 10.0)
    }

    @Test
    fun `Madrid-Tokio son unos 10770 kilometros`() {
        assertEquals(10770.0, between(madrid, tokio), 60.0)
    }

    @Test
    fun `la distancia de un punto a si mismo es cero`() {
        // El caso que hace fallar una haversine ingenua: el redondeo puede
        // sacar el argumento de asin() por encima de 1 y devolver NaN.
        val d = between(madrid, madrid)

        assertEquals(0.0, d, 0.0)
        assertTrue(!d.isNaN())
    }

    @Test
    fun `es simetrica`() {
        assertEquals(between(madrid, tokio), between(tokio, madrid), 0.0001)
    }

    @Test
    fun `corrige la convergencia de los meridianos`() {
        // Un grado de longitud son ~111 km en el ecuador y bastantes menos
        // cerca del polo. Una distancia euclídea en grados los daría iguales,
        // y por eso no vale para ordenar sitios de todo el mundo.
        val enElEcuador = distanceKm(0.0, 0.0, 0.0, 1.0)
        val enTromso = distanceKm(69.65, 18.95, 69.65, 19.95)

        assertEquals(111.0, enElEcuador, 1.0)
        assertTrue("En Tromsø un grado debe medir mucho menos", enTromso < 45.0)
    }

    @Test
    fun `el formato da un decimal en distancias cortas`() {
        // Entre dos pueblos vecinos, "8 km" y "8,4 km" es la diferencia entre
        // poder ordenarlos y no.
        withLocale(Locale.forLanguageTag("es-ES")) {
            assertEquals("8,4 km", formatDistance(8.42))
            assertEquals("34 km", formatDistance(34.2))
            assertEquals("640 m", formatDistance(0.64))
        }
    }

    @Test
    fun `en millas se cambia de unidad, no solo de nombre`() {
        withLocale(Locale.forLanguageTag("en-US")) {
            // 8,42 km son 5,2 millas; 34,2 km son 21 millas.
            assertEquals("5.2 mi", formatDistance(8.42, DistanceUnit.MILES))
            assertEquals("21 mi", formatDistance(34.2, DistanceUnit.MILES))
        }
    }

    @Test
    fun `por debajo de una milla se baja a pies`() {
        withLocale(Locale.forLanguageTag("en-US")) {
            // 0,1 km son unos 328 pies.
            assertEquals("328 ft", formatDistance(0.1, DistanceUnit.MILES))
        }
    }

    @Test
    fun `sin decir nada se mide en kilometros`() {
        withLocale(Locale.forLanguageTag("es-ES")) {
            assertEquals("8,4 km", formatDistance(8.42))
        }
    }

    @Test
    fun `el separador decimal es el del idioma, no el del que compila`() {
        // El separador sale de `Locale.getDefault()` desde que la app se
        // traduce, así que el test **tiene que fijar el idioma**: dejándolo al
        // de la máquina pasaba aquí y fallaba en el runner de CI, que está en
        // inglés. Y es justo lo que hay que comprobar, porque una coma metida a
        // mano se leería mal en media app.
        withLocale(Locale.forLanguageTag("en-US")) {
            assertEquals("8.4 km", formatDistance(8.42))
        }
        withLocale(Locale.forLanguageTag("es-ES")) {
            assertEquals("8,4 km", formatDistance(8.42))
        }
    }

    /** Ejecuta [block] con un idioma fijo y devuelve el que hubiera. */
    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
