package com.nubo.nubo.domain.weather

import com.nubo.nubo.domain.astro.SunTimes
import java.time.Duration
import java.time.LocalDateTime

/** Media hora a cada lado del orto y del ocaso, que es lo que dura el tránsito. */
private val TRANSITION: Duration = Duration.ofMinutes(30)

/**
 * Fase solar del momento a partir del amanecer y el ocaso del día.
 *
 * [now] **debe venir en la hora del sitio**, no en la del teléfono: los datos
 * solares se calculan en la zona de la localización, así que compararlos con
 * el reloj del dispositivo solo funciona si ambos coinciden. Ese descuido era
 * lo que pintaba Toronto con el cielo azul de mediodía y las estrellas encima.
 *
 * Vive en el dominio y no en el ViewModel porque quien la necesita es cada
 * ciudad por separado: no hay una fase solar de la aplicación, hay una por
 * sitio, y dos ciudades en pantalla pueden estar en momentos distintos del día.
 */
fun sunPhaseAt(sunTimes: SunTimes?, now: LocalDateTime): SunPhase {
    // Sin datos solares se asume de día: es el fondo más neutro y evita
    // arrancar la app en negro mientras se calcula.
    if (sunTimes == null) return SunPhase.DAY

    val sunriseStart = sunTimes.sunrise.minus(TRANSITION)
    val sunriseEnd = sunTimes.sunrise.plus(TRANSITION)
    val sunsetStart = sunTimes.sunset.minus(TRANSITION)
    val sunsetEnd = sunTimes.sunset.plus(TRANSITION)

    return when {
        now >= sunriseStart && now < sunriseEnd -> SunPhase.SUNRISE
        now >= sunriseEnd && now < sunsetStart -> SunPhase.DAY
        now >= sunsetStart && now < sunsetEnd -> SunPhase.SUNSET
        else -> SunPhase.NIGHT
    }
}
