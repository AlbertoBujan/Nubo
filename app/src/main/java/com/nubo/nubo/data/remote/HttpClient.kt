package com.nubo.nubo.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/** Respuesta cruda, ya leída a memoria para poder reintentar sin fugas. */
data class HttpResult(
    val code: Int,
    val bytes: ByteArray,
) {
    val isSuccess: Boolean get() = code in 200..299

    /**
     * Decodifica como UTF-8 y, si el contenido no es válido, reintenta en
     * ISO-8859-1. AEMET sirve algunos payloads en latin1 y otros en UTF-8 sin
     * declararlo de forma fiable.
     */
    fun decodeText(): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        // El carácter de reemplazo delata que no era UTF-8 válido.
        return if (utf8.contains('�')) {
            String(bytes, Charset.forName("ISO-8859-1"))
        } else {
            utf8
        }
    }

    override fun equals(other: Any?): Boolean =
        other is HttpResult && other.code == code && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = 31 * code + bytes.contentHashCode()
}

/**
 * Cliente HTTP con reintentos y espera creciente.
 *
 * Reintenta ante error de red y ante 429 (AEMET limita por minuto), doblando la
 * espera en cada intento. Un 4xx distinto de 429 no se reintenta: no va a
 * cambiar por insistir.
 */
class HttpClient(
    private val client: OkHttpClient = defaultClient,
    private val maxRetries: Int = 3,
    private val initialBackoffMillis: Long = 500,
) {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = 10,
    ): HttpResult = withContext(Dispatchers.IO) {
        var backoff = initialBackoffMillis
        var lastError: IOException? = null

        val call = client.newBuilder()
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()

        for (attempt in 0..maxRetries) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .build()

                call.newCall(request).execute().use { response ->
                    val result = HttpResult(response.code, response.body?.bytes() ?: ByteArray(0))
                    if (result.code == 429 && attempt < maxRetries) {
                        // Cae al backoff de abajo.
                    } else {
                        return@withContext result
                    }
                }
            } catch (e: IOException) {
                lastError = e
                if (attempt == maxRetries) throw e
            }

            delay(backoff)
            backoff *= 2
        }

        throw lastError ?: IOException("Error de red persistente en $url")
    }

    companion object {
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
