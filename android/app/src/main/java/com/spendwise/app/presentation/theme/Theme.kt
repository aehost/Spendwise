package com.spendwise.app.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Core palette ──────────────────────────────────────────────
val Primary       = Color(0xFF7C5CFC)   // vibrant violet
val PrimaryVar    = Color(0xFF9D6FFF)   // lighter violet
val Secondary     = Color(0xFF06C5D4)   // cyan-teal
val Background    = Color(0xFF08091C)   // deep space navy
val Surface       = Color(0xFF0D1030)   // navy surface
val CardBg        = Color(0xFF121535)   // navy card
val CardBg2       = Color(0xFF0F1428)   // alternate card

// ── Semantic colours ──────────────────────────────────────────
val ErrorColor    = Color(0xFFFF4757)   // vivid red
val SuccessColor  = Color(0xFF2ED573)   // vivid green
val WarningColor  = Color(0xFFFFD43B)   // vivid amber
val InfoColor     = Color(0xFF74B9FF)   // sky blue

// ── Text ──────────────────────────────────────────────────────
val TextPrimary   = Color(0xFFF0F4FF)   // near-white with blue tint
val TextSecondary = Color(0xFF8892B0)   // slate-blue
val TextMuted     = Color(0xFF4A5568)   // dim

// ── Borders & dividers ────────────────────────────────────────
val BorderColor   = Color(0xFF1E2550)   // dark navy border

// ── Accent palette for stat cards & charts ────────────────────
val Amber         = Color(0xFFFFD43B)
val Teal          = Color(0xFF06C5D4)
val Pink          = Color(0xFFFF6B9D)
val Green         = Color(0xFF2ED573)
val Orange        = Color(0xFFFF7F50)
val Purple        = Color(0xFF7C5CFC)

// ── Card gradient helpers ─────────────────────────────────────
val GradientPurple = listOf(Color(0xFF7C5CFC), Color(0xFF5A3FE0))
val GradientTeal   = listOf(Color(0xFF06C5D4), Color(0xFF0490A1))
val GradientGreen  = listOf(Color(0xFF2ED573), Color(0xFF1BB454))
val GradientAmber  = listOf(Color(0xFFFFD43B), Color(0xFFF7A800))
val GradientPink   = listOf(Color(0xFFFF6B9D), Color(0xFFD84472))

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
