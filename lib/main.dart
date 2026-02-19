import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';
import 'providers/weather_provider.dart';
import 'screens/home_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Inicializar formateo de fechas en español
  await initializeDateFormatting('es_ES', null);

  runApp(const NuboApp());
}

/// Aplicación meteorológica Nubo.
///
/// Usa Provider para inyectar el WeatherProvider y Material 3
/// con tema oscuro como base visual.
class NuboApp extends StatelessWidget {
  const NuboApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) {
        final provider = WeatherProvider();
        provider.init(); // Carga localizaciones guardadas y datos iniciales
        return provider;
      },
      child: MaterialApp(
        title: 'Nubo',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          useMaterial3: true,
          brightness: Brightness.dark,
          colorSchemeSeed: const Color(0xFF1A73E8),
          // Tipografía moderna con Google Fonts (Inter)
          textTheme: GoogleFonts.interTextTheme(
            ThemeData.dark().textTheme,
          ),
          scaffoldBackgroundColor: const Color(0xFF1A1A2E),
        ),
        home: const HomeScreen(),
      ),
    );
  }
}
