package com.nubo.nubo.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nubo.nubo.domain.astro.SunTimes
import com.nubo.nubo.domain.model.DailyForecast
import com.nubo.nubo.domain.model.HourlyForecast
import com.nubo.nubo.domain.model.SavedLocation
import com.nubo.nubo.domain.model.WeatherAlert
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Datos meteorológicos rehidratados desde la caché en disco. */
data class CachedWeather(
    val daily: List<DailyForecast>,
    val hourly: List<HourlyForecast>,
    val alerts: List<WeatherAlert>,
    val sunTimes: SunTimes?,
    val lastUpdated: LocalDateTime,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nubo")

/**
 * Persistencia de localizaciones y predicciones.
 *
 * Del tiempo se guarda el **JSON crudo de Open-Meteo**, no los modelos ya
 * parseados. Así la caché no se invalida cuando cambia la forma de los modelos
 * —como pasó al recalcular el icono diario— y evita mantener dos formatos de
 * serialización en paralelo.
 */
class WeatherStorage(private val context: Context) {

    private val store get() = context.dataStore

    // ── Localizaciones guardadas ─────────────────────────────────────────────

    private val flutterMigration by lazy { FlutterPreferencesMigration(context) }

    suspend fun loadLocations(): List<SavedLocation> {
        val stored = store.data.first()[LOCATIONS_KEY]

        // Primera ejecución tras actualizar desde la app Flutter: sus ciudades
        // siguen en las SharedPreferences del mismo paquete y se recuperan.
        if (stored == null && !flutterMigration.isDone()) {
            val migrated = flutterMigration.readSavedLocations()
            flutterMigration.markDone()
            if (migrated.isNotEmpty()) {
                saveLocations(migrated)
                return migrated
            }
        }

        val raw = stored ?: return emptyList()
        // Un Set no conserva el orden, así que el índice va como prefijo.
        return raw.mapNotNull { entry ->
            val separator = entry.indexOf(':')
            if (separator < 0) return@mapNotNull null
            val order = entry.substring(0, separator).toIntOrNull() ?: return@mapNotNull null
            val location = SavedLocation.fromPrefsString(entry.substring(separator + 1))
                ?: return@mapNotNull null
            order to location
        }.sortedBy { it.first }.map { it.second }
    }

    suspend fun saveLocations(locations: List<SavedLocation>) {
        store.edit { prefs ->
            prefs[LOCATIONS_KEY] = locations
                .mapIndexed { index, location -> "$index:${location.toPrefsString()}" }
                .toSet()
        }
    }

    // ── Predicción por municipio ─────────────────────────────────────────────

    suspend fun loadWeather(municipioId: String): CachedWeather? {
        val json = store.data.first()[weatherKey(municipioId)] ?: return null

        return try {
            val decoded = JSONObject(json)
            val openMeteo = decoded.optJSONObject("openMeteo") ?: return null

            CachedWeather(
                daily = DailyForecast.fromOpenMeteoJson(openMeteo),
                hourly = HourlyForecast.fromOpenMeteoJson(openMeteo),
                alerts = decoded.optJSONArray("alerts").toAlerts(),
                sunTimes = decoded.optJSONObject("sunTimes")?.toSunTimes(),
                lastUpdated = LocalDateTime.parse(decoded.getString("lastUpdated")),
            )
        } catch (_: Exception) {
            // Una caché ilegible se descarta en vez de arrastrarse.
            removeWeather(municipioId)
            null
        }
    }

    suspend fun saveWeather(
        municipioId: String,
        rawJson: JSONObject,
        alerts: List<WeatherAlert>,
        sunTimes: SunTimes?,
        updatedAt: LocalDateTime,
    ) {
        val payload = JSONObject().apply {
            put("openMeteo", rawJson)
            put("alerts", JSONArray().apply { alerts.forEach { put(it.toJson()) } })
            put(
                "sunTimes",
                sunTimes?.let {
                    JSONObject().apply {
                        put("sunrise", it.sunrise.toString())
                        put("sunset", it.sunset.toString())
                    }
                } ?: JSONObject.NULL,
            )
            put("lastUpdated", updatedAt.toString())
        }

        store.edit { prefs -> prefs[weatherKey(municipioId)] = payload.toString() }
    }

    suspend fun removeWeather(municipioId: String) {
        store.edit { prefs -> prefs.remove(weatherKey(municipioId)) }
    }

    // ── Preferencia de actualización en segundo plano ────────────────────────

    suspend fun loadBackgroundIntervalIndex(): Int =
        store.data.first()[BACKGROUND_INTERVAL_KEY] ?: 0

    suspend fun saveBackgroundIntervalIndex(index: Int) {
        store.edit { prefs -> prefs[BACKGROUND_INTERVAL_KEY] = index }
    }

    private companion object {
        val LOCATIONS_KEY = stringSetPreferencesKey("saved_locations")
        val BACKGROUND_INTERVAL_KEY = intPreferencesKey("bg_update_interval")

        fun weatherKey(municipioId: String) =
            stringPreferencesKey("weather_data_$municipioId")

        fun JSONArray?.toAlerts(): List<WeatherAlert> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { i ->
                optJSONObject(i)?.let { runCatching { WeatherAlert.fromJson(it) }.getOrNull() }
            }
        }

        fun JSONObject.toSunTimes(): SunTimes? = runCatching {
            SunTimes(
                sunrise = LocalDateTime.parse(getString("sunrise")),
                sunset = LocalDateTime.parse(getString("sunset")),
            )
        }.getOrNull()
    }
}
