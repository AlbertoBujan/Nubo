package com.nubo.nubo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre la serialización de las ciudades guardadas.
 *
 * De esto depende que nadie pierda sus ciudades al actualizar, así que el
 * formato viejo tiene que seguir leyéndose mientras queden instalaciones
 * anteriores a la apertura al mundo.
 */
class SavedLocationTest {

    private val tokio = SavedLocation(
        locationId = "om:1850147",
        nombre = "Tokio",
        lat = 35.6895,
        lon = 139.6917,
        timeZone = "Asia/Tokyo",
        countryCode = "JP",
        region = "Tokio, Japón",
    )

    @Test
    fun `ida y vuelta conserva todos los campos`() {
        assertEquals(tokio, SavedLocation.fromPrefsString(tokio.toPrefsString()))

        val leido = SavedLocation.fromPrefsString(tokio.toPrefsString())!!
        assertEquals("Asia/Tokyo", leido.timeZone)
        assertEquals(139.6917, leido.lon!!, 0.00001)
        assertEquals("Tokio, Japón", leido.region)
    }

    @Test
    fun `lee el formato antiguo de codigo INE`() {
        // Lo que tiene guardado quien viene de una versión anterior, y lo que
        // rescata la migración desde Flutter.
        val leido = SavedLocation.fromPrefsString("15032|Curtis")!!

        assertEquals("15032", leido.locationId)
        assertEquals("Curtis", leido.nombre)
        assertNull(leido.lat)
        assertTrue(leido.isLegacyIne)
    }

    @Test
    fun `una guardada antigua se da por española`() {
        // Cuando se guardó, la app solo cubría España; sin esto se quedaría
        // sin avisos al actualizar.
        assertTrue(SavedLocation.fromPrefsString("15032|Curtis")!!.isInSpain)
        assertTrue(!tokio.isInSpain)
    }

    @Test
    fun `un nombre con barra sobrevive a la ida y vuelta`() {
        // El separador es '|' y hay topónimos que lo llevan dentro.
        val raro = tokio.copy(nombre = "Bar|celona")

        assertEquals("Bar|celona", SavedLocation.fromPrefsString(raro.toPrefsString())!!.nombre)
    }

    @Test
    fun `un nombre con barra tambien se lee en el formato antiguo`() {
        assertEquals("Bar|celona", SavedLocation.fromPrefsString("08019|Bar|celona")!!.nombre)
    }

    @Test
    fun `la igualdad depende solo del identificador`() {
        assertEquals(tokio, tokio.copy(nombre = "TOKIO"))
        assertTrue(tokio != tokio.copy(locationId = "om:otro"))
    }

    @Test
    fun `una entrada sin campos suficientes se descarta`() {
        assertNull(SavedLocation.fromPrefsString("sinseparador"))
        assertNull(SavedLocation.fromPrefsString("solo-un-campo"))
        assertNull(SavedLocation.fromPrefsString(""))
    }

    @Test
    fun `los campos opcionales ausentes vuelven como nulos`() {
        val minima = SavedLocation(locationId = "om:1", nombre = "Sitio")

        val leida = SavedLocation.fromPrefsString(minima.toPrefsString())!!
        assertNull(leida.lat)
        assertNull(leida.timeZone)
        assertNull(leida.countryCode)
        assertEquals("Sitio", leida.nombre)
    }
}
