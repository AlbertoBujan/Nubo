import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:xml/xml.dart';
import '../models/weather_alert.dart';

/// Servicio para obtener alertas meteorológicas de AEMET.
///
/// Usa el endpoint de avisos CAP (Common Alerting Protocol) filtrado por
/// código de provincia. Parsea el XML y devuelve solo las alertas en español
/// que estén vigentes o próximas.
class AlertService {
  static const String _baseUrl = 'https://opendata.aemet.es/opendata';
  static const String _apiKey =
      'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJiaXJ0ZWJzQGdtYWlsLmNvbSIsImp0aSI6ImYwM2UxMjFmLTE2ODktNDdkMS1hYjNhLWI0MThlM2ZmMWNjMiIsImlzcyI6IkFFTUVUIiwiaWF0IjoxNzcxNDE3OTk3LCJ1c2VySWQiOiJmMDNlMTIxZi0xNjg5LTQ3ZDEtYWIzYS1iNDE4ZTNmZjFjYzIiLCJyb2xlIjoiIn0.npwJf-68OE2s0kIsRHVqjMqtmR9tedsgYrD03pjuYHc';

  final http.Client _client;

  AlertService({http.Client? client}) : _client = client ?? http.Client();

  /// Mapa de código de provincia (2 dígitos) → código de área AEMET.
  /// Los códigos de área de AEMET son los 2 dígitos de provincia del INE.
  /// Ej: Madrid = 28 → área 61 (Comunidad de Madrid incluye área 60)
  /// En realidad AEMET usa un código de CCAA, no de provincia directa.
  /// Usamos la tabla de correspondencias INE → área AEMET.
  static const Map<String, String> _provinciaToArea = {
    // Andalucía (Área 61)
    '04': '61', // Almería
    '11': '61', // Cádiz
    '14': '61', // Córdoba
    '18': '61', // Granada
    '21': '61', // Huelva
    '23': '61', // Jaén
    '29': '61', // Málaga
    '41': '61', // Sevilla

    // Aragón (Área 62)
    '22': '62', // Huesca
    '44': '62', // Teruel
    '50': '62', // Zaragoza

    // Asturias (Área 63)
    '33': '63', // Asturias

    // Illes Balears (Área 64 - Códigos internos AEMET para islas?)
    // Nota: El script devolvió 53, 54, 55 para Área 64.
    // El código INE de Baleares es 07. AEMET parece usar códigos especiales.
    // Mapeamos 07 -> 64 por si acaso.
    '07': '64',

    // Canarias (Área 65 - Códigos internos 90-96)
    // INE: Las Palmas (35), S.C. Tenerife (38).
    // Mapeamos ambos a 65.
    '35': '65',
    '38': '65',

    // Cantabria (Área 66)
    '39': '66', // Cantabria

    // Castilla y León (Área 67)
    '05': '67', // Ávila
    '09': '67', // Burgos
    '24': '67', // León
    '34': '67', // Palencia
    '37': '67', // Salamanca
    '40': '67', // Segovia
    '42': '67', // Soria
    '47': '67', // Valladolid
    '49': '67', // Zamora

    // Castilla-La Mancha (Área 68)
    '02': '68', // Albacete
    '13': '68', // Ciudad Real
    '16': '68', // Cuenca
    '19': '68', // Guadalajara
    '45': '68', // Toledo

    // Cataluña (Área 69)
    '08': '69', // Barcelona
    '17': '69', // Girona
    '25': '69', // Lleida
    '43': '69', // Tarragona

    // Extremadura (Área 70)
    '06': '70', // Badajoz
    '10': '70', // Cáceres

    // Galicia (Área 71)
    '15': '71', // A Coruña
    '27': '71', // Lugo
    '32': '71', // Ourense
    '36': '71', // Pontevedra

    // Madrid (Área 72)
    '28': '72', // Madrid

    // Murcia (Área 73)
    '30': '73', // Murcia

    // Navarra (Área 74)
    '31': '74', // Navarra

    // País Vasco (Área 75)
    '01': '75', // Álava
    '48': '75', // Bizkaia
    '20': '75', // Gipuzkoa

    // La Rioja (Área 76)
    '26': '76', // La Rioja

    // Comunidad Valenciana (Área 77)
    '03': '77', // Alicante
    '12': '77', // Castellón
    '46': '77', // Valencia

    // Ceuta (Área 78) - Nota: INE 51, script devolvió 51
    '51': '78',

    // Melilla (Área 79) - Nota: INE 52, script devolvió 52
    '52': '79',
  };

  /// Obtiene las alertas activas para un municipio dado.
  ///
  /// [municipioId] es el código INE del municipio (ej: "28079").
  /// Los 2 primeros dígitos son la provincia.
  Future<List<WeatherAlert>> fetchAlerts(String municipioId) async {
    if (municipioId.length < 2) return [];

    final provinciaCode = municipioId.substring(0, 2);
    final areaCode = _provinciaToArea[provinciaCode];
    if (areaCode == null) return [];

    try {
      // Paso 1: obtener URL temporal
      final endpoint =
          '$_baseUrl/api/avisos_cap/ultimoelaborado/area/$areaCode';
      final response1 = await _client.get(
        Uri.parse(endpoint),
        headers: {'api_key': _apiKey},
      );

      if (response1.statusCode != 200) return [];

      final body1 = jsonDecode(response1.body) as Map<String, dynamic>;
      final datosUrl = body1['datos'] as String?;
      if (datosUrl == null) return [];

      // Paso 2: obtener datos
      final response2 = await _client.get(Uri.parse(datosUrl));
      if (response2.statusCode != 200) return [];

      String decoded;
      try {
        decoded = utf8.decode(response2.bodyBytes);
      } catch (_) {
        decoded = latin1.decode(response2.bodyBytes);
      }

      return _parseCapAlerts(decoded, provinciaCode);
    } catch (_) {
      // Si falla no rompemos la app, simplemente no mostramos alertas
      return [];
    }
  }

  /// Parsea el contenido CAP/XML que puede contener múltiples alertas
  /// concatenadas (formato tar inline de AEMET).
  ///
  /// Filtra solo alertas cuya zona geográfica incluya [provinciaCode].
  List<WeatherAlert> _parseCapAlerts(String rawContent, String provinciaCode) {
    final alerts = <WeatherAlert>[];
    final areaCode = _provinciaToArea[provinciaCode] ?? '';

    // Prefijo del geocode para esta provincia: {área}{provincia}
    // Ej: Galicia=71, A Coruña=15 → "7115"
    final geocodePrefix = '$areaCode$provinciaCode';

    // AEMET concatena múltiples XMLs en el mismo body separados por
    // cabeceras tar. Extraemos cada bloque <alert>...</alert>.
    final alertPattern = RegExp(
      r'<alert[^>]*>.*?</alert>',
      dotAll: true,
    );

    for (final match in alertPattern.allMatches(rawContent)) {
      try {
        final xmlStr = '<?xml version="1.0" encoding="UTF-8"?>${match.group(0)}';
        final doc = XmlDocument.parse(xmlStr);
        final alertElement = doc.rootElement;

        // Buscamos solo los <info> en español
        for (final info in alertElement.findAllElements('info')) {
          final lang = info.getElement('language')?.innerText ?? '';
          if (!lang.startsWith('es')) continue;

          final alert = _parseInfoElement(info, geocodePrefix);
          if (alert != null && alert.isActiveOrUpcoming) {
            alerts.add(alert);
          }
        }
      } catch (_) {
        continue;
      }
    }

    return alerts;
  }

  WeatherAlert? _parseInfoElement(XmlElement info, String geocodePrefix) {
    // --- Filtrar por provincia via geocode ---
    bool matchesProvincia = false;
    for (final area in info.findAllElements('area')) {
      for (final geocode in area.findAllElements('geocode')) {
        final value = geocode.getElement('value')?.innerText ?? '';
        if (value.startsWith(geocodePrefix)) {
          matchesProvincia = true;
          break;
        }
      }
      if (matchesProvincia) break;
    }
    if (!matchesProvincia) return null;

    final event = info.getElement('event')?.innerText ?? '';
    final headline = info.getElement('headline')?.innerText ?? '';
    final description = info.getElement('description')?.innerText ?? '';
    final instruction = info.getElement('instruction')?.innerText ?? '';

    final onsetStr = info.getElement('onset')?.innerText;
    final expiresStr = info.getElement('expires')?.innerText;

    final onset = onsetStr != null ? DateTime.tryParse(onsetStr) : null;
    final expires = expiresStr != null ? DateTime.tryParse(expiresStr) : null;

    // Extraer nivel de los parámetros
    String nivel = '';
    String probability = '';
    for (final param in info.findAllElements('parameter')) {
      final name = param.getElement('valueName')?.innerText ?? '';
      final value = param.getElement('value')?.innerText ?? '';
      if (name.contains('nivel')) nivel = value;
      if (name.contains('probabilidad')) probability = value;
    }

    // Descartar "verde" → significa que NO hay alerta para ese fenómeno
    if (nivel.toLowerCase() == 'verde') return null;

    // Extraer área
    final area = info.getElement('area');
    final areaDesc = area?.getElement('areaDesc')?.innerText ?? '';

    if (event.isEmpty && headline.isEmpty) return null;

    return WeatherAlert(
      nivel: nivel,
      event: event,
      headline: headline,
      description: description,
      instruction: instruction,
      areaDescription: areaDesc,
      onset: onset,
      expires: expires,
      probability: probability,
    );
  }
}
