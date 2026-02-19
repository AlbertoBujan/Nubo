import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/hourly_forecast.dart';
import '../models/weather_enums.dart';

/// Vista horizontal de predicción por horas.
///
/// Muestra una lista horizontal con tarjetas de cristal (glassmorphism)
/// para cada hora, con icono, temperatura y hora.
class HourlyView extends StatelessWidget {
  final List<HourlyForecast> forecasts;

  const HourlyView({super.key, required this.forecasts});

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
          child: Text(
            'Predicción por horas',
            style: TextStyle(
              color: Colors.white,
              fontSize: 18,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        SizedBox(
          height: 150,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: displayForecasts.length,
            itemBuilder: (context, index) {
              return _HourlyCard(forecast: displayForecasts[index]);
            },
          ),
        ),
      ],
    );
  }
}

/// Tarjeta individual para cada hora.
class _HourlyCard extends StatelessWidget {
  final HourlyForecast forecast;

  const _HourlyCard({required this.forecast});

  @override
  Widget build(BuildContext context) {
    final weather = WeatherCode.fromCode(forecast.skyStateCode);
    final timeStr = DateFormat('HH:mm').format(forecast.dateTime);
    final isNow = DateTime.now().hour == forecast.dateTime.hour &&
        DateTime.now().day == forecast.dateTime.day;

    return Container(
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
        ],
      ),
    );
  }
}
