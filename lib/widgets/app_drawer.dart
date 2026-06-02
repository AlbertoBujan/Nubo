import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../providers/weather_provider.dart';
import '../models/saved_location.dart';
import '../services/background_update_service.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'search_location_sheet.dart';
import 'info_dialog.dart';

class AppDrawer extends StatefulWidget {
  const AppDrawer({super.key});

  @override
  State<AppDrawer> createState() => _AppDrawerState();
}

class _AppDrawerState extends State<AppDrawer> {
  bool _showSettings = false;

  void _openSearch(BuildContext context) {
    Navigator.of(context).pop();
    Future.microtask(() {
      if (context.mounted) {
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
      }
    });
  }

  Future<void> _confirmRemove(
    BuildContext context,
    WeatherProvider provider,
    int index,
    SavedLocation loc,
  ) async {
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
          '¿Eliminar "${loc.nombre}"?',
          style: const TextStyle(color: Colors.white70),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancelar',
                style: TextStyle(color: Colors.white54)),
          ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: Text('Eliminar',
                style: TextStyle(color: Colors.red.shade300)),
          ),
        ],
      ),
    );

    if (confirmed == true && context.mounted) {
      await context.read<WeatherProvider>().removeLocation(index);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Drawer(
      width: MediaQuery.of(context).size.width * 0.82,
      backgroundColor: Colors.transparent,
      child: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF1A2540), Color(0xFF0F1E35)],
          ),
        ),
        child: SafeArea(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _DrawerHeader(
                showingSettings: _showSettings,
                onSettingsTap: () => setState(() => _showSettings = true),
                onBackTap: () => setState(() => _showSettings = false),
              ),
              const SizedBox(height: 8),
              AnimatedSwitcher(
                duration: const Duration(milliseconds: 200),
                child: _showSettings
                    ? _buildSettingsView()
                    : _buildLocationsView(context),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLocationsView(BuildContext context) {
    return Column(
      key: const ValueKey('locations'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _SectionHeader(
          icon: Icons.location_on,
          label: 'Localizaciones',
        ),
        SizedBox(
          height: MediaQuery.of(context).size.height * 0.55,
          child: Consumer<WeatherProvider>(
            builder: (context, provider, _) {
              final locs = provider.savedLocations;
              if (locs.isEmpty) {
                return const Center(
                  child: Text(
                    'Sin localizaciones guardadas',
                    style: TextStyle(color: Colors.white38, fontSize: 14),
                  ),
                );
              }
              return ListView.builder(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                itemCount: locs.length,
                itemBuilder: (context, index) {
                  final loc = locs[index];
                  final isActive = provider.currentIndex == index;
                  return _LocationTile(
                    loc: loc,
                    isActive: isActive,
                    onTap: () {
                      provider.switchToIndex(index);
                      Navigator.of(context).pop();
                    },
                    onDelete: locs.length > 1
                        ? () => _confirmRemove(context, provider, index, loc)
                        : null,
                  );
                },
              );
            },
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: () => _openSearch(context),
              icon: const Icon(Icons.add_location_alt_outlined, size: 18),
              label: const Text('Añadir localización'),
              style: OutlinedButton.styleFrom(
                foregroundColor: Colors.white70,
                side: BorderSide(color: Colors.white.withValues(alpha: 0.15)),
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildSettingsView() {
    return Column(
      key: const ValueKey('settings'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _SectionHeader(
          icon: Icons.settings_outlined,
          label: 'Ajustes',
        ),
        const _BackgroundUpdateSetting(),
      ],
    );
  }
}

// ── Subwidgets ─────────────────────────────────────────────────────────────────

class _DrawerHeader extends StatelessWidget {
  final bool showingSettings;
  final VoidCallback onSettingsTap;
  final VoidCallback onBackTap;

  const _DrawerHeader({
    required this.showingSettings,
    required this.onSettingsTap,
    required this.onBackTap,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 20, 16, 12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          // Botón atrás en modo ajustes, o título en modo normal
          if (showingSettings)
            IconButton(
              icon: const Icon(LucideIcons.chevronDown,
                  color: Colors.white70, size: 20),
              tooltip: 'Volver',
              onPressed: onBackTap,
            )
          else
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    const Text(
                      'Nubo',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 0.5,
                      ),
                    ),
                    const SizedBox(width: 8),
                    FutureBuilder<PackageInfo>(
                      future: PackageInfo.fromPlatform(),
                      builder: (context, snapshot) {
                        if (snapshot.hasData) {
                          return Text(
                            'v${snapshot.data!.version}',
                            style: const TextStyle(
                              color: Colors.white54,
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                            ),
                          );
                        }
                        return const SizedBox.shrink();
                      },
                    ),
                  ],
                ),
              ],
            ),

          // Botones de la derecha
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (!showingSettings)
                IconButton(
                  icon: const Icon(LucideIcons.settings,
                      color: Colors.white70, size: 20),
                  tooltip: 'Ajustes',
                  onPressed: onSettingsTap,
                ),
              IconButton(
                icon: const Icon(LucideIcons.info,
                    color: Colors.white70, size: 20),
                tooltip: 'Información y créditos',
                onPressed: () {
                  Navigator.of(context).pop();
                  showDialog(
                    context: context,
                    builder: (context) => const InfoDialog(),
                  );
                },
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final IconData icon;
  final String label;

  const _SectionHeader({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 8, 4),
      child: Row(
        children: [
          Icon(icon, color: Colors.white38, size: 16),
          const SizedBox(width: 6),
          Text(
            label.toUpperCase(),
            style: const TextStyle(
              color: Colors.white38,
              fontSize: 11,
              fontWeight: FontWeight.w700,
              letterSpacing: 1.2,
            ),
          ),
        ],
      ),
    );
  }
}

class _LocationTile extends StatelessWidget {
  final SavedLocation loc;
  final bool isActive;
  final VoidCallback onTap;
  final VoidCallback? onDelete;

  const _LocationTile({
    required this.loc,
    required this.isActive,
    required this.onTap,
    this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      margin: const EdgeInsets.symmetric(vertical: 3),
      decoration: BoxDecoration(
        color: isActive
            ? Colors.white.withValues(alpha: 0.10)
            : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
        border: isActive
            ? Border.all(color: Colors.white.withValues(alpha: 0.12))
            : null,
      ),
      child: ListTile(
        dense: true,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        leading: Icon(
          isActive ? Icons.location_on : Icons.location_on_outlined,
          color: isActive ? Colors.blue.shade300 : Colors.white38,
          size: 20,
        ),
        title: Text(
          loc.nombre,
          style: TextStyle(
            color: isActive ? Colors.white : Colors.white70,
            fontWeight: isActive ? FontWeight.w600 : FontWeight.w400,
            fontSize: 15,
          ),
        ),
        trailing: onDelete != null
            ? IconButton(
                icon: const Icon(Icons.delete_outline,
                    color: Colors.white30, size: 18),
                tooltip: 'Eliminar',
                onPressed: onDelete,
                splashRadius: 18,
              )
            : null,
        onTap: onTap,
      ),
    );
  }
}

// ── Widget de ajuste de actualización en segundo plano ────────────────────────

class _BackgroundUpdateSetting extends StatefulWidget {
  const _BackgroundUpdateSetting();

  @override
  State<_BackgroundUpdateSetting> createState() =>
      _BackgroundUpdateSettingState();
}

class _BackgroundUpdateSettingState extends State<_BackgroundUpdateSetting> {
  BackgroundUpdateInterval _current = BackgroundUpdateInterval.off;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    BackgroundUpdateService.getInterval().then((value) {
      if (mounted) setState(() { _current = value; _loading = false; });
    });
  }

  Future<void> _select(BackgroundUpdateInterval value) async {
    setState(() => _current = value);
    await BackgroundUpdateService.setInterval(value);
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Actualización en segundo plano',
            style: TextStyle(
              color: Colors.white,
              fontSize: 14,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            'Mantén el tiempo de tus ciudades actualizado aunque la app esté cerrada.',
            style: TextStyle(color: Colors.white54, fontSize: 12, height: 1.4),
          ),
          const SizedBox(height: 12),
          if (_loading)
            const Center(child: CircularProgressIndicator(strokeWidth: 2))
          else
            ...BackgroundUpdateInterval.values.map((interval) {
              return _SettingOption(
                label: interval.label,
                selected: _current == interval,
                onTap: () => _select(interval),
              );
            }),
        ],
      ),
    );
  }
}

class _SettingOption extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _SettingOption({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        margin: const EdgeInsets.only(bottom: 6),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: selected
              ? Colors.blue.shade700.withValues(alpha: 0.25)
              : Colors.white.withValues(alpha: 0.05),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: selected
                ? Colors.blue.shade400.withValues(alpha: 0.5)
                : Colors.white.withValues(alpha: 0.08),
          ),
        ),
        child: Row(
          children: [
            AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              width: 18,
              height: 18,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: selected ? Colors.blue.shade300 : Colors.white38,
                  width: 2,
                ),
              ),
              child: selected
                  ? Center(
                      child: Container(
                        width: 8,
                        height: 8,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: Colors.blue.shade300,
                        ),
                      ),
                    )
                  : null,
            ),
            const SizedBox(width: 12),
            Text(
              label,
              style: TextStyle(
                color: selected ? Colors.white : Colors.white70,
                fontSize: 14,
                fontWeight:
                    selected ? FontWeight.w600 : FontWeight.w400,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
