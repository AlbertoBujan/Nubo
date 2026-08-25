package com.nubo.nubo.domain.model

import com.nubo.nubo.domain.weather.WeatherCode
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlin.math.floor
import kotlin.math.roundToInt

/** Predicción de una hora concreta. */
data class HourlyForecast(
    val dateTime: LocalDateTime,
    val temperature: Int?,
    /** Código WMO, con sufijo 'n' si esa hora es nocturna. */
    val skyStateCode: String,
    val skyDescription: String,
    val precipitationProbability: Int?,
    val humidity: Int?,
    val windSpeed: Int?,
    val windDirection: String?,
    val windDirectionDegrees: Int?,
    val dewPoint: Int?,
    /** Sensación térmica. Viene en la misma llamada que la temperatura. */
    val apparentTemperature: Int? = null,
    /** Índice ultravioleta, con decimales tal y como lo da Open-Meteo. */
    val uvIndex: Double? = null,
) {
    companion object {

        /**
         * Horas que se conservan de la predicción.
         *
         * Open-Meteo entrega siete días hora a hora, pero pasadas un par de
         * jornadas el detalle horario ya no dice nada que no diga el resumen
         * del día: la incertidumbre a esa distancia es mayor que la diferencia
         * entre una hora y la siguiente. Recortar aquí es lo que evita además
         * un carrusel de veinticinco pantallas.
         *
         * Los siete días siguen estando: "Próximos días" no sale de esta lista
         * sino del bloque `hourly` del JSON, que [DailyForecast] agrega por su
         * cuenta y entero.
         *
         * Son ocho bloques justos de los que muestra el carrusel.
         */
        const val MAX_HOURS = 48

        /**
         * Parsea el bloque `hourly` de Open-Meteo, que viene en arrays
         * paralelos: una entrada por hora en cada campo.
         *
         * Descarta las horas que ya han pasado hace más de una, igual que hacía
         * la app Flutter, para que el carrusel empiece en "Ahora", y corta por
         * el otro extremo en [MAX_HOURS].
         */
        fun fromOpenMeteoJson(json: JSONObject): List<HourlyForecast> {
            val hourly = json.optJSONObject("hourly") ?: return emptyList()

            val time = hourly.optJSONArray("time") ?: return emptyList()
            val temperature = hourly.optJSONArray("temperature_2m")
            val precipProb = hourly.optJSONArray("precipitation_probability")
            val weatherCode = hourly.optJSONArray("weather_code")
            val humidity = hourly.optJSONArray("relative_humidity_2m")
            val windSpeed = hourly.optJSONArray("wind_speed_10m")
            val windDirection = hourly.optJSONArray("wind_direction_10m")
            val isDay = hourly.optJSONArray("is_day")
            val dewPoint = hourly.optJSONArray("dew_point_2m")
            val apparent = hourly.optJSONArray("apparent_temperature")
            val uvIndex = hourly.optJSONArray("uv_index")

            val forecasts = mutableListOf<HourlyForecast>()

            for (i in 0 until time.length()) {
                val date = parseLocalDateTime(time.optString(i)) ?: continue

                var code = weatherCode?.optIntOrNull(i)?.toString()
                // La variante nocturna del icono se decide con is_day.
                if (code != null && isDay != null && i < isDay.length()) {
                    if (isDay.optInt(i, 1) == 0) code = "${code}n"
                }

                val degrees = windDirection?.optDoubleOrNull(i)

                forecasts += HourlyForecast(
                    dateTime = date,
                    temperature = temperature?.optDoubleOrNull(i)?.roundToInt(),
                    skyStateCode = code ?: "",
                    skyDescription = WeatherCode.fromCode(code).description,
                    precipitationProbability = precipProb?.optDoubleOrNull(i)?.roundToInt(),
                    humidity = humidity?.optDoubleOrNull(i)?.roundToInt(),
                    windSpeed = windSpeed?.optDoubleOrNull(i)?.roundToInt(),
                    windDirection = degrees?.let { degreesToCompass(it) },
                    windDirectionDegrees = degrees?.toInt(),
                    dewPoint = dewPoint?.optDoubleOrNull(i)?.roundToInt(),
                    apparentTemperature = apparent?.optDoubleOrNull(i)?.roundToInt(),
                    uvIndex = uvIndex?.optDoubleOrNull(i),
                )
            }

            // La hora de corte es la del sitio, no la del teléfono: las
            // marcas del JSON vienen en hora local de allí.
            val cutoff = LocalDateTime.now(zoneOf(json.optString("timezone"))).minusHours(1)
            return forecasts.filter { it.dateTime.isAfter(cutoff) }.take(MAX_HOURS)
        }

        /** Open-Meteo entrega `2026-08-24T13:00`, sin huso (ya es local). */
        fun parseLocalDateTime(raw: String?): LocalDateTime? {
            if (raw.isNullOrBlank()) return null
            return try {
                LocalDateTime.parse(raw)
            } catch (_: DateTimeParseException) {
                null
            }
        }

        private val COMPASS = listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
        )

        /** Convierte grados a punto cardinal en 16 sectores. */
        fun degreesToCompass(degrees: Double): String {
            val index = (floor(degrees / 22.5 + 0.5).toInt() % 16 + 16) % 16
            return COMPASS[index]
        }
    }
}

/** `null` cuando la entrada es JSONObject.NULL, no 0. */
internal fun org.json.JSONArray.optDoubleOrNull(index: Int): Double? {
    if (index >= length() || isNull(index)) return null
    return optDouble(index).takeUnless { it.isNaN() }
}

internal fun org.json.JSONArray.optIntOrNull(index: Int): Int? {
    if (index >= length() || isNull(index)) return null
    return optDouble(index).takeUnless { it.isNaN() }?.toInt()
}
