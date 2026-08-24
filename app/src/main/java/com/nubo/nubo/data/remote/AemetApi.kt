package com.nubo.nubo.data.remote

import org.json.JSONObject

/**
 * Acceso a AEMET OpenData.
 *
 * Su API funciona en dos pasos: el endpoint devuelve un JSON con una URL
 * temporal firmada en el campo `datos`, y el contenido real hay que pedirlo a
 * esa segunda URL (esta ya sin api_key).
 */
class AemetApi(private val http: HttpClient = HttpClient()) {

    /**
     * Resuelve un endpoint de AEMET y devuelve el cuerpo del paso 2.
     *
     * Devuelve `null` si cualquiera de los dos pasos falla, para que quien
     * llame degrade con elegancia: un fallo de AEMET no debe tumbar la app,
     * solo dejarla sin avisos.
     */
    suspend fun fetchData(path: String, timeoutSeconds: Long = 15): String? {
        val step1 = http.get(
            "$BASE_URL$path",
            headers = mapOf("api_key" to API_KEY),
            timeoutSeconds = timeoutSeconds,
        )
        if (!step1.isSuccess) return null

        val datosUrl = try {
            JSONObject(step1.decodeText()).optString("datos").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } ?: return null

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
        const val API_KEY =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJiaXJ0ZWJzQGdtYWlsLmNvbSIsImp0aSI6ImYwM2Ux" +
                "MjFmLTE2ODktNDdkMS1hYjNhLWI0MThlM2ZmMWNjMiIsImlzcyI6IkFFTUVUIiwiaWF0" +
                "IjoxNzcxNDE3OTk3LCJ1c2VySWQiOiJmMDNlMTIxZi0xNjg5LTQ3ZDEtYWIzYS1iNDE4" +
                "ZTNmZjFjYzIiLCJyb2xlIjoiIn0.npwJf-68OE2s0kIsRHVqjMqtmR9tedsgYrD03pjuYHc"
    }
}
