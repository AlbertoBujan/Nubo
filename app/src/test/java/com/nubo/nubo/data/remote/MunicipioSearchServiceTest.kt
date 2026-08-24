package com.nubo.nubo.data.remote

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Usa Robolectric porque el maestro se parsea con `org.json`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MunicipioSearchServiceTest {

    private val service = MunicipioSearchService()

    private val maestro = JSONArray(
        """
        [
          {"id":"id28079","nombre":"Madrid","latitud_dec":"40,4165","longitud_dec":"-3,7026"},
          {"id":"id35016","nombre":"Palmas de Gran Canaria, Las","latitud_dec":"28,1248","longitud_dec":"-15,4300"},
          {"id":"id15030","nombre":"Coruña, A","latitud_dec":"43,3713","longitud_dec":"-8,3960"},
          {"id":"id46250","nombre":"Valencia (Comunitat Valenciana)","latitud_dec":"39,4699","longitud_dec":"-0,3763"},
          {"id":"id07040","nombre":"Sin coordenadas"}
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
    fun `elimina el parentesis final con la provincia`() {
        assertEquals("Valencia", MunicipioSearchService.cleanNombre("Valencia (Comunitat Valenciana)"))
        assertEquals("Madrid", MunicipioSearchService.cleanNombre("Madrid"))
    }

    @Test
    fun `se puede buscar tanto por la forma de AEMET como por la natural`() {
        val lasPalmas = parsed().first { it.id == "35016" }

        // AEMET lo publica como "Palmas de Gran Canaria, Las", pero la app lo
        // muestra como "Las Palmas de Gran Canaria": ambas deben encontrarlo.
        assertTrue(lasPalmas.searchable.any { it.contains("las palmas") })
        assertTrue(lasPalmas.searchable.any { it.contains("palmas de gran canaria,") })
    }

    @Test
    fun `la coruña se encuentra escribiendo A Coruña`() {
        val coruna = parsed().first { it.id == "15030" }
        assertTrue(coruna.searchable.any { it.contains("a coruna") })
    }

    @Test
    fun `la normalizacion ignora tildes y mayusculas`() {
        assertEquals("a coruna", MunicipioSearchService.normalize("A Coruña"))
        assertEquals("alcaniz", MunicipioSearchService.normalize("Alcañiz"))
        assertEquals("aviles", MunicipioSearchService.normalize("Avilés"))
    }
}
