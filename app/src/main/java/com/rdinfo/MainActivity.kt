// Zielpfad: app/src/main/java/com/rdinfo/MainActivity.kt
// Ganze Datei ersetzen (Dropdown unter Gewicht, gleiche Höhe wie Zahlenfeld; keine DB nötig)

package com.rdinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rdinfo.ui.theme.AppColors
import com.rdinfo.ui.theme.RDInfoTheme
import com.rdinfo.ui.theme.Spacing
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isDark by rememberSaveable { mutableStateOf(false) }
            var currentScreen by rememberSaveable { mutableStateOf(Screen.MAIN) }

            RDInfoTheme(darkTheme = isDark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (currentScreen) {
                        Screen.MAIN -> MainScreen(
                            onOpenSettings = { currentScreen = Screen.SETTINGS }
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            isDark = isDark,
                            onBack = { currentScreen = Screen.MAIN },
                            onToggleDark = { isDark = it }
                        )
                    }
                }
            }
        }
    }
}

enum class Screen { MAIN, SETTINGS }

@Composable
private fun MainScreen(onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        Spacer(Modifier.height(Spacing.sm))
        HeaderRow(
            onMedicationsClick = { /* TODO */ },
            onMenuClick = onOpenSettings
        )
        HorizontalDivider(color = AppColors.SoftPink, thickness = 1.dp)
        Spacer(Modifier.height(Spacing.sm))

        // Alters-Slider
        var years by rememberSaveable { mutableStateOf(0) }
        LabeledCompactSlider("Jahre", years, 0..100) { years = it }
        Spacer(Modifier.height(Spacing.sm))

        var months by rememberSaveable { mutableStateOf(0) }
        LabeledCompactSlider("Monate", months, 0..11) { months = it }

        Spacer(Modifier.height(Spacing.lg))

        // Gewicht + Schätzung
        Text("Gewicht (kg)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.xs))
        var weightText by rememberSaveable { mutableStateOf("") }
        CompactNumberField(
            value = weightText,
            onValueChange = { t ->
                if (t.matches(Regex("^[0-9]*[.,]?[0-9]{0,2}$"))) weightText = t
            },
            modifier = Modifier.fillMaxWidth()
        )

        val estimated = remember(years, months) { estimateWeightKg(years, months) }
        val manualWeight = parseWeightKg(weightText)
        val effectiveWeight = manualWeight ?: estimated

        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Vorschlag: ${formatKg(estimated)} kg",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Spacing.sm))

        // Medikamenten-Dropdown (gleiche Höhe wie Zahlenfeld)
        Text("Medikament", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.xs))
        val medications = remember {
            listOf(
                "Adrenalin",
                "Amiodaron",
                "Atropin"
            )
        }
        var selectedMedication by rememberSaveable { mutableStateOf(medications.first()) }
        CompactDropdownField(
            selectedText = selectedMedication,
            options = medications,
            onSelect = { selectedMedication = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.lg))

        // Ergebnis (Platzhalter) – verwendet später effectiveWeight + selectedMedication
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.lg)) {
                Text("Berechnung", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Gewicht für Berechnung: ${formatKg(effectiveWeight)} kg | Medikament: $selectedMedication",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.small
    val borderColor = MaterialTheme.colorScheme.outline
    val textStyle = MaterialTheme.typography.bodyLarge

    Box(
        modifier
            .height(36.dp) // kompakt
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .then(modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CompactDropdownField(
    selectedText: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.small
    val borderColor = MaterialTheme.colorScheme.outline
    var expanded by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { mutableStateOf(0) }
    val fieldWidthDp = with(LocalDensity.current) { fieldWidthPx.toDp() }

    Box(
        modifier
            .height(36.dp)
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .onSizeChanged { fieldWidthPx = it.width }
            .then(modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .pointerInput(Unit) { detectTapGestures { expanded = true } },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(selectedText, style = MaterialTheme.typography.bodyLarge)
            DropdownChevron()
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(fieldWidthDp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DropdownChevron() {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width; val h = size.height
        val yTop = h * 0.4f; val yBottom = h * 0.6f
        val midX = w / 2f
        // Einfaches ▼ per zwei Linien
        drawLine(
            color = MaterialTheme.colorScheme.onSurface,
            start = Offset(midX - w * 0.25f, yTop),
            end = Offset(midX, yBottom),
            strokeWidth = 2f
        )
        drawLine(
            color = MaterialTheme.colorScheme.onSurface,
            start = Offset(midX + w * 0.25f, yTop),
            end = Offset(midX, yBottom),
            strokeWidth = 2f
        )
    }
}

private fun parseWeightKg(text: String): Double? =
    text.replace(',', '.').toDoubleOrNull()

private fun formatKg(v: Double): String = String.format(Locale.GERMANY, "%.1f", v)

/**
 * Realistische Gewichtsschätzung:
 *  - 0–12 Monate:  (0,5 × Monate) + 4
 *  - 1–5 Jahre:   APLS (2×J + 8), monatsgenau linear
 *  - 6–12 Jahre:  APLS (3×J + 7), monatsgenau linear
 *  - >12 Jahre:   Platzhalter 70 kg
 */
private fun estimateWeightKg(years: Int, months: Int): Double {
    val totalMonths = years * 12 + months
    if (totalMonths <= 12) return 4.0 + 0.5 * totalMonths

    fun aplsYearWeight(y: Int): Double = when (y) {
        in 1..5 -> 2.0 * y + 8.0
        in 6..12 -> 3.0 * y + 7.0
        else -> 70.0
    }

    return when (years) {
        in 1..11 -> {
            val w0 = aplsYearWeight(years)
            val w1 = aplsYearWeight(years + 1)
            val frac = (months.coerceIn(0, 11)) / 12.0
            w0 + (w1 - w0) * frac
        }
        12 -> aplsYearWeight(12)
        else -> 70.0
    }
}

@Composable
private fun HeaderRow(
    onMedicationsClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(
            onClick = onMedicationsClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("Medikamente") }

        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.semantics {
                contentDescription = "Mehr Optionen"
                stateDescription = "Öffnet Einstellungen"
            }
        ) { MoreVertIcon() }
    }
}

@Composable
private fun LabeledCompactSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label:", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(Spacing.xs))
        Text(value.toString(), style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(Modifier.height(0.dp))
    CompactSlider(
        value = value,
        onValueChange = onValueChange,
        range = range
    )
    Spacer(Modifier.height(Spacing.xs))
}

@Composable
private fun CompactSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 14.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = AppColors.SoftPink
) {
    val density = LocalDensity.current
    val trackHeightPx = with(density) { trackHeight.toPx() }
    var widthPx by remember { mutableStateOf(0f) }

    val min = range.first
    val max = range.last
    val span = (max - min).coerceAtLeast(1)

    fun xFromValue(v: Int): Float {
        val clamped = v.coerceIn(min, max)
        val frac = (clamped - min).toFloat() / span
        return frac * widthPx
    }

    fun valueFromX(x: Float): Int {
        if (widthPx <= 0f) return value
        val frac = (x / widthPx).coerceIn(0f, 1f)
        return (min + frac * span).toInt()
    }

    val inactive = inactiveColor
    val active = activeColor
    val thumbColor = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { size -> widthPx = size.width.toFloat() }
            .pointerInput(range) {
                // Tap-to-Set
                detectTapGestures(onTap = { pos -> onValueChange(valueFromX(pos.x)) })
            }
            .pointerInput(range) {
                // Drag
                detectDragGestures(
                    onDragStart = { o -> onValueChange(valueFromX(o.x)) },
                    onDrag = { change, _ -> onValueChange(valueFromX(change.position.x)) },
                )
            }
            .semantics { stateDescription = "$value" },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
            val corner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
            val centerY = size.height / 2f
            val top = centerY - trackHeightPx / 2f

            drawRoundRect(
                color = inactive,
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(size.width, trackHeightPx),
                cornerRadius = corner
            )

            val thumbX = xFromValue(value)
            val thumbRadius = trackHeightPx / 2f
            val cx = thumbX.coerceIn(thumbRadius, size.width - thumbRadius)
            drawRoundRect(
                color = active,
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(cx.coerceAtLeast(0f), trackHeightPx),
                cornerRadius = corner
            )

            drawCircle(
                color = thumbColor,
                radius = thumbRadius,
                center = Offset(cx, centerY)
            )
        }
    }
}

@Composable
private fun MoreVertIcon(
    modifier: Modifier = Modifier,
    dotSize: Dp = 3.dp,
    gap: Dp = 3.dp
) {
    val dot = with(LocalDensity.current) { dotSize.toPx() }
    val spacing = with(LocalDensity.current) { (dotSize + gap).toPx() }
    val dotColor = MaterialTheme.colorScheme.onSurface

    Canvas(Modifier.size(24.dp).then(modifier)) {
        val cx = size.width / 2f
        val startY = size.height / 2f - spacing
        repeat(3) { i ->
            drawCircle(
                color = dotColor,
                radius = dot / 2f,
                center = Offset(cx, startY + i * spacing)
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    isDark: Boolean,
    onBack: () -> Unit,
    onToggleDark: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(Spacing.lg)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Einstellungen", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Zurück") }
        }
        Spacer(Modifier.height(Spacing.lg))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Dark‑Mode", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Switch(checked = isDark, onCheckedChange = onToggleDark)
        }
    }
}
