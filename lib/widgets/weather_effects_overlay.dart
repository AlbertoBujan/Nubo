import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../models/weather_enums.dart';

/// Efecto meteorológico que se dibuja sobre el fondo dinámico.
///
/// La intensidad decide cuántas gotas caen y a qué velocidad; el destello es
/// exclusivo de la tormenta.
enum WeatherEffect {
  none(dropCount: 0, speed: 0, dropLength: 0, opacity: 0),
  drizzle(dropCount: 28, speed: 0.55, dropLength: 0.018, opacity: 0.26),
  rain(dropCount: 55, speed: 0.95, dropLength: 0.032, opacity: 0.34),
  heavyRain(dropCount: 80, speed: 1.35, dropLength: 0.05, opacity: 0.42),
  thunder(dropCount: 80, speed: 1.35, dropLength: 0.05, opacity: 0.42);

  const WeatherEffect({
    required this.dropCount,
    required this.speed,
    required this.dropLength,
    required this.opacity,
  });

  /// Nº de gotas simultáneas. Acotado a propósito: en gama baja el coste del
  /// repintado crece con las partículas, y por encima de ~80 no se aprecia.
  final int dropCount;

  /// Alturas de pantalla que recorre una gota por segundo.
  final double speed;

  /// Largo de la gota como fracción de la altura de pantalla.
  final double dropLength;

  /// Opacidad base del trazo.
  final double opacity;

  bool get hasFlashes => this == WeatherEffect.thunder;

  /// Deriva el efecto del código WMO del cielo (con o sin sufijo 'n').
  static WeatherEffect fromSkyCode(String? code) {
    final group = WeatherCodeGroup.fromCode(code);
    if (!group.hasRain) return WeatherEffect.none;
    if (group.hasThunder) return WeatherEffect.thunder;
    if (group == WeatherCodeGroup.drizzle) return WeatherEffect.drizzle;

    // Dentro de la lluvia, el dígito alto marca los chubascos e intensidades
    // fuertes (65, 67, 82) frente a los débiles (61, 66, 80).
    final numeric = WeatherCodeGroup.numericValue(code) ?? 0;
    const heavy = {65, 67, 82};
    return heavy.contains(numeric) ? WeatherEffect.heavyRain : WeatherEffect.rain;
  }
}

/// Capa de partículas que llueve —y relampaguea— sobre el fondo de la app.
///
/// Sigue el mismo planteamiento que Breezy Weather: partículas dibujadas a mano
/// sobre un canvas en vez de una animación empaquetada, para poder ajustar
/// densidad y velocidad al fenómeno real. El destello del rayo tampoco dibuja
/// un rayo: es un velo de color a pantalla completa cuya opacidad sigue una
/// curva corta, que es lo que de verdad se percibe de un relámpago.
///
/// El widget es puramente decorativo: va envuelto en [IgnorePointer] para no
/// robar los gestos del PageView que tiene debajo.
class WeatherEffectsOverlay extends StatefulWidget {
  final WeatherEffect effect;

  const WeatherEffectsOverlay({super.key, required this.effect});

  @override
  State<WeatherEffectsOverlay> createState() => _WeatherEffectsOverlayState();
}

class _WeatherEffectsOverlayState extends State<WeatherEffectsOverlay>
    with SingleTickerProviderStateMixin, WidgetsBindingObserver {
  /// Duración del ciclo del ticker. Las posiciones se calculan como función
  /// pura del tiempo transcurrido, así que este valor solo fija cada cuánto
  /// se reinicia el contador; se elige largo para que el bucle no se note.
  static const Duration _cycle = Duration(minutes: 1);

  /// Segundos que tarda un cambio de fenómeno en entrar o salir del todo.
  static const double _fadeSeconds = 1.2;

  late final AnimationController _controller;
  late List<_Drop> _drops;

  /// Destellos programados dentro del ciclo, en segundos.
  late List<double> _flashes;

  /// Opacidad global del efecto, para no cortar en seco al cambiar de ciudad.
  double _intensity = 0;
  double _lastTick = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    _controller = AnimationController(vsync: this, duration: _cycle);
    _drops = _buildDrops();
    _flashes = _buildFlashes();
    _intensity = widget.effect == WeatherEffect.none ? 0 : 1;

    if (widget.effect != WeatherEffect.none) _controller.repeat();
  }

  @override
  void didUpdateWidget(covariant WeatherEffectsOverlay oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.effect == widget.effect) return;

    // Se regeneran las gotas para adoptar la densidad del nuevo fenómeno,
    // pero el ticker no se reinicia: la transición la hace `_intensity`.
    _drops = _buildDrops();
    _syncTicker();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Sin esto el ticker seguiría corriendo con la app en segundo plano,
    // repintando y gastando batería para nadie.
    if (state == AppLifecycleState.resumed) {
      _syncTicker();
    } else {
      _controller.stop();
    }
  }

  void _syncTicker() {
    final needsTicker = widget.effect != WeatherEffect.none || _intensity > 0;
    if (needsTicker && !_controller.isAnimating) {
      _controller.repeat();
    } else if (!needsTicker && _controller.isAnimating) {
      _controller.stop();
    }
  }

  List<_Drop> _buildDrops() {
    // Semilla fija: el patrón debe ser estable entre reconstrucciones para que
    // las gotas no "salten" de sitio al cambiar de página.
    final random = math.Random(7);
    return List.generate(
      widget.effect.dropCount,
      (_) => _Drop(
        x: random.nextDouble(),
        phase: random.nextDouble(),
        // Variar velocidad y longitud da sensación de profundidad: las gotas
        // "cercanas" caen más rápido y se dibujan más largas.
        depth: 0.55 + random.nextDouble() * 0.45,
        drift: -0.06 + random.nextDouble() * 0.03,
      ),
    );
  }

  List<double> _buildFlashes() {
    final random = math.Random(23);
    final flashes = <double>[];
    double time = 2 + random.nextDouble() * 4;
    final cycleSeconds = _cycle.inSeconds.toDouble();

    while (time < cycleSeconds - 1) {
      flashes.add(time);
      time += 3 + random.nextDouble() * 5; // un destello cada 3-8 s
    }
    return flashes;
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: RepaintBoundary(
        child: AnimatedBuilder(
          animation: _controller,
          builder: (context, _) {
            final elapsed = _controller.value * _cycle.inSeconds;
            _advanceIntensity(elapsed);

            if (_intensity <= 0) return const SizedBox.expand();

            return CustomPaint(
              size: Size.infinite,
              painter: _WeatherEffectsPainter(
                drops: _drops,
                effect: widget.effect,
                elapsedSeconds: elapsed,
                intensity: _intensity,
                flashAlpha: widget.effect.hasFlashes
                    ? _flashAlphaAt(elapsed) * _intensity
                    : 0,
              ),
            );
          },
        ),
      ),
    );
  }

  /// Acerca [_intensity] a su objetivo con el tiempo real transcurrido, para
  /// que la entrada y salida del efecto sea gradual y no un corte.
  void _advanceIntensity(double elapsed) {
    // El ciclo reinicia el contador; en ese frame no hay delta fiable.
    final delta = elapsed >= _lastTick ? elapsed - _lastTick : 0.0;
    _lastTick = elapsed;

    final target = widget.effect == WeatherEffect.none ? 0.0 : 1.0;
    if (_intensity == target) {
      // Terminado el fundido de salida ya no hay nada que animar. Se aplaza a
      // después del frame porque esto corre dentro del build del AnimatedBuilder
      // y detener el ticker ahí tocaría el árbol mientras se está construyendo.
      if (target == 0 && _controller.isAnimating) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) _syncTicker();
        });
      }
      return;
    }

    final step = delta / _fadeSeconds;
    _intensity = target > _intensity
        ? math.min(target, _intensity + step)
        : math.max(target, _intensity - step);
  }

  /// Opacidad del destello en un instante dado.
  ///
  /// Cada relámpago dura 300 ms y describe un doble pico —brilla, cae, vuelve a
  /// brillar más fuerte y se apaga—, que es como se percibe una descarga real.
  double _flashAlphaAt(double elapsed) {
    for (final start in _flashes) {
      final t = elapsed - start;
      if (t < 0 || t > 0.3) continue;

      if (t < 0.05) return (t / 0.05) * 0.55;
      if (t < 0.10) return 0.55 - ((t - 0.05) / 0.05) * 0.35;
      if (t < 0.16) return 0.20 + ((t - 0.10) / 0.06) * 0.80;
      return 1.0 - ((t - 0.16) / 0.14);
    }
    return 0;
  }
}

/// Una gota, definida por parámetros constantes: su posición se deriva del
/// tiempo, así no hay estado que actualizar ni deriva acumulada.
class _Drop {
  /// Posición horizontal inicial (0..1).
  final double x;

  /// Desfase vertical inicial (0..1), para que no caigan todas a la vez.
  final double phase;

  /// Cercanía aparente (0..1): afecta a velocidad, largo y opacidad.
  final double depth;

  /// Desplazamiento horizontal durante la caída, que inclina la lluvia.
  final double drift;

  const _Drop({
    required this.x,
    required this.phase,
    required this.depth,
    required this.drift,
  });
}

class _WeatherEffectsPainter extends CustomPainter {
  final List<_Drop> drops;
  final WeatherEffect effect;
  final double elapsedSeconds;
  final double intensity;
  final double flashAlpha;

  _WeatherEffectsPainter({
    required this.drops,
    required this.effect,
    required this.elapsedSeconds,
    required this.intensity,
    required this.flashAlpha,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (flashAlpha > 0) {
      canvas.drawRect(
        Offset.zero & size,
        Paint()..color = const Color(0xFFEAF2FF).withValues(alpha: flashAlpha * 0.5),
      );
    }

    if (drops.isEmpty || intensity <= 0) return;

    final paint = Paint()
      ..strokeCap = StrokeCap.round
      ..style = PaintingStyle.stroke;

    for (final drop in drops) {
      // Recorrido normalizado 0..1 que se repite: función pura del tiempo.
      final travel =
          (drop.phase + elapsedSeconds * effect.speed * drop.depth) % 1.0;

      final startX = (drop.x + drop.drift * travel) * size.width;
      final startY = travel * size.height;
      final length = effect.dropLength * drop.depth * size.height;

      paint
        ..strokeWidth = 1.0 + drop.depth * 0.8
        ..color = Colors.white.withValues(
          alpha: effect.opacity * drop.depth * intensity,
        );

      canvas.drawLine(
        Offset(startX, startY),
        Offset(startX + drop.drift * size.width * 0.12, startY + length),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _WeatherEffectsPainter old) =>
      old.elapsedSeconds != elapsedSeconds ||
      old.intensity != intensity ||
      old.flashAlpha != flashAlpha ||
      old.effect != effect ||
      !identical(old.drops, drops);
}
