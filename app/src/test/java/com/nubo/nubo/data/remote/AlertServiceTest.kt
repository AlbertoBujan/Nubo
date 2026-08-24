package com.nubo.nubo.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Cubre el parseo de los avisos CAP tal y como los sirve AEMET: varios
 * `<alert>` concatenados en un mismo cuerpo, en varios idiomas y para toda una
 * comunidad autónoma, de donde hay que quedarse solo con lo de una zona.
 */
class AlertServiceTest {

    private val service = AlertService()

    /**
     * Interior de A Coruña, la zona de Curtis.
     *
     * Galicia (71) + A Coruña (15) + zona 03. La provincia tiene otras tres
     * zonas —01 Noroeste, 02 Oeste y 04 Suroeste— que son costeras.
     */
    private val zona = "711503"

    private val futuro = LocalDateTime.now().plusDays(1).toString() + "+02:00"
    private val pasado = LocalDateTime.now().minusDays(2).toString() + "+02:00"

    private fun alertXml(
        geocode: String,
        nivel: String,
        event: String = "Lluvias",
        language: String = "es-ES",
        expires: String = futuro,
        probability: String = "40%-70%",
    ) = """
        <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
          <identifier>2.49.0.1.724.0.$geocode</identifier>
          <info>
            <language>$language</language>
            <event>$event</event>
            <onset>$futuro</onset>
            <expires>$expires</expires>
            <headline>Aviso de $event de nivel $nivel</headline>
            <description>Se esperan $event en la zona.</description>
            <instruction>Precaución.</instruction>
            <parameter><valueName>AEMET-Meteoalerta nivel</valueName><value>$nivel</value></parameter>
            <parameter><valueName>AEMET-Meteoalerta probabilidad</valueName><value>$probability</value></parameter>
            <area>
              <areaDesc>Zona costera</areaDesc>
              <geocode><valueName>AEMET-Meteoalerta zona</valueName><value>$geocode</value></geocode>
            </area>
          </info>
        </alert>
    """.trimIndent()

    @Test
    fun `extrae varios avisos concatenados en un mismo cuerpo`() {
        val body = alertXml(zona, "amarillo", event = "Lluvias") +
            alertXml(zona, "naranja", event = "Tormentas")

        val alerts = service.parseCapAlerts(body, zona)

        assertEquals(2, alerts.size)
        assertEquals("amarillo", alerts[0].nivel)
        assertEquals("naranja", alerts[1].nivel)
    }

    @Test
    fun `descarta los avisos de otra provincia de la misma comunidad`() {
        // 7115 = A Coruña, 7127 = Lugo. Ambas están en el área 71.
        val body = alertXml(zona, "amarillo") + alertXml("712701", "rojo")

        val alerts = service.parseCapAlerts(body, zona)

        assertEquals(1, alerts.size)
        assertEquals("amarillo", alerts[0].nivel)
    }

    @Test
    fun `ignora el nivel verde porque significa que no hay aviso`() {
        val body = alertXml(zona, "verde")

        assertTrue(service.parseCapAlerts(body, zona).isEmpty())
    }

    @Test
    fun `se queda solo con la version en español`() {
        val body = alertXml(zona, "amarillo", event = "Rain", language = "en-GB") +
            alertXml(zona, "amarillo", event = "Lluvias", language = "es-ES")

        val alerts = service.parseCapAlerts(body, zona)

        assertEquals(1, alerts.size)
        assertEquals("Lluvias", alerts[0].event)
    }

    @Test
    fun `descarta los avisos ya caducados`() {
        val body = alertXml(zona, "amarillo", expires = pasado)

        assertTrue(service.parseCapAlerts(body, zona).isEmpty())
    }

    @Test
    fun `lee nivel, probabilidad, zona y textos`() {
        val alerts = service.parseCapAlerts(alertXml(zona, "naranja"), zona)

        assertEquals(1, alerts.size)
        val alert = alerts[0]
        assertEquals("naranja", alert.nivel)
        assertEquals("40%-70%", alert.probability)
        assertEquals("Zona costera", alert.areaDescription)
        assertEquals("Lluvias", alert.event)
        assertEquals("Precaución.", alert.instruction)
        assertTrue(alert.headline.contains("naranja"))
        assertEquals(2, alert.severity)
    }

    @Test
    fun `un bloque corrupto no invalida los demas`() {
        val body = "<alert><info><language>es</language>SIN CERRAR" +
            alertXml(zona, "amarillo")

        val alerts = service.parseCapAlerts(body, zona)

        assertEquals(1, alerts.size)
        assertEquals("amarillo", alerts[0].nivel)
    }

    @Test
    fun `un cuerpo vacio o sin avisos devuelve lista vacia`() {
        assertTrue(service.parseCapAlerts("", zona).isEmpty())
        assertTrue(service.parseCapAlerts("no es xml", zona).isEmpty())
    }

    @Test
    fun `descarta los avisos de otra zona de la misma provincia`() {
        // El fallo que arrastraba la app: filtrando por el prefijo "7115"
        // entraban las cuatro zonas de A Coruña, así que a Curtis —interior—
        // le llegaban los avisos costeros del Noroeste y del Oeste.
        val body = alertXml("711501", "rojo", event = "Costeros") +
            alertXml("711502", "naranja", event = "Costeros") +
            alertXml(zona, "amarillo", event = "Lluvias") +
            alertXml("711504", "rojo", event = "Costeros")

        val alerts = service.parseCapAlerts(body, zona)

        assertEquals(1, alerts.size)
        assertEquals("Lluvias", alerts[0].event)
        assertEquals("amarillo", alerts[0].nivel)
    }

    @Test
    fun `no confunde una zona con otra que empiece igual`() {
        // Igualdad, no prefijo: "7115" ya no debe casar con "711503".
        assertTrue(service.parseCapAlerts(alertXml(zona, "amarillo"), "7115").isEmpty())
    }
}
