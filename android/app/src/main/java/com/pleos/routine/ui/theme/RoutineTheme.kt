package com.pleos.routine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocalAstryx = staticCompositionLocalOf { AstryxLight }

/** Astryx semantic colours of the current mode (light or dark follows the system, as Pleos day/night does). */
val astryx: AstryxColors
    @Composable @ReadOnlyComposable get() = LocalAstryx.current

private fun scheme(c: AstryxColors, dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.hue(Hue.GRAY).background, onPrimaryContainer = c.textPrimary,
    secondary = c.success, onSecondary = c.onSuccess, secondaryContainer = c.hue(Hue.GRAY).background, onSecondaryContainer = c.textPrimary,
    tertiary = c.info, onTertiary = c.onInfo, tertiaryContainer = c.infoMuted, onTertiaryContainer = c.onInfoMuted,
    error = c.error, onError = c.onError, errorContainer = c.errorMuted, onErrorContainer = c.onErrorMuted,
    background = c.body, onBackground = c.textPrimary, surface = c.surface, onSurface = c.textPrimary,
    surfaceVariant = c.body, onSurfaceVariant = c.textSecondary, surfaceTint = c.surface,
    surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
    surfaceContainerHigh = c.popover, surfaceContainerHighest = c.surface, surfaceBright = c.surface, surfaceDim = c.body,
    inverseSurface = c.accent, inverseOnSurface = c.onAccent, outline = c.borderEmphasized, outlineVariant = c.border, scrim = c.textPrimary,
)

/**
 * In-car type scale: the app keeps its large Pleos sizes (body 20–22sp, nothing under 14sp) and takes
 * Astryx's weights (600 headings, 500 labels).
 */
private fun typography(): Typography {
    val t = Typography()
    val semi = FontWeight.SemiBold; val medium = FontWeight.Medium
    return Typography(
        displaySmall = t.displaySmall.copy(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = semi),
        headlineLarge = t.headlineLarge.copy(fontSize = 38.sp, lineHeight = 46.sp, fontWeight = semi),
        headlineMedium = t.headlineMedium.copy(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = semi),
        headlineSmall = t.headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = semi),
        titleLarge = t.titleLarge.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = semi),
        titleMedium = t.titleMedium.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = semi),
        titleSmall = t.titleSmall.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = semi),
        bodyLarge = t.bodyLarge.copy(fontSize = 22.sp, lineHeight = 30.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 20.sp, lineHeight = 28.sp),
        bodySmall = t.bodySmall.copy(fontSize = 16.sp, lineHeight = 24.sp),
        labelLarge = t.labelLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = medium),
        labelMedium = t.labelMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = medium),
        labelSmall = t.labelSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = medium),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(AstryxRadius.inner), small = RoundedCornerShape(AstryxRadius.element),
    medium = RoundedCornerShape(AstryxRadius.container), large = RoundedCornerShape(AstryxRadius.container * 1.5f),
    extraLarge = RoundedCornerShape(AstryxRadius.page),
)

@Composable
fun RoutineTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) AstryxDark else AstryxLight
    CompositionLocalProvider(LocalAstryx provides colors) {
        MaterialTheme(colorScheme = scheme(colors, dark), typography = typography(), shapes = shapes, content = content)
    }
}
