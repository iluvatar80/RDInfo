// File: app/src/main/java/com/example/rdinfo/ui/theme/MainActivity.kt
package com.example.rdinfo.ui.theme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

/**
 * Minimaler Activity-Host, der den neuen EinsatzScreen referenziert.
 * (Parameter vorerst null → nur zum Kompilieren. Später mit echtem State füllen.)
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    // Voll qualifizierter Name vermeidet Importprobleme
                    com.example.rdinfo.ui.EinsatzScreen(
                        appliedRule = null,
                        selectedFormulation = null,
                        manualConcMgPerMl = null,
                        weightKg = null
                    )
                }
            }
        }
    }
}
