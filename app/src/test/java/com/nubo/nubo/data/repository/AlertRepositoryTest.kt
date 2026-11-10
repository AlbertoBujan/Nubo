package com.nubo.nubo.data.repository

import com.nubo.nubo.data.remote.AlertService
import com.nubo.nubo.data.remote.Coordinates
import com.nubo.nubo.domain.model.SavedLocation
import com.nubo.nubo.domain.model.WeatherAlert
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * Lo que separa "no hay avisos" de "no he podido preguntar".
 *
 * No es una distinción cosmética: la primera sustituye a lo que la ciudad ya
 * tenía —en pantalla y en la caché— y la segunda no. Mientras las dos fueron
 * la lista vacía, cada 401 de AEMET dejaba la ciudad sin los avisos que
 * acababa de enseñar.
 */
class AlertRepositoryTest {

    private val coruna = SavedLocation(
        locationId = "om:3119841",
        nombre = "A Coruña",
        lat = 43.371,
        lon = -8.396,
        timeZone = "Europe/Madrid",
        countryCode = "ES",
    )

    private val tokio = coruna.copy(
        locationId = "om:1850147",
        nombre = "Tokio",
        timeZone = "Asia/Tokyo",
        countryCode = "JP",
    )

    private val aviso = WeatherAlert(
        nivel = "amarillo",
        event = "Aviso de costeros de nivel amarillo",
        headline = "Costa - Noroeste de A Coruña",
        description = "",
        instruction = "",
        areaDescription = "Costa - Noroeste de A Coruña",
        onset = null,
        expires = null,
        probability = "",
    )

    @Test
    fun `fuera de España no hay avisos, y eso es una respuesta`() = runTest {
        val repository = repositoryWith(zona = null, answer = null)

        assertEquals(AlertsResult.Fetched(emptyList()), repository.getAlerts(tokio))
    }

    @Test
    fun `quedarse sin zona dentro de España es no haber podido preguntar`() = runTest {
        // El maestro de municipios del que sale la zona se baja de AEMET con
        // la misma clave que los avisos, así que se cae con ellos. Eso no
        // significa que la ciudad no tenga avisos.
        val repository = repositoryWith(zona = null, answer = listOf(aviso))

        assertEquals(AlertsResult.Unavailable, repository.getAlerts(coruna))
    }

    @Test
    fun `si AEMET no contesta, no se ha podido preguntar`() = runTest {
        val repository = repositoryWith(zona = "711501", answer = null)

        assertEquals(AlertsResult.Unavailable, repository.getAlerts(coruna))
    }

    @Test
    fun `una respuesta sin avisos sigue siendo una respuesta`() = runTest {
        val repository = repositoryWith(zona = "711501", answer = emptyList())

        assertEquals(AlertsResult.Fetched(emptyList()), repository.getAlerts(coruna))
    }

    @Test
    fun `los avisos que llegan se devuelven tal cual`() = runTest {
        val repository = repositoryWith(zona = "711501", answer = listOf(aviso))

        assertEquals(AlertsResult.Fetched(listOf(aviso)), repository.getAlerts(coruna))
    }

    private fun repositoryWith(zona: String?, answer: List<WeatherAlert>?) =
        AlertRepositoryImpl(FakeLocations(zona), FakeAlerts(answer))

    /** `null` es lo que devuelve el servicio cuando AEMET no ha contestado. */
    private class FakeAlerts(private val answer: List<WeatherAlert>?) : AlertService() {
        override suspend fun fetchAlerts(zonaAviso: String, zone: ZoneId) = answer
    }

    private class FakeLocations(private val zona: String?) : LocationRepository {
        override suspend fun zonaAvisoFor(location: SavedLocation) = zona
        override suspend fun searchByName(query: String): List<SavedLocation> = emptyList()
        override suspend fun findNearest(lat: Double, lon: Double): SavedLocation? = null
        override suspend fun getCurrentPosition() = Coordinates(0.0, 0.0)
        override suspend fun resolveLegacy(location: SavedLocation): SavedLocation? = null
    }
}
