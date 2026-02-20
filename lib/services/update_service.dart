import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';

class GithubUpdateService {
  // Ajustar nombre de usuario y repo 
  static const String _repoOwner = 'AlbertoBujan';
  static const String _repoName = 'Nubo';
  static const String _releasesUrl = 'https://api.github.com/repos/$_repoOwner/$_repoName/releases/latest';

  /// Comprueba si hay una actualización disponible.
  /// Retorna un Map con { 'isAvailable': bool, 'version': String, 'downloadUrl': String }
  Future<Map<String, dynamic>> checkForUpdates() async {
    try {
      final response = await http.get(Uri.parse(_releasesUrl));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final String latestTag = data['tag_name'] ?? '';
        final String latestVersion = latestTag.replaceAll('v', '');
        
        // Obtener versión instalada
        PackageInfo packageInfo = await PackageInfo.fromPlatform();
        String currentVersion = packageInfo.version;

        if (_isNewerVersion(currentVersion, latestVersion)) {
          // Buscar el APK en los assets de la release
          String? downloadUrl;
          final assets = data['assets'] as List<dynamic>?;
          
          if (assets != null) {
             for(var asset in assets) {
               final name = asset['name'].toString().toLowerCase();
               if(name.endsWith('.apk')) {
                 downloadUrl = asset['browser_download_url'];
                 break;
               }
             }
          }

          // Si no encontramos APK directo, redirigir a la propia release en html
          downloadUrl ??= data['html_url'];

          return {
            'isAvailable': true,
            'version': latestVersion,
            'downloadUrl': downloadUrl,
          };
        }
      }
    } catch (e) {
      // Ignoramos silenciosamente errores de red en segundo plano
      debugPrint('Error comprobando actualizaciones: $e');
    }

    return {'isAvailable': false};
  }

  /// Lanza el enlace de descarga en el navegador por defecto
  Future<void> launchDownload(String url) async {
    final uri = Uri.parse(url);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  /// Compara dos versiones simples ej: '0.1.0' y '0.1.1'
  bool _isNewerVersion(String current, String latest) {
    List<int> currentParts = current.split('.').map(int.parse).toList();
    List<int> latestParts = latest.split('.').map(int.parse).toList();

    for (int i = 0; i < 3; i++) {
        int c = i < currentParts.length ? currentParts[i] : 0;
        int l = i < latestParts.length ? latestParts[i] : 0;
        
        if (l > c) return true;
        if (l < c) return false;
    }
    return false;
  }
}

// Simulacion de debugPrint basica si no está disponible, para no arrastrar dependencias grandes
void debugPrint(String message) {
  // print(message);
}
