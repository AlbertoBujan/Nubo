import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/weather_enums.dart';
import '../providers/weather_provider.dart';
import '../widgets/alert_box.dart';
import '../widgets/hourly_view.dart';
import '../widgets/daily_view.dart';
import '../widgets/app_drawer.dart';

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
        _buildTopBar(context, provider),

        Expanded(
          child: provider.savedLocations.isEmpty
              ? const Center(
                  child: CircularProgressIndicator(
                    color: Colors.white54,
                    strokeWidth: 2,
                  ),
                )
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
                    );
                  },
                ),
        ),
      ],
    );
  }

  Widget _buildTopBar(BuildContext context, WeatherProvider provider) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
      child: Row(
        children: [
          // Botón hamburguesa → abre el drawer
          _TopBarButton(
            icon: Icons.menu,
            tooltip: 'Menú',
            onTap: () => Scaffold.of(context).openDrawer(),
          ),

          // Dots de navegación centrados
          Expanded(
            child: Row(
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

          // Botón de refrescar
          _TopBarButton(
            icon: Icons.refresh,
            tooltip: 'Refrescar',
            onTap: () => provider.refreshCurrentWeather(),
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

  const _WeatherPage({required this.municipioId, required this.pageIndex});

  @override
  State<_WeatherPage> createState() => _WeatherPageState();
}

class _WeatherPageState extends State<_WeatherPage> {
  @override
  Widget build(BuildContext context) {
    return Consumer<WeatherProvider>(
      builder: (context, provider, _) {
        final isActive = provider.currentIndex == widget.pageIndex;

        if (!isActive) {
          return const SizedBox.shrink();
        }

        if (provider.isLoading) {
          return _buildLoadingState(provider.cityName);
        }

        if (provider.errorMessage != null) {
          return _buildErrorState(provider);
        }

        return _buildContent(provider);
      },
    );
  }

  Widget _buildLoadingState(String cityName) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const CircularProgressIndicator(
            color: Colors.white70,
            strokeWidth: 2,
          ),
          const SizedBox(height: 20),
          Text(
            'Consultando AEMET para $cityName...',
            style: const TextStyle(color: Colors.white70, fontSize: 15),
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
              provider.errorMessage ?? 'Error desconocido',
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white54, fontSize: 14),
            ),
          ),
        ),
        const SizedBox(height: 24),
        Center(
          child: ElevatedButton.icon(
            onPressed: () => provider.refreshCurrentWeather(),
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

  Widget _buildContent(WeatherProvider provider) {
    final tempRange = provider.todayTempRange;
    final weather = WeatherCode.fromCode(provider.currentSkyCode);

    return SingleChildScrollView(
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
                  provider.cityName,
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
                  provider.currentTemperature != null
                      ? '${provider.currentTemperature}°'
                      : '--°',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 72,
                    fontWeight: FontWeight.w200,
                    height: 1,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  provider.currentSkyDescription.isNotEmpty
                      ? provider.currentSkyDescription
                      : weather.description,
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
          AlertBox(alerts: provider.alerts),

          // --- Caja 1: Pronóstico por horas ---
          HourlyView(forecasts: provider.hourlyForecasts),

          const SizedBox(height: 8),

          // --- Caja 2: Pronóstico por días (scroll horizontal interno) ---
          DailyView(forecasts: provider.dailyForecasts),

          const SizedBox(height: 20),
        ],
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
