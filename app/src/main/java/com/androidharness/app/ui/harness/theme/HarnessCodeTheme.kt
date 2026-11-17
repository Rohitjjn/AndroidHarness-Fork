@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.androidharness.app.ui.harness.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidharness.app.R

/**
 * Harness Code design tokens.
 *
 * Warm paper in light mode, warm ink in dark mode, one clay accent. Every value
 * here is measured for WCAG AA against the surfaces it is used on; the ratios
 * are recorded in docs/harness-code/DESIGN.md.
 */
@Immutable
data class HarnessPalette(
    val paper: Color,
    val surface: Color,
    val raised: Color,
    val sunken: Color,
    val sidebar: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentHover: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val accentInk: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val code: Color,
    val codeBlock: Color,
    val diffAdd: Color,
    val diffRemove: Color,
    val scrim: Color,
    val isDark: Boolean,
)

internal val LightHarness = HarnessPalette(
    paper = Color(0xFFF6F2EC),
    surface = Color(0xFFFCFAF6),
    raised = Color(0xFFFFFFFF),
    sunken = Color(0xFFEFEAE1),
    sidebar = Color(0xFFF1ECE4),
    border = Color(0xFFE3DCD0),
    borderStrong = Color(0xFFD2C9BA),
    text = Color(0xFF241F1B),
    muted = Color(0xFF6A6257),
    faint = Color(0xFF8C8375),
    accent = Color(0xFFB4522F),
    accentHover = Color(0xFF9C4526),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFF3E3D9),
    accentInk = Color(0xFF8E3D1F),
    success = Color(0xFF2F6B45),
    warning = Color(0xFF8A5A12),
    danger = Color(0xFFA3342B),
    code = Color(0xFF3B342C),
    codeBlock = Color(0xFFF2EDE4),
    diffAdd = Color(0xFF2F6B45),
    diffRemove = Color(0xFFA3342B),
    scrim = Color(0x66000000),
    isDark = false,
)

internal val DarkHarness = HarnessPalette(
    paper = Color(0xFF1A1816),
    surface = Color(0xFF211E1C),
    raised = Color(0xFF2A2623),
    sunken = Color(0xFF141211),
    sidebar = Color(0xFF161413),
    border = Color(0xFF3A342F),
    borderStrong = Color(0xFF4A423B),
    text = Color(0xFFF1EBE3),
    muted = Color(0xFFB3A99C),
    faint = Color(0xFF8A8075),
    accent = Color(0xFFE08A66),
    accentHover = Color(0xFFEDA181),
    onAccent = Color(0xFF1A1816),
    accentSoft = Color(0xFF3A2A22),
    accentInk = Color(0xFFF0A585),
    success = Color(0xFF7FBF98),
    warning = Color(0xFFE0B270),
    danger = Color(0xFFE8968C),
    code = Color(0xFFD9D0C2),
    codeBlock = Color(0xFF2A2623),
    diffAdd = Color(0xFF7FBF98),
    diffRemove = Color(0xFFE8968C),
    scrim = Color(0x99000000),
    isDark = true,
)

val LocalHarnessPalette = staticCompositionLocalOf { LightHarness }

/** Convenience accessor: `HarnessTheme.palette.text`. */
object HarnessTheme {
    val palette: HarnessPalette
        @Composable get() = LocalHarnessPalette.current
}

/*
 * Type: Newsreader for display and headings, Inter for UI, JetBrains Mono for
 * code. All three are OFL; the static instances shipped in res/font are
 * generated from the upstream variable fonts (see docs/harness-code/DESIGN.md).
 */
val HarnessSerif = FontFamily(
    Font(R.font.newsreader_400, FontWeight.Normal),
    Font(R.font.newsreader_500, FontWeight.Medium),
    Font(R.font.newsreader_600, FontWeight.SemiBold),
)

val HarnessSans = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
)

val HarnessMono = FontFamily(
    Font(R.font.jetbrains_mono_400, FontWeight.Normal),
    Font(R.font.jetbrains_mono_500, FontWeight.Medium),
    Font(R.font.jetbrains_mono_700, FontWeight.Bold),
)

private val trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    line: Int,
    weight: FontWeight,
    family: FontFamily,
    tracking: Double = 0.0,
    includeFontPadding: Boolean = false,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = trim,
    platformStyle = PlatformTextStyle(includeFontPadding = includeFontPadding),
)

/** Claude-adjacent scale: 11, 13, 15, 17, 20, 24, 30, 38. */
val HarnessTypography = Typography(
    displayLarge = style(38, 44, FontWeight.Medium, HarnessSerif, (-0.6)),
    displayMedium = style(30, 36, FontWeight.Medium, HarnessSerif, (-0.4)),
    displaySmall = style(24, 30, FontWeight.Medium, HarnessSerif, (-0.2)),
    headlineMedium = style(24, 30, FontWeight.Medium, HarnessSerif),
    headlineSmall = style(20, 26, FontWeight.Medium, HarnessSerif),
    titleLarge = style(20, 26, FontWeight.SemiBold, HarnessSans, (-0.1)),
    titleMedium = style(17, 22, FontWeight.SemiBold, HarnessSans),
    titleSmall = style(15, 20, FontWeight.SemiBold, HarnessSans),
    bodyLarge = style(17, 26, FontWeight.Normal, HarnessSans),
    bodyMedium = style(15, 23, FontWeight.Normal, HarnessSans),
    bodySmall = style(13, 19, FontWeight.Normal, HarnessSans),
    labelLarge = style(15, 20, FontWeight.Medium, HarnessSans),
    labelMedium = style(13, 17, FontWeight.Medium, HarnessSans),
    labelSmall = style(11, 15, FontWeight.Medium, HarnessSans, 0.3),
)

val HarnessShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Spacing scale: 4-based, generous at the top end. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    /** Gutter used by session screens on a phone. */
    val gutter = 20.dp
    /** Reading measure for transcripts on tablets. */
    val measure = 720.dp
}

private fun paletteScheme(p: HarnessPalette) = if (p.isDark) darkColorScheme(
    primary = p.accent,
    onPrimary = p.onAccent,
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.accentInk,
    secondary = p.muted,
    onSecondary = p.raised,
    secondaryContainer = p.sunken,
    onSecondaryContainer = p.text,
    tertiary = p.muted,
    onTertiary = p.raised,
    tertiaryContainer = p.sunken,
    onTertiaryContainer = p.text,
    error = p.danger,
    onError = Color(0xFF1A1816),
    errorContainer = Color(0xFF43201C),
    onErrorContainer = p.danger,
    background = p.paper,
    onBackground = p.text,
    surface = p.surface,
    onSurface = p.text,
    surfaceVariant = p.sunken,
    onSurfaceVariant = p.muted,
    surfaceTint = p.accent,
    outline = p.borderStrong,
    outlineVariant = p.border,
    surfaceContainerLowest = p.sunken,
    surfaceContainerLow = p.surface,
    surfaceContainer = p.raised,
    surfaceContainerHigh = p.raised,
    surfaceContainerHighest = p.borderStrong,
    inverseSurface = p.text,
    inverseOnSurface = p.paper,
    inversePrimary = p.accentHover,
    scrim = p.scrim,
) else lightColorScheme(
    primary = p.accent,
    onPrimary = p.onAccent,
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.accentInk,
    secondary = p.muted,
    onSecondary = p.raised,
    secondaryContainer = p.sunken,
    onSecondaryContainer = p.text,
    tertiary = p.muted,
    onTertiary = p.raised,
    tertiaryContainer = p.sunken,
    onTertiaryContainer = p.text,
    error = p.danger,
    onError = Color.White,
    errorContainer = Color(0xFFF8E2DF),
    onErrorContainer = Color(0xFF6B1D16),
    background = p.paper,
    onBackground = p.text,
    surface = p.surface,
    onSurface = p.text,
    surfaceVariant = p.sunken,
    onSurfaceVariant = p.muted,
    surfaceTint = p.accent,
    outline = p.borderStrong,
    outlineVariant = p.border,
    surfaceContainerLowest = p.raised,
    surfaceContainerLow = p.surface,
    surfaceContainer = p.sunken,
    surfaceContainerHigh = p.sunken,
    surfaceContainerHighest = p.border,
    inverseSurface = p.text,
    inverseOnSurface = p.paper,
    inversePrimary = p.accentInk,
    scrim = p.scrim,
)

/**
 * Harness Code theme.
 *
 * [dark] is passed explicitly so the shell can honour the app's own
 * ThemeMode/LIGHT/DARK/AMOLED setting instead of only the system value.
 */
@Composable
fun HarnessCodeTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (dark) DarkHarness else LightHarness
    CompositionLocalProvider(LocalHarnessPalette provides palette) {
        MaterialTheme(
            colorScheme = paletteScheme(palette),
            typography = HarnessTypography,
            shapes = HarnessShapes,
            content = content,
        )
    }
}
