package com.nubo.nubo

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nubo.nubo.data.remote.AvailableUpdate
import com.nubo.nubo.di.ServiceLocator
import com.nubo.nubo.ui.theme.NuboTheme
import com.nubo.nubo.ui.weather.AppDrawer
import com.nubo.nubo.ui.weather.SearchLocationSheet
import com.nubo.nubo.ui.weather.UpdateDialog
import com.nubo.nubo.data.remote.UpdateService
import com.nubo.nubo.ui.components.LocalUnits
import com.nubo.nubo.ui.weather.UiScale
import com.nubo.nubo.ui.weather.AboutDialog
import com.nubo.nubo.ui.weather.CheckingUpdatesDialog
import com.nubo.nubo.ui.weather.NotificationsBlockedDialog
import com.nubo.nubo.ui.weather.UpdateCheckDialog
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

    private var onNotificationPermissionResult: ((Boolean) -> Unit)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onNotificationPermissionResult?.invoke(granted)
        onNotificationPermissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NuboTheme {
                NuboApp(
                    requestNotificationPermission = { callback ->
                        // Antes de Android 13 no existe el permiso: pedirlo no
                        // abriría ningún diálogo y el interruptor se quedaría
                        // esperando una respuesta que no llega.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            callback(true)
                        } else {
                            onNotificationPermissionResult = callback
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                        }
                    },
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
private fun NuboApp(
    requestLocationPermission: ((Boolean) -> Unit) -> Unit,
    requestNotificationPermission: ((Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: WeatherViewModel = viewModel(
        factory = ServiceLocator.weatherViewModelFactory(context),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showSearch by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var drawerSettings by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }
    // Solo la comprobación **manual** informa de que no hay novedades: la del
    // arranque no la ha pedido nadie y no debe interrumpir para decir que no
    // pasa nada.
    var manualCheck by remember { mutableStateOf<UpdateService.UpdateCheck?>(null) }
    var checking by remember { mutableStateOf(false) }
    var notificationsBlocked by remember { mutableStateOf(false) }

    val updateService = remember(context) { ServiceLocator.updateService(context) }

    // La comprobación de actualizaciones va al arrancar, una sola vez.
    LaunchedEffect(Unit) {
        (updateService.checkForUpdates() as? UpdateService.UpdateCheck.Available)?.let {
            update = it.update
        }
    }

    // La tarea periódica se reprograma con la preferencia guardada: WorkManager
    // pierde su registro tras reinstalar la app.
    LaunchedEffect(state.backgroundInterval, state.alertNotifications, state.isInitialized) {
        if (state.isInitialized) {
            BackgroundUpdateWorker.schedule(
                context,
                state.backgroundInterval,
                state.alertNotifications,
            )
        }
    }

    // Los ajustes son el otro contenido del cajón, no un destino: al cerrarlo
    // vuelve a lo que se abre por defecto, la lista de ubicaciones.
    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen) drawerSettings = false
    }

    // Estando en los ajustes, atrás vuelve a la lista en vez de cerrar el menú.
    BackHandler(enabled = drawerState.isOpen && drawerSettings) { drawerSettings = false }

    fun locateThenAdd() {
        requestLocationPermission { granted ->
            if (granted) viewModel.addCurrentLocation()
        }
    }

    // Agrandar la interfaz es multiplicar la densidad: crecen los dp y los sp a
    // la vez, así que las cajas acompañan a su texto en vez de quedarse
    // pequeñas. El `fontScale` del sistema se respeta tal cual y se acumula.
    val density = LocalDensity.current
    val scaled = remember(density, state.uiScale) {
        Density(density.density * state.uiScale.factor, density.fontScale)
    }

    CompositionLocalProvider(
        LocalUnits provides state.units,
        LocalDensity provides scaled,
    ) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                state = state,
                uiScale = state.uiScale,
                onUiScaleChange = viewModel::setUiScale,
                units = state.units,
                onUnitsChange = viewModel::setUnits,
                interval = state.backgroundInterval,
                showSettings = drawerSettings,
                onToggleSettings = { drawerSettings = it },
                onSelectCity = { index ->
                    viewModel.onPageSettled(index)
                    scope.launch { drawerState.close() }
                },
                onRemoveCity = viewModel::removeLocation,
                onUndoRemove = viewModel::undoRemove,
                onMoveCity = viewModel::moveLocation,
                onAddLocation = {
                    showSearch = true
                    scope.launch { drawerState.close() }
                },
                onIntervalChange = viewModel::setBackgroundInterval,
                alertNotifications = state.alertNotifications,
                onAlertNotificationsChange = { enabled ->
                    if (!enabled) {
                        viewModel.setAlertNotifications(false)
                    } else {
                        // El interruptor no se enciende hasta que el sistema
                        // concede el permiso: quedaría encendido sin que
                        // llegase nunca una notificación.
                        requestNotificationPermission { granted ->
                            if (granted) {
                                viewModel.setAlertNotifications(true)
                            } else {
                                notificationsBlocked = true
                            }
                        }
                    }
                },
                onCheckUpdates = {
                    scope.launch {
                        checking = true
                        drawerState.close()
                        val result = updateService.checkForUpdates()
                        checking = false
                        when (result) {
                            is UpdateService.UpdateCheck.Available -> update = result.update
                            else -> manualCheck = result
                        }
                    }
                },
                onShowAbout = { showAbout = true },
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
            nearby = state.searchNearby,
            onQueryChange = viewModel::search,
            onNearbyChange = viewModel::setSearchNearby,
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

    if (checking) {
        CheckingUpdatesDialog()
    }

    manualCheck?.let { result ->
        UpdateCheckDialog(result = result, onDismiss = { manualCheck = null })
    }

    if (notificationsBlocked) {
        NotificationsBlockedDialog(
            onOpenSettings = {
                notificationsBlocked = false
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    },
                )
            },
            onDismiss = { notificationsBlocked = false },
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    }
}
