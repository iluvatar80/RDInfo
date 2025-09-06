// ================================
// File: app/src/main/java/com/rdinfo/MainActivity.kt
// (Unverändert in der Optik – inkl. fixen Imports)
// ================================
package com.rdinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.rdinfo.ui.theme.RDInfoTheme
import kotlin.math.roundToInt

// Wunschfarbe für inaktive Sliderbereiche & Zierlinien (#E3B9BB)
private val SoftPink = Color(0xFFE3B9BB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var darkMode by rememberSaveable { mutableStateOf(false) }
            RDInfoTheme(darkTheme = darkMode) {
                RDInfoApp(
                    darkMode = darkMode,
                    onDarkModeChange = { darkMode = it }
                )
            }
        }
    }
}

private enum class Screen { MAIN, SETTINGS }

@Composable
fun RDInfoApp(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
        ) {
            when (screen) {
                Screen.MAIN -> MedicationInput(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    onOpenSettings = { screen = Screen.SETTINGS }
                )

                Screen.SETTINGS -> SettingsScreen(
                    darkMode = darkMode,
                    onDarkModeChange = onDarkModeChange,
                    onBack = { screen = Screen.MAIN }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(16.dp)
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Dark Mode", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text("Zurück") }
    }
}

/* =================== Hauptscreen – Eingaben =================== */
@Composable
fun MedicationInput(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit
) {
    var years by rememberSaveable { mutableIntStateOf(5) }   // 0..100
    var months by rememberSaveable { mutableIntStateOf(0) }  // 0..11
    var weightText by rememberSaveable { mutableStateOf("") } // kg
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Spacer(Modifier.height(8.dp)) // etwas mehr Luft nach oben

        // OBERSTE ZEILE: Medikamente-Button (links) + 3-Punkte-Menü (rechts) in einer Flucht
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // „Medikamente“ – wieder pill/abgerundet wie zuvor
            Button(
                onClick = {},
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text("Medikamente", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.weight(1f))

            // Drei-Punkte-Menü rechts – in einer Linie mit dem Button
            Box {
                TextButton(
                    onClick = { menuOpen = true },
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text(
                        "⋮",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            menuOpen = false
                            onOpenSettings()
                        }
                    )
                }
            }
        }

        // Zierlinie direkt unter der gesamten oberen Zeile
        Spacer(Modifier.height(4.dp))
        Divider(thickness = 1.dp, color = SoftPink)
        Spacer(Modifier.height(12.dp))

        LabeledIntCompactSlider(
            label = "Jahre",
            value = years,
            range = 0..100, // erhöht auf 100
            onChange = { years = it }
        )

        Spacer(Modifier.height(10.dp))

        LabeledIntCompactSlider(
            label = "Monate",
            value = months,
            range = 0..11,
            onChange = { months = it }
        )

        // Gewicht näher an die Slider
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = weightText,
            onValueChange = { s ->
                weightText = s.filter { it.isDigit() || it == '.' || it == ',' }
            },
            label = { Text("Gewicht (kg)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        // Berechnung näher nach oben
        Spacer(Modifier.height(4.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Berechnung", style = MaterialTheme.typography.titleMedium)
                Text("Folgt im nächsten Schritt.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

/* =================== Slider (kompakt & designgetreu) =================== */
@Composable
fun LabeledIntCompactSlider(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        // Label sehr nah am Slider
        Text("$label: $value", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(0.dp))
        CompactSlider(value = value, range = range, onChange = onChange)
        // Zierlinie direkt unter dem Slider
        Spacer(Modifier.height(0.dp))
        Divider(thickness = 1.dp, color = SoftPink)
    }
}

@Composable
fun CompactSlider(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(40.dp) // Gesamtraum
) {
    // Inaktiv: #E3B9BB, Aktiv: Primär-Rot
    val active = MaterialTheme.colorScheme.primary
    val inactive = SoftPink

    var widthPx by remember { mutableStateOf(1f) }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(range) {
                detectTapGestures { offset ->
                    val ratio = (offset.x.coerceIn(0f, widthPx)) / widthPx
                    val newVal = (range.first + (range.last - range.first) * ratio)
                        .roundToInt()
                        .coerceIn(range)
                    onChange(newVal)
                }
            }
            .pointerInput(range) {
                detectDragGestures { change, _ ->
                    val ratio = (change.position.x.coerceIn(0f, widthPx)) / widthPx
                    val newVal = (range.first + (range.last - range.first) * ratio)
                        .roundToInt()
                        .coerceIn(range)
                    onChange(newVal)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Track-Höhe – bestimmt auch die Thumb-Größe
            val trackHeight = 14.dp.toPx()
            val radius = trackHeight / 2
            val centerY = size.height / 2

            // Inaktiver Track (voll, #E3B9BB)
            drawRoundRect(
                color = inactive,
                topLeft = Offset(0f, centerY - trackHeight / 2),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(radius, radius)
            )

            // Aktiver Track (links vom Thumb, Rot)
            val ratio = (value - range.first).toFloat() /
                    (range.last - range.first).coerceAtLeast(1)
            val activeWidth = size.width * ratio
            drawRoundRect(
                color = active,
                topLeft = Offset(0f, centerY - trackHeight / 2),
                size = Size(activeWidth, trackHeight),
                cornerRadius = CornerRadius(radius, radius)
            )

            // Thumb – QUADRATISCH (eckig), Seitenlänge = trackHeight
            val thumbSide = trackHeight
            val half = thumbSide / 2f
            val thumbXCenter = activeWidth.coerceIn(half, size.width - half)
            val topLeft = Offset(thumbXCenter - half, centerY - half)

            // rotes Quadrat (eckig)
            drawRect(
                color = active,
                topLeft = topLeft,
                size = Size(thumbSide, thumbSide)
            )

            // weißer VERTIKALER Streifen – durchgehend & satt
            val stripeStroke = trackHeight * 0.48f // breit
            drawLine(
                color = Color.White,
                start = Offset(thumbXCenter, centerY - half),
                end = Offset(thumbXCenter, centerY + half),
                strokeWidth = stripeStroke,
                cap = StrokeCap.Butt
            )
        }
    }
}

