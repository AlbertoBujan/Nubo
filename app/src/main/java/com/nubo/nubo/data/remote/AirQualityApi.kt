package com.nubo.nubo.data.remote

import com.nubo.nubo.domain.model.ErrorReason
import org.json.JSONObject

/**
 * Calidad del aire de Open-Meteo.
 *
 * Va en un endpoint distinto al de la predicción, así que es **una segunda
 * petición por ubicación**. Se pide en paralelo con el tiempo y su fallo no
 * invalida nada: sin este dato la tarjeta enseña un guion, igual que los avisos
 * fuera de España.
 *
 * Se piden siete días para poder resumir cada uno en su desplegable, aunque
 * el modelo CAMS solo llega a unos cinco: los últimos vienen vacíos y esas
 * casillas enseñan un guion. El payload sigue siendo de unos pocos kilobytes.
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
            throw OpenMeteoException(ErrorReason.SERVER, response.code)
        }

        return JSONObject(response.decodeText())
    }

    private companion object {
        const val BASE_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"
        const val FORECAST_DAYS = 7
        const val TIMEOUT_SECONDS = 15L
    }
}
