package com.nubo.nubo.domain.model

/**
 * Familia a la que pertenece un aviso.
 *
 * AEMET no publica una categoría: publica un texto libre en español —"Lluvias",
 * "Vientos costeros"— que aquí se reduce a una familia. El **texto de entrada
 * seguirá siendo español pase lo que pase**, porque es lo que emite la fuente;
 * lo que sí se traduce es el nombre con el que se enseña la familia, y por eso
 * esto es un enum y no la palabra ya escrita.
 */
enum class AlertType {
    WIND,
    COASTAL,
    RAIN,
    SNOW,
    THUNDERSTORM,
    TEMPERATURE,
    FOG,
    DUST,
    AVALANCHE,
    THAW,
    GENERIC,
    ;

    companion object {
        /** Reduce el evento de AEMET a una familia. */
        fun of(event: String): AlertType {
            val lower = event.lowercase()
            return when {
                lower.contains("viento") -> WIND
                lower.contains("costero") -> COASTAL
                lower.contains("lluvia") || lower.contains("precipita") -> RAIN
                lower.contains("nieve") || lower.contains("nevada") -> SNOW
                lower.contains("tormenta") -> THUNDERSTORM
                lower.contains("temperatura") -> TEMPERATURE
                lower.contains("niebla") -> FOG
                lower.contains("polvo") -> DUST
                lower.contains("alud") -> AVALANCHE
                lower.contains("deshielo") -> THAW
                else -> GENERIC
            }
        }
    }
}
