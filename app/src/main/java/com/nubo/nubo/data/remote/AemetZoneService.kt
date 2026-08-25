package com.nubo.nubo.data.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Coordenadas de un punto. */
data class Coordinates(val lat: Double, val lon: Double)

/**
 * Zona de aviso de AEMET a partir del maestro de municipios.
 *
 * Antes esta clase era además el buscador de la app, pero desde que las
 * localizaciones vienen de Open-Meteo el maestro solo sirve para dos cosas,
 * ambas exclusivas de España:
 *
 * 1. Resolver la **zona de aviso** de un punto, que es lo único que permite
 *    filtrar los avisos CAP. Ver [AlertService].
 * 2. Rellenar las coordenadas de las ciudades guardadas en el formato
 *    anterior, que solo tenían el código INE.
 *
 * Los ~8.000 municipios se descargan una vez y se quedan en memoria. La
 * descarga es perezosa: quien nunca mire una ciudad española no la paga.
 */
class AemetZoneService(
    private val aemet: AemetApi = AemetApi(),
) {
    private var municipios: List<Municipio> = emptyList()
    private var loaded = false

    // Evita que varias consultas simultáneas lancen varias descargas.
    private val loadMutex = Mutex()

    /** Una entrada del maestro, con lo justo que se usa. */
    internal data class Municipio(
        val id: String,
        val lat: Double?,
        val lon: Double?,
        /**
         * Zona de aviso: área (2) + provincia (2) + zona (2). Es el mismo
         * código que viaja en el `geocode` de cada aviso CAP.
         */
        val zonaAviso: String?,
    )

    private suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return

            val body = try {
                aemet.fetchData("/api/maestro/municipios", timeoutSeconds = 30)
            } catch (_: Exception) {
                null
            } ?: return

            municipios = try {
                parseMaestro(JSONArray(body))
            } catch (_: Exception) {
                return
            }
            loaded = true
        }
    }

    internal fun parseMaestro(array: JSONArray): List<Municipio> {
        val result = ArrayList<Municipio>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val rawId = item.optString("id").takeIf { it.isNotBlank() } ?: continue

            result += Municipio(
                // El maestro trae el id con prefijo, p. ej. "id28079".
                id = rawId.removePrefix("id"),
                lat = parseDecimal(item, "latitud_dec"),
                lon = parseDecimal(item, "longitud_dec"),
                zonaAviso = parseZonaAviso(item),
            )
        }
        return result
    }

    /** Zona de aviso del municipio con ese código INE. */
    suspend fun zonaForIne(ineId: String): String? {
        if (ineId.isBlank()) return null
        ensureLoaded()
        return municipios.firstOrNull { it.id == ineId }?.zonaAviso
    }

    /**
     * Zona de aviso del municipio más cercano a unas coordenadas.
     *
     * Es como se resuelve la zona de una localización que viene de
     * Open-Meteo, que no trae código INE. Las zonas de AEMET son grandes
     * —A Coruña entera son cuatro—, así que el municipio más próximo cae en
     * la misma salvo justo en el borde.
     */
    suspend fun zonaNear(lat: Double, lon: Double): String? {
        ensureLoaded()
        return nearest(lat, lon)?.zonaAviso
    }

    /** Coordenadas del municipio con ese código INE. */
    suspend fun coordinatesForIne(ineId: String): Coordinates? {
        if (ineId.isBlank()) return null
        ensureLoaded()
        val municipio = municipios.firstOrNull { it.id == ineId } ?: return null
        val lat = municipio.lat ?: return null
        val lon = municipio.lon ?: return null
        return Coordinates(lat, lon)
    }

    internal fun nearest(lat: Double, lon: Double): Municipio? {
        var nearest: Municipio? = null
        var minDistance = Double.MAX_VALUE

        for (municipio in municipios) {
            val mLat = municipio.lat ?: continue
            val mLon = municipio.lon ?: continue

            // Distancia euclídea en grados: no es geodésica, pero para
            // comparar municipios dentro de España el orden que produce es el
            // mismo.
            val dLat = lat - mLat
            val dLon = lon - mLon
            val distance = dLat * dLat + dLon * dLon

            if (distance < minDistance) {
                minDistance = distance
                nearest = municipio
            }
        }
        return nearest
    }

    internal companion object {
        /** AEMET publica los decimales con coma en algunos registros. */
        fun parseDecimal(item: JSONObject, key: String): Double? =
            item.optString(key).takeIf { it.isNotBlank() }
                ?.replace(',', '.')
                ?.toDoubleOrNull()

        /**
         * Lee `zona_comarcal` del maestro, exigiendo los 6 dígitos.
         *
         * Se valida el formato en vez de aceptar lo que venga porque de este
         * campo depende qué avisos se muestran: un valor raro debe dejar al
         * municipio sin avisos, nunca colarle los de otra zona.
         */
        fun parseZonaAviso(item: JSONObject): String? =
            item.optString("zona_comarcal").trim().takeIf { ZONA_AVISO.matches(it) }

        /** Área (2) + provincia (2) + zona (2). */
        val ZONA_AVISO = Regex("""\d{6}""")
    }
}
