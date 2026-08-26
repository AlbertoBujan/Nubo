package com.nubo.nubo.domain.model

/**
 * Qué ha ido mal al cargar una ciudad.
 *
 * Antes esto era el `message` de la excepción, ya escrito en español y metido
 * en el estado tal cual. Un mensaje redactado no se puede traducir, así que lo
 * que viaja ahora es el **motivo**, y el texto lo pone la interfaz.
 *
 * [statusCode] acompaña al motivo [SERVER] porque un número de error sí es
 * igual en todos los idiomas y ayuda a saber qué pasó.
 */
data class CityError(val reason: ErrorReason, val statusCode: Int? = null)

enum class ErrorReason {
    NETWORK,
    SERVER,
    UNREADABLE,
    NO_COORDINATES,
    LOCATION_DISABLED,
    LOCATION_PERMISSION,
    LOCATION_TIMEOUT,
    LOCATION_UNKNOWN,
    UNKNOWN,
}
