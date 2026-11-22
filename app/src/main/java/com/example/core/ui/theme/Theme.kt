package com.example.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class AppThemeMode(val label: String) {
    DARK_CYBER("Cyber Dark"),
    AMOLED_BLACK("AMOLED Black"),
    LIGHT("Studio Light"),
    DYNAMIC("Material You Dynamic")
}

enum class AppAccentOption(val label: String, val primary: Color, val container: Color) {
    VIOLET_CYAN("Violet & Cyan", VioletPrimary, VioletPrimaryContainer),
    ELECTRIC_BLUE("Electric Blue", BluePrimary, BluePrimaryContainer),
    EMERALD_TEAL("Emerald Teal", TealPrimary, TealPrimaryContainer)
}

private val GenDevShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun GenDevManagerTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK_CYBER,
    accentOption: AppAccentOption = AppAccentOption.VIOLET_CYAN,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val colorScheme: ColorScheme = when (themeMode) {
        AppThemeMode.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                buildDarkScheme(amoled = false, accent = accentOption)
            }
        }
        AppThemeMode.AMOLED_BLACK -> buildDarkScheme(amoled = true, accent = accentOption)
        AppThemeMode.LIGHT -> buildLightScheme(accent = accentOption)
        AppThemeMode.DARK_CYBER -> buildDarkScheme(amoled = false, accent = accentOption)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GenDevTypography,
        shapes = GenDevShapes,
        content = content
    )
}

private fun buildDarkScheme(amoled: Boolean, accent: AppAccentOption): ColorScheme {
    val bg = if (amoled) AmoledBg else ObsidianBg
    val surface = if (amoled) AmoledSurface else ObsidianSurface
    val surfaceVariant = if (amoled) AmoledSurfaceVariant else ObsidianSurfaceVariant
    val secondary = when (accent) {
        AppAccentOption.VIOLET_CYAN -> CyanSecondary
        AppAccentOption.ELECTRIC_BLUE -> CyanSecondary
        AppAccentOption.EMERALD_TEAL -> EmeraldTertiary
    }
    return darkColorScheme(
        primary = accent.primary,
        onPrimary = Color.White,
        primaryContainer = accent.container,
        onPrimaryContainer = Color(0xFFEDE9FE),
        secondary = secondary,
        onSecondary = Color(0xFF042F2E),
        secondaryContainer = CyanSecondaryContainer,
        onSecondaryContainer = Color(0xFFCFFAFE),
        tertiary = EmeraldTertiary,
        onTertiary = Color(0xFF022C22),
        tertiaryContainer = EmeraldTertiaryContainer,
        onTertiaryContainer = Color(0xFFD1FAE5),
        background = bg,
        onBackground = Color(0xFFF1F5F9),
        surface = surface,
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFF94A3B8),
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceVariant,
        surfaceContainerHigh = ObsidianSurfaceHigh,
        outline = ObsidianOutline,
        outlineVariant = Color(0xFF1E293B),
        error = StatusDanger,
        onError = Color.White
    )
}

private fun buildLightScheme(accent: AppAccentOption): ColorScheme {
    return lightColorScheme(
        primary = accent.primary,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE9FE),
        onPrimaryContainer = Color(0xFF2E1065),
        secondary = Color(0xFF0284C7),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE0F2FE),
        onSecondaryContainer = Color(0xFF0C4A6E),
        tertiary = Color(0xFF059669),
        onTertiary = Color.White,
        background = LightBg,
        onBackground = LightTextPrimary,
        surface = LightSurface,
        onSurface = LightTextPrimary,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightTextSecondary,
        outline = Color(0xFFCBD5E1),
        error = StatusDanger,
        onError = Color.White
    )
}
