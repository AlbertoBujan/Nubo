import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import '../models/daily_forecast.dart';
import '../models/weather_enums.dart';

/// Vista vertical de predicción por días.
///
/// Muestra una lista de filas con el día de la semana, icono del tiempo
/// y temperaturas máxima/mínima con una barra de rango visual estilo iOS.
class DailyView extends StatelessWidget {
  final List<DailyForecast> forecasts;

  const DailyView({super.key, required this.forecasts});

  @override
  Widget build(BuildContext context) {
    if (forecasts.isEmpty) {
      return const Center(
        child: Text(
          'No hay datos de predicción disponibles',
          style: TextStyle(color: Colors.white70, fontSize: 16),
        ),
      );
    }

    // Calculamos el rango global de temperaturas para la barra visual
    int globalMin = 100;
    int globalMax = -100;
    for (final f in forecasts) {
      if (f.tempMin != null && f.tempMin! < globalMin) globalMin = f.tempMin!;
      if (f.tempMax != null && f.tempMax! > globalMax) globalMax = f.tempMax!;
    }
    final range = (globalMax - globalMin).clamp(1, 100);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(20, 16, 20, 8),
          child: Row(
            children: [
              Icon(LucideIcons.calendar, color: Colors.white70, size: 18),
              SizedBox(width: 6),
              Text(
                'Próximos días',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
        ...forecasts.map(
          (forecast) => _DailyRow(
            forecast: forecast,
            globalMin: globalMin,
            range: range,
          ),
        ),
      ],
    );
  }
}

/// Fila individual para cada día de predicción.
class _DailyRow extends StatelessWidget {
  final DailyForecast forecast;
  final int globalMin;
  final int range;

  const _DailyRow({
    required this.forecast,
    required this.globalMin,
    required this.range,
  });

  /// Formatea la fecha como nombre del día.
  String _formatDay(DateTime date) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final forecastDay = DateTime(date.year, date.month, date.day);

    if (forecastDay == today) return 'Hoy';
    if (forecastDay == today.add(const Duration(days: 1))) return 'Mañana';

    // Nombre del día de la semana en español
    final dayName = DateFormat('EEEE', 'es_ES').format(date);
    return dayName[0].toUpperCase() + dayName.substring(1);
  }

  @override
  Widget build(BuildContext context) {
    final weather = WeatherCode.fromCode(forecast.skyStateCode);
    final isToday = DateTime.now().day == forecast.date.day &&
        DateTime.now().month == forecast.date.month;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 3),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        color: isToday
            ? Colors.white.withValues(alpha: 0.1)
            : Colors.white.withValues(alpha: 0.05),
      ),
      child: Row(
        children: [
          // Nombre del día
          SizedBox(
            width: 90,
            child: Text(
              _formatDay(forecast.date),
              style: TextStyle(
                color: isToday ? Colors.white : Colors.white70,
                fontSize: 15,
                fontWeight: isToday ? FontWeight.w600 : FontWeight.w400,
              ),
            ),
          ),

          // Icono del tiempo
          SizedBox(
            width: 36,
            child: Icon(
              weather.icon,
              color: Colors.white70,
              size: 22,
            ),
          ),

          // Probabilidad de precipitación (si es > 0)
          SizedBox(
            width: 44,
            child: forecast.precipitationProbability != null &&
                    forecast.precipitationProbability! > 0
                ? Text(
                    '${forecast.precipitationProbability}%',
                    style: TextStyle(
                      color: Colors.blue.shade300,
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                    ),
                    textAlign: TextAlign.center,
                  )
                : const SizedBox.shrink(),
          ),

          const SizedBox(width: 8),

          // Temperatura mínima
          SizedBox(
            width: 32,
            child: Text(
              forecast.tempMin != null ? '${forecast.tempMin}°' : '--',
              style: const TextStyle(
                color: Colors.white54,
                fontSize: 15,
              ),
              textAlign: TextAlign.right,
            ),
          ),

          const SizedBox(width: 8),

          // Barra de rango de temperatura
          Expanded(
            child: _TemperatureBar(
              min: forecast.tempMin ?? 0,
              max: forecast.tempMax ?? 0,
              globalMin: globalMin,
              range: range,
            ),
          ),

          const SizedBox(width: 8),

          // Temperatura máxima
          SizedBox(
            width: 32,
            child: Text(
              forecast.tempMax != null ? '${forecast.tempMax}°' : '--',
              style: const TextStyle(
                color: Colors.white,
                fontSize: 15,
                fontWeight: FontWeight.w600,
              ),
              textAlign: TextAlign.left,
            ),
          ),
        ],
      ),
    );
  }
}

/// Barra visual que representa el rango de temperatura del día.
class _TemperatureBar extends StatelessWidget {
  final int min;
  final int max;
  final int globalMin;
  final int range;

  const _TemperatureBar({
    required this.min,
    required this.max,
    required this.globalMin,
    required this.range,
  });

  @override
  Widget build(BuildContext context) {
    final startFraction = ((min - globalMin) / range).clamp(0.0, 1.0);
    final endFraction = ((max - globalMin) / range).clamp(0.0, 1.0);

    return SizedBox(
      height: 6,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(3),
        child: Stack(
          children: [
            // Fondo
            Container(
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(3),
              ),
            ),
            // Barra de rango con degradado
            FractionallySizedBox(
              alignment: Alignment.centerLeft,
              widthFactor: 1.0,
              child: Padding(
                padding: EdgeInsets.only(
                  left: startFraction *
                      (MediaQuery.of(context).size.width * 0.25),
                ),
                child: FractionallySizedBox(
                  alignment: Alignment.centerLeft,
                  widthFactor: (endFraction - startFraction).clamp(0.05, 1.0),
                  child: Container(
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(3),
                      gradient: LinearGradient(
                        colors: [
                          Colors.blue.shade300,
                          Colors.amber.shade400,
                          Colors.orange.shade400,
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
