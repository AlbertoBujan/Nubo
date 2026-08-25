package com.nubo.nubo.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Cubre el parseo de la geocodificación inversa del botón de ubicación. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReverseGeocodingApiTest {

    @Test
    fun `usa la ciudad cuando viene`() {
        val json = JSONObject(
            """{"city":"Santa Eulalia de Curtis","locality":"Teixeiro",
                "principalSubdivision":"Galicia","countryCode":"ES","countryName":"España"}""",
        )

        val place = ReverseGeocodingApi.parsePlace(json, 43.1389, -8.0375)!!

        assertEquals("Santa Eulalia de Curtis", place.nombre)
        assertEquals("ES", place.countryCode)
        assertEquals(43.1389, place.lat!!, 0.00001)
    }

    @Test
    fun `en zona rural cae a la localidad`() {
        // `city` viene vacío fuera de los núcleos urbanos, y ahí el nombre
        // útil es el de la localidad.
        val json = JSONObject(
            """{"city":"","locality":"Teixeiro","principalSubdivision":"Galicia",
                "countryCode":"ES"}""",
        )

        assertEquals("Teixeiro", ReverseGeocodingApi.parsePlace(json, 43.1, -8.0)!!.nombre)
    }

    @Test
    fun `sin ningun nombre utilizable no devuelve nada`() {
        val json = JSONObject("""{"countryCode":"ES"}""")

        assertNull(ReverseGeocodingApi.parsePlace(json, 43.1, -8.0))
    }

    @Test
    fun `el id se redondea para no duplicar la ubicacion actual`() {
        // Pulsar dos veces el botón habiéndose movido unos metros no debe
        // crear dos entradas distintas.
        assertEquals(
            ReverseGeocodingApi.idFor(43.13891, -8.03751),
            ReverseGeocodingApi.idFor(43.13894, -8.03748),
        )
        assertEquals("geo:43.1389,-8.0375", ReverseGeocodingApi.idFor(43.1389, -8.0375))
    }
}
