package com.nubo.nubo.data.local

import android.content.Context
import android.util.Log
import com.nubo.nubo.domain.model.SavedLocation
import org.json.JSONArray

/**
 * Rescata los datos que dejó la versión Flutter de Nubo.
 *
 * Al actualizar, la app Kotlin sustituye a la Flutter dentro del **mismo
 * paquete**, así que sus `SharedPreferences` siguen en disco y se pueden leer.
 * Sin esto, quien actualizase perdería todas sus ciudades guardadas y vería la
 * pantalla de bienvenida como si acabara de instalar la aplicación.
 *
 * El plugin `shared_preferences` de Flutter guarda todo en un fichero llamado
 * `FlutterSharedPreferences`, antepone `flutter.` a cada clave y serializa las
 * listas como un texto que empieza por un prefijo fijo seguido de un JSON.
 */
class FlutterPreferencesMigration(private val context: Context) {

    /**
     * Localizaciones guardadas por la app Flutter, o lista vacía si no hay
     * nada que rescatar.
     *
     * Nunca lanza: si el formato no es el esperado se prefiere empezar de cero
     * antes que impedir que la app arranque.
     */
    fun readSavedLocations(): List<SavedLocation> = try {
        val prefs = context.getSharedPreferences(FLUTTER_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.all[FLUTTER_LOCATIONS_KEY]

        val entries = when (raw) {
            // Formato habitual: texto con prefijo + JSON.
            is String -> decodeStringList(raw)
            // Algunas versiones del plugin lo guardan como Set nativo.
            is Set<*> -> raw.filterIsInstance<String>()
            else -> emptyList()
        }

        entries.mapNotNull { SavedLocation.fromPrefsString(it) }
            .also { if (it.isNotEmpty()) Log.i(TAG, "Rescatadas ${it.size} ciudades de la app Flutter") }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudieron leer las preferencias de Flutter: ${e.message}")
        emptyList()
    }

    /** Marca la migración como hecha para no repetirla en cada arranque. */
    fun markDone() {
        context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MIGRATION_DONE_KEY, true)
            .apply()
    }

    fun isDone(): Boolean =
        context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
            .getBoolean(MIGRATION_DONE_KEY, false)

    internal companion object {
        private const val TAG = "FlutterMigration"

        const val FLUTTER_PREFS = "FlutterSharedPreferences"
        const val FLUTTER_LOCATIONS_KEY = "flutter.saved_locations"

        private const val MIGRATION_PREFS = "nubo_migration"
        private const val MIGRATION_DONE_KEY = "flutter_prefs_migrated"

        /**
         * Prefijo con el que `shared_preferences` marca una lista.
         *
         * Es el base64 de "This is the prefix for a list.", literal del plugin.
         */
        const val LIST_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"

        /** Separador que el plugin pone entre el prefijo y el JSON. */
        const val JSON_MARKER = "!"

        /**
         * Decodifica el texto en el que Flutter guarda una lista de cadenas.
         *
         * Se aceptan las dos formas que ha usado el plugin —con prefijo y sin
         * él— porque el formato ha cambiado entre versiones y no merece la pena
         * atarse a una sola.
         */
        fun decodeStringList(raw: String): List<String> {
            val json = when {
                // Verificado contra el valor real que escribe la app:
                // VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!["15032|Curtis"]
                // El plugin intercala un '!' entre el prefijo y el JSON.
                raw.startsWith(LIST_PREFIX) ->
                    raw.removePrefix(LIST_PREFIX).removePrefix(JSON_MARKER)
                raw.startsWith("[") -> raw
                // Un valor suelto que no es lista: se trata como un único ítem.
                else -> return listOf(raw)
            }

            return try {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
