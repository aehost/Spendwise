package com.spendwise.app.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ════════════════════════════════════════════════════════════════
//  Palette v2 — "Azure Aurora"
//  Fresh, vibrant azure + teal with warm amber/gold accents on a
//  deep slate base. Token NAMES are unchanged so every screen
//  re-skins automatically; only the values are new.
// ════════════════════════════════════════════════════════════════

// ── Brand ─────────────────────────────────────────────────────
val Primary       = Color(0xFF4F8CFF)   // Bright azure blue — fresh, trustworthy
val PrimaryVar    = Color(0xFF7AACFF)   // Lighter azure
val PrimaryDark   = Color(0xFF2F6BE6)   // Deeper azure
val Secondary     = Color(0xFF2DD4BF)   // Vibrant teal

// ── Backgrounds — deep slate ──────────────────────────────────
val Background    = Color(0xFF0A0E17)   // Deep blue-slate
val Surface       = Color(0xFF111725)   // Slate-900
val CardBg        = Color(0xFF161E2E)   // Slate-800
val CardBg2       = Color(0xFF1D2840)   // Slightly lighter slate
val CardElevated  = Color(0xFF243150)   // Elevated card surface

// ── Semantic ──────────────────────────────────────────────────
val SuccessColor  = Color(0xFF22C55E)   // Vivid green
val ErrorColor    = Color(0xFFFB5168)   // Coral red
val WarningColor  = Color(0xFFFBBF24)   // Amber
val InfoColor     = Color(0xFF4F8CFF)   // Azure

// ── Text — WCAG AA compliant on Background ────────────────────
val TextPrimary   = Color(0xFFEAF1FF)   // Near-white, cool tint
val TextSecondary = Color(0xFF94A6C8)   // Slate-grey
val TextMuted     = Color(0xFF566184)   // Muted slate

// ── Borders ───────────────────────────────────────────────────
val BorderColor   = Color(0xFF22304D)   // Slate border

// ── Achievement / Gold accent ─────────────────────────────────
val GoldAccent    = Color(0xFFFBBF24)   // Amber-gold for achievements
val GoldSoft      = Color(0xFFF59E0B)   // Softer gold

// ── Chart / accent palette ────────────────────────────────────
val Amber         = Color(0xFFFBBF24)
val Teal          = Color(0xFF2DD4BF)
val Pink          = Color(0xFFF472B6)
val Green         = Color(0xFF22C55E)
val Orange        = Color(0xFFFB923C)
val Purple        = Color(0xFFA78BFA)
val Indigo        = Color(0xFF4F8CFF)
val Rose          = Color(0xFFFB5168)
val Violet        = Color(0xFF8B7CF6)

// ── Gradient pairs (start → end) ─────────────────────────────
val GradientPurple  = listOf(Color(0xFF4F8CFF), Color(0xFF2F6BE6))   // primary azure
val GradientViolet  = listOf(Color(0xFF8B7CF6), Color(0xFF6D5BE0))
val GradientTeal    = listOf(Color(0xFF2DD4BF), Color(0xFF14B8A6))
val GradientGreen   = listOf(Color(0xFF22C55E), Color(0xFF16A34A))
val GradientAmber   = listOf(Color(0xFFFBBF24), Color(0xFFF59E0B))
val GradientPink    = listOf(Color(0xFFF472B6), Color(0xFFDB2777))
val GradientOrange  = listOf(Color(0xFFFB923C), Color(0xFFEA580C))
val GradientRose    = listOf(Color(0xFFFB5168), Color(0xFFE11D48))
val GradientGold    = listOf(Color(0xFFFBBF24), Color(0xFFF59E0B))
val GradientHero    = listOf(Color(0xFF12224A), Color(0xFF1E3A78), Color(0xFF2F6BE6))   // azure hero
val GradientCardBlue = listOf(Color(0xFF111725), Color(0xFF161E2E))

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
