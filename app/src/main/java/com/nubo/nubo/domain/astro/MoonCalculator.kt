package com.nubo.nubo.domain.astro

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Datos del ciclo lunar para un día y una ubicación. */
data class MoonData(
    val moonrise: LocalDateTime?,
    val moonset: LocalDateTime?,
    /** 0 = nueva, 0.5 = llena. */
    val phase: Double,
    /** Fracción del disco iluminada, 0..1. */
    val illumination: Double,
    val phaseName: String,
)

/** Calcula fase, iluminación y orto/ocaso lunar. */
object MoonCalculator {

    /** Altitud considerada horizonte, ya con la refracción atmosférica. */
    private const val HORIZON = -0.0145

    /** Paso del muestreo grueso al buscar el cruce del horizonte. */
    private const val STEP_MINUTES = 10L

    fun calculate(
        dateTime: LocalDateTime,
        lat: Double,
        lng: Double,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MoonData {
        val illumination = SunCalc.moonIllumination(dateTime.atZone(zone).toInstant())
        val date = dateTime.toLocalDate()

        // Si la luna salió antes de medianoche no hay cruce ascendente hoy, así
        // que se mira el día anterior; con el ocaso pasa lo simétrico.
        val moonrise = findMoonEvent(date, lat, lng, rising = true, zone = zone)
            ?: findMoonEvent(date.minusDays(1), lat, lng, rising = true, zone = zone)

        val moonset = findMoonEvent(date, lat, lng, rising = false, zone = zone)
            ?: findMoonEvent(date.plusDays(1), lat, lng, rising = false, zone = zone)

        return MoonData(
            moonrise = moonrise,
            moonset = moonset,
            phase = illumination.phase,
            illumination = illumination.fraction,
            phaseName = phaseName(illumination.phase),
        )
    }

    /**
     * Busca cuándo cruza la luna el horizonte dentro de [date].
     *
     * Primero recorre el día a intervalos de 10 minutos hasta detectar el
     * cambio de signo, y después afina ese intervalo por bisección. No hay
     * fórmula cerrada para el orto lunar, así que este es el método habitual.
     */
    private fun findMoonEvent(
        date: LocalDate,
        lat: Double,
        lng: Double,
        rising: Boolean,
        zone: ZoneId,
    ): LocalDateTime? {
        val startOfDay = date.atStartOfDay()
        val totalSteps = (24 * 60 / STEP_MINUTES).toInt()

        var previousAltitude: Double? = null
        var crossingStart: LocalDateTime? = null

        for (i in 0..totalSteps) {
            val t = startOfDay.plusMinutes(i * STEP_MINUTES)
            val altitude = SunCalc.moonAltitude(t.atZone(zone).toInstant(), lat, lng)

            val previous = previousAltitude
            if (previous != null) {
                val crossed = if (rising) {
                    previous <= HORIZON && altitude > HORIZON
                } else {
                    previous >= HORIZON && altitude < HORIZON
                }
                if (crossed) {
                    crossingStart = t.minusMinutes(STEP_MINUTES)
                    break
                }
            }
            previousAltitude = altitude
        }

        val start = crossingStart ?: return null

        // Bisección: 8 iteraciones sobre un intervalo de 10 min dejan el
        // resultado por debajo del segundo, de sobra para mostrar una hora.
        var low = start
        var high = start.plusMinutes(STEP_MINUTES)
        repeat(8) {
            val mid = low.plus(Duration.between(low, high).dividedBy(2))
            val altitude = SunCalc.moonAltitude(mid.atZone(zone).toInstant(), lat, lng)
            val keepLow = if (rising) altitude <= HORIZON else altitude >= HORIZON
            if (keepLow) low = mid else high = mid
        }

        return low.plus(Duration.between(low, high).dividedBy(2))
    }

    /** Nombre de la fase lunar en español. */
    fun phaseName(phase: Double): String = when {
        phase < 0.03 || phase > 0.97 -> "Luna nueva"
        phase < 0.22 -> "Creciente cóncava"
        phase < 0.28 -> "Cuarto creciente"
        phase < 0.47 -> "Creciente convexa"
        phase < 0.53 -> "Luna llena"
        phase < 0.72 -> "Menguante convexa"
        phase < 0.78 -> "Cuarto menguante"
        else -> "Menguante cóncava"
    }
}
