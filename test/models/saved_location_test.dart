import 'package:flutter_test/flutter_test.dart';
import 'package:nubo/models/saved_location.dart';

void main() {
  group('SavedLocation._formatNombre', () {
    test('reordena "Coruña, A" a "A Coruña"', () {
      final loc = SavedLocation(municipioId: '15030', nombre: 'Coruña, A');
      expect(loc.nombre, 'A Coruña');
    });

    test('reordena "Bañeza, La" a "La Bañeza"', () {
      final loc = SavedLocation(municipioId: '24008', nombre: 'Bañeza, La');
      expect(loc.nombre, 'La Bañeza');
    });

    test('no reordena nombres sin coma', () {
      final loc = SavedLocation(municipioId: '28079', nombre: 'Madrid');
      expect(loc.nombre, 'Madrid');
    });

    test('no reordena si el artículo tiene más de 4 caracteres', () {
      final loc = SavedLocation(municipioId: '99999', nombre: 'Nombre, Articulo');
      expect(loc.nombre, 'Nombre, Articulo');
    });

    test('maneja nombre vacío sin lanzar excepción', () {
      final loc = SavedLocation(municipioId: '00000', nombre: '');
      expect(loc.nombre, '');
    });

    test('nombre con solo coma-espacio no se rompe', () {
      final loc = SavedLocation(municipioId: '00001', nombre: ', ');
      // parts = ['', ''] → article='', name='' → article.length <= 4 → ' '
      expect(loc.nombre, isA<String>());
    });
  });

  group('SavedLocation serialización', () {
    test('toPrefsString y fromPrefsString round-trip', () {
      final original = SavedLocation(municipioId: '28079', nombre: 'Madrid');
      final serialized = original.toPrefsString();
      final restored = SavedLocation.fromPrefsString(serialized);

      expect(restored, isNotNull);
      expect(restored!.municipioId, original.municipioId);
      expect(restored.nombre, original.nombre);
    });

    test('fromPrefsString con formato inválido devuelve null', () {
      expect(SavedLocation.fromPrefsString('formato_sin_pipe'), isNull);
      expect(SavedLocation.fromPrefsString(''), isNull);
    });

    test('fromPrefsString soporta nombre con pipe', () {
      const s = '28079|Nombre|con|pipes';
      final loc = SavedLocation.fromPrefsString(s);
      expect(loc, isNotNull);
      expect(loc!.municipioId, '28079');
      expect(loc.nombre, 'Nombre|con|pipes');
    });

    test('fromPrefsString aplica formatNombre al restaurar', () {
      const s = '15030|Coruña, A';
      final loc = SavedLocation.fromPrefsString(s);
      expect(loc!.nombre, 'A Coruña');
    });
  });

  group('SavedLocation igualdad', () {
    test('dos localizaciones con mismo municipioId son iguales', () {
      final a = SavedLocation(municipioId: '28079', nombre: 'Madrid');
      final b = SavedLocation(municipioId: '28079', nombre: 'Madrid Centro');
      expect(a, equals(b));
    });

    test('localizaciones con distinto municipioId no son iguales', () {
      final a = SavedLocation(municipioId: '28079', nombre: 'Madrid');
      final b = SavedLocation(municipioId: '08019', nombre: 'Barcelona');
      expect(a, isNot(equals(b)));
    });

    test('hashCode consistente con la igualdad', () {
      final a = SavedLocation(municipioId: '28079', nombre: 'Madrid');
      final b = SavedLocation(municipioId: '28079', nombre: 'Madrid Centro');
      expect(a.hashCode, equals(b.hashCode));
    });
  });
}
