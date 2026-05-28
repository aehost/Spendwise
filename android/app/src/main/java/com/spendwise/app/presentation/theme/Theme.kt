package com.spendwise.app.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand ─────────────────────────────────────────────────────
val Primary       = Color(0xFF6366F1)   // Indigo-500 — professional, calm
val PrimaryVar    = Color(0xFF818CF8)   // Indigo-400
val PrimaryDark   = Color(0xFF4F46E5)   // Indigo-600
val Secondary     = Color(0xFF06B6D4)   // Cyan-500

// ── Backgrounds ───────────────────────────────────────────────
val Background    = Color(0xFF030712)   // Near-black (Gray-950)
val Surface       = Color(0xFF0F172A)   // Slate-900
val CardBg        = Color(0xFF1E293B)   // Slate-800
val CardBg2       = Color(0xFF162032)   // Slightly lighter card

// ── Semantic ──────────────────────────────────────────────────
val SuccessColor  = Color(0xFF10B981)   // Emerald-500 — WCAG AA on dark
val ErrorColor    = Color(0xFFEF4444)   // Red-500
val WarningColor  = Color(0xFFF59E0B)   // Amber-500
val InfoColor     = Color(0xFF3B82F6)   // Blue-500

// ── Text — WCAG AA compliant on Background ────────────────────
val TextPrimary   = Color(0xFFF8FAFC)   // Slate-50   — 19:1 contrast
val TextSecondary = Color(0xFF94A3B8)   // Slate-400  —  7:1 contrast
val TextMuted     = Color(0xFF64748B)   // Slate-500  —  4.6:1 contrast ✓

// ── Borders ───────────────────────────────────────────────────
val BorderColor   = Color(0xFF1E293B)   // Slate-800

// ── Chart / accent palette ────────────────────────────────────
val Amber         = Color(0xFFF59E0B)
val Teal          = Color(0xFF06B6D4)
val Pink          = Color(0xFFEC4899)
val Green         = Color(0xFF10B981)
val Orange        = Color(0xFFF97316)
val Purple        = Color(0xFF8B5CF6)
val Indigo        = Color(0xFF6366F1)
val Rose          = Color(0xFFF43F5E)

// ── Gradient pairs (start → end) ─────────────────────────────
val GradientPurple = listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
val GradientTeal   = listOf(Color(0xFF06B6D4), Color(0xFF0891B2))
val GradientGreen  = listOf(Color(0xFF10B981), Color(0xFF059669))
val GradientAmber  = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
val GradientPink   = listOf(Color(0xFFEC4899), Color(0xFFDB2777))
val GradientOrange = listOf(Color(0xFFF97316), Color(0xFFEA580C))
val GradientRose   = listOf(Color(0xFFF43F5E), Color(0xFFE11D48))

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
