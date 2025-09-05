package com.rdinfo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Zentrale Typografie für RDInfo
 * Alle 16sp-Texte (titleMedium, bodyLarge) → 14sp
 * titleLarge bleibt 22sp (für „⋮“ usw.)
 */
val Typography = Typography(
    // Titel
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Default
    ),
    titleMedium = TextStyle(
        fontSize = 14.sp, // vorher 16sp
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Default
    ),
    titleSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Default
    ),

    // Fließtext
    bodyLarge = TextStyle(
        fontSize = 14.sp, // vorher 16sp
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Default
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp, // belassen
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Default
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Default
    ),

    // Labels/Buttons
    labelLarge = TextStyle(
        fontSize = 14.sp, // TextButton „Zurück“, etc.
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Default
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Default
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Default
    ),
)
