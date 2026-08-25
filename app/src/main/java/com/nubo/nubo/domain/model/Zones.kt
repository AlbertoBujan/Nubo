package com.nubo.nubo.domain.model

import java.time.ZoneId

/**
 * Convierte una zona IANA en [ZoneId], cayendo a la del dispositivo.
 *
 * Un identificador desconocido —una zona retirada, un dato corrupto— no debe
 * tumbar la pantalla: se prefiere una hora aproximada a un cierre.
 */
fun zoneOf(timeZone: String?): ZoneId =
    timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
