package com.nubo.nubo.domain.astro

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Trayectoria de un astro entre su orto y su ocaso, para dibujarla.
 *
 * Devuelve alturas normalizadas de 0 a 1, repartidas a intervalos **iguales de
 * tiempo**: el valor `i` corresponde al instante `i / (n-1)` del recorrido. Así
 * la posición horizontal es la hora y la vertical la altura real sobre el
 * horizonte.
 *
 * No es una curva inventada. La altura del sol depende de la latitud y de la
 * declinación, así que la forma cambia con el sitio y con la época: en junio en
 * Galicia sube casi vertical y se mantiene alta —cima ancha—, mientras que en
 * diciembre describe un montículo redondeado. Una parábola fija no distingue
 * un solsticio de otro, ni Tromsø de Nairobi.
 */
object SkyPath {

    /**
     * Puntos que se muestrean.
     *
     * Cuarenta y ocho son uno cada cuarto de hora en un día de doce, de sobra
     * para que la curva se vea suave a la altura a la que se dibuja.
     */
    const val SAMPLES = 48

    /** Noventa grados en radianes: el cenit, que es la altura 1 del dibujo. */
    private const val ZENITH = Math.PI / 2

    /** Trayectoria del sol entre [sunrise] y [sunset]. */
    fun sun(
        sunrise: LocalDateTime,
        sunset: LocalDateTime,
        lat: Double,
        lon: Double,
        zone: ZoneId,
    ): List<Float> = sample(sunrise, sunset, zone) { SunCalc.sunAltitude(it, lat, lon) }

    /** Trayectoria de la luna entre [moonrise] y [moonset]. */
    fun moon(
        moonrise: LocalDateTime,
        moonset: LocalDateTime,
        lat: Double,
        lon: Double,
        zone: ZoneId,
    ): List<Float> = sample(moonrise, moonset, zone) { SunCalc.moonAltitude(it, lat, lon) }

    /**
     * Muestrea y normaliza contra el cenit, no contra el pico del propio día.
     *
     * Es lo que hace que la altura del arco signifique algo: en Galicia el sol
     * de junio culmina a 70º y el de diciembre a 24º, así que el arco de
     * invierno se dibuja bajo de verdad.
     *
     * Escalar cada día a su propio máximo era lo primero que probé y resulta
     * engañoso: un sol bajo, estirado hasta llenar la tarjeta, sale con la
     * cima **más plana** que uno alto —porque su máximo es más romo— y se lee
     * como "estuvo alto todo el día", que es justo lo contrario.
     *
     * Devuelve la lista vacía cuando el astro no llega a salir, que es lo que
     * pasa en un día polar; ahí quien dibuje decide qué enseñar.
     */
    private fun sample(
        start: LocalDateTime,
        end: LocalDateTime,
        zone: ZoneId,
        altitudeAt: (Instant) -> Double,
    ): List<Float> {
        val from = start.atZone(zone).toInstant()
        val to = end.atZone(zone).toInstant()
        val span = to.toEpochMilli() - from.toEpochMilli()
        if (span <= 0) return emptyList()

        val altitudes = DoubleArray(SAMPLES) { i ->
            val at = from.plusMillis(span * i / (SAMPLES - 1))
            altitudeAt(at)
        }

        if (altitudes.max() <= 0.0) return emptyList()

        // Los extremos pueden salir levemente negativos por el redondeo del
        // orto y del ocaso; se acotan para que la curva arranque del suelo.
        return altitudes.map { (it / ZENITH).coerceIn(0.0, 1.0).toFloat() }
    }

    /**
     * Altura de la curva en un punto del recorrido, interpolando entre
     * muestras. [progress] va de 0 a 1.
     */
    fun heightAt(path: List<Float>, progress: Float): Float {
        if (path.isEmpty()) return 0f
        if (path.size == 1) return path[0]

        val position = (progress.coerceIn(0f, 1f) * (path.size - 1))
        val index = position.toInt().coerceAtMost(path.size - 2)
        val fraction = position - index
        return path[index] + (path[index + 1] - path[index]) * fraction
    }
}
