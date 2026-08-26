package com.nubo.nubo.data.remote

import com.nubo.nubo.domain.model.SavedLocation
import org.json.JSONArray
import org.json.JSONObject

/**
 * Búsqueda de localizaciones contra la geocodificación de Open-Meteo.
 *
 * Sustituye al maestro de municipios de AEMET, que solo cubría España. Es
 * gratuita, no lleva clave y devuelve de una vez lo que la app necesita para
 * funcionar luego sin red: coordenadas, país y **zona horaria**.
 *
 * A diferencia del maestro, no se descarga entera: son ~8.000 municipios
 * frente a millones de sitios en el mundo, así que se consulta por término de
 * búsqueda.
 */
class GeocodingApi(private val http: HttpClient = HttpClient()) {

    /** Localizaciones cuyo nombre empieza por [query]. Nunca lanza. */
    suspend fun search(query: String, limit: Int = MAX_RESULTS): List<SavedLocation> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY) return emptyList()

        val url = buildString {
            append(BASE_URL).append("/search?name=").append(encode(trimmed))
            append("&count=").append(limit)
            // En el idioma de la app. En español devuelve la forma oficial
            // larga de cada división ("Comunidad Autónoma de Galicia") y en
            // inglés otra distinta ("Province of A Coruña"); las dos se podan
            // en `trimAdminPrefix`, que por eso tiene reglas de ambos idiomas.
            append("&language=").append(apiLanguage()).append("&format=json")
        }

        val response = runCatching { http.get(url, timeoutSeconds = TIMEOUT_SECONDS) }
            .getOrNull() ?: return emptyList()
        if (!response.isSuccess) return emptyList()

        return runCatching {
            val results = JSONObject(response.decodeText()).optJSONArray("results")
            parseResults(results)
        }.getOrDefault(emptyList())
    }

    internal companion object {
        const val BASE_URL = "https://geocoding-api.open-meteo.com/v1"
        const val TIMEOUT_SECONDS = 10L
        /**
         * Candidatos que se piden.
         *
         * Es el máximo que admite Open-Meteo, y hace falta llegar tan lejos
         * para que "buscar cerca" tenga con qué trabajar: en "Santa Cruz" el
         * primer resultado gallego está en el puesto 38, porque el servicio
         * ordena por población. Son unos 34 KB por consulta, con antirrebote.
         */
        const val MAX_RESULTS = 100

        /** Open-Meteo ignora las consultas de una sola letra. */
        const val MIN_QUERY = 2

        fun parseResults(results: JSONArray?): List<SavedLocation> {
            if (results == null) return emptyList()

            val parsed = ArrayList<SavedLocation>(results.length())
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                parsed += parseResult(item) ?: continue
            }
            return parsed
        }

        /**
         * Una entrada del resultado.
         *
         * Sin coordenadas la localización no sirve para nada —ni predicción ni
         * sol— así que se descarta en vez de guardarse a medias.
         */
        fun parseResult(item: JSONObject): SavedLocation? {
            val id = item.optLong("id").takeIf { it != 0L } ?: return null
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: return null
            if (!item.has("latitude") || !item.has("longitude")) return null

            return SavedLocation(
                locationId = "$ID_PREFIX$id",
                nombre = name,
                lat = item.getDouble("latitude"),
                lon = item.getDouble("longitude"),
                // Sin zona horaria las horas se interpretarían en la del
                // teléfono, que es justo el fallo que esto viene a evitar.
                timeZone = item.optString("timezone").takeIf { it.isNotBlank() },
                countryCode = item.optString("country_code").takeIf { it.isNotBlank() },
                region = regionOf(item),
            )
        }

        /**
         * Texto que distingue dos sitios homónimos en la lista: provincia,
         * región y país.
         *
         * Buscar "Curtis" devuelve el de A Coruña y el de Nebraska: sin esto
         * la lista mostraría dos filas idénticas. Y con la región sola no
         * basta, porque dentro de Galicia hay varios "Santa Cruz".
         *
         * Se poda lo que se repite —una provincia que se llama igual que el
         * sitio o que su región— y el relleno administrativo. Sin podar,
         * Tenerife sería "Provincia de Santa Cruz de Tenerife, Comunidad
         * Autónoma de Canarias, España": 75 caracteres para decir "Canarias".
         */
        fun regionOf(item: JSONObject): String? {
            val name = item.optString("name")
            val province = trimAdminPrefix(item.optString("admin2"))
            val region = trimAdminPrefix(item.optString("admin1"))
            val country = item.optString("country").takeIf { it.isNotBlank() }

            return listOfNotNull(
                province?.takeIf { it != name && it != region },
                region?.takeIf { it != name },
                country?.takeIf { it != region },
            ).joinToString(", ").takeIf { it.isNotBlank() }
        }

        /**
         * Quita el encabezado administrativo de una división.
         *
         * Open-Meteo las devuelve con el nombre largo y oficial, y ahí el dato
         * útil es siempre la última parte. Una división que no empiece por
         * ninguno de los encabezados conocidos se deja intacta.
         */
        fun trimAdminPrefix(raw: String?): String? {
            val value = raw?.trim().orEmpty()
            if (value.isBlank()) return null

            val prefix = ADMIN_PREFIXES.firstOrNull { value.startsWith(it, ignoreCase = true) }
            if (prefix != null) {
                // Si detrás del encabezado no queda nada, el encabezado *era*
                // el nombre de la división y hay que dejarlo.
                val rest = value.removeRange(0, prefix.length).trim()
                    .takeIf { it.isNotBlank() } ?: return value
                return dropLowercaseArticle(rest)
            }

            val suffix = ADMIN_SUFFIXES.firstOrNull { value.endsWith(it, ignoreCase = true) }
                ?: return value

            return value.dropLast(suffix.length).trim().takeIf { it.isNotBlank() } ?: value
        }

        /**
         * Quita el artículo solo si va en minúscula.
         *
         * La mayúscula es lo que distingue a quién pertenece: en "Provincia de
         * **las** Islas Baleares" el artículo es del encabezado y sobra, pero
         * en "Provincia de **La** Coruña" es del topónimo y quitarlo dejaría
         * la provincia en "Coruña".
         */
        fun dropLowercaseArticle(value: String): String {
            val article = ARTICLES.firstOrNull { value.startsWith(it) } ?: return value
            return value.removeRange(0, article.length).trim().takeIf { it.isNotBlank() } ?: value
        }

        /** En minúscula a propósito: la comparación es sensible a mayúsculas. */
        val ARTICLES = listOf("las ", "los ", "la ", "el ")

        /** Ordenados de más largo a más corto: "Estado de " antes que "Estado ". */
        val ADMIN_PREFIXES = listOf(
            "Comunidad Autónoma del ",
            "Comunidad Autónoma de ",
            "Provincia del ",
            "Provincia de ",
            "Provincia ",
            "Región del ",
            "Región de ",
            "Estado del ",
            "Estado de ",
            "Estado ",
            "Principado de ",
            "Departamento del ",
            "Departamento de ",
            "Condado de ",
            "Municipio de ",
            "Province of ",
            "County of ",
            "State of ",
            "Region of ",
        )

        /**
         * Encabezados que en inglés van detrás.
         *
         * Deliberadamente corta: " State" se lleva por delante "Free State"
         * —una provincia sudafricana— y " Region" a cualquier "… Region" que
         * sea nombre propio. Solo entran las que no dejan un nombre cojo.
         */
        val ADMIN_SUFFIXES = listOf(
            " County",
            " Department",
            " Province",
            " Municipality",
        )

        /** Prefijo que distingue estos ids de los códigos INE heredados. */
        const val ID_PREFIX = "om:"

        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8")
    }
}
