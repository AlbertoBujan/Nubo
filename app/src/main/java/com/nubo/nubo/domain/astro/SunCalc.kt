package com.nubo.nubo.domain.astro

import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Cálculos de posición solar y lunar.
 *
 * Port de los algoritmos de SunCalc (Vladimir Agafonkin), que son los que ya
 * usaba la app Flutter a través de los paquetes `sunrise_sunset_calc` y
 * `apsl_sun_calc`. Se reimplementan aquí en vez de depender de una librería
 * para no atarse a un paquete Android más de lo necesario: son unas pocas
 * fórmulas cerradas y quedan cubiertas por tests.
 *
 * Todos los ángulos internos van en radianes.
 */
internal object SunCalc {

    private const val RAD = PI / 180.0
    private const val DAY_MS = 1000L * 60 * 60 * 24
    private const val J1970 = 2440588.0
    private const val J2000 = 2451545.0

    /** Oblicuidad de la eclíptica. */
    private val E = RAD * 23.4397

    // ── Conversión de fechas ─────────────────────────────────────────────────

    private fun toJulian(instant: Instant): Double =
        instant.toEpochMilli().toDouble() / DAY_MS - 0.5 + J1970

    private fun fromJulian(j: Double): Instant =
        Instant.ofEpochMilli(((j + 0.5 - J1970) * DAY_MS).toLong())

    private fun toDays(instant: Instant): Double = toJulian(instant) - J2000

    // ── Posición en la esfera celeste ────────────────────────────────────────

    private fun rightAscension(l: Double, b: Double): Double =
        atan2(sin(l) * cos(E) - tan(b) * sin(E), cos(l))

    private fun declination(l: Double, b: Double): Double =
        asin(sin(b) * cos(E) + cos(b) * sin(E) * sin(l))

    private fun azimuth(h: Double, phi: Double, dec: Double): Double =
        atan2(sin(h), cos(h) * sin(phi) - tan(dec) * cos(phi))

    private fun altitude(h: Double, phi: Double, dec: Double): Double =
        asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h))

    private fun siderealTime(d: Double, lw: Double): Double =
        RAD * (280.16 + 360.9856235 * d) - lw

    /** Refracción atmosférica: eleva aparentemente los astros cerca del horizonte. */
    private fun astroRefraction(h: Double): Double {
        // Bajo el horizonte la fórmula diverge; se acota al valor del horizonte.
        val alt = if (h < 0) 0.0 else h
        return 0.0002967 / tan(alt + 0.00312536 / (alt + 0.08901179))
    }

    // ── Sol ──────────────────────────────────────────────────────────────────

    private fun solarMeanAnomaly(d: Double): Double = RAD * (357.5291 + 0.98560028 * d)

    private fun eclipticLongitude(m: Double): Double {
        // Ecuación del centro
        val c = RAD * (1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m))
        // Perihelio de la Tierra
        val p = RAD * 102.9372
        return m + c + p + PI
    }

    private data class SunCoords(val dec: Double, val ra: Double)

    private fun sunCoords(d: Double): SunCoords {
        val m = solarMeanAnomaly(d)
        val l = eclipticLongitude(m)
        return SunCoords(dec = declination(l, 0.0), ra = rightAscension(l, 0.0))
    }

    /**
     * Altitud del sol sobre el horizonte, en radianes.
     *
     * Es lo que dibuja la trayectoria de la tarjeta del sol: su forma depende
     * de la latitud y de la época del año, y por eso no puede ser una curva
     * fija. En junio en Galicia el sol sube casi vertical y se queda alto; en
     * diciembre describe un montículo bajo. Sin refracción, a diferencia de
     * [moonAltitude]: aquí interesa la geometría, no cuándo se ve asomar.
     */
    fun sunAltitude(instant: Instant, lat: Double, lng: Double): Double {
        val lw = RAD * -lng
        val phi = RAD * lat
        val d = toDays(instant)
        val c = sunCoords(d)
        val h = siderealTime(d, lw) - c.ra
        return altitude(h, phi, c.dec)
    }

    // ── Amanecer y atardecer ─────────────────────────────────────────────────

    private const val J0 = 0.0009

    private fun julianCycle(d: Double, lw: Double): Double = round(d - J0 - lw / (2 * PI))

    private fun approxTransit(ht: Double, lw: Double, n: Double): Double =
        J0 + (ht + lw) / (2 * PI) + n

    private fun solarTransitJ(ds: Double, m: Double, l: Double): Double =
        J2000 + ds + 0.0053 * sin(m) - 0.0069 * sin(2 * l)

    private fun hourAngle(h: Double, phi: Double, d: Double): Double =
        acos((sin(h) - sin(phi) * sin(d)) / (cos(phi) * cos(d)))

    /**
     * Instantes de amanecer y ocaso para una fecha y coordenadas.
     *
     * Devuelve `null` en cada campo cuando el sol no cruza el horizonte ese día
     * (noche o día polar), caso en el que el arcocoseno queda fuera de rango.
     */
    fun sunTimes(instant: Instant, lat: Double, lng: Double): Pair<Instant?, Instant?> {
        val lw = RAD * -lng
        val phi = RAD * lat
        val d = toDays(instant)

        val n = julianCycle(d, lw)
        val ds = approxTransit(0.0, lw, n)
        val m = solarMeanAnomaly(ds)
        val l = eclipticLongitude(m)
        val dec = declination(l, 0.0)
        val jNoon = solarTransitJ(ds, m, l)

        // -0.833°: centro del disco solar bajo el horizonte, considerando su
        // radio aparente y la refracción atmosférica. Es la definición estándar.
        val h0 = -0.833 * RAD

        val cosH = (sin(h0) - sin(phi) * sin(dec)) / (cos(phi) * cos(dec))
        if (cosH > 1 || cosH < -1) return null to null

        val w = hourAngle(h0, phi, dec)
        val jSet = solarTransitJ(approxTransit(w, lw, n), m, l)
        val jRise = jNoon - (jSet - jNoon)

        return fromJulian(jRise) to fromJulian(jSet)
    }

    // ── Luna ─────────────────────────────────────────────────────────────────

    private data class MoonCoords(val ra: Double, val dec: Double, val dist: Double)

    private fun moonCoords(d: Double): MoonCoords {
        val l = RAD * (218.316 + 13.176396 * d) // longitud eclíptica media
        val m = RAD * (134.963 + 13.064993 * d) // anomalía media
        val f = RAD * (93.272 + 13.229350 * d)  // distancia media al nodo

        val lng = l + RAD * 6.289 * sin(m)
        val lat = RAD * 5.128 * sin(f)
        val dist = 385001 - 20905 * cos(m)      // km

        return MoonCoords(
            ra = rightAscension(lng, lat),
            dec = declination(lng, lat),
            dist = dist,
        )
    }

    /** Altitud de la luna sobre el horizonte, en radianes, con refracción. */
    fun moonAltitude(instant: Instant, lat: Double, lng: Double): Double {
        val lw = RAD * -lng
        val phi = RAD * lat
        val d = toDays(instant)
        val c = moonCoords(d)
        val h = siderealTime(d, lw) - c.ra
        return altitude(h, phi, c.dec) + astroRefraction(altitude(h, phi, c.dec))
    }

    /** Azimut de la luna, en radianes desde el sur. */
    fun moonAzimuth(instant: Instant, lat: Double, lng: Double): Double {
        val lw = RAD * -lng
        val phi = RAD * lat
        val d = toDays(instant)
        val c = moonCoords(d)
        return azimuth(siderealTime(d, lw) - c.ra, phi, c.dec)
    }

    /**
     * Fase e iluminación lunar.
     *
     * `phase` va de 0 a 1 (0 = nueva, 0.5 = llena) y `fraction` es la porción
     * del disco iluminada.
     */
    fun moonIllumination(instant: Instant): MoonIllumination {
        val d = toDays(instant)
        val s = sunCoords(d)
        val m = moonCoords(d)

        val sdist = 149_598_000.0 // distancia media Tierra-Sol, km

        val phi = acos(
            sin(s.dec) * sin(m.dec) + cos(s.dec) * cos(m.dec) * cos(s.ra - m.ra),
        )
        val inc = atan2(sdist * sin(phi), m.dist - sdist * cos(phi))
        val angle = atan2(
            cos(s.dec) * sin(s.ra - m.ra),
            sin(s.dec) * cos(m.dec) - cos(s.dec) * sin(m.dec) * cos(s.ra - m.ra),
        )

        return MoonIllumination(
            fraction = (1 + cos(inc)) / 2,
            phase = 0.5 + 0.5 * inc * (if (angle < 0) -1 else 1) / PI,
            angle = angle,
        )
    }

    /** Altura del observador sobre el terreno; no se usa, se deja por paridad. */
    @Suppress("unused")
    private fun observerAngle(height: Double): Double = -2.076 * sqrt(abs(height)) / 60
}

data class MoonIllumination(
    val fraction: Double,
    val phase: Double,
    val angle: Double,
)
