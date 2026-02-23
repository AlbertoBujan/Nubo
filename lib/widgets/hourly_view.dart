import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import '../models/hourly_forecast.dart';
import '../models/weather_enums.dart';
import '../models/weather_alert.dart';

/// Vista horizontal de predicción por horas.
///
/// Muestra una lista horizontal con tarjetas de cristal (glassmorphism)
/// para cada hora, con icono, temperatura y hora.
class HourlyView extends StatelessWidget {
  final List<HourlyForecast> forecasts;
  final List<WeatherAlert> alerts;

  const HourlyView({
    super.key, 
    required this.forecasts,
    this.alerts = const [],
  });

  @override
  Widget build(BuildContext context) {
    if (forecasts.isEmpty) {
      return const Center(
        child: Text(
          'No hay datos horarios disponibles',
          style: TextStyle(color: Colors.white70, fontSize: 16),
        ),
      );
    }

    // Filtramos para mostrar solo las horas futuras o del día actual
    final now = DateTime.now();
    final filteredForecasts = forecasts
        .where((f) => f.dateTime.isAfter(now.subtract(const Duration(hours: 1))))
        .toList();

    final displayForecasts =
        filteredForecasts.isEmpty ? forecasts : filteredForecasts;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(20, 16, 20, 12),
          child: Row(
            children: [
              Icon(LucideIcons.clock, color: Colors.white70, size: 18),
              SizedBox(width: 6),
              Text(
                'Predicción por horas',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
        SizedBox(
          height: 135, // Recortado agresivamente desde 175 para compactar la lista horizontal
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: displayForecasts.length,
            itemBuilder: (context, index) {
              return _HourlyCard(
                forecast: displayForecasts[index],
                alerts: alerts,
              );
            },
          ),
        ),
      ],
    );
  }
}

class _HourlyCard extends StatelessWidget {
  final HourlyForecast forecast;
  final List<WeatherAlert> alerts;

  const _HourlyCard({
    required this.forecast,
    required this.alerts,
  });

  /// Busca alertas vigentes durante esta hora.
  List<WeatherAlert> _getActiveAlertsForHour() {
    final startOfHour = forecast.dateTime;
    final endOfHour = startOfHour.add(const Duration(hours: 1));
    
    return alerts.where((alert) {
      if (alert.onset == null && alert.expires == null) return false;
      final onset = (alert.onset ?? alert.expires!.subtract(const Duration(days: 1))).toLocal();
      final expires = (alert.expires ?? alert.onset!.add(const Duration(days: 1))).toLocal();
      
      // Mismo comportamiento: onset debe ser <= startOfHour o estar dentro, pero comprobamos estricto
      // para atrapar todo el intervalo
      return (onset.isBefore(endOfHour) || onset.isAtSameMomentAs(endOfHour)) && 
             (expires.isAfter(startOfHour) || expires.isAtSameMomentAs(startOfHour));
    }).toList();
  }

  /// Devuelve el icono apropiado según el texto descriptivo del evento
  IconData _getIconForEvent(String event) {
    final text = event.toLowerCase();
    if (text.contains('viento')) return LucideIcons.wind;
    if (text.contains('costero') || text.contains('mar')) return LucideIcons.waves;
    if (text.contains('lluvia') || text.contains('precip')) return LucideIcons.cloudRain;
    if (text.contains('tormenta')) return LucideIcons.cloudLightning;
    if (text.contains('nieve') || text.contains('nevada')) return LucideIcons.snowflake;
    if (text.contains('niebla')) return LucideIcons.cloudFog;
    if (text.contains('temperatura') || text.contains('calor') || text.contains('frío')) {
      return LucideIcons.thermometer;
    }
    return Icons.warning;
  }

  /// Calcula el nombre del día en formato corto (Hoy, Mañana, Mié, Jue, ...)
  String _getDayLabel() {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final forecastDate = DateTime(forecast.dateTime.year, forecast.dateTime.month, forecast.dateTime.day);
    
    if (forecastDate == today) return 'Hoy';
    if (forecastDate == today.add(const Duration(days: 1))) return 'Mañana';
    
    final dayName = DateFormat('EEEE', 'es_ES').format(forecast.dateTime);
    return dayName[0].toUpperCase() + dayName.substring(1);
  }

  @override
  Widget build(BuildContext context) {
    final weather = WeatherCode.fromCode(forecast.skyStateCode);
    final timeStr = DateFormat('HH:mm').format(forecast.dateTime);
    final isNow = DateTime.now().hour == forecast.dateTime.hour &&
        DateTime.now().day == forecast.dateTime.day;
        
    final activeAlerts = _getActiveAlertsForHour();

    return Column(
      children: [
        // Etiqueta superior del día
        Padding(
          padding: const EdgeInsets.only(bottom: 6),
          child: Text(
            _getDayLabel(),
            style: const TextStyle(
              color: Colors.white70,
              fontSize: 12,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
        // Tarjeta de la hora
        Container(
          width: 80,
          margin: const EdgeInsets.symmetric(horizontal: 4),
          decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        gradient: isNow
            ? LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Colors.blue.shade400.withValues(alpha: 0.6),
                  Colors.blue.shade700.withValues(alpha: 0.4),
                ],
              )
            : null,
        color: isNow ? null : Colors.white.withValues(alpha: 0.08),
        border: Border.all(
          color: isNow
              ? Colors.blue.shade300.withValues(alpha: 0.5)
              : Colors.white.withValues(alpha: 0.1),
          width: 1,
        ),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            isNow ? 'Ahora' : timeStr,
            style: TextStyle(
              color: isNow ? Colors.white : Colors.white70,
              fontSize: 13,
              fontWeight: isNow ? FontWeight.w600 : FontWeight.w400,
            ),
          ),
          const SizedBox(height: 8),
          Icon(
            weather.icon,
            color: Colors.white70,
            size: 28,
          ),
          const SizedBox(height: 8),
          Text(
            forecast.temperature != null ? '${forecast.temperature}°' : '--',
            style: const TextStyle(
              color: Colors.white,
              fontSize: 17,
              fontWeight: FontWeight.w600,
            ),
          ),
          // Caja de iconos con altura fija para evitar saltos en el layout
          const SizedBox(height: 6),
          SizedBox(
            height: 14,
            child: activeAlerts.isNotEmpty
                ? Wrap(
                    spacing: 4,
                    alignment: WrapAlignment.center,
                    children: activeAlerts.map((alert) {
                      return Icon(
                        _getIconForEvent(alert.event),
                        color: alert.color,
                        size: 14,
                      );
                    }).toList(),
                  )
                : null,
          ),
        ],
      ),
    ),
      ],
    );
  }
}
