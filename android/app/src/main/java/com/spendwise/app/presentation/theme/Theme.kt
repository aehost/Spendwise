package com.spendwise.app.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand ─────────────────────────────────────────────────────
val Primary       = Color(0xFF7C6BFF)   // Violet-Indigo — premium, modern
val PrimaryVar    = Color(0xFF9D8FFF)   // Lighter violet
val PrimaryDark   = Color(0xFF5B4EE0)   // Deeper violet
val Secondary     = Color(0xFF00D4FF)   // Electric cyan

// ── Backgrounds ───────────────────────────────────────────────
val Background    = Color(0xFF080B14)   // Deep navy-black
val Surface       = Color(0xFF0D1425)   // Navy-900
val CardBg        = Color(0xFF141D2E)   // Navy-800
val CardBg2       = Color(0xFF1A2640)   // Slightly lighter navy
val CardElevated  = Color(0xFF1E2D47)   // Elevated card surface

// ── Semantic ──────────────────────────────────────────────────
val SuccessColor  = Color(0xFF00E5A0)   // Mint-green — vibrant, readable
val ErrorColor    = Color(0xFFFF4D6A)   // Bright coral-red
val WarningColor  = Color(0xFFFFB547)   // Warm amber
val InfoColor     = Color(0xFF4DABF7)   // Sky blue

// ── Text — WCAG AA compliant on Background ────────────────────
val TextPrimary   = Color(0xFFF0F4FF)   // Near-white with blue tint
val TextSecondary = Color(0xFF8A9BC4)   // Blue-grey
val TextMuted     = Color(0xFF4A5880)   // Muted blue-grey

// ── Borders ───────────────────────────────────────────────────
val BorderColor   = Color(0xFF1E2D47)   // Navy border

// ── Achievement / Gold accent ─────────────────────────────────
val GoldAccent    = Color(0xFFFFD700)   // Gold for achievements
val GoldSoft      = Color(0xFFFFB800)   // Softer gold

// ── Chart / accent palette ────────────────────────────────────
val Amber         = Color(0xFFFFB547)
val Teal          = Color(0xFF00D4FF)
val Pink          = Color(0xFFFF6BE8)
val Green         = Color(0xFF00E5A0)
val Orange        = Color(0xFFFF8C42)
val Purple        = Color(0xFFAB87FF)
val Indigo        = Color(0xFF7C6BFF)
val Rose          = Color(0xFFFF4D6A)
val Violet        = Color(0xFF9B59B6)

// ── Gradient pairs (start → end) ─────────────────────────────
val GradientPurple  = listOf(Color(0xFF7C6BFF), Color(0xFF5B4EE0))
val GradientViolet  = listOf(Color(0xFF9B59B6), Color(0xFF6C3483))
val GradientTeal    = listOf(Color(0xFF00D4FF), Color(0xFF0099CC))
val GradientGreen   = listOf(Color(0xFF00E5A0), Color(0xFF00B377))
val GradientAmber   = listOf(Color(0xFFFFB547), Color(0xFFE08A00))
val GradientPink    = listOf(Color(0xFFFF6BE8), Color(0xFFCC00CC))
val GradientOrange  = listOf(Color(0xFFFF8C42), Color(0xFFE05A00))
val GradientRose    = listOf(Color(0xFFFF4D6A), Color(0xFFCC0033))
val GradientGold    = listOf(Color(0xFFFFD700), Color(0xFFFF9900))
val GradientHero    = listOf(Color(0xFF1A1060), Color(0xFF2D1B8E), Color(0xFF3D2ABF))
val GradientCardBlue = listOf(Color(0xFF0D1425), Color(0xFF141D2E))

private val DarkColorScheme = darkColorScheme(
    primary          = Primary,
    secondary        = Secondary,
    background       = Background,
    surface          = Surface,
    error            = ErrorColor,
    onPrimary        = Color.White,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    surfaceVariant   = CardBg,
    outline          = BorderColor,
)

@Composable
fun SpendWiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = SpendWiseTypography,
        content     = content
    )
}
