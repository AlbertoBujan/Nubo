package com.nubo.nubo.data.remote

import com.nubo.nubo.domain.model.SavedLocation
import org.json.JSONObject

/**
 * Nombre del sitio a partir de unas coordenadas, para el botón de ubicación
 * actual.
 *
 * Open-Meteo solo geocodifica en directo, así que la inversa hay que pedirla a
 * otro sitio. BigDataCloud tiene un endpoint gratuito, sin clave y pensado
 * para llamarse desde el cliente, que es lo que encaja aquí: antes esto lo
 * resolvía el maestro de AEMET buscando el municipio más cercano, y eso solo
 * funcionaba dentro de España.
 *
 * No devuelve zona horaria; da igual, porque la autoritativa es la que viene
 * en la respuesta de la predicción.
 */
class ReverseGeocodingApi(private val http: HttpClient = HttpClient()) {

    /** Sitio habitado más cercano a unas coordenadas, o `null`. */
    suspend fun findPlace(lat: Double, lon: Double): SavedLocation? {
        val url = "$BASE_URL/data/reverse-geocode-client" +
            "?latitude=$lat&longitude=$lon&localityLanguage=${apiLanguage()}"

        val response = runCatching { http.get(url, timeoutSeconds = TIMEOUT_SECONDS) }
            .getOrNull() ?: return null
        if (!response.isSuccess) return null

        return runCatching { parsePlace(JSONObject(response.decodeText()), lat, lon) }
            .getOrNull()
    }

    internal companion object {
        const val BASE_URL = "https://api.bigdatacloud.net"
        const val TIMEOUT_SECONDS = 10L

        fun parsePlace(json: JSONObject, lat: Double, lon: Double): SavedLocation? {
            // `city` viene vacío en zonas rurales, donde el nombre útil es la
            // `locality`; en ciudades pasa al revés con la pedanía.
            val name = firstNonBlank(json, "city", "locality", "principalSubdivision")
                ?: return null

            return SavedLocation(
                locationId = idFor(lat, lon),
                nombre = name,
                lat = lat,
                lon = lon,
                countryCode = json.optString("countryCode").takeIf { it.isNotBlank() },
                region = firstNonBlank(json, "principalSubdivision", "countryName"),
            )
        }

        /**
         * Identificador de una posición.
         *
         * Se redondea a cuatro decimales —unos 11 m— para que volver a pulsar
         * el botón de ubicación no cree una entrada duplicada por haberse
         * movido el usuario un par de metros.
         */
        fun idFor(lat: Double, lon: Double): String =
            "$ID_PREFIX%.4f,%.4f".format(java.util.Locale.US, lat, lon)

        const val ID_PREFIX = "geo:"

        private fun firstNonBlank(json: JSONObject, vararg keys: String): String? =
            keys.firstNotNullOfOrNull { json.optString(it).takeIf { v -> v.isNotBlank() } }
    }
}
