package com.nubo.nubo.data.remote

import com.nubo.nubo.domain.model.WeatherAlert
import java.time.LocalDateTime
import java.time.ZoneId
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
 *    después mirando el `geocode` de cada zona.
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
    suspend fun fetchAlerts(zonaAviso: String, zone: ZoneId): List<WeatherAlert> {
        // Sin zona no se puede filtrar, y devolver los de toda la comunidad
        // sería peor que no devolver ninguno.
        if (!ZONA_AVISO.matches(zonaAviso)) return emptyList()

        // Los dos primeros dígitos de la zona son el área por la que pregunta
        // el servicio, así que no hace falta ninguna tabla aparte.
        val area = zonaAviso.take(2)

        return try {
            val body = aemet.fetchData(
                "/api/avisos_cap/ultimoelaborado/area/$area",
                timeoutSeconds = TIMEOUT_SECONDS,
            ) ?: return emptyList()

            parseCapAlerts(body, zonaAviso, zone)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Extrae los avisos de un cuerpo con uno o más bloques `<alert>`.
     *
     * Solo conserva los de [zonaAviso], en español y de nivel distinto de
     * verde (que en el CAP de AEMET significa "sin aviso").
     */
    internal fun parseCapAlerts(
        rawContent: String,
        zonaAviso: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<WeatherAlert> {
        val alerts = mutableListOf<WeatherAlert>()

        for (match in ALERT_BLOCK.findAll(rawContent)) {
            // Un bloque ilegible no debe invalidar los demás: se descarta y
            // se sigue con el siguiente.
            val parsed = runCatching {
                val xml = """<?xml version="1.0" encoding="UTF-8"?>${match.value}"""
                parseAlertBlock(xml, zonaAviso, zone)
            }.getOrNull() ?: continue

            alerts.addAll(parsed)
        }

        return alerts
    }

    private fun parseAlertBlock(
        xml: String,
        zonaAviso: String,
        zone: ZoneId,
    ): List<WeatherAlert>? {
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

            parseInfoElement(info, zonaAviso, zone)?.let { result += it }
        }

        return result.ifEmpty { null }
    }

    private fun parseInfoElement(
        info: Element,
        zonaAviso: String,
        zone: ZoneId,
    ): WeatherAlert? {
        if (!matchesZona(info, zonaAviso)) return null

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
            onset = WeatherAlert.parseDateTime(info.childText("onset"), zone),
            expires = WeatherAlert.parseDateTime(info.childText("expires"), zone),
            probability = probability,
        )

        return alert.takeIf { it.isActiveAt(LocalDateTime.now(zone)) }
    }

    /**
     * Comprueba si el aviso cubre exactamente la zona del municipio.
     *
     * La comparación es de igualdad, no de prefijo. Filtrando por los cuatro
     * primeros dígitos —área y provincia— entraban los avisos de **todas** las
     * zonas de la provincia: A Coruña tiene cuatro, y a un municipio del
     * interior le llegaban los avisos costeros de las otras tres.
     */
    private fun matchesZona(info: Element, zonaAviso: String): Boolean {
        var matches = false
        info.eachChild("area") { area ->
            area.eachChild("geocode") { geocode ->
                if (geocode.childText("value") == zonaAviso) matches = true
            }
        }
        return matches
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L

        /** Área (2) + provincia (2) + zona (2). */
        val ZONA_AVISO = Regex("""\d{6}""")

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
