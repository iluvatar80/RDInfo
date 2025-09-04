package com.example.rdinfo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import com.example.rdinfo.data.MedsSeeder
import com.example.rdinfo.ui.theme.EinsatzScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        // Während der Entwicklung optional true – aber jetzt OHNE den UI-Start zu blockieren
        private const val DEV_ALWAYS_RESEED: Boolean = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) UI SOFORT zeichnen → vermeidet ANR beim App-Start
        setContent {
            MaterialTheme { Surface { EinsatzScreen() } }
        }

        // 2) Seeding IM HINTERGRUND (IO-Thread). Flows aktualisieren die UI automatisch.
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (DEV_ALWAYS_RESEED) {
                    MedsSeeder.seedFromAssetsReplaceAll(this@MainActivity)
                } else {
                    MedsSeeder.seedFromAssetsIfEmpty(this@MainActivity)
                }
            } catch (t: Throwable) {
                Log.e("RDInfo", "Seeding failed", t)
            }
        }
    }
}
