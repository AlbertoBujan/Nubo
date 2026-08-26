package com.nubo.nubo.domain.weather

import com.nubo.nubo.domain.model.WeatherAlert
import java.time.LocalDateTime

/**
 * Qué avisos hay que anunciar y cuáles ya se anunciaron.
 *
 * La tarea de fondo vuelve a descargar los mismos avisos cada doce horas, y un
 * aviso de nieve dura días: sin memoria, el teléfono repetiría la misma alerta
 * en cada vuelta hasta que caducase. Por eso se guarda una **clave** por aviso
 * ya notificado y aquí se restan.
 *
 * Todo esto es aritmética sobre datos, sin Android, y por eso se puede probar
 * sin instrumentación. Lo que sí necesita ayuda de fuera es el "ahora": las
 * horas del aviso están en la zona del sitio, no en la del teléfono.
 */
object AlertNotifications {

    /**
     * Identidad de un aviso a efectos de no repetirlo.
     *
     * AEMET no publica un identificador estable entre descargas, así que la
     * clave se compone de lo que define al aviso: dónde, qué, de qué nivel y
     * para cuándo. Incluir el final es lo que hace que **prorrogar** un aviso
     * cuente como aviso nuevo — que es justo lo que se quiere avisar —, mientras
     * que volver a descargar el mismo no.
     */
    fun keyOf(locationId: String, alert: WeatherAlert): String = listOf(
        locationId,
        alert.nivel.lowercase(),
        alert.event.lowercase(),
        alert.areaDescription.lowercase(),
        alert.onset?.toString().orEmpty(),
        alert.expires?.toString().orEmpty(),
    ).joinToString("|")

    /**
     * Avisos vigentes de [locationId] que todavía no se han anunciado.
     *
     * Los caducados no entran aunque nunca se anunciaran: una tarea que corre
     * cada doce horas se despierta a menudo con avisos que ya pasaron, y avisar
     * de una tormenta de ayer solo enseña a ignorar las notificaciones.
     */
    fun pending(
        locationId: String,
        alerts: List<WeatherAlert>,
        alreadyNotified: Set<String>,
        now: LocalDateTime,
    ): List<WeatherAlert> = alerts
        .filter { it.isActiveAt(now) }
        .filterNot { keyOf(locationId, it) in alreadyNotified }

    /**
     * Deja en la memoria solo las claves que siguen correspondiendo a un aviso
     * vigente.
     *
     * Sin esto el conjunto crecería para siempre dentro de DataStore. Y como un
     * aviso caducado no se vuelve a anunciar, olvidarlo no lo resucita.
     */
    fun prune(notified: Set<String>, stillActive: Set<String>): Set<String> =
        notified intersect stillActive
}
