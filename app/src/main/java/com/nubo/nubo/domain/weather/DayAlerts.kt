package com.nubo.nubo.domain.weather

import com.nubo.nubo.domain.model.AlertLevel
import com.nubo.nubo.domain.model.AlertType
import com.nubo.nubo.domain.model.WeatherAlert
import java.time.LocalDate

/**
 * Un aviso resumido para la fila de un día: qué fenómeno y de qué nivel.
 *
 * No lleva texto ni icono, solo los dos enums: la capa de dominio no sabe
 * dibujar ni traducir, y quien convierte esto en un icono de color es
 * `DailyView` a través de `iconForType` y `toColor`.
 */
data class DayAlert(val type: AlertType, val level: AlertLevel)

/**
 * Los avisos de un día, **uno por fenómeno** y con su peor nivel.
 *
 * La fila de cada día enseñaba un punto del color del aviso más grave, que
 * decía lo serio que era el día pero no de qué. Un icono por fenómeno dice las
 * dos cosas en el mismo sitio y sin ocupar más.
 *
 * Se agrupa por familia a propósito: AEMET parte un mismo fenómeno en varios
 * avisos cuando cambia de nivel a lo largo del día —amarillo por la mañana y
 * naranja por la tarde son dos avisos de viento—, y pintarlos los dos sería
 * repetir el mismo icono en la misma esquina. Se enseña una vez con el nivel
 * **más grave**, que es el que manda para decidir si el día preocupa.
 *
 * El orden es por gravedad, lo peor pegado a la esquina, que es donde cae el
 * ojo. Los empates los rompe el orden de declaración de [AlertType] y no el de
 * llegada: con el de llegada, dos avisos del mismo nivel podían intercambiarse
 * de un refresco a otro sin que hubiera cambiado nada.
 */
fun dayAlerts(alerts: List<WeatherAlert>, day: LocalDate): List<DayAlert> {
    val from = day.atStartOfDay()
    val to = from.plusDays(1)

    return alerts
        .filter { it.overlaps(from, to) }
        .groupBy { AlertType.of(it.event) }
        .map { (type, group) -> DayAlert(type, group.maxBy { it.severity }.level) }
        .sortedWith(
            compareByDescending<DayAlert> { it.level.severity }.thenBy { it.type.ordinal },
        )
}
