package com.nubo.nubo.domain.astro

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Cuándo cae la próxima luna llena y la próxima luna nueva.
 *
 * No hay fórmula cerrada: se recorre el tiempo hacia delante calculando la fase
 * y se afina por bisección el instante en que cruza el valor buscado, que es lo
 * mismo que hace [MoonCalculator] con el orto y el ocaso. El paso grueso es
 * holgado —seis horas— porque la fase avanza despacio: un ciclo entero dura
 * 29,5 días, así que en seis horas no se puede saltar un cruce.
 *
 * Lo que se busca es el **siguiente**: si la llena fue hace una hora, lo que
 * toca enseñar es la de dentro de un mes, no la que acaba de pasar.
 */
object MoonPhases {

    /** Posiciones del ciclo en las cuatro fases que son un instante. */
    private const val NEW = 0.0
    private const val FIRST_QUARTER = 0.25
    private const val FULL = 0.5
    private const val LAST_QUARTER = 0.75

    /** Paso del muestreo grueso. */
    private val COARSE_STEP: Duration = Duration.ofHours(6)

    /** Hasta dónde se busca: un ciclo completo y un poco más. */
    private val SEARCH_LIMIT: Duration = Duration.ofDays(31)

    /**
     * Cuándo se deja de afinar.
     *
     * Se enseñan minutos, así que bastarían treinta segundos de holgura, pero
     * con esa el instante devuelto se movía dentro del margen según desde
     * cuándo se buscara y el minuto bailaba entre una consulta y la siguiente.
     * Con dos segundos el minuto ya no depende de cuándo se pregunte.
     */
    private val PRECISION: Duration = Duration.ofSeconds(2)

    fun nextFullMoon(from: LocalDateTime, zone: ZoneId): LocalDateTime? =
        next(from, zone, FULL)

    fun nextNewMoon(from: LocalDateTime, zone: ZoneId): LocalDateTime? =
        next(from, zone, NEW)

    /**
     * Cómo se llama la luna de [day] en [zone].
     *
     * Las cuatro fases que son un **instante** —nueva, cuartos y llena— se
     * enseñan el día en que cae ese instante, y no repartiendo el ciclo en
     * bandas: con bandas, "luna llena" duraba casi dos días y la tarjeta se
     * contradecía consigo misma, diciendo "llena" arriba y "próxima llena:
     * mañana" al darle la vuelta. Los otros cuatro nombres describen tramos y
     * llenan el resto del mes.
     */
    fun phaseOn(day: LocalDate, zone: ZoneId, cycle: Double): MoonPhase {
        // Se busca desde un poco antes de medianoche para no perderse un
        // instante que caiga en los primeros minutos del día.
        val from = day.atStartOfDay().minusHours(2)

        val instant = listOf(
            NEW to MoonPhase.NEW,
            FIRST_QUARTER to MoonPhase.FIRST_QUARTER,
            FULL to MoonPhase.FULL,
            LAST_QUARTER to MoonPhase.LAST_QUARTER,
        ).firstOrNull { (target, _) -> next(from, zone, target)?.toLocalDate() == day }

        return instant?.second ?: stretchOf(cycle)
    }

    /** El tramo del ciclo en el que está, para los días sin fase señalada. */
    private fun stretchOf(cycle: Double): MoonPhase = when {
        cycle < FIRST_QUARTER -> MoonPhase.WAXING_CRESCENT
        cycle < FULL -> MoonPhase.WAXING_GIBBOUS
        cycle < LAST_QUARTER -> MoonPhase.WANING_GIBBOUS
        else -> MoonPhase.WANING_CRESCENT
    }

    private fun next(from: LocalDateTime, zone: ZoneId, target: Double): LocalDateTime? {
        val start = from.atZone(zone).toInstant()
        val limit = start.plus(SEARCH_LIMIT)

        var previous = start
        var previousDiff = diffTo(previous, target)

        while (previous.isBefore(limit)) {
            val current = previous.plus(COARSE_STEP)
            val currentDiff = diffTo(current, target)

            // El cruce es donde la diferencia pasa de negativa a positiva. Se
            // exige ese sentido porque la fase solo avanza: al revés estaría
            // mirando el cruce por el otro lado del ciclo.
            if (previousDiff < 0 && currentDiff >= 0) {
                return refine(previous, current, target).atZone(zone).toLocalDateTime()
            }

            previous = current
            previousDiff = currentDiff
        }

        return null
    }

    /** Bisección hasta [PRECISION] entre un instante antes y otro después. */
    private fun refine(before: Instant, after: Instant, target: Double): Instant {
        var low = before
        var high = after

        while (Duration.between(low, high) > PRECISION) {
            val middle = low.plus(Duration.between(low, high).dividedBy(2))
            if (diffTo(middle, target) < 0) low = middle else high = middle
        }

        return high
    }

    /**
     * A qué distancia está la fase de [target], con signo y por el camino
     * corto.
     *
     * El ciclo da la vuelta —del 0,99 se pasa al 0,01— así que restar sin más
     * daría un salto enorme justo en la luna nueva, que es uno de los dos
     * valores que se buscan. Envolviéndolo en (-0,5, 0,5] la diferencia es
     * continua alrededor del cruce y la bisección funciona igual para los dos.
     */
    private fun diffTo(instant: Instant, target: Double): Double {
        val phase = SunCalc.moonIllumination(instant).phase
        val raw = phase - target
        return raw - Math.floor(raw + 0.5)
    }
}
