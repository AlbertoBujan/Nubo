package com.nubo.nubo.domain.model

import com.nubo.nubo.domain.weather.DailyCodeAggregator
import com.nubo.nubo.domain.weather.HourSample
import com.nubo.nubo.domain.weather.WeatherCode
import com.nubo.nubo.domain.weather.WeatherCodeGroup
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/** Predicción de un día completo. */
data class DailyForecast(
    val date: LocalDate,
    val tempMax: Int?,
    val tempMin: Int?,
    /** Código WMO representativo del día, ya sin sufijo nocturno. */
    val skyStateCode: String,
    val precipitationProbability: Int?,
    /**
     * Resumen de condiciones del día, para el desplegable.
     *
     * Son campos del bloque `daily` de la misma llamada que ya se hacía, así
     * que cubren los siete días sin coste de red. La calidad del aire no está
     * aquí: vive en otra API que no tiene bloque diario y se agrega aparte.
     */
    val uvIndexMax: Double? = null,
    val apparentMax: Int? = null,
    val apparentMin: Int? = null,
    val humidityMean: Int? = null,
) {
    companion object {

        /**
         * Parsea el bloque `daily` de Open-Meteo.
         *
         * Open-Meteo devuelve en `daily.weather_code` el fenómeno *más
         * significativo* del día, no el más duradero: una hora de nubes en un
         * día despejado convertía el día entero en "intervalos nubosos". Por
         * eso, cuando el JSON trae datos horarios, el código de cada día se
         * recalcula con [DailyCodeAggregator]; si no los trae, se usa el valor
         * de la API tal cual.
         */
        fun fromOpenMeteoJson(json: JSONObject): List<DailyForecast> {
            val daily = json.optJSONObject("daily") ?: return emptyList()

            val time = daily.optJSONArray("time") ?: return emptyList()
            val weatherCode = daily.optJSONArray("weather_code")
            val tempMax = daily.optJSONArray("temperature_2m_max")
            val tempMin = daily.optJSONArray("temperature_2m_min")
            val precipProb = daily.optJSONArray("precipitation_probability_max")
            val uvMax = daily.optJSONArray("uv_index_max")
            val apparentMax = daily.optJSONArray("apparent_temperature_max")
            val apparentMin = daily.optJSONArray("apparent_temperature_min")
            val humidityMean = daily.optJSONArray("relative_humidity_2m_mean")

            val hoursByDay = hourlySamplesByDay(json)
            val forecasts = mutableListOf<DailyForecast>()

            for (i in 0 until time.length()) {
                val raw = time.optString(i)
                val date = parseDate(raw) ?: continue

                val apiCode = weatherCode?.optIntOrNull(i)?.toString()
                val dominant = hoursByDay[dayKey(raw)]
                    ?.let { DailyCodeAggregator.dominantCode(it) }
                    ?.toString()
                val code = dominant ?: apiCode

                forecasts += DailyForecast(
                    date = date,
                    tempMax = tempMax?.optDoubleOrNull(i)?.roundToInt(),
                    tempMin = tempMin?.optDoubleOrNull(i)?.roundToInt(),
                    skyStateCode = code ?: "",
                    precipitationProbability = precipProb?.optDoubleOrNull(i)?.roundToInt(),
                    uvIndexMax = uvMax?.optDoubleOrNull(i),
                    apparentMax = apparentMax?.optDoubleOrNull(i)?.roundToInt(),
                    apparentMin = apparentMin?.optDoubleOrNull(i)?.roundToInt(),
                    humidityMean = humidityMean?.optDoubleOrNull(i)?.roundToInt(),
                )
            }

            return forecasts
        }

        /**
         * Agrupa las horas del bloque `hourly` por día.
         *
         * Se leen los arrays crudos en vez de reutilizar [HourlyForecast]
         * porque este descarta las horas ya pasadas, y aquí interesa el día
         * completo.
         */
        private fun hourlySamplesByDay(json: JSONObject): Map<String, List<HourSample>> {
            val hourly = json.optJSONObject("hourly") ?: return emptyMap()
            val time = hourly.optJSONArray("time") ?: return emptyMap()
            val weatherCode = hourly.optJSONArray("weather_code") ?: return emptyMap()
            val isDay = hourly.optJSONArray("is_day")

            val result = mutableMapOf<String, MutableList<HourSample>>()

            for (i in 0 until time.length()) {
                if (i >= weatherCode.length()) break
                val code = weatherCode.optIntOrNull(i) ?: continue

                // Sin dato de is_day se asume hora diurna: no distorsiona el
                // peso relativo mientras se aplique igual a todas las horas.
                val daytime = if (isDay != null && i < isDay.length()) {
                    isDay.optInt(i, 1) != 0
                } else {
                    true
                }

                result.getOrPut(dayKey(time.optString(i))) { mutableListOf() }
                    .add(HourSample(code, daytime))
            }

            return result
        }

        /**
         * Clave de agrupación por día: los 10 primeros caracteres.
         *
         * Open-Meteo devuelve todas las marcas en la zona horaria de la
         * localización (`timezone=auto`), así que comparar el prefijo textual
         * evita reconstruir fechas y no introduce desfases de huso.
         */
        private fun dayKey(timestamp: String): String =
            if (timestamp.length >= 10) timestamp.substring(0, 10) else timestamp

        private fun parseDate(raw: String?): LocalDate? {
            if (raw.isNullOrBlank()) return null
            return try {
                LocalDate.parse(raw.substring(0, minOf(10, raw.length)))
            } catch (_: DateTimeParseException) {
                null
            } catch (_: StringIndexOutOfBoundsException) {
                null
            }
        }
    }

    /** Grupo de severidad al que pertenece el día. */
    val group: WeatherCodeGroup get() = WeatherCodeGroup.fromCode(skyStateCode)
}
