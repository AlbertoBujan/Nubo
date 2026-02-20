<p align="center">
  <img src="assets/icon/nubo.png" width="100" alt="Nubo Logo"/>
</p>

# Nubo ⛅

Nubo es una aplicación meteorológica minimalista y moderna para Android desarrollada en Flutter. Obtiene datos en tiempo real de la Agencia Estatal de Meteorología (AEMET) de España.

## Características ✨
- **Multilocalización:** Guarda y desliza entre tus ciudades favoritas de España para ver el tiempo al instante.
- **Predicción a Corto y Largo Plazo:** Consulta el tiempo actual, su evolución por horas y el pronóstico detallado para los próximos días.
- **Alertas Meteorológicas:** Recibe notificaciones de avisos emitidos por la AEMET.
- **Actualizaciones In-App:** El sistema de autoactualización de Nubo descargará e instalará directamente nuevas versiones desde GitHub Releases de forma transparente y visual.
- **Ciclo Solar Integrado:** El fondo de la aplicación cambia automática y gradualmente basándose en los ciclos del atardecer, amanecer, y las horas solares específicas de cada ciudad.

## Créditos 🏆
- Los datos climatológicos son proveídos por el servicio público de la [AEMET (Agencia Estatal de Meteorología)](https://opendata.aemet.es/).
- Animaciones Lottie (Loading Sun) creadas originalmente por [Michelle Hardi](https://lottiefiles.com/hardi).

## Desarrollo 🛠️
Para construir la versión de release:
```bash
flutter build apk --release
```
