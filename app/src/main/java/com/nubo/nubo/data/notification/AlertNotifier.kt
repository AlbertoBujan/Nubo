package com.nubo.nubo.data.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nubo.nubo.MainActivity
import com.nubo.nubo.R
import com.nubo.nubo.domain.model.AlertLevel
import com.nubo.nubo.domain.model.AlertType
import com.nubo.nubo.domain.model.WeatherAlert
import com.nubo.nubo.ui.components.labelRes

/**
 * Anuncia un aviso meteorológico en la barra de notificaciones.
 *
 * Vive fuera de la interfaz porque quien lo usa es la tarea de fondo, que corre
 * con la app cerrada. Sí lee los textos de recursos: la regla de que el dominio
 * no guarda texto sigue en pie —el aviso llega aquí como enums— pero traducirlo
 * hay que hacerlo en algún sitio, y este ya tiene un `Context`.
 */
class AlertNotifier(private val context: Context) {

    /**
     * El permiso puede estar denegado aunque el ajuste esté encendido: se
     * conceden por separado y el del sistema manda. Publicar sin él no lanza,
     * simplemente no aparece nada, así que se comprueba antes para no dar por
     * anunciado lo que nadie ha visto.
     */
    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    // El permiso se comprueba en `canNotify()`, justo debajo; lint no sigue la
    // llamada a través del método y pide verlo en la misma función.
    @SuppressLint("MissingPermission")
    fun notify(cityName: String, alert: WeatherAlert, key: String) {
        if (!canNotify()) return

        ensureChannel()

        val level = context.getString(alert.level.labelRes)
        val family = context.getString(AlertType.of(alert.event).labelRes)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setColor(accentOf(alert.level))
            .setContentTitle(context.getString(R.string.alert_notification_title, family, cityName))
            .setContentText(context.getString(R.string.alert_notification_text, level))
            // El titular de AEMET es una frase larga; plegado se corta, pero
            // desplegando la notificación se lee entero sin abrir la app.
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.headline.ifBlank { alert.event }))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alert_notifications),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alert_notifications_channel)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun accentOf(level: AlertLevel): Int = when (level) {
        AlertLevel.YELLOW -> 0xFFFFC107.toInt()
        AlertLevel.ORANGE -> 0xFFFF9800.toInt()
        AlertLevel.RED -> 0xFFE53935.toInt()
    }

    private companion object {
        const val CHANNEL_ID = "weather_alerts"
    }
}
