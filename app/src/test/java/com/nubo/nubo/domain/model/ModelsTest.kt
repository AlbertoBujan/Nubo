package com.nubo.nubo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

class SavedLocationNameTest {

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
        val ahora = LocalDateTime.now()

        assertTrue(!alert("rojo", expires = ahora.minusDays(1)).isActiveAt(ahora))
        assertTrue(alert("rojo", expires = ahora.plusDays(1)).isActiveAt(ahora))
        // Sin fecha de fin se considera vigente.
        assertTrue(alert("rojo").isActiveAt(ahora))
    }

    @Test
    fun `la vigencia se juzga con la hora del sitio, no con la del movil`() {
        // Un aviso español que caduca a las 23:00 hora peninsular sigue
        // vigente a las 22:00 de allí aunque quien mire esté en Tokio, donde
        // ya son las 05:00 del día siguiente.
        val caducaAllí = LocalDateTime.of(2026, 8, 24, 23, 0)
        val aviso = alert("naranja", expires = caducaAllí)

        assertTrue(aviso.isActiveAt(LocalDateTime.of(2026, 8, 24, 22, 0)))
        assertTrue(!aviso.isActiveAt(LocalDateTime.of(2026, 8, 25, 5, 0)))
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

/**
 * Recorte de la predicción horaria por los dos extremos.
 *
 * Por delante se descartan las horas ya pasadas y por detrás todo lo que
 * exceda [HourlyForecast.MAX_HOURS], porque a partir de ahí el detalle hora a
 * hora no aporta sobre el resumen del día.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HourlyForecastTrimTest {

    /**
     * Un `hourly` de Open-Meteo con [hours] horas a partir de la hora en curso.
     *
     * Empieza en la hora en punto actual y no en la anterior porque el corte
     * inicial es `ahora - 1 h` **con minutos**: la hora en punto previa solo
     * sobrevive si son las y cero, así que incluirla haría que la cuenta
     * bailase según el minuto en que se lancen los tests.
     *
     * El huso del JSON es el del sistema para que la hora de corte se calcule
     * contra el mismo reloj con el que se generan las marcas.
     */
    private fun payload(hours: Int): org.json.JSONObject {
        val start = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
        val times = org.json.JSONArray()
        val temps = org.json.JSONArray()
        repeat(hours) { i ->
            times.put(start.plusHours(i.toLong()).toString())
            temps.put(15.0 + i)
        }
        return org.json.JSONObject().apply {
            put("timezone", java.time.ZoneId.systemDefault().id)
            put(
                "hourly",
                org.json.JSONObject().apply {
                    put("time", times)
                    put("temperature_2m", temps)
                },
            )
        }
    }

    @Test
    fun `una semana de horas se queda en un dia`() {
        val parsed = HourlyForecast.fromOpenMeteoJson(payload(hours = 168))
        assertEquals(HourlyForecast.MAX_HOURS, parsed.size)
    }

    @Test
    fun `el recorte cae en un numero justo de bloques de seis`() {
        assertEquals(0, HourlyForecast.MAX_HOURS % 6)
    }

    @Test
    fun `si la api devuelve menos horas se respetan todas`() {
        val parsed = HourlyForecast.fromOpenMeteoJson(payload(hours = 12))
        assertEquals(12, parsed.size)
    }

    @Test
    fun `las horas conservadas son las primeras, no unas cualesquiera`() {
        val parsed = HourlyForecast.fromOpenMeteoJson(payload(hours = 168))
        assertEquals(15, parsed.first().temperature)
        assertEquals(15 + HourlyForecast.MAX_HOURS - 1, parsed.last().temperature)
    }
}

/** Lectura del `hourly` de la API de calidad del aire. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AirQualityForecastTest {

    private fun json(vararg values: Any?): org.json.JSONObject {
        val times = org.json.JSONArray()
        val aqi = org.json.JSONArray()
        values.forEachIndexed { i, value ->
            times.put("2026-08-25T${"%02d".format(i)}:00")
            if (value == null) aqi.put(org.json.JSONObject.NULL) else aqi.put(value)
        }
        return org.json.JSONObject().apply {
            put(
                "hourly",
                org.json.JSONObject().apply {
                    put("time", times)
                    put("european_aqi", aqi)
                },
            )
        }
    }

    @Test
    fun `lee la serie horaria completa`() {
        val parsed = AirQualityForecast.fromOpenMeteoJson(json(18, 22, 41))

        assertEquals(3, parsed.size)
        assertEquals(18, parsed[0].europeanAqi)
        assertEquals(41, parsed[2].europeanAqi)
        assertEquals(LocalDateTime.of(2026, 8, 25, 2, 0), parsed[2].dateTime)
    }

    @Test
    fun `las horas sin dato se descartan, no se rellenan`() {
        // Fuera de Europa la resolución del modelo es peor y hay huecos.
        val parsed = AirQualityForecast.fromOpenMeteoJson(json(18, null, 41))

        assertEquals(2, parsed.size)
        assertEquals(listOf(18, 41), parsed.map { it.europeanAqi })
    }

    @Test
    fun `redondea el indice al entero mas proximo`() {
        val parsed = AirQualityForecast.fromOpenMeteoJson(json(19.6, 19.4))

        assertEquals(listOf(20, 19), parsed.map { it.europeanAqi })
    }

    @Test
    fun `una respuesta sin bloque horario no revienta`() {
        assertTrue(
            AirQualityForecast.fromOpenMeteoJson(org.json.JSONObject()).isEmpty(),
        )
        assertTrue(
            AirQualityForecast.fromOpenMeteoJson(
                org.json.JSONObject().put("hourly", org.json.JSONObject()),
            ).isEmpty(),
        )
    }
}
