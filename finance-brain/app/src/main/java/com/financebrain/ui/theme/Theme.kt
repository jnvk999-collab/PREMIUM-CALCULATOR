package com.financebrain.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** FinanceOS palette: deep navy surfaces, gold accent, clear semantic colours. */
data class Palette(
    val bg: Color, val s1: Color, val s2: Color, val s3: Color, val bd: Color, val bd2: Color,
    val gold: Color, val green: Color, val red: Color, val blue: Color, val purple: Color, val orange: Color, val teal: Color,
    val t1: Color, val t2: Color, val t3: Color,
)

val DarkPalette = Palette(
    bg = Color(0xFF06080F), s1 = Color(0xFF0B0F1C), s2 = Color(0xFF111828), s3 = Color(0xFF182035), bd = Color(0xFF1C2540), bd2 = Color(0xFF263050),
    gold = Color(0xFFD9A84C), green = Color(0xFF27C882), red = Color(0xFFFF526A), blue = Color(0xFF4A9EFF), purple = Color(0xFF9B7FFF), orange = Color(0xFFFF8C42), teal = Color(0xFF2DD4BF),
    t1 = Color(0xFFE6EAF8), t2 = Color(0xFF6070A0), t3 = Color(0xFF3F4A6B),
)
val LightPalette = Palette(
    bg = Color(0xFFFAFAF9), s1 = Color(0xFFFFFFFF), s2 = Color(0xFFF6F5F0), s3 = Color(0xFFEFEDE8), bd = Color(0xFFE5E2DA), bd2 = Color(0xFFD4D0C8),
    gold = Color(0xFFC17B2A), green = Color(0xFF1A7A4A), red = Color(0xFFCC3333), blue = Color(0xFF2563AA), purple = Color(0xFF6B4ECC), orange = Color(0xFFC4500A), teal = Color(0xFF0F766E),
    t1 = Color(0xFF1A1915), t2 = Color(0xFF6B6860), t3 = Color(0xFFA8A59E),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }
val P: Palette @Composable get() = LocalPalette.current

// Names kept from the first design so existing screens compile; they now map to the FinanceOS palette.
val Teal = DarkPalette.gold
val TealDark = Color(0xFF0F1830)
val Mint = Color(0xFFF0D18C)
val MintSoft = Color(0xFF2A2A1E)
val Coral = DarkPalette.red
val Leaf = DarkPalette.green
val Amber = DarkPalette.orange
val Blue = DarkPalette.blue
val Purple = DarkPalette.purple

val CategoryPalette = listOf(
    DarkPalette.orange, DarkPalette.blue, DarkPalette.purple, DarkPalette.gold, DarkPalette.green, DarkPalette.red,
    DarkPalette.teal, Color(0xFFD9558F), Color(0xFF9C6B2E), Color(0xFF6C7A89), Color(0xFF5CB85C), Color(0xFFB04E4E),
)

private fun scheme(p: Palette, dark: Boolean) = if (dark) darkColorScheme(
    primary = p.gold, onPrimary = Color(0xFF1A1200), primaryContainer = Color(0xFF3A2E12), onPrimaryContainer = Mint,
    secondary = p.green, secondaryContainer = Color(0xFF123526), onSecondaryContainer = Color(0xFFBFF3D9),
    tertiary = p.blue, error = p.red, errorContainer = Color(0xFF4A1A22),
    background = p.bg, onBackground = p.t1, surface = p.s2, onSurface = p.t1,
    surfaceVariant = p.s3, onSurfaceVariant = p.t2, outline = p.bd2, outlineVariant = p.bd,
) else lightColorScheme(
    primary = p.gold, onPrimary = Color.White, primaryContainer = Color(0xFFF5E6C8), onPrimaryContainer = Color(0xFF3A2E12),
    secondary = p.green, secondaryContainer = Color(0xFFDDF3E6), onSecondaryContainer = Color(0xFF0E3D25),
    tertiary = p.blue, error = p.red, errorContainer = Color(0xFFFBE3E3),
    background = p.bg, onBackground = p.t1, surface = p.s1, onSurface = p.t1,
    surfaceVariant = p.s3, onSurfaceVariant = p.t2, outline = p.bd2, outlineVariant = p.bd,
)

val AppTypography = Typography().let {
    it.copy(
        displaySmall = it.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = it.labelLarge.copy(fontWeight = FontWeight.Medium),
        labelSmall = it.labelSmall.copy(letterSpacing = 0.6.sp),
    )
}

@Composable
fun FinanceBrainTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (darkTheme) DarkPalette else LightPalette
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme(p, darkTheme), typography = AppTypography, content = content)
    }
}
