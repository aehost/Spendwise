package com.spendwise.app.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Primary       = Color(0xFF6C63FF)
val PrimaryVar    = Color(0xFF8B5CF6)
val Secondary     = Color(0xFFEC4899)
val Background    = Color(0xFF0A0A0F)
val Surface       = Color(0xFF12121A)
val CardBg        = Color(0xFF1A1A28)
val ErrorColor    = Color(0xFFEF4444)
val SuccessColor  = Color(0xFF10B981)
val WarningColor  = Color(0xFFF59E0B)
val TextPrimary   = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted     = Color(0xFF64748B)
val BorderColor   = Color(0xFF1E2036)
val Amber         = Color(0xFFF59E0B)
val Teal          = Color(0xFF06B6D4)
val Pink          = Color(0xFFEC4899)
val Green         = Color(0xFF10B981)

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
