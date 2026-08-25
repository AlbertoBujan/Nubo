package com.nubo.nubo.data.remote

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cubre el parseo de la geocodificación de Open-Meteo, que es de donde salen
 * las localizaciones desde que la app dejó de limitarse a España.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeocodingApiTest {

    /** Recorte real de la respuesta para "Curtis". */
    private val respuesta = JSONArray(
        """
        [
          {"id":3124001,"name":"Curtis","latitude":43.12374,"longitude":-8.14818,
           "country_code":"ES","country":"España","admin2":"Provincia de La Coruña",
           "admin1":"Comunidad Autónoma de Galicia",
           "timezone":"Europe/Madrid"},
          {"id":5694650,"name":"Curtis","latitude":40.63001,"longitude":-100.5157,
           "country_code":"US","country":"Estados Unidos","admin1":"Nebraska",
           "timezone":"America/Chicago"}
        ]
        """.trimIndent(),
    )

    private fun parsed() = GeocodingApi.parseResults(respuesta)

    @Test
    fun `distingue dos sitios con el mismo nombre`() {
        val results = parsed()

        assertEquals(2, results.size)
        assertEquals(results[0].nombre, results[1].nombre)
        // Lo que los separa en la lista es la región.
        assertTrue(results[0].region != results[1].region)
        assertEquals("La Coruña, Galicia, España", results[0].region)
        assertEquals("Nebraska, Estados Unidos", results[1].region)
    }

    @Test
    fun `guarda coordenadas y zona horaria`() {
        val curtisGalicia = parsed().first()

        assertEquals(43.12374, curtisGalicia.lat!!, 0.00001)
        assertEquals(-8.14818, curtisGalicia.lon!!, 0.00001)
        assertEquals("Europe/Madrid", curtisGalicia.timeZone)
        assertEquals("ES", curtisGalicia.countryCode)
    }

    @Test
    fun `el id lleva prefijo para no confundirse con un codigo INE`() {
        // Los guardados antiguos son códigos INE pelados ("15032"), y de eso
        // depende saber a cuáles hay que rellenarles las coordenadas.
        val id = parsed().first().locationId

        assertEquals("om:3124001", id)
        assertTrue(!parsed().first().isLegacyIne)
    }

    @Test
    fun `solo España tiene avisos`() {
        val (galicia, nebraska) = parsed()

        assertTrue(galicia.isInSpain)
        assertTrue(!nebraska.isInSpain)
    }

    @Test
    fun `la region lleva provincia, comunidad y pais`() {
        val json = JSONObject(
            """{"id":1,"name":"Montaos","latitude":43.03,"longitude":-8.41,
                "admin2":"Provincia de La Coruña","admin1":"Comunidad Autónoma de Galicia",
                "country":"España","country_code":"ES"}""",
        )

        assertEquals("La Coruña, Galicia, España", GeocodingApi.parseResult(json)!!.region)
    }

    @Test
    fun `no repite la provincia cuando se llama como el sitio`() {
        // Sin podar, esto sería "Provincia de Santa Cruz de Tenerife,
        // Comunidad Autónoma de Canarias, España": 75 caracteres.
        val json = JSONObject(
            """{"id":1,"name":"Santa Cruz de Tenerife","latitude":28.46,"longitude":-16.25,
                "admin2":"Provincia de Santa Cruz de Tenerife",
                "admin1":"Comunidad Autónoma de Canarias","country":"España"}""",
        )

        assertEquals("Canarias, España", GeocodingApi.parseResult(json)!!.region)
    }

    @Test
    fun `no repite la provincia cuando se llama como la comunidad`() {
        val json = JSONObject(
            """{"id":1,"name":"Santa Cruz","latitude":37.9,"longitude":-1.1,
                "admin2":"Provincia de Murcia","admin1":"Región de Murcia",
                "country":"España"}""",
        )

        assertEquals("Murcia, España", GeocodingApi.parseResult(json)!!.region)
    }

    @Test
    fun `tambien poda los encabezados que en ingles van detras`() {
        // Aun pidiendo en español, GeoNames deja en inglés las divisiones de
        // muchos países: "Province of Laguna", "Santa Rosa Department".
        assertEquals("Santa Cruz", GeocodingApi.trimAdminPrefix("Santa Cruz County"))
        assertEquals("Santa Rosa", GeocodingApi.trimAdminPrefix("Santa Rosa Department"))
        assertEquals("Laguna", GeocodingApi.trimAdminPrefix("Province of Laguna"))
        assertEquals("Murcia", GeocodingApi.trimAdminPrefix("Region of Murcia"))

        // Y no toca las que dejarían un nombre cojo.
        assertEquals("Free State", GeocodingApi.trimAdminPrefix("Free State"))
        assertEquals("Central Region", GeocodingApi.trimAdminPrefix("Central Region"))
    }

    @Test
    fun `una division sin encabezado conocido se deja intacta`() {
        assertEquals("Azores", GeocodingApi.trimAdminPrefix("Azores"))
        assertEquals("Bizkaia", GeocodingApi.trimAdminPrefix("Bizkaia"))
        assertEquals("Calabarzón", GeocodingApi.trimAdminPrefix("Calabarzón"))
    }

    @Test
    fun `quita el articulo del encabezado pero no el del toponimo`() {
        // La mayúscula dice de quién es el artículo. "las Islas Baleares"
        // sobra entero; "La Coruña" sin el "La" deja la provincia coja.
        assertEquals("Islas Baleares", GeocodingApi.trimAdminPrefix("Provincia de las Islas Baleares"))
        assertEquals("La Coruña", GeocodingApi.trimAdminPrefix("Provincia de La Coruña"))
        assertEquals("País Vasco", GeocodingApi.trimAdminPrefix("Comunidad Autónoma del País Vasco"))
        assertEquals("Chaco", GeocodingApi.trimAdminPrefix("Provincia del Chaco"))
        assertEquals("Coahuila de Zaragoza", GeocodingApi.trimAdminPrefix("Estado de Coahuila de Zaragoza"))
    }

    @Test
    fun `un encabezado que es todo el nombre no se poda`() {
        // Quitarlo dejaría la división sin nombre.
        assertEquals("Estado", GeocodingApi.trimAdminPrefix("Estado"))
        assertNull(GeocodingApi.trimAdminPrefix(""))
    }

    @Test
    fun `una entrada sin coordenadas se descarta`() {
        // Sin coordenadas no hay ni predicción ni sol: guardarla a medias
        // solo daría una ciudad rota en la lista.
        val sinCoords = JSONObject("""{"id":1,"name":"Sitio raro"}""")

        assertNull(GeocodingApi.parseResult(sinCoords))
    }

    @Test
    fun `una respuesta vacia o ausente no rompe`() {
        assertTrue(GeocodingApi.parseResults(null).isEmpty())
        assertTrue(GeocodingApi.parseResults(JSONArray()).isEmpty())
    }
}
