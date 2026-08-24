package com.nubo.nubo.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nubo.nubo.data.local.WeatherStorage
import com.nubo.nubo.data.location.LocationService
import com.nubo.nubo.data.remote.AemetApi
import com.nubo.nubo.data.remote.AlertService
import com.nubo.nubo.data.remote.HttpClient
import com.nubo.nubo.data.remote.MunicipioSearchService
import com.nubo.nubo.data.remote.OpenMeteoApi
import com.nubo.nubo.data.remote.UpdateService
import com.nubo.nubo.data.repository.AlertRepository
import com.nubo.nubo.data.repository.AlertRepositoryImpl
import com.nubo.nubo.data.repository.LocationRepository
import com.nubo.nubo.data.repository.LocationRepositoryImpl
import com.nubo.nubo.data.repository.WeatherRepository
import com.nubo.nubo.data.repository.WeatherRepositoryImpl
import com.nubo.nubo.ui.weather.WeatherViewModel

/**
 * Dependencias compartidas.
 *
 * Se resuelve a mano en vez de con Hilt: la app tiene un único ViewModel y un
 * puñado de servicios, así que un contenedor completo añadiría procesadores de
 * anotaciones y tiempo de compilación sin resolver ningún problema real.
 *
 * El maestro de municipios se comparte a propósito: son unos 8.000 registros
 * que se descargan una vez y conviene que el worker y la interfaz reutilicen la
 * misma copia en memoria.
 */
object ServiceLocator {

    private val http by lazy { HttpClient() }
    private val aemetApi by lazy { AemetApi(http) }

    val municipioSearchService: MunicipioSearchService by lazy {
        MunicipioSearchService(aemetApi)
    }

    private val openMeteoApi by lazy { OpenMeteoApi(http) }

    @Volatile
    private var storage: WeatherStorage? = null

    fun weatherStorage(context: Context): WeatherStorage =
        storage ?: synchronized(this) {
            storage ?: WeatherStorage(context.applicationContext).also { storage = it }
        }

    fun locationRepository(context: Context): LocationRepository = LocationRepositoryImpl(
        searchService = municipioSearchService,
        locationService = LocationService(context.applicationContext),
    )

    fun weatherRepository(context: Context): WeatherRepository = WeatherRepositoryImpl(
        locationRepository = locationRepository(context),
        api = openMeteoApi,
    )

    fun alertRepository(): AlertRepository = AlertRepositoryImpl(AlertService(aemetApi))

    fun updateService(context: Context): UpdateService =
        UpdateService(context.applicationContext, http)

    /** Factoría del ViewModel de la pantalla del tiempo. */
    fun weatherViewModelFactory(context: Context): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                return WeatherViewModel(
                    weatherRepository = weatherRepository(appContext),
                    alertRepository = alertRepository(),
                    locationRepository = locationRepository(appContext),
                    storage = weatherStorage(appContext),
                ) as T
            }
        }
}
