package com.nubo.nubo.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.nubo.nubo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** Actualización disponible en GitHub Releases. */
data class AvailableUpdate(
    val version: String,
    val downloadUrl: String,
)

/**
 * Comprueba e instala actualizaciones publicadas como GitHub Releases.
 *
 * Nubo se distribuye fuera de Play, así que la propia app consulta la release
 * más reciente del repositorio y se descarga el APK. Es el motivo por el que la
 * migración a Kotlin conserva este repositorio: las instalaciones existentes
 * miran aquí, y publicar en otro sitio las dejaría sin actualizaciones.
 */
class UpdateService(
    private val context: Context,
    private val http: HttpClient = HttpClient(),
) {

    /**
     * Resultado de mirar si hay versión nueva.
     *
     * "No hay novedades" y "no he podido preguntar" son cosas distintas y el
     * único sitio donde se sabe es aquí: devolver `null` para las dos obligaba a
     * la pantalla a callar siempre, porque decir "estás al día" sin red sería
     * mentira. Con esto, la comprobación manual puede responder a las dos.
     */
    sealed interface UpdateCheck {
        data class Available(val update: AvailableUpdate) : UpdateCheck
        data object UpToDate : UpdateCheck
        data object Failed : UpdateCheck
    }

    suspend fun checkForUpdates(): UpdateCheck = try {
        val response = http.get(RELEASES_URL, timeoutSeconds = 10)
        if (!response.isSuccess) {
            UpdateCheck.Failed
        } else {
            val data = JSONObject(response.decodeText())
            val latest = data.optString("tag_name").removePrefix("v")
            val apkUrl = data.optJSONArray("assets")?.let { assets ->
                (0 until assets.length())
                    .mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    ?.optString("browser_download_url")
            }

            when {
                // Una release sin APK es una publicación a medias, no un "estás
                // al día": no hay nada que ofrecer, pero tampoco que confirmar.
                apkUrl.isNullOrBlank() -> UpdateCheck.Failed
                isNewerVersion(BuildConfig.VERSION_NAME, latest) ->
                    UpdateCheck.Available(AvailableUpdate(latest, apkUrl))
                else -> UpdateCheck.UpToDate
            }
        }
    } catch (_: Exception) {
        UpdateCheck.Failed
    }

    /**
     * Descarga el APK informando del progreso y lanza el instalador.
     *
     * @param onProgress recibe valores de 0 a 1.
     */
    suspend fun downloadAndInstall(
        url: String,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "nubo_update.apk")
            if (file.exists()) file.delete()

            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                val body = response.body ?: return@withContext false
                if (!response.isSuccessful) return@withContext false

                val total = body.contentLength()
                var downloaded = 0L

                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) onProgress(downloaded.toFloat() / total)
                        }
                    }
                }
            }

            onProgress(1f)
            launchInstaller(file)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun launchInstaller(apk: File) {
        // Desde Android 7 no se puede pasar un file:// a otra app, hace falta
        // un content:// servido por FileProvider.
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        private const val REPO_OWNER = "AlbertoBujan"
        private const val REPO_NAME = "Nubo"
        private const val RELEASES_URL =
            "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
        private const val DOWNLOAD_BUFFER = 8 * 1024

        /**
         * Compara dos versiones tipo `0.2.10` segmento a segmento.
         *
         * Se compara numéricamente y no como texto, porque "0.2.10" es
         * posterior a "0.2.9" pero alfabéticamente iría antes.
         */
        fun isNewerVersion(current: String, candidate: String): Boolean {
            val a = current.split('.', '-').mapNotNull { it.toIntOrNull() }
            val b = candidate.split('.', '-').mapNotNull { it.toIntOrNull() }
            if (b.isEmpty()) return false

            for (i in 0 until maxOf(a.size, b.size)) {
                val left = a.getOrElse(i) { 0 }
                val right = b.getOrElse(i) { 0 }
                if (right != left) return right > left
            }
            return false
        }
    }
}
