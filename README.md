<p align="center">
  <img src="assets/icon/nubo_icon.png" width="100" alt="Nubo Logo"/>
</p>

# Nubo ⛅

Nubo es una aplicación meteorológica minimalista y moderna para Android, escrita en **Kotlin con Jetpack Compose**. La predicción la proporciona [Open-Meteo](https://open-meteo.com/) y los avisos meteorológicos la [AEMET](https://opendata.aemet.es/).

## Características ✨
- **Multilocalización:** Guarda y desliza entre tus ciudades favoritas de España para ver el tiempo al instante.
- **Predicción a corto y largo plazo:** El tiempo actual, su evolución por horas con gráfico de temperatura y punto de rocío, y el pronóstico de los próximos días.
- **Avisos de AEMET:** Los avisos vigentes de tu provincia, con su nivel, zona y vigencia, señalados también en las horas y los días a los que afectan.
- **Fondo dinámico:** El cielo cambia gradualmente según el amanecer, el atardecer y las horas solares de cada ciudad, con lluvia y relámpagos cuando el tiempo lo pide.
- **Ciclo solar y lunar:** Orto y ocaso del sol y de la luna, con la fase lunar del día.
- **Actualizaciones in-app:** Nubo descarga e instala las nuevas versiones directamente desde GitHub Releases.

## Desarrollo 🛠️

Requiere JDK 17 y el SDK de Android (`sdk.dir` en `local.properties`).

```bash
./gradlew testDebugUnitTest   # tests unitarios
./gradlew assembleDebug       # APK de depuración
./gradlew assembleRelease     # APK de publicación
```

Para publicar una versión: sube `versionName` y `versionCode` en `app/build.gradle.kts` y ejecuta `./deploy.sh`, que etiqueta y dispara el workflow de release.

## Créditos 🏆
- Predicción meteorológica de [Open-Meteo](https://open-meteo.com/).
- Avisos del servicio público de la [AEMET (Agencia Estatal de Meteorología)](https://opendata.aemet.es/).
- Animación Lottie (Loading Sun) creada originalmente por [Michelle Hardi](https://lottiefiles.com/hardi).
- Los efectos de lluvia y relámpago siguen el planteamiento de [Breezy Weather](https://github.com/breezy-weather/breezy-weather).
