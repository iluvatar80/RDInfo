// File: app/src/main/java/com/example/rdinfo/MainActivity.kt
package com.example.rdinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.rdinfo.ui.theme.RDInfoTheme
import com.example.rdinfo.ui.theme.EinsatzScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.rdinfo.data.MedsSeeder


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            MedsSeeder.seedFromAssetsReplaceAll(applicationContext)
        }

        setContent {
            RDInfoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EinsatzScreen()
                }
            }
        }
    }
}
