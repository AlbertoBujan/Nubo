package com.nubo.nubo.data.remote

import org.json.JSONObject

/**
 * Calidad del aire de Open-Meteo.
 *
 * Va en un endpoint distinto al de la predicción, así que es **una segunda
 * petición por ubicación**. Se pide en paralelo con el tiempo y su fallo no
 * invalida nada: sin este dato la tarjeta enseña un guion, igual que los avisos
 * fuera de España.
 *
 * Se piden solo dos días de índice europeo porque es todo lo que se enseña, y
 * el payload se queda en algo más de un kilobyte.
 */
class AirQualityApi(private val http: HttpClient = HttpClient()) {

    suspend fun fetchAirQuality(lat: Double, lon: Double): JSONObject {
        val url = buildString {
            append(BASE_URL)
            append("?latitude=").append(lat)
            append("&longitude=").append(lon)
            append("&hourly=european_aqi")
            append("&forecast_days=").append(FORECAST_DAYS)
            append("&timezone=auto")
        }

        val response = http.get(url, timeoutSeconds = TIMEOUT_SECONDS)
        if (!response.isSuccess) {
            throw OpenMeteoException(
                "Calidad del aire no disponible. Código ${response.code}",
                response.code,
            )
        }

        return JSONObject(response.decodeText())
    }

    private companion object {
        const val BASE_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"
        const val FORECAST_DAYS = 2
        const val TIMEOUT_SECONDS = 15L
    }
}
