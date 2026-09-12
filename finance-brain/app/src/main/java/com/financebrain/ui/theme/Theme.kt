package com.financebrain.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Teal = Color(0xFF0B5C4D)
val TealDark = Color(0xFF063D33)
val Mint = Color(0xFF7BE0B0)
val MintSoft = Color(0xFFDDF5EA)
val Coral = Color(0xFFE05A4E)
val CoralSoft = Color(0xFFFCE4E1)
val Leaf = Color(0xFF1E9E6A)
val LeafSoft = Color(0xFFDCF3E8)
val Ink = Color(0xFF14201C)
val Cloud = Color(0xFFF4F7F5)
val Amber = Color(0xFFE8A13A)

val CategoryPalette = listOf(
    Color(0xFF0B5C4D), Color(0xFFE05A4E), Color(0xFF3B7DD8), Color(0xFFE8A13A),
    Color(0xFF8E5BD1), Color(0xFF1E9E6A), Color(0xFFD9558F), Color(0xFF3FA9C9),
    Color(0xFF9C6B2E), Color(0xFF6C7A89), Color(0xFF5CB85C), Color(0xFFB04E4E),
)

private val LightScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = MintSoft,
    onPrimaryContainer = TealDark,
    secondary = Leaf,
    secondaryContainer = LeafSoft,
    onSecondaryContainer = TealDark,
    tertiary = Amber,
    error = Coral,
    errorContainer = CoralSoft,
    background = Cloud,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9EFEC),
    onSurfaceVariant = Color(0xFF52605B),
    outline = Color(0xFFC3CDC8),
)

private val DarkScheme = darkColorScheme(
    primary = Mint,
    onPrimary = TealDark,
    primaryContainer = Color(0xFF14483D),
    onPrimaryContainer = MintSoft,
    secondary = Color(0xFF7ED9AE),
    secondaryContainer = Color(0xFF1B4536),
    onSecondaryContainer = LeafSoft,
    tertiary = Amber,
    error = Color(0xFFFF8A7E),
    errorContainer = Color(0xFF5A2620),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFE6ECE9),
    surface = Color(0xFF161E1B),
    onSurface = Color(0xFFE6ECE9),
    surfaceVariant = Color(0xFF232D29),
    onSurfaceVariant = Color(0xFFAEBBB5),
    outline = Color(0xFF4A5753),
)

val AppTypography = Typography().let {
    it.copy(
        displaySmall = it.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = it.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

@Composable
fun FinanceBrainTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
