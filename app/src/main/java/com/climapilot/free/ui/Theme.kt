package com.climapilot.free.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// EN: A fresh cool-air palette: teal/cyan primaries with a deep ink background.
// DE: Eine frische „Kühle-Luft"-Palette: Türkis/Cyan als Primärfarben mit dunklem Tinten-Hintergrund.
private val Cyan = Color(0xFF00BCD4)
private val CyanDark = Color(0xFF0097A7)
private val Teal = Color(0xFF1DE9B6)
private val Sky = Color(0xFF4FC3F7)
private val Amber = Color(0xFFFFB74D)

private val LightColors = lightColorScheme(
    primary = CyanDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF00363D),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    tertiary = Amber,
    background = Color(0xFFF4FBFC),
    onBackground = Color(0xFF0E1B1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E1B1E),
    surfaceVariant = Color(0xFFDCEBEE),
    onSurfaceVariant = Color(0xFF3F484B),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF005662),
    onPrimaryContainer = Color(0xFFB2EBF2),
    secondary = Teal,
    onSecondary = Color(0xFF003731),
    tertiary = Amber,
    background = Color(0xFF0B1416),
    onBackground = Color(0xFFE0F3F5),
    surface = Color(0xFF111E21),
    onSurface = Color(0xFFE0F3F5),
    surfaceVariant = Color(0xFF26383C),
    onSurfaceVariant = Color(0xFFBFC9CB),
    error = Color(0xFFFFB4AB),
)

/**
 * EN: App theme. Uses Android 12+ dynamic (wallpaper-based) colors when available, otherwise falls
 *     back to the custom light/dark cool-air palettes above. Also keeps the status-bar icons legible.
 * DE: App-Theme. Nutzt ab Android 12 dynamische (vom Hintergrundbild abgeleitete) Farben, sonst die
 *     eigenen hellen/dunklen „Kühle-Luft"-Paletten oben. Hält außerdem die Statusleisten-Symbole lesbar.
 */
@Composable
fun MideaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
