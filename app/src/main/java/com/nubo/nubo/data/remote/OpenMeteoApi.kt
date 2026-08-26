package com.nubo.nubo.data.remote

import com.nubo.nubo.domain.model.ErrorReason
import org.json.JSONObject

/**
 * Error al hablar con Open-Meteo.
 *
 * Lleva el **motivo** y no una frase: el texto se resuelve en la interfaz, que
 * es la única capa que puede traducirlo.
 */
class OpenMeteoException(
    val reason: ErrorReason,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(reason.name, cause)

/**
 * Predicción de Open-Meteo.
 *
 * Se pide todo —horas y días— en una sola llamada, y se devuelve el JSON crudo
 * porque la caché en disco lo guarda tal cual para no tener que reparsear ni
 * mantener dos formatos.
 */
class OpenMeteoApi(private val http: HttpClient = HttpClient()) {

    suspend fun fetchForecast(lat: Double, lon: Double): JSONObject {
        val url = buildString {
            append(BASE_URL)
            append("/forecast?latitude=").append(lat)
            append("&longitude=").append(lon)
            append("&hourly=").append(HOURLY_FIELDS)
            append("&daily=").append(DAILY_FIELDS)
            // Devuelve las marcas de tiempo en la zona de la localización.
            append("&timezone=auto")
        }

        val response = try {
            http.get(url, timeoutSeconds = TIMEOUT_SECONDS)
        } catch (e: Exception) {
            throw OpenMeteoException(ErrorReason.NETWORK, cause = e)
        }

        if (!response.isSuccess) {
            throw OpenMeteoException(ErrorReason.SERVER, response.code)
        }

        return try {
            JSONObject(response.decodeText())
        } catch (e: Exception) {
            throw OpenMeteoException(ErrorReason.UNREADABLE, cause = e)
        }
    }

    private companion object {
        const val BASE_URL = "https://api.open-meteo.com/v1"
        const val TIMEOUT_SECONDS = 10L

        // `apparent_temperature` y `uv_index` vienen en esta misma llamada:
        // alimentan la tarjeta de condiciones actuales sin coste de red. La
        // calidad del aire, en cambio, vive en otro endpoint (`AirQualityApi`).
        const val HOURLY_FIELDS =
            "temperature_2m,relative_humidity_2m,precipitation_probability," +
                "weather_code,wind_speed_10m,wind_direction_10m,is_day,dew_point_2m," +
                "apparent_temperature,uv_index"

        // Los cuatro últimos alimentan el desplegable de cada día. Cubren los
        // siete días y viajan en esta misma petición: el resumen diario sí
        // existe para UV, sensación y humedad, a diferencia del aire.
        const val DAILY_FIELDS =
            "weather_code,temperature_2m_max,temperature_2m_min," +
                "precipitation_probability_max,uv_index_max," +
                "apparent_temperature_max,apparent_temperature_min," +
                "relative_humidity_2m_mean"
    }
}
