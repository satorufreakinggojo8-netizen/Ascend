package com.cadence.fit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Calm, low-contrast surfaces with a single confident accent. Colours are chosen
 * for readability in a dim gym as well as daylight.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B5E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE9E0),
    onPrimaryContainer = Color(0xFF07342C),
    secondary = Color(0xFF456B7C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E5EF),
    onSecondaryContainer = Color(0xFF16323F),
    tertiary = Color(0xFFB4633A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF7DECF),
    onTertiaryContainer = Color(0xFF43210F),
    background = Color(0xFFF6F7F4),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFECEFE9),
    onSurfaceVariant = Color(0xFF53574E),
    outline = Color(0xFFC7CCC3),
    outlineVariant = Color(0xFFE1E4DD),
    error = Color(0xFFA5402D),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD3C0),
    onPrimary = Color(0xFF08251F),
    primaryContainer = Color(0xFF1F5046),
    onPrimaryContainer = Color(0xFFCDE9E0),
    secondary = Color(0xFFA6C9DA),
    onSecondary = Color(0xFF10262F),
    secondaryContainer = Color(0xFF2B4552),
    onSecondaryContainer = Color(0xFFD3E5EF),
    tertiary = Color(0xFFE5A585),
    onTertiary = Color(0xFF3A1A0B),
    tertiaryContainer = Color(0xFF5D321D),
    onTertiaryContainer = Color(0xFFF7DECF),
    background = Color(0xFF111310),
    onBackground = Color(0xFFE3E5DE),
    surface = Color(0xFF191C17),
    onSurface = Color(0xFFE3E5DE),
    surfaceVariant = Color(0xFF2A2E26),
    onSurfaceVariant = Color(0xFFC2C7BC),
    outline = Color(0xFF4E5349),
    outlineVariant = Color(0xFF34382F),
    error = Color(0xFFF0B4A6),
    onError = Color(0xFF5C1408),
)

/** Extra tokens the Material palette does not cover. */
data class CadenceTones(
    val heatLow: Color,
    val heatMid: Color,
    val heatHigh: Color,
    val heatPeak: Color,
    val heatEmpty: Color,
    val success: Color,
    val warning: Color,
    val rest: Color,
)

private val LightTones = CadenceTones(
    heatLow = Color(0xFFBFD8CE),
    heatMid = Color(0xFF74B3A0),
    heatHigh = Color(0xFF2E6B5E),
    heatPeak = Color(0xFF1B4A40),
    heatEmpty = Color(0xFFE4E7E0),
    success = Color(0xFF2F6F4A),
    warning = Color(0xFF9A6B1F),
    rest = Color(0xFF456B7C),
)

private val DarkTones = CadenceTones(
    heatLow = Color(0xFF2C4A42),
    heatMid = Color(0xFF4E8C79),
    heatHigh = Color(0xFF8FD3C0),
    heatPeak = Color(0xFFB7E4D6),
    heatEmpty = Color(0xFF2A2E26),
    success = Color(0xFF8FD3A6),
    warning = Color(0xFFE0BC7C),
    rest = Color(0xFFA6C9DA),
)

val LocalCadenceTones = staticCompositionLocalOf { LightTones }

private val CadenceTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 21.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    ),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
)

private val CadenceShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun CadenceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tones = if (darkTheme) DarkTones else LightTones
    CompositionLocalProvider(LocalCadenceTones provides tones) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = CadenceTypography,
            shapes = CadenceShapes,
            content = content,
        )
    }
}

/** Convenience accessor: `CadenceTheme.tones.heatHigh`. */
object CadenceTheme {
    val tones: CadenceTones
        @Composable get() = LocalCadenceTones.current
}
