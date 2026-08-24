package com.nubo.nubo.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.nubo.nubo.data.remote.Coordinates
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Error de geolocalización con un mensaje ya apto para enseñar al usuario. */
class LocationException(message: String) : Exception(message)

/**
 * Obtiene la posición del dispositivo.
 *
 * Mantiene la estrategia de la app Flutter: primero la última posición
 * conocida —que es instantánea— y solo si falta o está rancia se pide una
 * lectura nueva, que puede tardar bastante.
 */
class LocationService(private val context: Context) {

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Posición actual del dispositivo.
     *
     * @throws LocationException si el GPS está apagado, falta el permiso o no
     * se consigue una lectura a tiempo.
     */
    suspend fun getCurrentPosition(): Coordinates {
        if (!isLocationEnabled()) {
            throw LocationException(
                "El GPS está desactivado. Actívalo en los ajustes del dispositivo.",
            )
        }
        if (!hasPermission()) {
            throw LocationException("Permiso de ubicación denegado.")
        }

        lastKnownFresh()?.let { return it }

        val fresh = withTimeoutOrNull(FRESH_TIMEOUT_MILLIS) { requestFreshLocation() }
        return fresh ?: throw LocationException(
            "No se pudo obtener la ubicación a tiempo. " +
                "Asegúrate de estar al aire libre o con buena señal GPS.",
        )
    }

    /** Última posición conocida, si no ha envejecido demasiado. */
    private suspend fun lastKnownFresh(): Coordinates? = try {
        suspendCancellableCoroutine { continuation ->
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    val fresh = location != null &&
                        System.currentTimeMillis() - location.time < MAX_CACHE_AGE_MILLIS
                    continuation.resumeIfActive(
                        if (fresh) Coordinates(location!!.latitude, location.longitude) else null,
                    )
                }
                .addOnFailureListener { continuation.resumeIfActive(null) }
                .addOnCanceledListener { continuation.resumeIfActive(null) }
        }
    } catch (_: SecurityException) {
        null
    }

    private suspend fun requestFreshLocation(): Coordinates? = try {
        suspendCancellableCoroutine { continuation ->
            val request = CurrentLocationRequest.Builder()
                // BALANCED basta para resolver el municipio y no enciende el
                // GPS de alta precisión, que gasta batería y tarda más.
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(MAX_CACHE_AGE_MILLIS)
                .build()

            fusedClient.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    continuation.resumeIfActive(
                        location?.let { Coordinates(it.latitude, it.longitude) },
                    )
                }
                .addOnFailureListener { continuation.resumeIfActive(null) }
                .addOnCanceledListener { continuation.resumeIfActive(null) }
        }
    } catch (_: SecurityException) {
        null
    }

    private companion object {
        const val MAX_CACHE_AGE_MILLIS = 5 * 60 * 1000L
        const val FRESH_TIMEOUT_MILLIS = 30_000L
    }
}

/** Reanuda solo si nadie canceló antes; Play Services puede llamar dos veces. */
private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) resume(value)
}
