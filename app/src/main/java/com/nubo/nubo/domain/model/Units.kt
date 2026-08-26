package com.nubo.nubo.domain.model

import kotlin.math.roundToInt

/** En qué unidad se enseña la temperatura. */
enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT,
    ;

    /**
     * Pasa a esta unidad un valor que viene en grados Celsius.
     *
     * La predicción se pide **siempre** en unidades métricas y se convierte al
     * pintarla, no al descargarla. Así cambiar de unidad es inmediato y no
     * invalida lo que hay en caché: pedirlo en otra unidad obligaría a volver a
     * bajar todas las ciudades para ver el mismo dato escrito de otra forma.
     *
     * A cambio se convierte un valor ya redondeado, y eso puede desviar hasta
     * un grado Fahrenheit respecto a convertir el original. Es el precio de no
     * tocar la caché, y a la resolución a la que se enseña el tiempo no cambia
     * ninguna decisión.
     */
    fun fromCelsius(celsius: Int): Int = when (this) {
        CELSIUS -> celsius
        FAHRENHEIT -> (celsius * 9.0 / 5.0 + 32.0).roundToInt()
    }
}

/** En qué unidad se enseña la velocidad del viento. */
enum class SpeedUnit {
    KMH,
    MPH,
    ;

    fun fromKmh(kmh: Int): Int = when (this) {
        KMH -> kmh
        MPH -> (kmh * MILES_PER_KILOMETRE).roundToInt()
    }

    private companion object {
        const val MILES_PER_KILOMETRE = 0.621371
    }
}

/** En qué unidad se enseñan las distancias de la búsqueda. */
enum class DistanceUnit {
    KILOMETRES,
    MILES,
    ;

    fun fromKm(km: Double): Double = when (this) {
        KILOMETRES -> km
        MILES -> km * MILES_PER_KILOMETRE
    }

    private companion object {
        const val MILES_PER_KILOMETRE = 0.621371
    }
}

/** Las unidades elegidas, que viajan juntas por la interfaz. */
data class Units(
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val speed: SpeedUnit = SpeedUnit.KMH,
    val distance: DistanceUnit = DistanceUnit.KILOMETRES,
) {
    fun temperature(celsius: Int): Int = temperature.fromCelsius(celsius)

    fun speed(kmh: Int): Int = speed.fromKmh(kmh)
}
