package com.nubo.nubo.domain.model

import org.json.JSONObject
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Calidad del aire de una hora concreta.
 *
 * Se guarda la serie horaria y no solo el valor de ahora, aunque la tarjeta
 * enseñe uno solo, por la misma razón que la predicción: la caché persiste el
 * JSON crudo y al releerla horas después hay que poder volver a elegir la hora
 * en curso. Con un único valor "actual", una caché de la mañana enseñaría por
 * la tarde un dato viejo dándolo por bueno.
 */
data class AirQualityForecast(
    val dateTime: LocalDateTime,
    val europeanAqi: Int,
) {
    companion object {

        /**
         * Parsea el bloque `hourly` de la API de calidad del aire.
         *
         * Las horas sin dato se descartan en vez de rellenarse: fuera de Europa
         * la resolución del modelo es peor y algún hueco es esperable.
         */
        fun fromOpenMeteoJson(json: JSONObject): List<AirQualityForecast> {
            val hourly = json.optJSONObject("hourly") ?: return emptyList()
            val time = hourly.optJSONArray("time") ?: return emptyList()
            val aqi = hourly.optJSONArray("european_aqi") ?: return emptyList()

            val samples = mutableListOf<AirQualityForecast>()
            for (i in 0 until time.length()) {
                val date = HourlyForecast.parseLocalDateTime(time.optString(i)) ?: continue
                if (i >= aqi.length() || aqi.isNull(i)) continue
                samples += AirQualityForecast(date, aqi.optDouble(i).roundToInt())
            }
            return samples
        }
    }
}
