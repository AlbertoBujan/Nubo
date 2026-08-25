package com.nubo.nubo.domain.model

import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Nivel de un aviso de AEMET, ordenado por gravedad. */
enum class AlertLevel(val label: String, val severity: Int) {
    YELLOW("Amarillo", 1),
    ORANGE("Naranja", 2),
    RED("Rojo", 3),
    ;

    companion object {
        /** AEMET emite el nivel en español dentro del parámetro del CAP. */
        fun fromNivel(nivel: String?): AlertLevel = when (nivel?.lowercase()?.trim()) {
            "rojo" -> RED
            "naranja" -> ORANGE
            else -> YELLOW
        }
    }
}

/** Aviso meteorológico de AEMET, en formato CAP. */
data class WeatherAlert(
    /** Nivel tal cual lo publica AEMET: amarillo, naranja o rojo. */
    val nivel: String,
    /** Fenómeno, p. ej. "Aviso de costeros de nivel amarillo". */
    val event: String,
    val headline: String,
    val description: String,
    /** Recomendaciones de protección civil. */
    val instruction: String,
    /** Zona afectada dentro de la provincia. */
    val areaDescription: String,
    val onset: LocalDateTime?,
    val expires: LocalDateTime?,
    /** Probabilidad, p. ej. "40%-70%". */
    val probability: String,
) {
    val level: AlertLevel get() = AlertLevel.fromNivel(nivel)

    val severity: Int get() = level.severity

    val nivelDisplay: String
        get() = nivel.replaceFirstChar { it.uppercase() }

    /**
     * Un aviso ya caducado no se muestra.
     *
     * El "ahora" se recibe porque las horas del aviso están en la zona del
     * sitio: comparar con el reloj del teléfono caducaba avisos antes de
     * tiempo, o los alargaba, en cuanto quien mira está en otro huso.
     */
    fun isActiveAt(now: LocalDateTime): Boolean =
        expires == null || !expires.isBefore(now)

    /** Comprueba si el aviso solapa con el intervalo dado. */
    fun overlaps(from: LocalDateTime, to: LocalDateTime): Boolean {
        if (onset == null && expires == null) return false
        val start = onset ?: expires!!.minusDays(1)
        val end = expires ?: onset!!.plusDays(1)
        return start.isBefore(to) && end.isAfter(from)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("nivel", nivel)
        put("event", event)
        put("headline", headline)
        put("description", description)
        put("instruction", instruction)
        put("areaDescription", areaDescription)
        put("onset", onset?.toString() ?: JSONObject.NULL)
        put("expires", expires?.toString() ?: JSONObject.NULL)
        put("probability", probability)
    }

    companion object {
        fun fromJson(json: JSONObject): WeatherAlert = WeatherAlert(
            nivel = json.optString("nivel"),
            event = json.optString("event"),
            headline = json.optString("headline"),
            description = json.optString("description"),
            instruction = json.optString("instruction"),
            areaDescription = json.optString("areaDescription"),
            onset = parseDateTime(json.optString("onset", "")),
            expires = parseDateTime(json.optString("expires", "")),
            probability = json.optString("probability"),
        )

        /**
         * Acepta tanto marcas con huso (las del CAP de AEMET, p. ej.
         * `2026-08-24T12:00:00+02:00`) como las locales que escribe [toJson].
         * Las primeras se convierten a [zone], la del sitio del aviso, para
         * que casen con las horas de la predicción.
         */
        fun parseDateTime(raw: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? {
            if (raw.isNullOrBlank() || raw == "null") return null
            return try {
                OffsetDateTime.parse(raw)
                    .atZoneSameInstant(zone)
                    .toLocalDateTime()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(raw)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }
}
