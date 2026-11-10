package com.nubo.nubo.data.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * De dónde sale la clave de AEMET, que no es eterna.
 *
 * AEMET pone fecha de caducidad a sus apiKey y, pasada, contesta 401 a casi
 * todo: la app se queda sin avisos sin decir nada. Con la clave solo embebida
 * en el binario, renovarla exigía compilar y publicar una versión, y las
 * instalaciones que no se actualizasen quedaban rotas para siempre.
 *
 * Así que la clave buena se publica **en el repositorio**, en un fichero que la
 * app lee igual que ya mira ahí si hay versión nueva. Renovarla pasa a ser un
 * commit de una línea: sin release, sin reinstalar, y arregla también lo que ya
 * está instalado.
 *
 * Solo se baja **cuando AEMET falla**, nunca por costumbre: mientras la clave
 * que lleva dentro funcione, esto no gasta ni una petición. Y como el reintento
 * ocurre dentro de la misma llamada, no hace falta guardarla en disco — basta
 * con recordarla mientras el proceso viva. El precio es una petición de ciento
 * y pico bytes la primera vez que AEMET falla en cada arranque, y a cambio no
 * hay un formato persistido más que mantener.
 */
class AemetKeyring(
    private val http: HttpClient = HttpClient(),
    /** La que viaja en el binario. Es el respaldo, no la fuente de verdad. */
    private val bakedIn: String = AemetApi.API_KEY,
    private val url: String = PUBLISHED_KEY_URL,
) {
    @Volatile
    private var published: String? = null

    // Dos ciudades refrescando a la vez fallarían a la vez; sin esto se
    // bajaría el fichero dos veces para llegar a la misma clave.
    private val renewing = Mutex()

    /** La mejor clave conocida ahora mismo. */
    fun current(): String = published ?: bakedIn

    /**
     * Una clave distinta de [used], si es que hay otra publicada.
     *
     * Devuelve `null` cuando no se puede bajar el fichero o cuando trae la
     * misma que acaba de fallar, que es la señal de que insistir no va a
     * arreglar nada: el problema no era la clave.
     */
    suspend fun renewed(used: String): String? = renewing.withLock {
        // Otra llamada pudo renovarla mientras esta esperaba el candado.
        published?.let { return@withLock it.takeIf { key -> key != used } }

        val fresh = download()?.takeIf { it != used } ?: return@withLock null
        published = fresh
        fresh
    }

    private suspend fun download(): String? = try {
        val response = http.get(url, timeoutSeconds = TIMEOUT_SECONDS)
        if (response.isSuccess) parseKey(response.decodeText()) else null
    } catch (_: Exception) {
        null
    }

    companion object {
        /**
         * El fichero vive en `main`, así que renovar es editarlo y empujar.
         * No es un secreto: la clave ya viaja en cada APK publicado y está en
         * el historial del repositorio desde la época de Flutter.
         */
        const val PUBLISHED_KEY_URL =
            "https://raw.githubusercontent.com/AlbertoBujan/Nubo/main/aemet-key.json"

        private const val TIMEOUT_SECONDS = 10L

        /**
         * La clave del fichero, si trae una con forma de JWT.
         *
         * Se comprueba la forma porque lo que contesta esa URL cuando algo va
         * mal no es un error: es una página HTML con código 200 a veces, y
         * tomarla por clave sustituiría una que funciona por basura.
         */
        internal fun parseKey(body: String): String? = try {
            JSONObject(body).optString("key")
                .trim()
                .takeIf { it.count { c -> c == '.' } == 2 && it.length > 40 }
        } catch (_: Exception) {
            null
        }
    }
}
