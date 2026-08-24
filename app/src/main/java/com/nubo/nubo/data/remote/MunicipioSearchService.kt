package com.nubo.nubo.data.remote

import com.nubo.nubo.domain.model.SavedLocation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Coordenadas de un municipio. */
data class Coordinates(val lat: Double, val lon: Double)

/**
 * Búsqueda de municipios españoles contra el maestro de AEMET.
 *
 * El listado completo (unos 8.000 municipios) se descarga una sola vez y se
 * mantiene en memoria, de modo que el autocompletado no toque la red en cada
 * pulsación.
 */
class MunicipioSearchService(
    private val aemet: AemetApi = AemetApi(),
) {
    private var municipios: List<Municipio> = emptyList()
    private var loaded = false

    // Evita que varias búsquedas simultáneas lancen varias descargas.
    private val loadMutex = Mutex()

    /** Una entrada del maestro, ya normalizada. */
    internal data class Municipio(
        val id: String,
        val nombre: String,
        /**
         * Formas contra las que se busca.
         *
         * Son dos porque AEMET pospone el artículo ("Palmas de Gran Canaria,
         * Las") pero la app muestra la forma natural ("Las Palmas de Gran
         * Canaria"). Indexando solo la cruda, buscar "Las Palmas" no encontraba
         * nada; indexando solo la mostrada, fallaría quien escriba "Palmas,".
         */
        val searchable: List<String>,
        val lat: Double?,
        val lon: Double?,
        /**
         * Zona de aviso de AEMET a la que pertenece el municipio.
         *
         * Seis dígitos: área (2) + provincia (2) + zona dentro de la provincia
         * (2). Es el mismo código que viaja en el `geocode` de cada aviso CAP,
         * así que es lo que permite quedarse solo con los avisos que de verdad
         * afectan al municipio. Ver [AlertService].
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
            val nombre = cleanNombre(item.optString("nombre"))
            if (nombre.isBlank()) continue

            val display = SavedLocation.formatNombre(nombre)
            result += Municipio(
                // El maestro trae el id con prefijo, p. ej. "id28079".
                id = rawId.removePrefix("id"),
                nombre = nombre,
                searchable = listOf(normalize(nombre), normalize(display)).distinct(),
                lat = parseDecimal(item, "latitud_dec"),
                lon = parseDecimal(item, "longitud_dec"),
                zonaAviso = parseZonaAviso(item),
            )
        }
        return result
    }

    /** Hasta 10 municipios cuyo nombre contenga [query], sin distinguir tildes. */
    suspend fun searchByName(query: String): List<SavedLocation> {
        if (query.isBlank()) return emptyList()
        ensureLoaded()

        val q = normalize(query)
        return municipios
            .asSequence()
            .filter { municipio -> municipio.searchable.any { it.contains(q) } }
            .take(MAX_RESULTS)
            .map { SavedLocation.of(it.id, it.nombre) }
            .toList()
    }

    /** Municipio más próximo a unas coordenadas. */
    suspend fun findNearestMunicipio(lat: Double, lon: Double): SavedLocation? {
        ensureLoaded()

        var nearest: Municipio? = null
        var minDistance = Double.MAX_VALUE

        for (municipio in municipios) {
            val mLat = municipio.lat ?: continue
            val mLon = municipio.lon ?: continue

            // Distancia euclídea en grados: no es geodésica, pero para comparar
            // municipios dentro de España el orden que produce es el mismo.
            val dLat = lat - mLat
            val dLon = lon - mLon
            val distance = dLat * dLat + dLon * dLon

            if (distance < minDistance) {
                minDistance = distance
                nearest = municipio
            }
        }

        return nearest?.let { SavedLocation.of(it.id, it.nombre) }
    }

    /**
     * Zona de aviso de AEMET de un municipio, o `null` si el maestro no la
     * trae o no se pudo descargar.
     */
    suspend fun getZonaAviso(municipioId: String): String? {
        if (municipioId.isBlank()) return null
        ensureLoaded()

        val id = municipioId.removePrefix("id")
        return municipios.firstOrNull { it.id == id }?.zonaAviso
    }

    /** Coordenadas de un municipio por su código INE. */
    suspend fun getCoordinates(municipioId: String): Coordinates? {
        if (municipioId.isBlank()) return null
        ensureLoaded()

        val id = municipioId.removePrefix("id")
        val municipio = municipios.firstOrNull { it.id == id } ?: return null
        val lat = municipio.lat ?: return null
        val lon = municipio.lon ?: return null
        return Coordinates(lat, lon)
    }

    internal companion object {
        const val MAX_RESULTS = 10

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

        /** AEMET publica los decimales con coma en algunos registros. */
        fun parseDecimal(item: JSONObject, key: String): Double? =
            item.optString(key).takeIf { it.isNotBlank() }
                ?.replace(',', '.')
                ?.toDoubleOrNull()

        /** Quita el paréntesis final con la provincia, si lo trae. */
        fun cleanNombre(nombre: String): String =
            nombre.replace(Regex("""\s*\(.*?\)\s*$"""), "").trim()

        private val ACCENTS = mapOf(
            'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a',
            'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e',
            'í' to 'i', 'ì' to 'i', 'ï' to 'i', 'î' to 'i',
            'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o',
            'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u',
            'ñ' to 'n', 'ç' to 'c',
        )

        /** Minúsculas y sin tildes, para que "Coruna" encuentre "A Coruña". */
        fun normalize(s: String): String =
            s.lowercase().map { ACCENTS[it] ?: it }.joinToString("")
    }
}
