package com.nubo.nubo.domain.geo

import com.nubo.nubo.domain.model.DistanceUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Radio medio de la Tierra, en kilómetros. */
private const val EARTH_RADIUS_KM = 6371.0

/**
 * Distancia en kilómetros entre dos puntos, por la fórmula del haversine.
 *
 * Aquí sí hace falta la distancia geodésica y no la euclídea en grados que
 * usa [com.nubo.nubo.data.remote.AemetZoneService]: aquella comparaba
 * municipios dentro de España, donde el orden que produce es el mismo, pero
 * esto ordena sitios de todo el planeta. Un grado de longitud son 111 km en el
 * ecuador y 20 en Tromsø, así que sin corregirlo por la latitud los resultados
 * del norte saldrían sistemáticamente demasiado lejos.
 */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val rLat1 = Math.toRadians(lat1)
    val rLat2 = Math.toRadians(lat2)

    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)

    // El min(1.0) protege del redondeo: para dos puntos idénticos `a` puede
    // salir un pelo por encima de 1 y asin() devolvería NaN.
    return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(a)))
}

/**
 * Distancia en el formato en el que se muestra junto a cada resultado.
 *
 * Por debajo de diez unidades se da un decimal, porque entre dos pueblos
 * vecinos "8 km" y "8,4 km" es la diferencia entre ordenarlos y no. Y por
 * debajo de una, se baja a la unidad pequeña de cada sistema —metros o pies—
 * en vez de enseñar "0,6 km", que se lee peor.
 */
fun formatDistance(km: Double, unit: DistanceUnit = DistanceUnit.KILOMETRES): String {
    val value = unit.fromKm(km)
    // El separador decimal sale del idioma del teléfono: "8,4 km" en español
    // y "8.4 km" en inglés.
    val locale = java.util.Locale.getDefault()
    return when (unit) {
        DistanceUnit.KILOMETRES -> when {
            value < 1 -> "${(value * 1000).toInt()} m"
            value < 10 -> "%.1f km".format(locale, value)
            else -> "${value.toInt()} km"
        }

        DistanceUnit.MILES -> when {
            value < 0.1 -> "${(value * FEET_PER_MILE).toInt()} ft"
            value < 10 -> "%.1f mi".format(locale, value)
            else -> "${value.toInt()} mi"
        }
    }
}

/** Pies que tiene una milla. */
private const val FEET_PER_MILE = 5280
