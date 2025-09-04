// File: app/src/main/java/com/example/rdinfo/MainActivity.kt
package com.example.rdinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    // Voll qualifizierter Name -> kein Import-Fehler mehr
                    com.example.rdinfo.ui.theme.EinsatzScreen(
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
