import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:url_launcher/url_launcher.dart';

class InfoDialog extends StatelessWidget {
  const InfoDialog({super.key});

  Future<void> _launchUrl(String url) async {
    final uri = Uri.parse(url);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: const Color(0xFF1E2A3A),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      title: const Row(
        children: [
          Icon(LucideIcons.info, color: Colors.blueAccent),
          SizedBox(width: 10),
          Text('Información de Nubo', style: TextStyle(color: Colors.white)),
        ],
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Nubo es una aplicación meteorológica de código abierto desarrollada en Flutter.',
            style: TextStyle(color: Colors.white70, fontSize: 14, height: 1.4),
          ),
          const SizedBox(height: 20),
          const Text(
            'Fuentes de datos',
            style: TextStyle(
              color: Colors.white,
              fontSize: 16,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            'Todos los datos climatológicos mostrados en esta aplicación son proporcionados y elaborados por la Agencia Estatal de Meteorología (AEMET).',
            style: TextStyle(color: Colors.white70, fontSize: 14, height: 1.4),
          ),
          const SizedBox(height: 8),
          InkWell(
            onTap: () => _launchUrl('https://opendata.aemet.es/'),
            child: const Text(
              'AEMET OpenData',
              style: TextStyle(
                color: Colors.blueAccent,
                fontSize: 14,
                decoration: TextDecoration.underline,
              ),
            ),
          ),
          const SizedBox(height: 20),
          const Text(
            'Créditos visuales',
            style: TextStyle(
              color: Colors.white,
              fontSize: 16,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            'La animación del sol mostrada durante las cargas es pública y gratuita, creada originalmente por Michelle Hardi.',
            style: TextStyle(color: Colors.white70, fontSize: 14, height: 1.4),
          ),
          const SizedBox(height: 8),
          InkWell(
            onTap: () => _launchUrl('https://lottiefiles.com/hardi'),
            child: const Text(
              'Perfil de Michelle Hardi en LottieFiles',
              style: TextStyle(
                color: Colors.blueAccent,
                fontSize: 14,
                decoration: TextDecoration.underline,
              ),
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Hecho', style: TextStyle(color: Colors.white)),
        ),
      ],
    );
  }
}
