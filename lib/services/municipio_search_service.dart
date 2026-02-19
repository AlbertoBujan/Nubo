import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/saved_location.dart';

/// Servicio para buscar municipios usando la API de AEMET.
///
/// Descarga el listado completo de municipios una única vez y lo cachea
/// en memoria para que el autocompletado sea instantáneo.
class MunicipioSearchService {
  static const String _baseUrl = 'https://opendata.aemet.es/opendata';
  static const String _apiKey =
      'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJiaXJ0ZWJzQGdtYWlsLmNvbSIsImp0aSI6ImYwM2UxMjFmLTE2ODktNDdkMS1hYjNhLWI0MThlM2ZmMWNjMiIsImlzcyI6IkFFTUVUIiwiaWF0IjoxNzcxNDE3OTk3LCJ1c2VySWQiOiJmMDNlMTIxZi0xNjg5LTQ3ZDEtYWIzYS1iNDE4ZTNmZjFjYzIiLCJyb2xlIjoiIn0.npwJf-68OE2s0kIsRHVqjMqtmR9tedsgYrD03pjuYHc';

  final http.Client _client;

  // Caché en memoria: lista completa de municipios de AEMET.
  // Cada elemento tiene: {id, nombre, latitud_dec, longitud_dec}
  List<Map<String, dynamic>> _allMunicipios = [];
  bool _loaded = false;

  MunicipioSearchService({http.Client? client})
      : _client = client ?? http.Client();

  /// Carga el listado completo si aún no está en caché.
  Future<void> _ensureLoaded() async {
    if (_loaded) return;

    const endpoint = '$_baseUrl/api/maestro/municipios';

    final response1 = await _client.get(
      Uri.parse(endpoint),
      headers: {'api_key': _apiKey},
    );

    if (response1.statusCode != 200) return;

    final body1 = jsonDecode(response1.body) as Map<String, dynamic>;
    final datosUrl = body1['datos'] as String?;
    if (datosUrl == null) return;

    final response2 = await _client.get(Uri.parse(datosUrl));
    if (response2.statusCode != 200) return;

    String decoded;
    try {
      decoded = utf8.decode(response2.bodyBytes);
    } catch (_) {
      decoded = latin1.decode(response2.bodyBytes);
    }

    final data = jsonDecode(decoded);
    if (data is List) {
      _allMunicipios = data.cast<Map<String, dynamic>>();
    }
    _loaded = true;
  }

  /// Busca municipios cuyo nombre contenga [query] (insensible a mayúsculas).
  ///
  /// Retorna hasta 10 resultados.
  Future<List<SavedLocation>> searchByName(String query) async {
    if (query.trim().isEmpty) return [];

    await _ensureLoaded();

    final q = _normalize(query);

    return _allMunicipios
        .where((m) {
          final nombre = _normalize(m['nombre'] as String? ?? '');
          return nombre.contains(q);
        })
        .take(10)
        .map((m) => SavedLocation(
              municipioId: (m['id'] as String).replaceFirst('id', ''),
              nombre: _cleanNombre(m['nombre'] as String? ?? ''),
            ))
        .toList();
  }

  /// Encuentra el municipio más cercano a las coordenadas dadas.
  Future<SavedLocation?> findNearestMunicipio(double lat, double lon) async {
    await _ensureLoaded();
    if (_allMunicipios.isEmpty) return null;

    SavedLocation? nearest;
    double minDist = double.infinity;

    for (final m in _allMunicipios) {
      final latStr = m['latitud_dec'] as String?;
      final lonStr = m['longitud_dec'] as String?;
      if (latStr == null || lonStr == null) continue;

      final mLat = double.tryParse(latStr.replaceAll(',', '.'));
      final mLon = double.tryParse(lonStr.replaceAll(',', '.'));
      if (mLat == null || mLon == null) continue;

      final dist = _dist(lat, lon, mLat, mLon);
      if (dist < minDist) {
        minDist = dist;
        nearest = SavedLocation(
          municipioId: (m['id'] as String).replaceFirst('id', ''),
          nombre: _cleanNombre(m['nombre'] as String? ?? ''),
        );
      }
    }

    return nearest;
  }

  /// Distancia euclídea sobre grados (suficiente para este uso).
  double _dist(double lat1, double lon1, double lat2, double lon2) {
    final dlat = lat1 - lat2;
    final dlon = lon1 - lon2;
    return dlat * dlat + dlon * dlon;
  }

  /// Normaliza el texto para comparación: minúsculas, sin tildes.
  String _normalize(String s) {
    return s
        .toLowerCase()
        .replaceAll('á', 'a')
        .replaceAll('é', 'e')
        .replaceAll('í', 'i')
        .replaceAll('ó', 'o')
        .replaceAll('ú', 'u')
        .replaceAll('ü', 'u')
        .replaceAll('à', 'a')
        .replaceAll('è', 'e')
        .replaceAll('ì', 'i')
        .replaceAll('ò', 'o')
        .replaceAll('ù', 'u')
        .replaceAll('ñ', 'n');
  }

  /// Limpia el nombre AEMET: elimina el prefijo de provincia entre paréntesis.
  /// Ej: "Madrid" queda igual; "Valencia/València" se queda como está.
  String _cleanNombre(String nombre) {
    // AEMET a veces incluye "(provincia)" al final
    return nombre.replaceAll(RegExp(r'\s*\(.*?\)\s*$'), '').trim();
  }
  /// Obtiene las coordenadas (latitud, longitud) de un municipio por su ID.
  Future<({double lat, double lon})?> getCoordinates(String municipioId) async {
    await _ensureLoaded();
    
    // El id en el JSON tiene prefijo "id" (ej: "id28079")
    final searchId = municipioId.startsWith('id') ? municipioId : 'id$municipioId';
    
    try {
      final municipio = _allMunicipios.firstWhere(
        (m) => m['id'] == searchId,
        orElse: () => {},
      );
      
      if (municipio.isEmpty) return null;
      
      final latStr = municipio['latitud_dec'] as String?;
      final lonStr = municipio['longitud_dec'] as String?;
      
      if (latStr == null || lonStr == null) return null;
      
      final lat = double.tryParse(latStr.replaceAll(',', '.'));
      final lon = double.tryParse(lonStr.replaceAll(',', '.'));
      
      if (lat != null && lon != null) {
        return (lat: lat, lon: lon);
      }
    } catch (_) {
      return null;
    }
    return null;
  }
}
