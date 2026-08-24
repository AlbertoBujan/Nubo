package com.nubo.nubo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

class SavedLocationTest {

    @Test
    fun `reordena el articulo pospuesto de AEMET`() {
        assertEquals("A Coruña", SavedLocation.formatNombre("Coruña, A"))
        assertEquals("La Bañeza", SavedLocation.formatNombre("Bañeza, La"))
        assertEquals("Els Alamús", SavedLocation.formatNombre("Alamús, Els"))
    }

    @Test
    fun `deja intactos los nombres sin articulo`() {
        assertEquals("Madrid", SavedLocation.formatNombre("Madrid"))
        assertEquals("Valencia/València", SavedLocation.formatNombre("Valencia/València"))
    }

    @Test
    fun `no reordena cuando lo que sigue a la coma es un toponimo`() {
        // "Bilbao, Vizcaya" no debe convertirse en "Vizcaya Bilbao".
        assertEquals("Bilbao, Vizcaya", SavedLocation.formatNombre("Bilbao, Vizcaya"))
    }

    @Test
    fun `la serializacion sobrevive a nombres con barra vertical`() {
        val location = SavedLocation("28079", "Raro|Nombre")
        val restored = SavedLocation.fromPrefsString(location.toPrefsString())
        assertEquals(location.municipioId, restored?.municipioId)
        assertEquals("Raro|Nombre", restored?.nombre)
    }

    @Test
    fun `una cadena malformada no rompe la deserializacion`() {
        assertNull(SavedLocation.fromPrefsString("sinseparador"))
    }

    @Test
    fun `la igualdad depende solo del municipio`() {
        assertEquals(SavedLocation("28079", "Madrid"), SavedLocation("28079", "MADRID"))
        assertTrue(SavedLocation("28079", "Madrid") != SavedLocation("08019", "Madrid"))
    }
}

/**
 * Usa Robolectric porque el ciclo JSON pasa por `org.json`, que en un test de
 * JVM puro es un stub que lanza al invocarlo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.13 aún no simula la API 36 del targetSdk.
class WeatherAlertTest {

    private fun alert(
        nivel: String,
        onset: LocalDateTime? = null,
        expires: LocalDateTime? = null,
    ) = WeatherAlert(
        nivel = nivel,
        event = "Lluvias",
        headline = "",
        description = "",
        instruction = "",
        areaDescription = "",
        onset = onset,
        expires = expires,
        probability = "",
    )

    @Test
    fun `la severidad ordena los niveles`() {
        assertTrue(alert("amarillo").severity < alert("naranja").severity)
        assertTrue(alert("naranja").severity < alert("rojo").severity)
    }

    @Test
    fun `un nivel desconocido cae en amarillo`() {
        assertEquals(AlertLevel.YELLOW, alert("morado").level)
        assertEquals(AlertLevel.YELLOW, alert("").level)
    }

    @Test
    fun `un aviso caducado deja de estar vigente`() {
        val pasado = LocalDateTime.now().minusDays(1)
        assertTrue(!alert("rojo", expires = pasado).isActiveOrUpcoming)
        assertTrue(alert("rojo", expires = LocalDateTime.now().plusDays(1)).isActiveOrUpcoming)
        // Sin fecha de fin se considera vigente.
        assertTrue(alert("rojo").isActiveOrUpcoming)
    }

    @Test
    fun `el solape detecta los avisos que cruzan un intervalo`() {
        val base = LocalDateTime.of(2026, 8, 24, 12, 0)
        val a = alert("rojo", onset = base.plusHours(2), expires = base.plusHours(6))

        assertTrue(a.overlaps(base, base.plusHours(4)))
        assertTrue(a.overlaps(base.plusHours(5), base.plusHours(8)))
        assertTrue(!a.overlaps(base, base.plusHours(1)))
        assertTrue(!a.overlaps(base.plusHours(7), base.plusHours(9)))
    }

    @Test
    fun `el ciclo json conserva los campos`() {
        val original = WeatherAlert(
            nivel = "naranja",
            event = "Costeros",
            headline = "Titular",
            description = "Descripción",
            instruction = "Instrucción",
            areaDescription = "Zona",
            onset = LocalDateTime.of(2026, 8, 24, 10, 0),
            expires = LocalDateTime.of(2026, 8, 24, 20, 0),
            probability = "40%-70%",
        )

        val restored = WeatherAlert.fromJson(original.toJson())

        assertEquals(original, restored)
    }

    @Test
    fun `acepta marcas con huso horario del CAP`() {
        val parsed = WeatherAlert.parseDateTime("2026-08-24T12:00:00+02:00")
        assertTrue(parsed != null)
    }

    @Test
    fun `una fecha ilegible se descarta`() {
        assertNull(WeatherAlert.parseDateTime("no es fecha"))
        assertNull(WeatherAlert.parseDateTime(""))
        assertNull(WeatherAlert.parseDateTime(null))
    }
}

class HourlyForecastCompassTest {

    @Test
    fun `los grados se traducen al sector cardinal`() {
        assertEquals("N", HourlyForecast.degreesToCompass(0.0))
        assertEquals("N", HourlyForecast.degreesToCompass(360.0))
        assertEquals("E", HourlyForecast.degreesToCompass(90.0))
        assertEquals("S", HourlyForecast.degreesToCompass(180.0))
        assertEquals("W", HourlyForecast.degreesToCompass(270.0))
        assertEquals("NE", HourlyForecast.degreesToCompass(45.0))
        assertEquals("NNE", HourlyForecast.degreesToCompass(22.5))
    }

    @Test
    fun `los limites de sector redondean al mas proximo`() {
        assertEquals("N", HourlyForecast.degreesToCompass(11.0))
        assertEquals("NNE", HourlyForecast.degreesToCompass(12.0))
    }
}
