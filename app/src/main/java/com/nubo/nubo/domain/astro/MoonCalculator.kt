package com.nubo.nubo.domain.astro

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Datos del ciclo lunar para un día y una ubicación. */
data class MoonData(
    val moonrise: LocalDateTime?,
    val moonset: LocalDateTime?,
    /** Posición en el ciclo: 0 = nueva, 0.5 = llena. */
    val cycle: Double,
    /** Fracción del disco iluminada, 0..1. */
    val illumination: Double,
    /** Fase con la que se nombra esa posición. */
    val phase: MoonPhase,
)

/** Calcula fase, iluminación y orto/ocaso lunar. */
object MoonCalculator {

    /** Altitud considerada horizonte, ya con la refracción atmosférica. */
    private const val HORIZON = -0.0145

    /** Paso del muestreo grueso al buscar el cruce del horizonte. */
    private const val STEP_MINUTES = 10L

    private const val DAY_HOURS = 24L

    /**
     * Ventana en la que buscar el ocaso tras el orto.
     *
     * La luna no aguanta sobre el horizonte más de unas trece horas fuera de
     * latitudes polares, así que con veintiséis sobra y no se cuela el orto
     * siguiente.
     */
    private const val ABOVE_HORIZON_HOURS = 26L

    fun calculate(
        dateTime: LocalDateTime,
        lat: Double,
        lng: Double,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MoonData {
        val illumination = SunCalc.moonIllumination(dateTime.atZone(zone).toInstant())
        val date = dateTime.toLocalDate()

        // Si la luna salió antes de medianoche no hay cruce ascendente hoy, así
        // que se mira el día anterior.
        val moonrise = findMoonEvent(date.atStartOfDay(), DAY_HOURS, lat, lng, true, zone)
            ?: findMoonEvent(date.minusDays(1).atStartOfDay(), DAY_HOURS, lat, lng, true, zone)

        // El ocaso se busca **a partir del orto**, no dentro del día natural.
        // Buscándolo desde medianoche se cogía el de la madrugada, que es el
        // final de la noche anterior: para una luna que sale a las 16:35 y se
        // pone a la 01:53 del día siguiente, devolvía un ocaso *anterior* al
        // orto, y con el intervalo invertido no había ni trayecto ni posición.
        val moonset = moonrise
            ?.let { findMoonEvent(it, ABOVE_HORIZON_HOURS, lat, lng, false, zone) }
            ?: findMoonEvent(date.atStartOfDay(), DAY_HOURS, lat, lng, false, zone)

        return MoonData(
            moonrise = moonrise,
            moonset = moonset,
            cycle = illumination.phase,
            illumination = illumination.fraction,
            phase = phaseOf(illumination.phase),
        )
    }

    /**
     * Busca cuándo cruza la luna el horizonte en las [hours] siguientes a
     * [from].
     *
     * Primero recorre el día a intervalos de 10 minutos hasta detectar el
     * cambio de signo, y después afina ese intervalo por bisección. No hay
     * fórmula cerrada para el orto lunar, así que este es el método habitual.
     */
    private fun findMoonEvent(
        from: LocalDateTime,
        hours: Long,
        lat: Double,
        lng: Double,
        rising: Boolean,
        zone: ZoneId,
    ): LocalDateTime? {
        val startOfDay = from
        val totalSteps = (hours * 60 / STEP_MINUTES).toInt()

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
    fun phaseOf(phase: Double): MoonPhase = when {
        phase < 0.03 || phase > 0.97 -> MoonPhase.NEW
        phase < 0.22 -> MoonPhase.WAXING_CRESCENT
        phase < 0.28 -> MoonPhase.FIRST_QUARTER
        phase < 0.47 -> MoonPhase.WAXING_GIBBOUS
        phase < 0.53 -> MoonPhase.FULL
        phase < 0.72 -> MoonPhase.WANING_GIBBOUS
        phase < 0.78 -> MoonPhase.LAST_QUARTER
        else -> MoonPhase.WANING_CRESCENT
    }
}
