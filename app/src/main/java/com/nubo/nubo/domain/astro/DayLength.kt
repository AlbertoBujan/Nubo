package com.nubo.nubo.domain.astro

import java.time.Duration

/**
 * Cuánto dura el día y cuánto ha cambiado desde ayer.
 *
 * La duración se lee en la tarjeta del sol; el cambio es lo que de verdad se
 * nota al pasar las semanas y lo único de los dos que no se puede deducir
 * mirando el arco.
 */
data class DayLength(
    val today: Duration,
    /** Positivo si hoy hay más luz que ayer. Nulo si ayer no se sabe. */
    val sinceYesterday: Duration?,
)

/**
 * Mide el día de [today] y lo compara con el de [yesterday].
 *
 * Los dos tiempos vienen ya en la zona del sitio, así que restarlos como
 * fechas locales es correcto **salvo el día del cambio de hora**: ahí el reloj
 * salta y la resta local daría una hora de más o de menos. Por eso se comparan
 * como instantes, que es lo que no miente.
 */
fun dayLengthOf(today: SunTimes, yesterday: SunTimes?, zone: java.time.ZoneId): DayLength {
    val length = lengthOf(today, zone)
    val previous = yesterday?.let { lengthOf(it, zone) }

    return DayLength(
        today = length,
        sinceYesterday = previous?.let { length.minus(it) },
    )
}

private fun lengthOf(times: SunTimes, zone: java.time.ZoneId): Duration = Duration.between(
    times.sunrise.atZone(zone).toInstant(),
    times.sunset.atZone(zone).toInstant(),
)
