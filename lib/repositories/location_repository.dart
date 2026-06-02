import 'package:geolocator/geolocator.dart';
import '../models/saved_location.dart';
import '../services/location_service.dart';
import '../services/municipio_search_service.dart';

abstract interface class LocationRepository {
  Future<({double lat, double lon})?> getCoordinates(String municipioId);
  Future<SavedLocation?> findNearest(double lat, double lon);
  Future<List<SavedLocation>> searchByName(String query);
  Future<Position> getCurrentPosition();
}

class LocationRepositoryImpl implements LocationRepository {
  final MunicipioSearchService _searchService;
  final LocationService _locationService;

  LocationRepositoryImpl({
    MunicipioSearchService? searchService,
    LocationService? locationService,
  })  : _searchService = searchService ?? MunicipioSearchService(),
        _locationService = locationService ?? LocationService();

  @override
  Future<({double lat, double lon})?> getCoordinates(String municipioId) =>
      _searchService.getCoordinates(municipioId);

  @override
  Future<SavedLocation?> findNearest(double lat, double lon) =>
      _searchService.findNearestMunicipio(lat, lon);

  @override
  Future<List<SavedLocation>> searchByName(String query) =>
      _searchService.searchByName(query);

  @override
  Future<Position> getCurrentPosition() =>
      _locationService.getCurrentPosition();
}
