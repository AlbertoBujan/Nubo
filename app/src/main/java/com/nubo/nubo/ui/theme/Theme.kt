package com.nubo.nubo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Azul de marca de Nubo, heredado de la app Flutter. */
val NuboBlue = Color(0xFF1A73E8)

/** Fondo de arranque, antes de que se resuelva el gradiente del cielo. */
val NuboBackground = Color(0xFF1A1A2E)

private val NuboColors = darkColorScheme(
    primary = NuboBlue,
    // Sin fijar los "on…", Material deriva tonos que sobre el azul de marca
    // quedan ilegibles (el texto de los botones salía granate).
    onPrimary = Color.White,
    secondary = Color(0xFF64B5F6),
    onSecondary = Color.White,
    background = NuboBackground,
    onBackground = Color.White,
    surface = Color(0xFF1E2A3A),
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
    surfaceVariant = Color(0xFF243447),
    error = Color(0xFFEF5350),
    onError = Color.White,
)

private val NuboTypography = Typography(
    displayLarge = TextStyle(fontSize = 92.sp, fontWeight = FontWeight.Thin),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.W500),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W600),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
)

/**
 * Tema de la app.
 *
 * Nubo siempre va en oscuro: el fondo lo pinta un gradiente de cielo y el
 * contenido va en blanco sobre él, así que un esquema claro no encajaría.
 */
@Composable
fun NuboTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = NuboColors,
        typography = NuboTypography,
        content = content,
    )
}
