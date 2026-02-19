/// Modelo para una localización guardada por el usuario.
class SavedLocation {
  final String municipioId;
  final String nombre;

  const SavedLocation({required this.municipioId, required this.nombre});

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
