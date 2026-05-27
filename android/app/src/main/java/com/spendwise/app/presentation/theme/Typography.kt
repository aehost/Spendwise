package com.spendwise.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SpendWiseTypography = Typography(
    displayLarge  = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Black),
    displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    headlineMedium= TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleLarge    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleMedium   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge     = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium    = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall     = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelSmall    = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)
