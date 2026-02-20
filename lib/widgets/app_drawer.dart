import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../providers/weather_provider.dart';
import '../models/saved_location.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'search_location_sheet.dart';
import 'info_dialog.dart';

/// Drawer lateral con ajustes de la aplicación.
///
/// Secciones:
/// - Localizaciones: lista de ciudades guardadas con opción de eliminar/añadir
class AppDrawer extends StatelessWidget {
  const AppDrawer({super.key});

  void _openSearch(BuildContext context) {
    // Cerrar el drawer primero
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
              // ── Cabecera del drawer ──────────────────────
              _DrawerHeader(),

              const SizedBox(height: 8),

              // ── Sección: Localizaciones ──────────────────
              const _SectionHeader(
                icon: Icons.location_on,
                label: 'Localizaciones',
              ),

              // Lista de localizaciones guardadas
              Expanded(
                child: Consumer<WeatherProvider>(
                  builder: (context, provider, _) {
                    final locs = provider.savedLocations;
                    if (locs.isEmpty) {
                      return const Center(
                        child: Text(
                          'Sin localizaciones guardadas',
                          style:
                              TextStyle(color: Colors.white38, fontSize: 14),
                        ),
                      );
                    }

                    return ListView.builder(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 4),
                      itemCount: locs.length,
                      itemBuilder: (context, index) {
                        final loc = locs[index];
                        final isActive = provider.currentIndex == index;

                        return _LocationTile(
                          loc: loc,
                          isActive: isActive,
                          onTap: () {
                            provider.switchToIndex(index);
                            Navigator.of(context).pop(); // cierra el drawer
                          },
                          onDelete: locs.length > 1
                              ? () => _confirmRemove(
                                    context,
                                    provider,
                                    index,
                                    loc,
                                  )
                              : null,
                        );
                      },
                    );
                  },
                ),
              ),

              // ── Botón añadir localización ────────────────
              Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                child: SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: () => _openSearch(context),
                    icon: const Icon(Icons.add_location_alt_outlined,
                        size: 18),
                    label: const Text('Añadir localización'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Colors.white70,
                      side:
                          BorderSide(color: Colors.white.withValues(alpha: 0.15)),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── Subwidgets internos ────────────────────────────────────────────────────────

class _DrawerHeader extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 20, 16, 12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
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
              const Text(
                'Meteorología AEMET',
                style: TextStyle(color: Colors.white38, fontSize: 12),
              ),
            ],
          ),
          IconButton(
            icon: Icon(LucideIcons.info, color: Colors.white70),
            tooltip: 'Información y créditos',
            onPressed: () {
              Navigator.of(context).pop(); // Cierra el menú lateral primero
              showDialog(
                context: context,
                builder: (context) => const InfoDialog(),
              );
            },
          ),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final IconData icon;
  final String label;
  final Widget? trailing;

  const _SectionHeader({
    required this.icon,
    required this.label,
    this.trailing,
  });

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
          const Spacer(),
          ?trailing,
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
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        leading: Icon(
          isActive ? Icons.location_on : Icons.location_on_outlined,
          color: isActive ? Colors.blue.shade300 : Colors.white38,
          size: 20,
        ),
        title: Text(
          loc.nombre,
          style: TextStyle(
            color: isActive ? Colors.white : Colors.white70,
            fontWeight:
                isActive ? FontWeight.w600 : FontWeight.w400,
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
