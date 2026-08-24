package com.nubo.nubo.domain.astro

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Amanecer y ocaso de un día concreto, en la zona horaria del dispositivo. */
data class SunTimes(
    val sunrise: LocalDateTime,
    val sunset: LocalDateTime,
)

/** Calcula los eventos solares de una fecha y unas coordenadas. */
object SunCalculator {

    /**
     * Devuelve el amanecer y el ocaso del día de [dateTime] en [lat]/[lng].
     *
     * En latitudes extremas puede no haber salida ni puesta (día o noche
     * polar); ahí se devuelve `null` y quien llame debe decidir qué mostrar.
     */
    fun calculateTimes(
        dateTime: LocalDateTime,
        lat: Double,
        lng: Double,
        zone: ZoneId = ZoneId.systemDefault(),
    ): SunTimes? {
        // Se calcula sobre el mediodía local para que el día natural quede
        // centrado en la ventana y no se cuele el amanecer del día contiguo.
        val noon: Instant = dateTime.toLocalDate()
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()

        val (sunrise, sunset) = SunCalc.sunTimes(noon, lat, lng)
        if (sunrise == null || sunset == null) return null

        return SunTimes(
            sunrise = LocalDateTime.ofInstant(sunrise, zone),
            sunset = LocalDateTime.ofInstant(sunset, zone),
        )
    }
}
