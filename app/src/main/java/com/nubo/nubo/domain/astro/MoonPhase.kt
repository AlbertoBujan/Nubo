package com.nubo.nubo.domain.astro

/**
 * Fase de la luna.
 *
 * Enum y no el nombre ya escrito: el dominio no puede depender de Android y
 * traducir exige recursos. El texto se resuelve en `ui/WeatherLabels.kt`.
 */
enum class MoonPhase {
    NEW,
    WAXING_CRESCENT,
    FIRST_QUARTER,
    WAXING_GIBBOUS,
    FULL,
    WANING_GIBBOUS,
    LAST_QUARTER,
    WANING_CRESCENT,
}
