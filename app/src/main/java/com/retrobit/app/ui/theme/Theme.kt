package com.retrobit.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.retrobit.app.data.SettingsData

private val DarkScheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF140B33),
    primaryContainer = Color(0xFF2A2158),
    onPrimaryContainer = Color(0xFFE7E2FF),
    secondary = Cyan,
    onSecondary = Color(0xFF03211D),
    secondaryContainer = Color(0xFF0C3733),
    onSecondaryContainer = Color(0xFFB8FFF5),
    tertiary = Pink,
    onTertiary = Color(0xFF3A0A22),
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = Stroke,
    outlineVariant = Color(0xFF1F1F2C),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF2A000A)
)

private val LightScheme = lightColorScheme(
    primary = VioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E1FF),
    onPrimaryContainer = Color(0xFF1B1040),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF7F2),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFC2185B),
    onTertiary = Color.White,
    background = Color(0xFFF7F6FC),
    onBackground = Color(0xFF16141F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16141F),
    surfaceVariant = Color(0xFFF0EEF9),
    onSurfaceVariant = Color(0xFF5A5670),
    outline = Color(0xFFD8D5E6),
    outlineVariant = Color(0xFFE9E6F4),
    error = Color(0xFFB3261E),
    onError = Color.White
)

/** True blacks for OLED panels; surfaces stay a hair above #000 so borders remain visible. */
private val OledScheme = darkColorScheme(
    primary = Color(0xFFB4A5FF),
    onPrimary = Color(0xFF130A2E),
    primaryContainer = Color(0xFF241A55),
    onPrimaryContainer = Color(0xFFE9E3FF),
    secondary = Color(0xFF3EE8D2),
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF073A34),
    onSecondaryContainer = Color(0xFFB9FFF6),
    tertiary = Color(0xFFFF8FC7),
    onTertiary = Color(0xFF3A0A22),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF1F1F6),
    surface = Color(0xFF08080A),
    onSurface = Color(0xFFF1F1F6),
    surfaceVariant = Color(0xFF141418),
    onSurfaceVariant = Color(0xFFA9A8B6),
    outline = Color(0xFF2C2C33),
    outlineVariant = Color(0xFF1A1A1F),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF2A000A)
)

private val AmberScheme = darkColorScheme(
    primary = Color(0xFFFFB000),
    onPrimary = Color(0xFF241700),
    primaryContainer = Color(0xFF3A2500),
    onPrimaryContainer = Color(0xFFFFE0A8),
    secondary = Color(0xFFFF8A00),
    onSecondary = Color(0xFF241000),
    secondaryContainer = Color(0xFF3D2000),
    onSecondaryContainer = Color(0xFFFFD59A),
    tertiary = Color(0xFFFFD166),
    onTertiary = Color(0xFF2A1D00),
    background = Color(0xFF0A0600),
    onBackground = Color(0xFFFFD79A),
    surface = Color(0xFF140D00),
    onSurface = Color(0xFFFFD79A),
    surfaceVariant = Color(0xFF221600),
    onSurfaceVariant = Color(0xFFC99A4D),
    outline = Color(0xFF6B4A00),
    outlineVariant = Color(0xFF3A2800),
    error = Color(0xFFFF6B5C),
    onError = Color(0xFF2A0500)
)

private val GreenScheme = darkColorScheme(
    primary = Color(0xFF45FF7A),
    onPrimary = Color(0xFF00220B),
    primaryContainer = Color(0xFF0A3D1C),
    onPrimaryContainer = Color(0xFFB7FFC9),
    secondary = Color(0xFF00C853),
    onSecondary = Color(0xFF00210C),
    secondaryContainer = Color(0xFF063A18),
    onSecondaryContainer = Color(0xFF9CFFB8),
    tertiary = Color(0xFFB6FF9E),
    onTertiary = Color(0xFF0A2A00),
    background = Color(0xFF000800),
    onBackground = Color(0xFFB7FFC9),
    surface = Color(0xFF021400),
    onSurface = Color(0xFFB7FFC9),
    surfaceVariant = Color(0xFF062400),
    onSurfaceVariant = Color(0xFF6FBF84),
    outline = Color(0xFF1F5A30),
    outlineVariant = Color(0xFF0F3319),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF2A000A)
)

private val NintendoScheme = darkColorScheme(
    primary = Color(0xFFE60012),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A0007),
    onPrimaryContainer = Color(0xFFFFD9DB),
    secondary = Color(0xFF00A0E9),
    onSecondary = Color(0xFF00131F),
    secondaryContainer = Color(0xFF003A57),
    onSecondaryContainer = Color(0xFFC2E9FF),
    tertiary = Color(0xFFFFC400),
    onTertiary = Color(0xFF2A2000),
    background = Color(0xFF0C0C11),
    onBackground = Color(0xFFF4F4F8),
    surface = Color(0xFF17171E),
    onSurface = Color(0xFFF4F4F8),
    surfaceVariant = Color(0xFF232330),
    onSurfaceVariant = Color(0xFFB0B0C0),
    outline = Color(0xFF3A3A4A),
    outlineVariant = Color(0xFF262633),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF2A000A)
)

private val SegaScheme = darkColorScheme(
    primary = Color(0xFF1E90FF),
    onPrimary = Color(0xFF00142B),
    primaryContainer = Color(0xFF073A6B),
    onPrimaryContainer = Color(0xFFCBE6FF),
    secondary = Color(0xFFFFB300),
    onSecondary = Color(0xFF2A1D00),
    secondaryContainer = Color(0xFF4A3200),
    onSecondaryContainer = Color(0xFFFFE3A8),
    tertiary = Color(0xFFFF4D6D),
    onTertiary = Color(0xFF2A000A),
    background = Color(0xFF050B18),
    onBackground = Color(0xFFE9F1FF),
    surface = Color(0xFF0C1526),
    onSurface = Color(0xFFE9F1FF),
    surfaceVariant = Color(0xFF14223A),
    onSurfaceVariant = Color(0xFF9DB2D0),
    outline = Color(0xFF29405F),
    outlineVariant = Color(0xFF172B45),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF2A000A)
)

private val PsxScheme = darkColorScheme(
    primary = Color(0xFF4A9BFF),
    onPrimary = Color(0xFF001B3D),
    primaryContainer = Color(0xFF0B3B78),
    onPrimaryContainer = Color(0xFFD3E6FF),
    secondary = Color(0xFFB6C2D4),
    onSecondary = Color(0xFF15202E),
    secondaryContainer = Color(0xFF2E3A4C),
    onSecondaryContainer = Color(0xFFE1E8F2),
    tertiary = Color(0xFFE60012),
    onTertiary = Color.White,
    background = Color(0xFF0A0A10),
    onBackground = Color(0xFFEDEFF5),
    surface = Color(0xFF14141C),
    onSurface = Color(0xFFEDEFF5),
    surfaceVariant = Color(0xFF20202B),
    onSurfaceVariant = Color(0xFFA7A9B8),
    outline = Color(0xFF3A3A48),
    outlineVariant = Color(0xFF24242F),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF2A000A)
)

@Composable
fun RetrobitTheme(
    themeId: String = SettingsData.THEME_DARK,
    content: @Composable () -> Unit
) {
    val dark = when (themeId) {
        SettingsData.THEME_LIGHT -> false
        SettingsData.THEME_SYSTEM -> isSystemInDarkTheme()
        else -> true
    }
    val scheme = when (themeId) {
        SettingsData.THEME_LIGHT -> LightScheme
        SettingsData.THEME_OLED -> OledScheme
        SettingsData.THEME_AMBER -> AmberScheme
        SettingsData.THEME_GREEN -> GreenScheme
        SettingsData.THEME_NINTENDO -> NintendoScheme
        SettingsData.THEME_SEGA -> SegaScheme
        SettingsData.THEME_PSX -> PsxScheme
        else -> if (dark) DarkScheme else LightScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = RetroTypography,
        content = content
    )
}
