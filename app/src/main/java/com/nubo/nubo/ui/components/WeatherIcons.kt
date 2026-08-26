package com.nubo.nubo.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.nubo.nubo.domain.weather.WeatherIcon

/**
 * Traduce el icono del dominio a un vector de Material.
 *
 * La app Flutter usaba Lucide, que no tiene versión para Compose. El set de
 * Material que viene con `material-icons-extended` no incluye equivalentes de
 * "sol tras nube" ni de niebla —no existen `Foggy` ni `PartlyCloudy`—, así que
 * en esos casos estos son los más cercanos disponibles.
 *
 * Cuando ni el más cercano vale, el camino es dibujar el vector, como se hizo
 * con la precipitación en `NuboWeatherIcons`; no cambiar de librería.
 */
fun WeatherIcon.toImageVector(): ImageVector = when (this) {
    WeatherIcon.SUN -> Icons.Outlined.WbSunny
    WeatherIcon.MOON -> Icons.Outlined.DarkMode
    // No hay "sol tras nube"; se usa la nube rellena para distinguirla de la
    // nube de contorno que representa el cielo cubierto.
    WeatherIcon.CLOUD_SUN -> Icons.Outlined.WbCloudy
    WeatherIcon.CLOUD_MOON -> Icons.Outlined.NightsStay
    WeatherIcon.CLOUD -> Icons.Outlined.Cloud
    // Nubes con precipitación, dibujadas en `NuboWeatherIcons`: Material no
    // trae ninguna, y la gota y el copo sueltos que se usaban antes no son el
    // estándar.
    WeatherIcon.CLOUD_DRIZZLE -> NuboWeatherIcons.CloudDrizzle
    WeatherIcon.CLOUD_RAIN_LIGHT -> NuboWeatherIcons.CloudRainLight
    WeatherIcon.CLOUD_RAIN -> NuboWeatherIcons.CloudRain
    WeatherIcon.CLOUD_RAIN_HEAVY -> NuboWeatherIcons.CloudRainHeavy
    WeatherIcon.CLOUD_SNOW_LIGHT -> NuboWeatherIcons.CloudSnowLight
    WeatherIcon.CLOUD_SNOW -> NuboWeatherIcons.CloudSnow
    WeatherIcon.CLOUD_SNOW_HEAVY -> NuboWeatherIcons.CloudSnowHeavy
    // Nube con rayo, más reconocible que el rayo suelto de `Bolt`.
    WeatherIcon.CLOUD_LIGHTNING -> Icons.Outlined.Thunderstorm
    WeatherIcon.CLOUD_FOG -> Icons.Outlined.CloudQueue
    WeatherIcon.UNKNOWN -> Icons.Outlined.HelpOutline
}

/** Iconos sueltos que usa la interfaz fuera del mapa de códigos. */
object NuboIcons {
    val Sun = Icons.Filled.WbSunny
    val Cloud = Icons.Filled.Cloud
    val Umbrella = Icons.Outlined.Umbrella
}
