import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/daily_forecast.dart';
import '../models/hourly_forecast.dart';
import '../models/weather_alert.dart';
import '../models/weather_enums.dart';
import '../providers/weather_provider.dart';
import '../utils/sun_calculator.dart';
import '../utils/moon_calculator.dart';
import '../widgets/alert_box.dart';
import '../widgets/hourly_view.dart';
import '../widgets/daily_view.dart';
import '../widgets/sun_moon_card.dart';
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
          // El contenido se pasa como `child` del AnimatedBuilder para que
          // NO se reconstruya en cada frame del scroll — solo el gradiente.
          return AnimatedBuilder(
            animation: _pageController,
            child: SafeArea(
              child: _buildBodyContent(context, provider),
            ),
            builder: (context, child) {
              final gradient = _interpolatedGradient(provider);
              return DecoratedBox(
                decoration: BoxDecoration(gradient: gradient),
                child: child,
              );
            },
          );
        },
      ),
    );
  }

  /// Calcula el gradiente interpolado según la posición del PageController.
  LinearGradient _interpolatedGradient(WeatherProvider provider) {
    if (provider.savedLocations.length < 2 || !_pageController.hasClients) {
      return provider.backgroundGradient;
    }

    final maxIndex = provider.savedLocations.length - 1;
    final rawPage = _pageController.page ?? provider.currentIndex.toDouble();
    // Clampear al rango válido para evitar RangeError tras eliminar ciudades
    final page = rawPage.clamp(0.0, maxIndex.toDouble());
    final currentPage = page.floor().clamp(0, maxIndex);
    final nextPage = (currentPage + 1).clamp(0, maxIndex);
    final t = page - currentPage; // Fracción entre 0.0 y 1.0

    if (t == 0.0 || currentPage == nextPage) {
      final id = provider.savedLocations[currentPage].municipioId;
      return provider.gradientForMunicipio(id);
    }

    final gradA = provider.gradientForMunicipio(
      provider.savedLocations[currentPage].municipioId,
    );
    final gradB = provider.gradientForMunicipio(
      provider.savedLocations[nextPage].municipioId,
    );

    return WeatherProvider.lerpGradient(gradA, gradB, t);
  }


  // Flag para evitar que el postFrameCallback luche contra el gesto del usuario
  bool _isUserSwiping = false;

  Widget _buildBodyContent(BuildContext context, WeatherProvider provider) {
    // Sincronizar PageController con el índice del provider SOLO
    // cuando el cambio viene del provider (ej: añadir ciudad), no del swipe.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_isUserSwiping &&
          _pageController.hasClients &&
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
              : NotificationListener<ScrollNotification>(
                  onNotification: (notification) {
                    if (notification is ScrollStartNotification) {
                      _isUserSwiping = true;
                    } else if (notification is ScrollEndNotification) {
                      _isUserSwiping = false;
                    }
                    return false;
                  },
                  child: PageView.builder(
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

          // Dots de navegación centrados — escuchan el PageController directamente
          // para evitar rebuilds del provider durante el swipe.
          AnimatedBuilder(
            animation: _pageController,
            builder: (context, _) {
              final activeDot = _pageController.hasClients
                  ? (_pageController.page?.round() ?? provider.currentIndex)
                  : provider.currentIndex;
              return SizedBox(
                height: 36,
                child: Row(
                  mainAxisSize: MainAxisSize.min,
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
                        width: activeDot == i ? 18 : 6,
                        height: 6,
                        decoration: BoxDecoration(
                          color: activeDot == i
                              ? Colors.white
                              : Colors.white30,
                          borderRadius: BorderRadius.circular(3),
                        ),
                      ),
                    ),
                  ),
                ),
              );
            },
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
      // Calcular el índice destino ANTES de eliminar para sincronizar
      // el PageController primero y evitar RangeError en _interpolatedGradient
      final targetIndex = index >= weatherProvider.savedLocations.length - 1
          ? (weatherProvider.savedLocations.length - 2).clamp(0, weatherProvider.savedLocations.length - 1)
          : index;
      if (_pageController.hasClients) {
        _pageController.jumpToPage(targetIndex);
      }
      await weatherProvider.removeLocation(index);
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
    // Selector extrae solo los datos de ESTE municipio. Solo rebuild si cambian.
    return Selector<WeatherProvider, _WeatherPageData>(
      selector: (_, provider) => _WeatherPageData(
        isLoading: provider.isLoadingFor(widget.municipioId),
        errorMessage: provider.errorMessageFor(widget.municipioId),
        daily: provider.dailyForecastsFor(widget.municipioId),
        hourly: provider.hourlyForecastsFor(widget.municipioId),
        cityName: provider.cityNameFor(widget.municipioId),
        alerts: provider.alertsFor(widget.municipioId),
        currentTemp: provider.currentTemperatureFor(widget.municipioId),
        skyCode: provider.currentSkyCodeFor(widget.municipioId),
        skyDesc: provider.currentSkyDescriptionFor(widget.municipioId),
        tempRange: provider.todayTempRangeFor(widget.municipioId),
        sunTimes: provider.currentSunTimes,
        moonData: provider.currentMoonData,
      ),
      shouldRebuild: (prev, next) => prev != next,
      builder: (context, data, _) {
        final provider = context.read<WeatherProvider>();

        Widget content;
        if (data.isLoading) {
          content = _buildLoadingState(data.cityName);
        } else if (data.errorMessage != null) {
          content = _buildErrorState(provider);
        } else if (data.daily.isEmpty || data.hourly.isEmpty) {
          content = _buildNoDataState(provider, data.cityName);
        } else {
          content = _buildContent(provider, data);
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

  Widget _buildContent(WeatherProvider provider, _WeatherPageData data) {
    final weather = WeatherCode.fromCode(data.skyCode);

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
                    data.cityName,
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
                    data.currentTemp != null ? '${data.currentTemp}°' : '--°',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 72,
                      fontWeight: FontWeight.w200,
                      height: 1,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    data.skyDesc.isNotEmpty ? data.skyDesc : weather.description,
                    style: const TextStyle(color: Colors.white70, fontSize: 18),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Máx: ${data.tempRange.$1 ?? '--'}°  Mín: ${data.tempRange.$2 ?? '--'}°',
                    style: const TextStyle(color: Colors.white54, fontSize: 15),
                  ),
                ],
              ),
            ),

            // --- Alertas meteorológicas (si las hay) ---
            AlertBox(alerts: data.alerts),

            // --- Caja 1: Pronóstico por horas ---
            RepaintBoundary(
              child: HourlyView(forecasts: data.hourly, alerts: data.alerts),
            ),

            const SizedBox(height: 8),

            // --- Caja 2: Pronóstico por días ---
            RepaintBoundary(
              child: DailyView(forecasts: data.daily, alerts: data.alerts),
            ),

            const SizedBox(height: 8),

            // --- Caja 3: Ciclo solar y lunar ---
            RepaintBoundary(
              child: SunMoonCard(provider: provider),
            ),

            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Datos extraídos para el Selector (evita rebuilds innecesarios)
// ---------------------------------------------------------------------------

class _WeatherPageData {
  final bool isLoading;
  final String? errorMessage;
  final List<DailyForecast> daily;
  final List<HourlyForecast> hourly;
  final String cityName;
  final List<WeatherAlert> alerts;
  final int? currentTemp;
  final String skyCode;
  final String skyDesc;
  final (int?, int?) tempRange;
  final SunTimes? sunTimes;
  final MoonData? moonData;

  const _WeatherPageData({
    required this.isLoading,
    required this.errorMessage,
    required this.daily,
    required this.hourly,
    required this.cityName,
    required this.alerts,
    required this.currentTemp,
    required this.skyCode,
    required this.skyDesc,
    required this.tempRange,
    required this.sunTimes,
    required this.moonData,
  });

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    if (other is! _WeatherPageData) return false;
    return isLoading == other.isLoading &&
        errorMessage == other.errorMessage &&
        identical(daily, other.daily) &&
        identical(hourly, other.hourly) &&
        cityName == other.cityName &&
        identical(alerts, other.alerts) &&
        currentTemp == other.currentTemp &&
        skyCode == other.skyCode &&
        skyDesc == other.skyDesc &&
        tempRange == other.tempRange &&
        sunTimes == other.sunTimes &&
        moonData == other.moonData;
  }

  @override
  int get hashCode => Object.hash(
    isLoading, errorMessage, cityName, currentTemp,
    skyCode, skyDesc, tempRange,
  );
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
