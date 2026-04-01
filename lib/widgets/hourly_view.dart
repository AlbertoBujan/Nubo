import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import '../models/hourly_forecast.dart';
import '../models/weather_enums.dart';
import '../models/weather_alert.dart';
import 'wind_compass_arrow.dart';

/// Vista horizontal de predicción por horas.
/// Muestra un único contenedor con scroll horizontal continuo,
/// donde la información meteorológica se alinea en la parte superior y 
/// las temperaturas forman un gráfico de línea continuo inferior.
class HourlyView extends StatefulWidget {
  final List<HourlyForecast> forecasts;
  final List<WeatherAlert> alerts;

  const HourlyView({
    super.key, 
    required this.forecasts,
    this.alerts = const [],
  });

  @override
  State<HourlyView> createState() => _HourlyViewState();
}

class _HourlyViewState extends State<HourlyView> {
  // Datos pre-computados que se calculan una sola vez cuando cambian los forecasts.
  List<HourlyForecast> _displayForecasts = const [];
  bool _hasAnyRain = false;
  // Cache de la imagen del chart rasterizada para evitar re-paint costoso.
  ui.Image? _chartImage;
  double _chartWidth = 0;
  final double _chartHeight = 85;

  static const double _itemWidth = 65.0;
  static const double _paddingLeft = 32.0;

  @override
  void initState() {
    super.initState();
    _computeDisplayData();
  }

  @override
  void didUpdateWidget(HourlyView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.forecasts, widget.forecasts) ||
        !identical(oldWidget.alerts, widget.alerts)) {
      _invalidateChartCache();
      _computeDisplayData();
    }
  }

  @override
  void dispose() {
    _chartImage?.dispose();
    super.dispose();
  }

  void _invalidateChartCache() {
    _chartImage?.dispose();
    _chartImage = null;
  }

  void _computeDisplayData() {
    final now = DateTime.now();
    final filtered = widget.forecasts
        .where((f) => f.dateTime.isAfter(now.subtract(const Duration(hours: 1))))
        .toList();
    _displayForecasts = filtered.isEmpty ? widget.forecasts : filtered;
    _hasAnyRain = _displayForecasts.any((f) => (f.precipitationProbability ?? 0) > 0);
    _chartWidth = _paddingLeft + (_itemWidth * _displayForecasts.length) + 16;
  }

  /// Rasteriza el chart a una imagen offscreen para que el scroll
  /// no tenga que re-pintar el CustomPainter en cada frame.
  Future<void> _rasterizeChart() async {
    if (_displayForecasts.isEmpty) return;

    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    final size = Size(_chartWidth, _chartHeight);

    final painter = _HourlyChartPainter(
      forecasts: _displayForecasts,
      itemWidth: _itemWidth,
      paddingLeft: _paddingLeft,
    );
    painter.paint(canvas, size);

    final picture = recorder.endRecording();
    final image = await picture.toImage(
      size.width.ceil(),
      size.height.ceil(),
    );
    picture.dispose();

    if (mounted) {
      setState(() {
        _chartImage?.dispose();
        _chartImage = image;
      });
    } else {
      image.dispose();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.forecasts.isEmpty) {
      return const Center(
        child: Text(
          'No hay datos horarios disponibles',
          style: TextStyle(color: Colors.white70, fontSize: 16),
        ),
      );
    }

    // Rasterizar el chart si no hay cache
    if (_chartImage == null && _displayForecasts.isNotEmpty) {
      // Disparar rasterización asíncrona (solo la primera vez o tras invalidación)
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_chartImage == null) _rasterizeChart();
      });
    }

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      padding: const EdgeInsets.only(top: 16, bottom: 8),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.1),
          width: 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Título — estático, no necesita rebuild
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                Icon(LucideIcons.clock, color: Colors.white70, size: 18),
                SizedBox(width: 8),
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
          const SizedBox(height: 20),
          // Contenido desplazable — RepaintBoundary aísla el scroll del resto
          RepaintBoundary(
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              physics: const BouncingScrollPhysics(),
              child: SizedBox(
                width: _chartWidth,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Fila superior de información
                    Padding(
                      padding: EdgeInsets.only(left: _paddingLeft),
                      child: Row(
                        children: List.generate(_displayForecasts.length, (index) {
                          final showDay = index == 0 || _displayForecasts[index].dateTime.hour == 0;
                          return SizedBox(
                            width: _itemWidth,
                            child: _HourlyInfoColumn(
                              forecast: _displayForecasts[index],
                              alerts: widget.alerts,
                              showDay: showDay,
                              hasAnyRain: _hasAnyRain,
                            ),
                          );
                        }),
                      ),
                    ),
                    // Gráfico — usa imagen rasterizada si está lista, fallback a CustomPaint
                    SizedBox(
                      width: _chartWidth,
                      height: _chartHeight,
                      child: _chartImage != null
                          ? RawImage(
                              image: _chartImage,
                              fit: BoxFit.fill,
                              width: _chartWidth,
                              height: _chartHeight,
                            )
                          : CustomPaint(
                              size: Size(_chartWidth, _chartHeight),
                              painter: _HourlyChartPainter(
                                forecasts: _displayForecasts,
                                itemWidth: _itemWidth,
                                paddingLeft: _paddingLeft,
                              ),
                            ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _HourlyInfoColumn extends StatelessWidget {
  final HourlyForecast forecast;
  final List<WeatherAlert> alerts;
  final bool showDay;
  final bool hasAnyRain;

  const _HourlyInfoColumn({
    required this.forecast,
    required this.alerts,
    required this.showDay,
    required this.hasAnyRain,
  });

  List<WeatherAlert> _getActiveAlertsForHour() {
    final startOfHour = forecast.dateTime;
    final endOfHour = startOfHour.add(const Duration(hours: 1));
    
    final activeAlerts = alerts.where((alert) {
      if (alert.onset == null && alert.expires == null) return false;
      final onset = alert.onset ?? alert.expires!.subtract(const Duration(days: 1));
      final expires = alert.expires ?? alert.onset!.add(const Duration(days: 1));
      return onset.isBefore(endOfHour) && expires.isAfter(startOfHour);
    });

    final deduplicatedAlerts = <IconData, WeatherAlert>{};
    for (final alert in activeAlerts) {
      final icon = _getIconForEvent(alert.event);
      if (!deduplicatedAlerts.containsKey(icon) || alert.severity > deduplicatedAlerts[icon]!.severity) {
        deduplicatedAlerts[icon] = alert;
      }
    }
    
    final result = deduplicatedAlerts.values.toList();
    result.sort((a, b) => b.severity.compareTo(a.severity));
    return result;
  }

  static IconData _getIconForEvent(String event) {
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

  static Color _getWindColor(int? speed) {
    if (speed == null) return Colors.white70;
    if (speed >= 80) return Colors.redAccent.shade200;
    if (speed >= 65) return Colors.orange.shade400;
    if (speed >= 45) return Colors.yellow.shade400;
    return Colors.white70;
  }

  String _getDayLabel() {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final forecastDate = DateTime(forecast.dateTime.year, forecast.dateTime.month, forecast.dateTime.day);
    
    if (forecastDate == today) return 'Hoy';
    if (forecastDate == today.add(const Duration(days: 1))) return 'Mañana';
    
    final dayName = DateFormat('EEE', 'es_ES').format(forecast.dateTime);
    return dayName[0].toUpperCase() + dayName.substring(1);
  }

  @override
  Widget build(BuildContext context) {
    final weather = WeatherCode.fromCode(forecast.skyStateCode);
    final timeStr = DateFormat('HH:mm').format(forecast.dateTime);
    final isNow = DateTime.now().hour == forecast.dateTime.hour &&
        DateTime.now().day == forecast.dateTime.day;
    final activeAlerts = _getActiveAlertsForHour();
    final rain = forecast.precipitationProbability ?? 0;

    return Column(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        SizedBox(
          height: 14,
          child: showDay ? Text(
            _getDayLabel(),
            style: const TextStyle(color: Colors.white54, fontSize: 10, fontWeight: FontWeight.bold),
          ) : null,
        ),
        SizedBox(
          height: 18,
          child: Text(
            isNow ? 'Ahora' : timeStr,
            style: TextStyle(
              color: isNow ? Colors.white : Colors.white70,
              fontSize: 13,
              fontWeight: isNow ? FontWeight.w600 : FontWeight.w400,
            ),
          ),
        ),
        const SizedBox(height: 6),
        SizedBox(
          height: 36,
          child: Center(
            child: Icon(weather.icon, color: Colors.white, size: 28),
          ),
        ),
        if (hasAnyRain)
          SizedBox(
            height: 14,
            child: rain > 0 ? Text(
              '$rain%',
              style: TextStyle(
                color: Colors.lightBlue.shade300,
                fontSize: 10,
                fontWeight: FontWeight.bold,
              ),
            ) : null,
          ),
        if (hasAnyRain)
          const SizedBox(height: 4),
        SizedBox(
          height: 20,
          child: forecast.windSpeed != null ? Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              WindCompassArrow(
                windDirectionDegrees: forecast.windDirectionDegrees ?? 0,
                color: _getWindColor(forecast.windSpeed),
                size: 10,
              ),
              const SizedBox(width: 4),
              Text(
                '${forecast.windSpeed} km/h',
                style: TextStyle(
                  color: _getWindColor(forecast.windSpeed),
                  fontSize: 10,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ) : null,
        ),
        SizedBox(
          height: 16,
          child: activeAlerts.isNotEmpty ? Wrap(
            spacing: 2,
            alignment: WrapAlignment.center,
            children: activeAlerts.map((alert) {
              return Icon(
                _getIconForEvent(alert.event),
                color: alert.color,
                size: 14,
              );
            }).toList(),
          ) : null,
        ),
      ],
    );
  }
}

class _HourlyChartPainter extends CustomPainter {
  final List<HourlyForecast> forecasts;
  final double itemWidth;
  final double paddingLeft;

  // Objetos Paint reutilizables — se crean una vez, no en cada paint()
  static final Paint _guidePaint = Paint()
    ..color = Colors.white.withValues(alpha: 0.1)
    ..strokeWidth = 1;

  static final Paint _verticalLinePaint = Paint()
    ..color = Colors.white.withValues(alpha: 0.03)
    ..strokeWidth = 1;

  static final Paint _dotPaint = Paint()
    ..color = Colors.white;

  static const TextStyle _legendStyle = TextStyle(color: Colors.white54, fontSize: 10);
  static const TextStyle _tempStyle = TextStyle(
    color: Colors.white,
    fontSize: 14,
    fontWeight: FontWeight.bold,
  );

  const _HourlyChartPainter({
    required this.forecasts,
    required this.itemWidth,
    required this.paddingLeft,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (forecasts.isEmpty) return;

    double maxT = double.negativeInfinity;
    double minT = double.infinity;
    for (var f in forecasts) {
      if (f.temperature != null) {
        if (f.temperature! > maxT) maxT = f.temperature!.toDouble();
        if (f.temperature! < minT) minT = f.temperature!.toDouble();
      }
    }

    if (maxT == double.negativeInfinity) return;
    if (maxT == minT) {
      maxT += 1;
      minT -= 1;
    }

    final double paddingTop = 10.0;
    final double paddingBottom = 25.0;
    final double chartH = size.height - paddingTop - paddingBottom;

    // Dibujar textos de leyenda (max y min)
    final maxPainter = TextPainter(
      text: TextSpan(text: '${maxT.round()}°', style: _legendStyle),
      textDirection: ui.TextDirection.ltr,
    );
    maxPainter.layout();
    maxPainter.paint(canvas, Offset(8, paddingTop - maxPainter.height / 2));

    final minPainter = TextPainter(
      text: TextSpan(text: '${minT.round()}°', style: _legendStyle),
      textDirection: ui.TextDirection.ltr,
    );
    minPainter.layout();
    minPainter.paint(canvas, Offset(8, size.height - paddingBottom - minPainter.height / 2));

    // Líneas guía horizontales
    canvas.drawLine(Offset(paddingLeft, paddingTop), Offset(size.width, paddingTop), _guidePaint);
    canvas.drawLine(Offset(paddingLeft, size.height - paddingBottom), Offset(size.width, size.height - paddingBottom), _guidePaint);

    // Calcular puntos
    final points = List<Offset>.generate(forecasts.length, (i) {
      final t = forecasts[i].temperature?.toDouble() ?? minT;
      final x = paddingLeft + (i + 0.5) * itemWidth;
      final y = paddingTop + chartH * (1.0 - (t - minT) / (maxT - minT));
      return Offset(x, y);
    });

    // Líneas verticales por cada hora (batch con el mismo paint)
    for (int i = 0; i < points.length; i++) {
      canvas.drawLine(
        Offset(points[i].dx, 0),
        Offset(points[i].dx, size.height),
        _verticalLinePaint,
      );
    }

    // Trazar línea de temperaturas usando curvas cúbicas
    final path = Path();
    path.moveTo(points[0].dx, points[0].dy);
    for (int i = 0; i < points.length - 1; i++) {
        final p0 = points[i];
        final p1 = points[i + 1];
        final midX = (p0.dx + p1.dx) / 2;
        path.cubicTo(midX, p0.dy, midX, p1.dy, p1.dx, p1.dy);
    }

    // Crear gradiente horizontal basado en la temperatura
    final tempColors = List<Color>.generate(
      points.length,
      (i) => _colorForTemperature(forecasts[i].temperature?.toDouble() ?? minT),
    );
    final totalDx = points.last.dx - points.first.dx;
    final tempStops = List<double>.generate(
      points.length,
      (i) => totalDx > 0 ? (points[i].dx - points.first.dx) / totalDx : 0.0,
    );

    final tempGradient = ui.Gradient.linear(
      Offset(points.first.dx, 0),
      Offset(points.last.dx, 0),
      tempColors,
      tempStops,
    );

    // Dibujar borde de la línea con gradiente
    final strokePaint = Paint()
      ..shader = tempGradient
      ..strokeWidth = 2.5
      ..style = PaintingStyle.stroke;
    canvas.drawPath(path, strokePaint);

    // Pintar relleno sutil
    final fillPath = Path.from(path);
    fillPath.lineTo(points.last.dx, size.height);
    fillPath.lineTo(points.first.dx, size.height);
    fillPath.close();

    final fillPaint = Paint()
      ..shader = ui.Gradient.linear(
        Offset(0, paddingTop),
        Offset(0, size.height),
        [
          Colors.white.withValues(alpha: 0.15),
          Colors.white.withValues(alpha: 0.0),
        ],
      )
      ..style = PaintingStyle.fill;
    canvas.drawPath(fillPath, fillPaint);

    // Dibujar temperaturas y puntos
    for (int i = 0; i < points.length; i++) {
      final t = forecasts[i].temperature;
      if (t == null) continue;

      final tempPainter = TextPainter(
        text: TextSpan(text: '$t°', style: _tempStyle),
        textDirection: ui.TextDirection.ltr,
      );
      tempPainter.layout();
      tempPainter.paint(
        canvas, 
        Offset(points[i].dx - tempPainter.width / 2, points[i].dy + 8),
      );
      
      canvas.drawCircle(points[i], 2.5, _dotPaint);
    }
  }

  /// Mapea una temperatura (°C) a un color con transición suave.
  static Color _colorForTemperature(double temp) {
    const stops = [
      (temp: 0.0,  color: Color(0xFF2196F3)),
      (temp: 10.0, color: Color(0xFF00BCD4)),
      (temp: 18.0, color: Color(0xFF4CAF50)),
      (temp: 25.0, color: Color(0xFFFFEB3B)),
      (temp: 32.0, color: Color(0xFFFF9800)),
      (temp: 38.0, color: Color(0xFFF44336)),
    ];

    if (temp <= stops.first.temp) return stops.first.color;
    if (temp >= stops.last.temp) return stops.last.color;

    for (int i = 0; i < stops.length - 1; i++) {
      if (temp >= stops[i].temp && temp <= stops[i + 1].temp) {
        final t = (temp - stops[i].temp) / (stops[i + 1].temp - stops[i].temp);
        return Color.lerp(stops[i].color, stops[i + 1].color, t)!;
      }
    }
    return stops.last.color;
  }

  @override
  bool shouldRepaint(covariant _HourlyChartPainter oldDelegate) {
    if (oldDelegate.itemWidth != itemWidth || oldDelegate.paddingLeft != paddingLeft) return true;
    if (oldDelegate.forecasts.length != forecasts.length) return true;
    if (identical(oldDelegate.forecasts, forecasts)) return false;
    for (int i = 0; i < forecasts.length; i++) {
      if (oldDelegate.forecasts[i].temperature != forecasts[i].temperature) return true;
    }
    return false;
  }
}
