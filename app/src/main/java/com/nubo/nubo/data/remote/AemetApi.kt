package com.nubo.nubo.data.remote

import org.json.JSONObject

/**
 * Acceso a AEMET OpenData.
 *
 * Su API funciona en dos pasos: el endpoint devuelve un JSON con una URL
 * temporal firmada en el campo `datos`, y el contenido real hay que pedirlo a
 * esa segunda URL (esta ya sin api_key).
 */
class AemetApi(
    private val http: HttpClient = HttpClient(),
    private val keyring: AemetKeyring = AemetKeyring(http),
) {

    /**
     * Resuelve un endpoint de AEMET y devuelve el cuerpo del paso 2.
     *
     * Devuelve `null` si cualquiera de los dos pasos falla, para que quien
     * llame degrade con elegancia: un fallo de AEMET no debe tumbar la app,
     * solo dejarla sin avisos.
     *
     * Antes de darse por vencido mira si hay una **clave nueva publicada** y
     * repite una vez. Es el caso que importa: cuando la clave embebida caduca,
     * AEMET contesta 401 a todo y sin esto la app se quedaría sin avisos hasta
     * que alguien publicase otra versión. Solo se reintenta una vez y solo con
     * una clave distinta de la que acaba de fallar, así que un apagón de AEMET
     * no se convierte en un bucle. Ver [AemetKeyring].
     */
    suspend fun fetchData(path: String, timeoutSeconds: Long = 15): String? {
        val key = keyring.current()
        attempt(path, timeoutSeconds, key)?.let { return it }

        val renewed = keyring.renewed(key) ?: return null
        return attempt(path, timeoutSeconds, renewed)
    }

    /** Los dos pasos del protocolo con una clave concreta. */
    private suspend fun attempt(
        path: String,
        timeoutSeconds: Long,
        apiKey: String,
    ): String? {
        val step1 = http.get(
            "$BASE_URL$path",
            headers = mapOf("api_key" to apiKey),
            timeoutSeconds = timeoutSeconds,
        )
        if (!step1.isSuccess) return null

        val datosUrl = try {
            JSONObject(step1.decodeText()).optString("datos").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } ?: return null

        // El 401 por clave caducada también llega **aquí**: la URL firmada del
        // paso 1 se entrega igualmente y es la descarga la que lo devuelve,
        // envuelta en un 500. Por eso el reintento cuelga de fetchData y no de
        // un código concreto del primer paso.
        val step2 = http.get(datosUrl, timeoutSeconds = timeoutSeconds)
        if (!step2.isSuccess) return null

        return step2.decodeText()
    }

    companion object {
        const val BASE_URL = "https://opendata.aemet.es/opendata"

        // NOTA: la clave viaja embebida en el binario, igual que en la app
        // Flutter. Es una clave pública gratuita de AEMET OpenData y ya está
        // publicada en los APK anteriores y en el historial del repositorio,
        // así que moverla ahora no la des-filtraría. Conviene rotarla y
        // servirla desde el build si en algún momento pasa a importar.
        //
        // **Caduca**, y por eso esta es solo el respaldo: la clave viva se
        // publica en `aemet-key.json`, en la raíz del repositorio, y la app la
        // baja sola en cuanto AEMET falla. Ver [AemetKeyring]. Esta de aquí es
        // la que se usa mientras funcione y la que salva el primer arranque
        // sin acceso a GitHub; conviene actualizarla de vez en cuando, pero ya
        // no hace falta publicar una versión para renovar.
        //
        // Esta vale hasta el 18/02/2027 —lleva la fecha dentro, en el campo
        // `exp` del JWT—. Se renueva pidiendo otra en
        // https://opendata.aemet.es/centrodedescargas/obtencionAPIKey, con un
        // correo y un captcha, y escribiéndola en `aemet-key.json`.
        const val API_KEY =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJiaXJ0ZWJzQGdtYWlsLmNvbSIsImp0aSI6IjJ" +
                "kMjMyZGEwLWM4YTgtNDQwZS05ZGU2LTNlNmU1YWUzZGJhYSIsImV4cCI6MTgwMjkzOTI" +
                "xNSwiaXNzIjoiQUVNRVQiLCJpYXQiOjE3OTQyOTkyMTUsInVzZXJJZCI6IjJkMjMyZGE" +
                "wLWM4YTgtNDQwZS05ZGU2LTNlNmU1YWUzZGJhYSIsInJvbGUiOiIifQ.gxi7pJ2cNddk" +
                "cpG_YCwarT_UYKL-Meu9Yt7tQ9YvnTA"
    }
}
