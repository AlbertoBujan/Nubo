import 'dart:ui' as ui;

import 'package:flutter/material.dart';

/// Tarjeta de cristal esmerilado: desenfoca lo que tiene detrás.
///
/// Las tarjetas de Nubo son translúcidas (5-10 % de blanco sobre el gradiente),
/// así que por sí solas no ocultan nada: la lluvia del fondo se veía a través
/// de ellas casi sin atenuar y parecía pasar por delante. Al desenfocar el
/// fondo dentro del recorte de la tarjeta, las gotas quedan claramente
/// detrás del cristal sin perder la estética translúcida.
///
/// El desenfoque es lo más caro que se pinta en la app, así que conviene
/// usarlo en las tarjetas de verdad y no en adornos pequeños.
class GlassCard extends StatelessWidget {
  /// Desenfoque por defecto. Suficiente para difuminar una gota sin convertir
  /// el gradiente de fondo en una mancha plana.
  static const double defaultBlur = 12;

  final Widget child;

  /// Relleno translúcido que se pinta sobre el fondo ya desenfocado.
  final Color color;

  final BorderRadius borderRadius;

  /// Se dibuja por encima del recorte para que no se coma medio trazo.
  final BoxBorder? border;

  final EdgeInsetsGeometry? padding;
  final EdgeInsetsGeometry? margin;
  final double blurSigma;

  const GlassCard({
    super.key,
    required this.child,
    required this.color,
    required this.borderRadius,
    this.border,
    this.padding,
    this.margin,
    this.blurSigma = defaultBlur,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: margin,
      // El borde va en `foregroundDecoration` para pintarse sobre el contenido
      // recortado; en `decoration` quedaría tapado por el propio cristal.
      foregroundDecoration: border == null
          ? null
          : BoxDecoration(borderRadius: borderRadius, border: border),
      child: ClipRRect(
        // El recorte es obligatorio: sin él, BackdropFilter desenfocaría toda
        // la capa que hay por debajo, no solo el trozo de la tarjeta.
        borderRadius: borderRadius,
        child: BackdropFilter(
          filter: ui.ImageFilter.blur(sigmaX: blurSigma, sigmaY: blurSigma),
          child: Container(
            padding: padding,
            color: color,
            child: child,
          ),
        ),
      ),
    );
  }
}
