import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import '../providers/weather_provider.dart';

class SunCard extends StatelessWidget {
  final WeatherProvider provider;

  const SunCard({super.key, required this.provider});

  @override
  Widget build(BuildContext context) {
    final sunTimes = provider.currentSunTimes;
    if (sunTimes == null) return const SizedBox.shrink();

    final sunriseStr = DateFormat('HH:mm').format(sunTimes.sunrise);
    final sunsetStr = DateFormat('HH:mm').format(sunTimes.sunset);
    final now = DateTime.now();

    // Comprobar estado actual para resaltar ligeramente uno u otro
    final isDay = now.isAfter(sunTimes.sunrise) && now.isBefore(sunTimes.sunset);
    
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 20),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.1),
          width: 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(LucideIcons.sun, color: Colors.white70, size: 18),
              SizedBox(width: 6),
              Text(
                'Ciclo Solar',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              // Bloque Amanecer
              Column(
                children: [
                  Icon(
                    LucideIcons.sunrise,
                    color: isDay ? Colors.orangeAccent : Colors.white54,
                    size: 32,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Amanecer',
                    style: TextStyle(
                      color: isDay ? Colors.white : Colors.white54,
                      fontSize: 12,
                    ),
                  ),
                  Text(
                    sunriseStr,
                    style: TextStyle(
                      color: isDay ? Colors.white : Colors.white70,
                      fontSize: 18,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
              
              // Separador visual suave
              Container(
                height: 40,
                width: 1,
                color: Colors.white.withValues(alpha: 0.2),
              ),

              // Bloque Atardecer
              Column(
                children: [
                  Icon(
                    LucideIcons.sunset,
                    color: !isDay ? Colors.orangeAccent : Colors.white54,
                    size: 32,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Atardecer',
                    style: TextStyle(
                      color: !isDay ? Colors.white : Colors.white54,
                      fontSize: 12,
                    ),
                  ),
                  Text(
                    sunsetStr,
                    style: TextStyle(
                      color: !isDay ? Colors.white : Colors.white70,
                      fontSize: 18,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }
}
