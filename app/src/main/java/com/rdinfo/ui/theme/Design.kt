// Zielpfad: app/src/main/java/com/rdinfo/ui/theme/Design.kt
// Neu anlegen (ganze Datei einfügen)

package com.rdinfo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Zentrale UI‑Konstanten (ohne Theme-Umbau).
 * Schritt 1/3 für deine Design‑Tweaks.
 */
object AppColors {
    // Inaktiver Track/Divider bei den Slidern
    val SoftPink = Color(0xFFE3B9BB)
}

object Spacing {
    // Einheitliches Spacing – vermeidet Magic Numbers
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}
