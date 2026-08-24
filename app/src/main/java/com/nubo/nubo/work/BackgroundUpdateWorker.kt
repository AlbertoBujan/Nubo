package com.nubo.nubo.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nubo.nubo.di.ServiceLocator
import com.nubo.nubo.ui.weather.BackgroundInterval
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Refresca en segundo plano la predicción de todas las ciudades guardadas.
 *
 * No pasa por el ViewModel: construye sus propias dependencias porque corre sin
 * interfaz, posiblemente con la app cerrada. Un fallo en una ciudad no aborta
 * las demás.
 */
class BackgroundUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val storage = ServiceLocator.weatherStorage(applicationContext)
        val locations = storage.loadLocations()

        if (locations.isEmpty()) {
            Log.d(TAG, "Sin localizaciones guardadas — nada que actualizar")
            return Result.success()
        }

        val weatherRepository = ServiceLocator.weatherRepository(applicationContext)
        val alertRepository = ServiceLocator.alertRepository()

        var updated = 0
        for (location in locations) {
            try {
                val forecast = weatherRepository.getForecast(location.municipioId)
                val alerts = runCatching { alertRepository.getAlerts(location.municipioId) }
                    .getOrDefault(emptyList())

                storage.saveWeather(
                    municipioId = location.municipioId,
                    rawJson = forecast.rawJson,
                    alerts = alerts,
                    // El sol y la luna los recalcula la app al abrirse; aquí
                    // solo importa dejar frescos predicción y avisos.
                    sunTimes = null,
                    updatedAt = LocalDateTime.now(),
                )
                updated++
            } catch (e: Exception) {
                Log.w(TAG, "Error actualizando ${location.municipioId}: ${e.message}")
            }
        }

        Log.d(TAG, "Actualizadas $updated/${locations.size} ciudades")
        // Se reporta éxito aunque alguna fallase: reintentar la tanda entera
        // por una ciudad caída gastaría batería sin arreglar nada.
        return Result.success()
    }

    companion object {
        private const val TAG = "BgUpdate"
        private const val WORK_NAME = "nubo-bg-weather"

        /** Programa o cancela la tarea periódica según la preferencia. */
        fun schedule(context: Context, interval: BackgroundInterval) {
            val workManager = WorkManager.getInstance(context)

            val hours = interval.hours
            if (hours == null) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val request = PeriodicWorkRequestBuilder<BackgroundUpdateWorker>(
                hours,
                TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                // UPDATE conserva la cuenta atrás en curso si el intervalo no
                // cambió, en vez de reiniciarla en cada arranque de la app.
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
