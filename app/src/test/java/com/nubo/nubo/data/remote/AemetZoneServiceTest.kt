package com.nubo.nubo.data.remote

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cubre lo único que queda del maestro de AEMET desde que la búsqueda es
 * mundial: resolver la zona de aviso y las coordenadas de un municipio.
 *
 * Usa Robolectric porque el maestro se parsea con `org.json`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AemetZoneServiceTest {

    private val service = AemetZoneService()

    private val maestro = JSONArray(
        """
        [
          {"id":"id28079","nombre":"Madrid","latitud_dec":"40,4165","longitud_dec":"-3,7026","zona_comarcal":"722801"},
          {"id":"id35016","nombre":"Palmas de Gran Canaria, Las","latitud_dec":"28,1248","longitud_dec":"-15,4300","zona_comarcal":"659001"},
          {"id":"id15030","nombre":"Coruña, A","latitud_dec":"43,3713","longitud_dec":"-8,3960","zona_comarcal":"711501"},
          {"id":"id15032","nombre":"Curtis","latitud_dec":"43,1389","longitud_dec":"-8,0375","zona_comarcal":"711503"},
          {"id":"id46250","nombre":"Sin zona","latitud_dec":"39,4699","longitud_dec":"-0,3763","zona_comarcal":""},
          {"id":"id07040","nombre":"Zona corta","zona_comarcal":"64"}
        ]
        """.trimIndent(),
    )

    private fun parsed() = service.parseMaestro(maestro)

    @Test
    fun `quita el prefijo id del codigo de municipio`() {
        assertEquals("28079", parsed().first().id)
    }

    @Test
    fun `parsea decimales escritos con coma`() {
        val madrid = parsed().first()

        assertEquals(40.4165, madrid.lat!!, 0.0001)
        assertEquals(-3.7026, madrid.lon!!, 0.0001)
    }

    @Test
    fun `una entrada sin coordenadas se conserva pero sin posicion`() {
        val sinCoords = parsed().first { it.id == "07040" }

        assertNull(sinCoords.lat)
        assertNull(sinCoords.lon)
    }

    @Test
    fun `lee la zona de aviso del maestro`() {
        val zonas = parsed().associate { it.id to it.zonaAviso }

        assertEquals("722801", zonas["28079"])
        assertEquals("711503", zonas["15032"])
    }

    @Test
    fun `en Canarias la zona no lleva los digitos de provincia del INE`() {
        // Las Palmas es la provincia 35, pero su zona es 6590xx: por eso
        // filtrar los avisos por el prefijo de provincia nunca funcionó allí.
        val laspalmas = parsed().first { it.id == "35016" }

        assertEquals("659001", laspalmas.zonaAviso)
        assertTrue(!laspalmas.zonaAviso!!.startsWith("6535"))
    }

    @Test
    fun `una zona ausente o mal formada se descarta`() {
        // Sin zona el municipio se queda sin avisos, que es preferible a
        // colarle los de otra: ver `AlertService`.
        val zonas = parsed().associate { it.id to it.zonaAviso }

        assertNull(zonas["46250"])
        assertNull(zonas["07040"])
    }

    @Test
    fun `el municipio mas cercano da la zona de un punto sin codigo INE`() {
        // Es como se resuelve la zona de una localización de Open-Meteo, que
        // no trae código INE. Open-Meteo sitúa Curtis en 43.1237, -8.1482.
        val municipios = parsed()
        val nearest = municipios.minByOrNull {
            val dLat = 43.1237 - (it.lat ?: 999.0)
            val dLon = -8.1482 - (it.lon ?: 999.0)
            dLat * dLat + dLon * dLon
        }

        assertEquals("15032", nearest?.id)
        assertEquals("711503", nearest?.zonaAviso)
    }
}
