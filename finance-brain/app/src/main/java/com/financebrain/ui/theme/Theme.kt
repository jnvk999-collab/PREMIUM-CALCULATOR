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
/** Bright theme: soft blue-white ground, white cards, saturated semantic colours. */
val LightPalette = Palette(
    bg = Color(0xFFF2F6FC), s1 = Color(0xFFFFFFFF), s2 = Color(0xFFFFFFFF), s3 = Color(0xFFEDF2FA), bd = Color(0xFFDCE4F0), bd2 = Color(0xFFC5D1E3),
    gold = Color(0xFFD48A12), green = Color(0xFF16A34A), red = Color(0xFFE11D48), blue = Color(0xFF2563EB), purple = Color(0xFF7C3AED), orange = Color(0xFFF97316), teal = Color(0xFF0D9488),
    t1 = Color(0xFF0F172A), t2 = Color(0xFF5B6B84), t3 = Color(0xFF94A3B8),
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
    Color(0xFFF97316), Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFFD48A12), Color(0xFF16A34A), Color(0xFFE11D48),
    Color(0xFF0D9488), Color(0xFFDB2777), Color(0xFF9C6B2E), Color(0xFF6C7A89), Color(0xFF65A30D), Color(0xFFB04E4E),
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

/** "light" (default), "dark" or "system". */
@Composable
fun isDarkFor(mode: String): Boolean = when (mode) { "dark" -> true; "system" -> isSystemInDarkTheme(); else -> false }

@Composable
fun FinanceBrainTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val p = if (darkTheme) DarkPalette else LightPalette
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme(p, darkTheme), typography = AppTypography, content = content)
    }
}
