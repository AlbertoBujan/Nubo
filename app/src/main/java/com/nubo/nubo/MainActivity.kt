package com.nubo.nubo

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nubo.nubo.data.remote.AvailableUpdate
import com.nubo.nubo.di.ServiceLocator
import com.nubo.nubo.ui.theme.NuboTheme
import com.nubo.nubo.ui.weather.AppDrawer
import com.nubo.nubo.ui.weather.SearchLocationSheet
import com.nubo.nubo.ui.weather.UpdateDialog
import com.nubo.nubo.ui.weather.AboutDialog
import com.nubo.nubo.ui.weather.WeatherScreen
import com.nubo.nubo.ui.weather.WeatherViewModel
import com.nubo.nubo.work.BackgroundUpdateWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** El permiso se pide solo cuando el usuario elige localizarse. */
    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        onPermissionResult?.invoke(grants.values.any { it })
        onPermissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NuboTheme {
                NuboApp(
                    requestLocationPermission = { callback ->
                        onPermissionResult = callback
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun NuboApp(requestLocationPermission: ((Boolean) -> Unit) -> Unit) {
    val context = LocalContext.current
    val viewModel: WeatherViewModel = viewModel(
        factory = ServiceLocator.weatherViewModelFactory(context),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showSearch by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }

    val updateService = remember(context) { ServiceLocator.updateService(context) }

    // La comprobación de actualizaciones va al arrancar, una sola vez.
    LaunchedEffect(Unit) {
        update = updateService.checkForUpdates()
    }

    // La tarea periódica se reprograma con la preferencia guardada: WorkManager
    // pierde su registro tras reinstalar la app.
    LaunchedEffect(state.backgroundInterval, state.isInitialized) {
        if (state.isInitialized) {
            BackgroundUpdateWorker.schedule(context, state.backgroundInterval)
        }
    }

    fun locateThenAdd() {
        requestLocationPermission { granted ->
            if (granted) viewModel.addCurrentLocation()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                state = state,
                onSelectCity = { index ->
                    viewModel.onPageSettled(index)
                    scope.launch { drawerState.close() }
                },
                onRemoveCity = viewModel::removeLocation,
                onAddLocation = {
                    showSearch = true
                    scope.launch { drawerState.close() }
                },
                onRefreshAll = {
                    viewModel.refreshAll()
                    scope.launch { drawerState.close() }
                },
                onIntervalChange = viewModel::setBackgroundInterval,
                onCheckUpdates = {
                    scope.launch {
                        update = updateService.checkForUpdates()
                        drawerState.close()
                    }
                },
                onShowAbout = {
                    showAbout = true
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        WeatherScreen(
            state = state,
            onRefreshAll = viewModel::refreshAll,
            onRetry = viewModel::refreshWeather,
            onPageSettled = viewModel::onPageSettled,
            onOpenMenu = { scope.launch { drawerState.open() } },
            onRemoveLocation = viewModel::removeLocation,
            onAddLocation = { showSearch = true },
        )
    }

    if (showSearch) {
        SearchLocationSheet(
            results = state.searchResults,
            isSearching = state.isSearching,
            isLocating = state.isLocating,
            onQueryChange = viewModel::search,
            onSelect = { location ->
                viewModel.addLocation(location)
                viewModel.clearSearch()
                showSearch = false
            },
            onUseCurrentLocation = {
                locateThenAdd()
                showSearch = false
            },
            onDismiss = {
                viewModel.clearSearch()
                showSearch = false
            },
        )
    }

    update?.let { available ->
        UpdateDialog(
            update = available,
            onDismiss = { update = null },
            onInstall = { onProgress -> updateService.downloadAndInstall(available.downloadUrl, onProgress) },
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}
