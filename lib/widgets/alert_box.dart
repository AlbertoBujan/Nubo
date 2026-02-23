import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import '../models/weather_alert.dart';

/// Widget que muestra las alertas meteorológicas activas agrupadas por tipo.
class AlertBox extends StatelessWidget {
  final List<WeatherAlert> alerts;

  const AlertBox({super.key, required this.alerts});

  @override
  Widget build(BuildContext context) {
    if (alerts.isEmpty) return const SizedBox.shrink();

    // Agrupar alertas por tipo normalizado
    final groupedAlerts = <String, List<WeatherAlert>>{};
    for (final alert in alerts) {
      final type = _normalizeAlertType(alert.event);
      if (!groupedAlerts.containsKey(type)) {
        groupedAlerts[type] = [];
      }
      groupedAlerts[type]!.add(alert);
    }

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: groupedAlerts.entries.map((entry) {
          return _AlertGroupTile(
            title: entry.key,
            alerts: entry.value,
          );
        }).toList(),
      ),
    );
  }

  String _normalizeAlertType(String event) {
    final lower = event.toLowerCase();
    if (lower.contains('viento')) return 'Viento';
    if (lower.contains('costero')) return 'Costeros';
    if (lower.contains('lluvia') || lower.contains('precipita')) return 'Lluvia';
    if (lower.contains('nieve') || lower.contains('nevada')) return 'Nieve';
    if (lower.contains('tormenta')) return 'Tormenta';
    if (lower.contains('temperatura')) return 'Temperaturas';
    if (lower.contains('niebla')) return 'Niebla';
    if (lower.contains('polvo')) return 'Polvo en supensión';
    if (lower.contains('alud')) return 'Aludes';
    if (lower.contains('deshielo')) return 'Deshielos';
    return 'Avisos Meteorológicos';
  }
}

class _AlertGroupTile extends StatefulWidget {
  final String title;
  final List<WeatherAlert> alerts;

  const _AlertGroupTile({
    required this.title,
    required this.alerts,
  });

  @override
  State<_AlertGroupTile> createState() => _AlertGroupTileState();
}

class _AlertGroupTileState extends State<_AlertGroupTile> {
  bool _expanded = false;

  // Determina el color del grupo basándose en la alerta más severa
  Color get _maxSeverityColor {
    if (widget.alerts.any((a) => a.nivel.toLowerCase() == 'rojo')) {
      return const Color(0xFFD32F2F);
    }
    if (widget.alerts.any((a) => a.nivel.toLowerCase() == 'naranja')) {
      return const Color(0xFFFF8F00);
    }
    return const Color(0xFFFBC02D); // Amarillo por defecto
  }
  
  // Fondo con transparencia
  Color get _backgroundColor => _maxSeverityColor.withValues(alpha: 0.1);

  IconData get _icon {
    switch (widget.title) {
      case 'Viento': return LucideIcons.wind;
      case 'Costeros': return LucideIcons.waves;
      case 'Lluvia': return LucideIcons.cloudRain;
      case 'Nieve': return LucideIcons.snowflake;
      case 'Tormenta': return LucideIcons.cloudLightning;
      case 'Temperaturas': return LucideIcons.thermometer;
      case 'Niebla': return LucideIcons.cloudFog;
      default: return LucideIcons.triangleAlert;
    }
  }

  @override
  Widget build(BuildContext context) {
    final color = _maxSeverityColor;
    final count = widget.alerts.length;

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: _backgroundColor,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color, width: 1.5),
      ),
      child: Column(
        children: [
          // Cabecera del grupo (siempre visible)
          InkWell(
            onTap: () => setState(() => _expanded = !_expanded),
            borderRadius: BorderRadius.circular(16),
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Row(
                children: [
                   Icon(_icon, color: color, size: 20),
                   const SizedBox(width: 12),
                     Expanded(
                       child: Text(
                         widget.title,
                         style: const TextStyle(
                           color: Colors.white,
                           fontSize: 16,
                           fontWeight: FontWeight.w600,
                         ),
                       ),
                     ),
                   // Badge de severidad máxima
                   Container(
                     padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                     decoration: BoxDecoration(
                       color: color,
                       borderRadius: BorderRadius.circular(12),
                     ),
                     child: Text(
                       _getMaxSeverityLabel(),
                       style: const TextStyle(
                         color: Colors.black87,
                         fontSize: 11,
                         fontWeight: FontWeight.bold,
                       ),
                     ),
                   ),
                   const SizedBox(width: 8),
                   Icon(
                     _expanded ? LucideIcons.chevronUp : LucideIcons.chevronDown,
                     color: Colors.white54,
                     size: 20,
                   ),
                ],
              ),
            ),
          ),
          
          // Lista expandida
          if (_expanded)
            Padding(
               padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
               child: Column(
                 children: widget.alerts.map((alert) => _AlertDetailCard(alert: alert)).toList(),
               ),
            ),
        ],
      ),
    );
  }

  String _getMaxSeverityLabel() {
    if (widget.alerts.any((a) => a.nivel.toLowerCase() == 'rojo')) return 'ROJO';
    if (widget.alerts.any((a) => a.nivel.toLowerCase() == 'naranja')) return 'NARANJA';
    return 'AMARILLO';
  }
}

class _AlertDetailCard extends StatelessWidget {
  final WeatherAlert alert;

  const _AlertDetailCard({required this.alert});

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('EEE d, HH:mm', 'es_ES');

    return Container(
      margin: const EdgeInsets.only(top: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.black12,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 8, 
                height: 8, 
                decoration: BoxDecoration(
                  shape: BoxShape.circle, 
                  color: alert.color,
                  boxShadow: [
                    BoxShadow(color: alert.color.withValues(alpha: 0.5), blurRadius: 4),
                  ]
                )
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  alert.areaDescription,
                  style: const TextStyle(
                    color: Colors.white, 
                    fontWeight: FontWeight.w600,
                    fontSize: 13
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          if (alert.onset != null || alert.expires != null)
            Padding(
              padding: const EdgeInsets.only(left: 16),
              child: Text(
                _buildVigencia(alert, dateFormat),
                style: const TextStyle(color: Colors.white54, fontSize: 12),
              ),
            ),
          if (alert.description.isNotEmpty) ...[
            const SizedBox(height: 4),
            Padding(
               padding: const EdgeInsets.only(left: 16),
               child: Text(
                 alert.description,
                 maxLines: 4,
                 overflow: TextOverflow.ellipsis,
                 style: const TextStyle(color: Colors.white70, fontSize: 12, fontStyle: FontStyle.italic),
               ),
            ),
          ]
        ],
      ),
    );
  }

  String _buildVigencia(WeatherAlert alert, DateFormat fmt) {
    if (alert.onset != null && alert.expires != null) {
      return '${fmt.format(alert.onset!)} - ${fmt.format(alert.expires!)}';
    }
    if (alert.onset != null) return 'Desde: ${fmt.format(alert.onset!)}';
    if (alert.expires != null) return 'Hasta: ${fmt.format(alert.expires!)}';
    return '';
  }
}
