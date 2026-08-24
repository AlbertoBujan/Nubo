package com.nubo.nubo.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.nubo.nubo.domain.weather.WeatherIcon

/**
 * Traduce el icono del dominio a un vector de Material.
 *
 * La app Flutter usaba Lucide, que no tiene versión para Compose. El set de
 * Material que viene con `material-icons-extended` no incluye equivalentes de
 * "sol tras nube" ni de niebla —no existen `Rainy`, `Foggy` ni
 * `PartlyCloudy`—, así que estos son los más cercanos disponibles. Si el
 * parecido se queda corto, la solución sería dibujar los vectores propios en
 * `res/drawable` en vez de cambiar de librería.
 */
fun WeatherIcon.toImageVector(): ImageVector = when (this) {
    WeatherIcon.SUN -> Icons.Outlined.WbSunny
    WeatherIcon.MOON -> Icons.Outlined.DarkMode
    // No hay "sol tras nube"; se usa la nube rellena para distinguirla de la
    // nube de contorno que representa el cielo cubierto.
    WeatherIcon.CLOUD_SUN -> Icons.Outlined.WbCloudy
    WeatherIcon.CLOUD_MOON -> Icons.Outlined.NightsStay
    WeatherIcon.CLOUD -> Icons.Outlined.Cloud
    WeatherIcon.CLOUD_DRIZZLE -> Icons.Outlined.Grain
    WeatherIcon.CLOUD_RAIN -> Icons.Outlined.WaterDrop
    WeatherIcon.CLOUD_SNOW -> Icons.Outlined.AcUnit
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
