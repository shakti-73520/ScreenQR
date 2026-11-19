package app.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.scanner.data.ThemeMode

@Immutable
data class PrismExtendedColors(
    val isDark: Boolean,
    val canvasDeep: Color,
    val canvasSecondary: Color,
    val glassSurface: Color,
    val glassElevated: Color,
    val glassBorder: Color,
    val accentEmerald: Color,
    val accentGold: Color,
    val accentCyan: Color,
    val safe: Color,
    val caution: Color,
    val danger: Color,
    val mutedText: Color
)

val LocalPrismColors = staticCompositionLocalOf {
    PrismExtendedColors(
        isDark = true,
        canvasDeep = PrismPalette.ObsidianVoid,
        canvasSecondary = PrismPalette.ObsidianSlate,
        glassSurface = PrismPalette.CarbonGlass,
        glassElevated = PrismPalette.GraphiteSurface,
        glassBorder = PrismPalette.ObsidianBorder,
        accentEmerald = PrismPalette.PrismEmerald,
        accentGold = PrismPalette.ChampagneGold,
        accentCyan = PrismPalette.ElectricCyan,
        safe = PrismPalette.SafeGreen,
        caution = PrismPalette.CautionAmber,
        danger = PrismPalette.DangerCrimson,
        mutedText = PrismPalette.ObsidianMutedText
    )
}

private val ObsidianDarkColorScheme = darkColorScheme(
    primary = PrismPalette.PrismEmerald,
    onPrimary = PrismPalette.ObsidianVoid,
    primaryContainer = Color(0xFF0E3326),
    onPrimaryContainer = PrismPalette.PrismEmerald,
    secondary = PrismPalette.ChampagneGold,
    onSecondary = PrismPalette.ObsidianVoid,
    secondaryContainer = Color(0xFF312814),
    onSecondaryContainer = PrismPalette.ChampagneGold,
    tertiary = PrismPalette.ElectricCyan,
    onTertiary = PrismPalette.ObsidianVoid,
    tertiaryContainer = Color(0xFF0D2E3D),
    onTertiaryContainer = PrismPalette.ElectricCyan,
    background = PrismPalette.ObsidianVoid,
    onBackground = PrismPalette.ObsidianText,
    surface = PrismPalette.ObsidianSlate,
    onSurface = PrismPalette.ObsidianText,
    surfaceVariant = PrismPalette.GraphiteSurface,
    onSurfaceVariant = PrismPalette.ObsidianMutedText,
    surfaceContainerLow = PrismPalette.CarbonGlass,
    surfaceContainer = PrismPalette.GraphiteSurface,
    surfaceContainerHigh = PrismPalette.GraphiteElevated,
    outline = PrismPalette.ObsidianBorder,
    outlineVariant = Color(0xFF1D2638),
    error = PrismPalette.DangerCrimson,
    onError = PrismPalette.ObsidianVoid
)

private val AlabasterLightColorScheme = lightColorScheme(
    primary = PrismPalette.PrismEmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F7E8),
    onPrimaryContainer = Color(0xFF054B2E),
    secondary = PrismPalette.ChampagneGoldDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFAF0D4),
    onSecondaryContainer = Color(0xFF5A4107),
    tertiary = PrismPalette.ElectricCyanDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7F2FF),
    onTertiaryContainer = Color(0xFF00456B),
    background = PrismPalette.AlabasterCanvas,
    onBackground = PrismPalette.AlabasterText,
    surface = PrismPalette.AlabasterCard,
    onSurface = PrismPalette.AlabasterText,
    surfaceVariant = PrismPalette.AlabasterElevated,
    onSurfaceVariant = PrismPalette.AlabasterMutedText,
    surfaceContainerLow = PrismPalette.AlabasterCard,
    surfaceContainer = PrismPalette.AlabasterElevated,
    surfaceContainerHigh = Color(0xFFE0E8F2),
    outline = PrismPalette.AlabasterBorder,
    outlineVariant = Color(0xFFE6ECF4),
    error = Color(0xFFD92D43),
    onError = Color.White
)

@Composable
fun PrismTheme(
    themeMode: ThemeMode = ThemeMode.OBSIDIAN_DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.OBSIDIAN_DARK -> true
        ThemeMode.ALABASTER_LIGHT -> false
    }

    val colorScheme = if (isDark) ObsidianDarkColorScheme else AlabasterLightColorScheme
    val extended = if (isDark) {
        PrismExtendedColors(
            isDark = true,
            canvasDeep = PrismPalette.ObsidianVoid,
            canvasSecondary = PrismPalette.ObsidianSlate,
            glassSurface = PrismPalette.CarbonGlass,
            glassElevated = PrismPalette.GraphiteSurface,
            glassBorder = PrismPalette.ObsidianBorder,
            accentEmerald = PrismPalette.PrismEmerald,
            accentGold = PrismPalette.ChampagneGold,
            accentCyan = PrismPalette.ElectricCyan,
            safe = PrismPalette.SafeGreen,
            caution = PrismPalette.CautionAmber,
            danger = PrismPalette.DangerCrimson,
            mutedText = PrismPalette.ObsidianMutedText
        )
    } else {
        PrismExtendedColors(
            isDark = false,
            canvasDeep = PrismPalette.AlabasterCanvas,
            canvasSecondary = PrismPalette.AlabasterCard,
            glassSurface = PrismPalette.AlabasterCard,
            glassElevated = PrismPalette.AlabasterElevated,
            glassBorder = PrismPalette.AlabasterBorder,
            accentEmerald = PrismPalette.PrismEmeraldDeep,
            accentGold = PrismPalette.ChampagneGoldDeep,
            accentCyan = PrismPalette.ElectricCyanDeep,
            safe = PrismPalette.PrismEmeraldDeep,
            caution = Color(0xFFD98200),
            danger = Color(0xFFD92D43),
            mutedText = PrismPalette.AlabasterMutedText
        )
    }

    CompositionLocalProvider(LocalPrismColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PrismTypography,
            shapes = PrismShapes,
            content = content
        )
    }
}
