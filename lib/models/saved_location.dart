/// Modelo para una localización guardada por el usuario.
class SavedLocation {
  final String municipioId;
  final String nombre;

  SavedLocation({required this.municipioId, required String nombre})
      : nombre = _formatNombre(nombre);

  /// AEMET devuelve nombres como "Coruña, A" o "Bañeza, La".
  /// Esto lo detecta y lo reordena a "A Coruña" o "La Bañeza".
  static String _formatNombre(String rawName) {
    if (!rawName.contains(', ')) return rawName;
    
    // Si contiene una coma y un espacio, asume formato "Nombre, Artículo"
    final parts = rawName.split(', ');
    if (parts.length == 2) {
      final article = parts[1].trim();
      final name = parts[0].trim();
      // Heurística básica: Si lo que hay después de la coma es corto (un artículo), se da la vuelta
      if (article.length <= 4) {
        return '$article $name';
      }
    }
    return rawName;
  }

  /// Serialización a String para SharedPreferences.
  /// Formato: "municipioId|nombre"
  String toPrefsString() => '$municipioId|$nombre';

  /// Deserialización desde SharedPreferences.
  static SavedLocation? fromPrefsString(String s) {
    final parts = s.split('|');
    if (parts.length < 2) return null;
    return SavedLocation(
      municipioId: parts[0],
      nombre: parts.sublist(1).join('|'), // soporta nombres con pipe
    );
  }

  @override
  bool operator ==(Object other) =>
      other is SavedLocation && other.municipioId == municipioId;

  @override
  int get hashCode => municipioId.hashCode;

  @override
  String toString() => 'SavedLocation($municipioId, $nombre)';
}
