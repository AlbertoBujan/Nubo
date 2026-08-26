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
import com.nubo.nubo.data.notification.AlertNotifier
import com.nubo.nubo.di.ServiceLocator
import com.nubo.nubo.domain.model.zoneOf
import com.nubo.nubo.domain.weather.AlertNotifications
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

        val weatherRepository = ServiceLocator.weatherRepository()
        val alertRepository = ServiceLocator.alertRepository(applicationContext)

        val notifyAlerts = storage.loadAlertNotifications()
        val notifier = AlertNotifier(applicationContext)
        val alreadyNotified = storage.loadNotifiedAlerts()
        val newlyNotified = mutableSetOf<String>()
        val stillActive = mutableSetOf<String>()

        var updated = 0
        for (location in locations) {
            try {
                val forecast = weatherRepository.getForecast(location)
                val alerts = runCatching { alertRepository.getAlerts(location) }
                    .getOrDefault(emptyList())

                storage.saveWeather(
                    locationId = location.locationId,
                    rawJson = forecast.rawJson,
                    alerts = alerts,
                    // El sol y la luna los recalcula la app al abrirse; aquí
                    // solo importa dejar frescos predicción y avisos.
                    sunTimes = null,
                    updatedAt = LocalDateTime.now(),
                    airQualityJson = forecast.airQualityRawJson,
                )
                updated++

                if (notifyAlerts) {
                    // La hora se mide en la zona del sitio, no en la del
                    // teléfono: comparar con el reloj de aquí caducaría los
                    // avisos antes de tiempo en cuanto la ciudad esté lejos.
                    val nowThere = LocalDateTime.now(zoneOf(location.timeZone))
                    alerts.filter { it.isActiveAt(nowThere) }
                        .mapTo(stillActive) { AlertNotifications.keyOf(location.locationId, it) }

                    AlertNotifications.pending(
                        locationId = location.locationId,
                        alerts = alerts,
                        alreadyNotified = alreadyNotified,
                        now = nowThere,
                    ).forEach { alert ->
                        val key = AlertNotifications.keyOf(location.locationId, alert)
                        notifier.notify(location.nombre, alert, key)
                        newlyNotified += key
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error actualizando ${location.locationId}: ${e.message}")
            }
        }

        if (notifyAlerts) {
            // Se recuerdan los que siguen vigentes más los recién anunciados;
            // lo demás se olvida para que el conjunto no crezca sin fin. Solo
            // se escribe si el aparato puede notificar de verdad: dar por
            // anunciado lo que el sistema ha tirado lo silenciaría para siempre.
            if (notifier.canNotify()) {
                storage.saveNotifiedAlerts(
                    AlertNotifications.prune(alreadyNotified, stillActive) + newlyNotified,
                )
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

        /** Cada cuánto se mira si hay avisos cuando no hay otro motivo. */
        const val ALERTS_ONLY_HOURS = 12L

        /**
         * Programa o cancela la tarea periódica según la preferencia.
         *
         * Los avisos solo se descubren cuando esta tarea corre, así que
         * encenderlos con la actualización en segundo plano apagada la
         * programa igualmente, cada [ALERTS_ONLY_HOURS] horas. No es un
         * ajuste que se pise a escondidas: la pantalla de ajustes lo dice
         * justo debajo del interruptor.
         */
        fun schedule(
            context: Context,
            interval: BackgroundInterval,
            alertNotifications: Boolean = false,
        ) {
            val workManager = WorkManager.getInstance(context)

            val hours = interval.hours ?: ALERTS_ONLY_HOURS.takeIf { alertNotifications }
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
