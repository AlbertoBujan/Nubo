import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/weather_enums.dart';
import '../providers/weather_provider.dart';
import '../widgets/alert_box.dart';
import '../widgets/hourly_view.dart';
import '../widgets/daily_view.dart';
import '../widgets/app_drawer.dart';
import '../widgets/search_location_sheet.dart';
import '../services/update_service.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:lottie/lottie.dart';

/// Pantalla principal de la aplicación meteorológica.
///
/// Utiliza un [PageView] horizontal para navegar entre las localizaciones
/// guardadas. Cada página es un [_WeatherPage] independiente.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late PageController _pageController;

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
    _checkForUpdates();
  }

  Future<void> _checkForUpdates() async {
    final updateService = GithubUpdateService();
    final result = await updateService.checkForUpdates();

    if (result['isAvailable'] == true && mounted) {
      _showUpdateDialog(result['version'], result['downloadUrl']);
    }
  }

  void _showUpdateDialog(String newVersion, String downloadUrl) {
    bool isDownloading = false;
    double downloadProgress = 0.0;

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setState) {
            return AlertDialog(
              backgroundColor: const Color(0xFF1E2A3A),
              title: const Text('¡Nueva Actualización!', style: TextStyle(color: Colors.white)),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (!isDownloading)
                    Text(
                      'Una nueva versión (v$newVersion) de Nubo está disponible. '
                      '¿Deseas descargarla e instalarla ahora?',
                      style: const TextStyle(color: Colors.white70),
                    )
                  else ...[
                    const Text(
                      'Descargando actualización...',
                      style: TextStyle(color: Colors.white70),
                    ),
                    const SizedBox(height: 16),
                    LinearProgressIndicator(
                      value: downloadProgress,
                      backgroundColor: Colors.white24,
                      color: Colors.blue.shade400,
                      minHeight: 8,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '${(downloadProgress * 100).toStringAsFixed(1)}%',
                      style: const TextStyle(color: Colors.white54, fontSize: 13),
                    ),
                  ],
                ],
              ),
              actions: [
                if (!isDownloading) ...[
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(), // Cierra el diálogo y lo omite temporalmente
                    child: const Text('Más tarde', style: TextStyle(color: Colors.white54)),
                  ),
                  ElevatedButton(
                    onPressed: () {
                      setState(() {
                        isDownloading = true;
                      });
                      
                      GithubUpdateService().downloadAndInstallUpdate(
                        downloadUrl,
                        (progress) {
                          setState(() {
                            downloadProgress = progress;
                          });
                          
                          if (progress >= 1.0) {
                            // Cerrar el diálogo cuando termine la descarga
                            // El servicio se encargará de abrir el APK
                            Navigator.of(context).pop();
                          }
                        },
                      );
                    },
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.blue.shade700),
                    child: const Text('Actualizar', style: TextStyle(color: Colors.white)),
                  ),
                ] else
                  // Durante la descarga opcionalmente podrías añadir un botón de "Cancelar", 
                  // pero por simplicidad ocultamos los botones mientras descarga.
                  const SizedBox.shrink(),
              ],
            );
          },
        );
      },
    );
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      drawer: const AppDrawer(),
      body: Consumer<WeatherProvider>(
        builder: (context, provider, _) {
          return AnimatedContainer(
            duration: const Duration(seconds: 2),
            curve: Curves.easeInOut,
            decoration: BoxDecoration(
              gradient: provider.backgroundGradient,
            ),
            child: SafeArea(
              child: _buildBodyContent(context, provider),
            ),
          );
        },
      ),

    );
  }

  Widget _buildBodyContent(BuildContext context, WeatherProvider provider) {
    // Sincronizar PageController con el índice del provider
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_pageController.hasClients &&
          _pageController.page?.round() != provider.currentIndex) {
        _pageController.animateToPage(
          provider.currentIndex,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeInOut,
        );
      }
    });

    return Column(
      children: [
        if (provider.savedLocations.isNotEmpty)
          _buildTopBar(context, provider),

        Expanded(
          child: provider.savedLocations.isEmpty
              ? _buildWelcomeState(context)
              : PageView.builder(
                  controller: _pageController,
                  itemCount: provider.savedLocations.length,
                  onPageChanged: (index) {
                    provider.switchToIndex(index);
                  },
                  itemBuilder: (context, index) {
                    final loc = provider.savedLocations[index];
                    return _WeatherPage(
                      municipioId: loc.municipioId,
                      pageIndex: index,
                      pageController: _pageController,
                    );
                  },
                ),
        ),
      ],
    );
  }

  Widget _buildWelcomeState(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Lottie.asset(
              'assets/lottie/sun_animation.json',
              width: 150,
              height: 150,
            ),
            const SizedBox(height: 32),
            const Text(
              '¡Bienvenido a Nubo!',
              style: TextStyle(
                color: Colors.white,
                fontSize: 28,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              'Para empezar a disfrutar del tiempo con datos de la AEMET, añade tu primera ubicación.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.white70, fontSize: 16, height: 1.4),
            ),
            const SizedBox(height: 48),
            ElevatedButton.icon(
              onPressed: () {
                showModalBottomSheet(
                  context: context,
                  isScrollControlled: true,
                  backgroundColor: Colors.transparent,
                  builder: (_) => const SearchLocationSheet(),
                ).then((_) {
                  if (context.mounted) {
                    context.read<WeatherProvider>().clearSearch();
                  }
                });
              },
              icon: const Icon(Icons.add_location_alt_outlined),
              label: const Text('Añadir Ubicación'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blue.shade600,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
                textStyle: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTopBar(BuildContext context, WeatherProvider provider) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
      child: Stack(
        alignment: Alignment.topCenter,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Botón hamburguesa
              _TopBarButton(
                icon: Icons.menu,
                tooltip: 'Menú',
                onTap: () => Scaffold.of(context).openDrawer(),
              ),
              
              // Indicador de refresco o texto de última actualización (derecha)
              if (provider.isRefreshing)
                Padding(
                  padding: const EdgeInsets.only(top: 2.0, right: 4.0),
                  child: Lottie.asset(
                    'assets/lottie/sun_animation.json',
                    width: 32,
                    height: 32,
                  ),
                )
              else if (provider.lastRefreshText.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 10.0, right: 8.0),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(LucideIcons.clock, size: 12, color: Colors.white70),
                      const SizedBox(width: 4),
                      Text(
                        provider.lastRefreshText,
                        style: const TextStyle(
                          color: Colors.white70,
                          fontSize: 10,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),
                )
              else
                const SizedBox(width: 36), // Espacio placeholder para mantener el centro equilibrado
            ],
          ),

          // Dots de navegación centrados de manera absoluta
          SizedBox(
            height: 36, // Misma altura que el IconButton
            child: Row(
              mainAxisSize: MainAxisSize.min, // Contrae el Row a su contenido exacto
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(
                provider.savedLocations.length,
                (i) => GestureDetector(
                  onTap: () {
                    _pageController.animateToPage(
                      i,
                      duration: const Duration(milliseconds: 300),
                      curve: Curves.easeInOut,
                    );
                  },
                  onLongPress: provider.savedLocations.length > 1
                      ? () => _confirmRemove(context, provider, i)
                      : null,
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 250),
                    margin: const EdgeInsets.symmetric(horizontal: 3),
                    width: provider.currentIndex == i ? 18 : 6,
                    height: 6,
                    decoration: BoxDecoration(
                      color: provider.currentIndex == i
                          ? Colors.white
                          : Colors.white30,
                      borderRadius: BorderRadius.circular(3),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _confirmRemove(
    BuildContext context,
    WeatherProvider provider,
    int index,
  ) async {
    final loc = provider.savedLocations[index];
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E2A3A),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text(
          'Eliminar localización',
          style: TextStyle(color: Colors.white),
        ),
        content: Text(
          '¿Quieres eliminar "${loc.nombre}" de tus localizaciones guardadas?',
          style: const TextStyle(color: Colors.white70),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancelar', style: TextStyle(color: Colors.white54)),
          ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: Text(
              'Eliminar',
              style: TextStyle(color: Colors.red.shade300),
            ),
          ),
        ],
      ),
    );

    if (confirmed == true && context.mounted) {
      final weatherProvider = context.read<WeatherProvider>();
      await weatherProvider.removeLocation(index);
      // Sincronizar PageController tras eliminar
      if (_pageController.hasClients) {
        _pageController.jumpToPage(weatherProvider.currentIndex);
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Widget de cada página del PageView
// ---------------------------------------------------------------------------

/// Página individual de una localización en el [PageView].
class _WeatherPage extends StatefulWidget {
  final String municipioId;
  final int pageIndex;
  final PageController pageController;

  const _WeatherPage({
    required this.municipioId,
    required this.pageIndex,
    required this.pageController,
  });

  @override
  State<_WeatherPage> createState() => _WeatherPageState();
}

class _WeatherPageState extends State<_WeatherPage> {
  @override
  Widget build(BuildContext context) {
    return Consumer<WeatherProvider>(
      builder: (context, provider, _) {
        final isLoading = provider.isLoadingFor(widget.municipioId);
        final errorMessage = provider.errorMessageFor(widget.municipioId);
        final daily = provider.dailyForecastsFor(widget.municipioId);
        final hourly = provider.hourlyForecastsFor(widget.municipioId);
        final cityName = provider.cityNameFor(widget.municipioId);

        Widget content;
        if (isLoading) {
          content = _buildLoadingState(cityName);
        } else if (errorMessage != null) {
          content = _buildErrorState(provider);
        } else if (daily.isEmpty || hourly.isEmpty) {
          content = _buildNoDataState(provider, cityName);
        } else {
          content = _buildContent(provider, cityName);
        }

        return content;
      },
    );
  }

  Widget _buildNoDataState(WeatherProvider provider, String cityName) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.cloud_off, size: 64, color: Colors.white54),
          const SizedBox(height: 16),
          Text(
            'Sin datos para $cityName',
            style: const TextStyle(color: Colors.white, fontSize: 18),
          ),
          const SizedBox(height: 8),
          const Text(
            'Toca para obtener el tiempo actual',
            style: TextStyle(color: Colors.white54, fontSize: 14),
          ),
          const SizedBox(height: 24),
          ElevatedButton.icon(
            onPressed: () => provider.refreshWeather(widget.municipioId),
            icon: const Icon(Icons.cloud_download),
            label: const Text('Descargar Datos'),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.blue.shade700,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLoadingState(String cityName) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Lottie.asset(
            'assets/lottie/sun_animation.json',
            width: 120,
            height: 120,
          ),
        ],
      ),
    );
  }

  Widget _buildErrorState(WeatherProvider provider) {
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      children: [
        SizedBox(height: MediaQuery.of(context).size.height * 0.25),
        const Center(child: Text('⚠️', style: TextStyle(fontSize: 48))),
        const SizedBox(height: 16),
        const Center(
          child: Text(
            'Error al obtener datos',
            style: TextStyle(
              color: Colors.white,
              fontSize: 20,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        const SizedBox(height: 8),
        Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: Text(
              provider.errorMessageFor(widget.municipioId) ?? 'Error desconocido',
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white54, fontSize: 14),
            ),
          ),
        ),
        const SizedBox(height: 24),
        Center(
          child: ElevatedButton.icon(
            onPressed: () => provider.refreshWeather(widget.municipioId),
            icon: const Icon(Icons.refresh),
            label: const Text('Reintentar'),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.blue.shade700,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildContent(WeatherProvider provider, String cityName) {
    final tempRange = provider.todayTempRangeFor(widget.municipioId);
    final skyCode = provider.currentSkyCodeFor(widget.municipioId);
    final weather = WeatherCode.fromCode(skyCode);
    final currentTemp = provider.currentTemperatureFor(widget.municipioId);
    final skyDesc = provider.currentSkyDescriptionFor(widget.municipioId);
    final alerts = provider.alertsFor(widget.municipioId);
    final hourlyForecasts = provider.hourlyForecastsFor(widget.municipioId);
    final dailyForecasts = provider.dailyForecastsFor(widget.municipioId);

    return RefreshIndicator(
      onRefresh: () => provider.refreshAllWeather(),
      backgroundColor: const Color(0xFF1E2A3A),
      color: Colors.white,
      child: SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: Column(
          children: [
            // --- Nombre de la ciudad ---
            Padding(
              padding: const EdgeInsets.only(top: 4, bottom: 0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.location_on, color: Colors.white54, size: 16),
                  const SizedBox(width: 4),
                  Text(
                    cityName,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 22,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),

            // --- Información principal del tiempo (sin icono grande) ---
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Column(
                children: [
                  Text(
                    currentTemp != null ? '$currentTemp°' : '--°',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 72,
                      fontWeight: FontWeight.w200,
                      height: 1,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    skyDesc.isNotEmpty ? skyDesc : weather.description,
                    style: const TextStyle(color: Colors.white70, fontSize: 18),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Máx: ${tempRange.$1 ?? '--'}°  Mín: ${tempRange.$2 ?? '--'}°',
                    style: const TextStyle(color: Colors.white54, fontSize: 15),
                  ),
                ],
              ),
            ),

            // --- Alertas meteorológicas (si las hay) ---
            AlertBox(alerts: alerts),

            // --- Caja 1: Pronóstico por horas ---
            HourlyView(forecasts: hourlyForecasts),

            const SizedBox(height: 8),

            // --- Caja 2: Pronóstico por días (scroll horizontal interno) ---
            DailyView(forecasts: dailyForecasts, alerts: alerts),

            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Botón de la barra superior
// ---------------------------------------------------------------------------

class _TopBarButton extends StatelessWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;

  const _TopBarButton({
    required this.icon,
    required this.tooltip,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Icon(icon, color: Colors.white70, size: 20),
        ),
      ),
    );
  }
}
