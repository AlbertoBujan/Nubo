package com.nubo.nubo.data.remote

import com.nubo.nubo.domain.model.WeatherAlert
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Avisos meteorológicos de AEMET en formato CAP.
 *
 * Dos detalles del servicio real condicionan este código:
 *
 * 1. AEMET no devuelve un XML por aviso, sino **varios `<alert>` concatenados**
 *    en un mismo cuerpo (restos de un tar servido en línea). Un parser XML se
 *    atraganta con eso porque no hay un único elemento raíz, así que primero se
 *    trocea por expresión regular y luego se parsea cada bloque por separado.
 * 2. Los avisos se piden por área (comunidad autónoma), así que hay que filtrar
 *    después por provincia mirando el `geocode` de cada zona.
 */
class AlertService(
    private val aemet: AemetApi = AemetApi(),
) {

    /**
     * Avisos vigentes para un municipio, identificado por su código INE.
     *
     * Nunca lanza: si AEMET falla, devuelve lista vacía. Quedarse sin avisos es
     * preferible a que la pantalla del tiempo no cargue.
     */
    suspend fun fetchAlerts(municipioId: String): List<WeatherAlert> {
        val provincia = AemetAreas.provinciaOf(municipioId) ?: return emptyList()
        val area = AemetAreas.areaForMunicipio(municipioId) ?: return emptyList()

        return try {
            val body = aemet.fetchData(
                "/api/avisos_cap/ultimoelaborado/area/$area",
                timeoutSeconds = TIMEOUT_SECONDS,
            ) ?: return emptyList()

            parseCapAlerts(body, provincia, area)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Extrae los avisos de un cuerpo con uno o más bloques `<alert>`.
     *
     * Solo conserva los que afectan a [provincia], están en español y no son
     * de nivel verde (que en el CAP de AEMET significa "sin aviso").
     */
    internal fun parseCapAlerts(
        rawContent: String,
        provincia: String,
        area: String,
    ): List<WeatherAlert> {
        val alerts = mutableListOf<WeatherAlert>()

        // El geocode de una zona empieza por {área}{provincia}.
        // Ej.: Galicia = 71 y A Coruña = 15 → "7115".
        val geocodePrefix = "$area$provincia"

        for (match in ALERT_BLOCK.findAll(rawContent)) {
            // Un bloque ilegible no debe invalidar los demás: se descarta y
            // se sigue con el siguiente.
            val parsed = runCatching {
                val xml = """<?xml version="1.0" encoding="UTF-8"?>${match.value}"""
                parseAlertBlock(xml, geocodePrefix)
            }.getOrNull() ?: continue

            alerts.addAll(parsed)
        }

        return alerts
    }

    private fun parseAlertBlock(xml: String, geocodePrefix: String): List<WeatherAlert>? {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // El payload viene de un tercero: se cierran las entidades externas.
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
        }

        val document = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        val result = mutableListOf<WeatherAlert>()

        val infos = document.getElementsByTagName("info")
        for (i in 0 until infos.length) {
            val info = infos.item(i) as? Element ?: continue

            // Cada aviso se publica en varios idiomas; basta el español.
            val language = info.childText("language") ?: ""
            if (!language.startsWith("es")) continue

            parseInfoElement(info, geocodePrefix)?.let { result += it }
        }

        return result.ifEmpty { null }
    }

    private fun parseInfoElement(info: Element, geocodePrefix: String): WeatherAlert? {
        if (!matchesProvincia(info, geocodePrefix)) return null

        // El nivel y la probabilidad viajan como <parameter> con nombre libre.
        var nivel = ""
        var probability = ""
        info.eachChild("parameter") { parameter ->
            val name = parameter.childText("valueName").orEmpty()
            val value = parameter.childText("value").orEmpty()
            if (name.contains("nivel", ignoreCase = true)) nivel = value
            if (name.contains("probabilidad", ignoreCase = true)) probability = value
        }

        // "verde" no es un aviso: significa que ese fenómeno está descartado.
        if (nivel.equals("verde", ignoreCase = true)) return null

        val event = info.childText("event").orEmpty()
        val headline = info.childText("headline").orEmpty()
        if (event.isEmpty() && headline.isEmpty()) return null

        val alert = WeatherAlert(
            nivel = nivel,
            event = event,
            headline = headline,
            description = info.childText("description").orEmpty(),
            instruction = info.childText("instruction").orEmpty(),
            areaDescription = info.firstChild("area")?.childText("areaDesc").orEmpty(),
            onset = WeatherAlert.parseDateTime(info.childText("onset")),
            expires = WeatherAlert.parseDateTime(info.childText("expires")),
            probability = probability,
        )

        return alert.takeIf { it.isActiveOrUpcoming }
    }

    /** Comprueba si alguna zona del aviso cae en la provincia buscada. */
    private fun matchesProvincia(info: Element, geocodePrefix: String): Boolean {
        var matches = false
        info.eachChild("area") { area ->
            area.eachChild("geocode") { geocode ->
                val value = geocode.childText("value").orEmpty()
                if (value.startsWith(geocodePrefix)) matches = true
            }
        }
        return matches
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L

        /**
         * Cada aviso completo.
         *
         * El lookahead impide que el contenido de un bloque incluya la apertura
         * de otro. Sin él, un `<alert>` truncado se tragaría el siguiente aviso
         * —que sí es válido— hasta el primer `</alert>` que encontrase, y se
         * perderían los dos. Así un bloque corrupto solo se pierde a sí mismo.
         */
        val ALERT_BLOCK = Regex("""<alert[^>]*>(?:(?!<alert[\s>])[\s\S])*?</alert>""")
    }
}

// ── Utilidades mínimas sobre el DOM ─────────────────────────────────────────

/** Texto del primer descendiente con ese nombre, o `null`. */
private fun Element.childText(tag: String): String? =
    firstChild(tag)?.textContent?.trim()

private fun Element.firstChild(tag: String): Element? {
    val nodes = getElementsByTagName(tag)
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node.nodeType == Node.ELEMENT_NODE) return node as Element
    }
    return null
}

private inline fun Element.eachChild(tag: String, action: (Element) -> Unit) {
    val nodes = getElementsByTagName(tag)
    for (i in 0 until nodes.length) {
        (nodes.item(i) as? Element)?.let(action)
    }
}
