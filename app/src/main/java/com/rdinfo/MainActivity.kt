// app/src/main/java/com/rdinfo/MainActivity.kt
package com.rdinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rdinfo.data.MedicationInfoSections
import com.rdinfo.data.MedicationRepository
import com.rdinfo.logic.RuleDosingUiAdapter
import com.rdinfo.ui.theme.RDInfoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RDInfoTheme { AppScreen() } }
    }
}

@Composable
private fun AppScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // Daten laden
    val meds by remember { mutableStateOf(MedicationRepository.listMedications(ctx)) }

    var medId by remember { mutableStateOf(meds.firstOrNull()?.id ?: "adrenalin") }
    var useCaseId by remember(medId) { mutableStateOf(MedicationRepository.useCasesFor(ctx, medId).firstOrNull()?.id) }
    var route by remember(medId, useCaseId) { mutableStateOf(useCaseId?.let { MedicationRepository.routesFor(ctx, medId, it).firstOrNull() }) }

    // Eingaben
    var years by remember { mutableIntStateOf(8) }
    var months by remember { mutableIntStateOf(0) }
    var weight by remember { mutableStateOf("") }

    var manualAmp by remember { mutableStateOf(false) }
    var ampMg by remember { mutableStateOf("") }
    var ampMl by remember { mutableStateOf("") }

    // Berechnung
    val ui = remember(medId, useCaseId, route, years, months, weight, manualAmp, ampMg, ampMl) {
        val mg = if (manualAmp) ampMg.replace(',', '.').toDoubleOrNull() else null
        val ml = if (manualAmp) ampMl.replace(',', '.').toDoubleOrNull() else null
        RuleDosingUiAdapter.compute(
            context = ctx,
            medicationId = medId,
            useCaseId = useCaseId ?: "rea",
            routeOrNull = route,
            ageYears = years,
            ageMonthsRemainder = months,
            weightKg = weight.replace(',', '.').toDoubleOrNull(),
            manualAmpMg = mg,
            manualAmpMl = ml
        )
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Medikament
            Text("Medikament", style = MaterialTheme.typography.labelSmall)
            Dropdown(
                items = meds.map { it.id to it.name },
                selectedId = medId,
                onSelect = { id ->
                    medId = id
                    useCaseId = MedicationRepository.useCasesFor(ctx, id).firstOrNull()?.id
                    route = useCaseId?.let { MedicationRepository.routesFor(ctx, id, it).firstOrNull() }
                }
            )
            Spacer(Modifier.height(12.dp))

            // Use-Case
            Text("Einsatzfall", style = MaterialTheme.typography.labelSmall)
            Dropdown(
                items = MedicationRepository.useCasesFor(ctx, medId).map { it.id to it.name },
                selectedId = useCaseId,
                onSelect = { uc ->
                    useCaseId = uc
                    route = uc?.let { MedicationRepository.routesFor(ctx, medId, it).firstOrNull() }
                }
            )
            Spacer(Modifier.height(12.dp))

            // Route
            Text("Applikationsart", style = MaterialTheme.typography.labelSmall)
            Dropdown(
                items = (useCaseId?.let { MedicationRepository.routesFor(ctx, medId, it) } ?: emptyList()).map { it to it },
                selectedId = route,
                onSelect = { route = it }
            )

            Divider(Modifier.padding(vertical = 12.dp))

            // Alter
            Text("Alter", style = MaterialTheme.typography.labelSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(years, { years = it }, "Jahre", 0..17)
                Spacer(Modifier.width(8.dp))
                NumberField(months, { months = it }, "Monate", 0..11)
            }
            Spacer(Modifier.height(8.dp))

            // Gewicht
            Text("Gewicht (kg)", style = MaterialTheme.typography.labelSmall)
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                singleLine = true,
                keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Divider(Modifier.padding(vertical = 12.dp))

            // Ampulle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Manuelle Ampulle", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(8.dp))
                Switch(checked = manualAmp, onCheckedChange = { manualAmp = it })
            }
            if (manualAmp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = ampMg,
                        onValueChange = { ampMg = it },
                        label = { Text("mg") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = ampMl,
                        onValueChange = { ampMl = it },
                        label = { Text("ml") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Divider(Modifier.padding(vertical = 12.dp))

            // Ergebnis
            Text("Berechnung", style = MaterialTheme.typography.titleMedium)
            if (!ui.ok) {
                Text(ui.error ?: "Fehler", color = MaterialTheme.colorScheme.error)
            } else {
                ResultRow("Dosierung:", ui.doseMg ?: "–")
                ResultRow("Volumen:", ui.volumeMl ?: "–", right = true)
                ResultRow("Lösung:", ui.concentration ?: "–")
                ResultRow("Gesamt:", ui.total ?: "–", right = true)
                ui.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }

            Divider(Modifier.padding(vertical = 12.dp))

            // Info-Tabs
            val info = MedicationRepository.infoTextsFor(ctx, medId)
            var tab by remember { mutableStateOf(MedicationInfoSections.Indication) }
            TabRow(selectedTabIndex = tab.ordinal) {
                MedicationInfoSections.values().forEach { sec ->
                    Tab(selected = tab == sec, onClick = { tab = sec }, text = { Text(sec.toUiTitle()) })
                }
            }
            when (tab) {
                MedicationInfoSections.Indication -> info?.indication?.let { InfoCard(it) }
                MedicationInfoSections.Contraindication -> info?.contraindication?.let { InfoCard(it) }
                MedicationInfoSections.Effect -> info?.effect?.let { InfoCard(it) }
                MedicationInfoSections.SideEffect -> info?.sideEffect?.let { InfoCard(it) }
            }
        }
    }
}

private fun MedicationInfoSections.toUiTitle(): String = when (this) {
    MedicationInfoSections.Indication -> "Indikation"
    MedicationInfoSections.Contraindication -> "Kontraindikation"
    MedicationInfoSections.Effect -> "Wirkung"
    MedicationInfoSections.SideEffect -> "Nebenwirkung"
}

@Composable
private fun ResultRow(label: String, value: String, right: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        Text(value, modifier = Modifier.weight(1f), textAlign = if (right) TextAlign.End else TextAlign.Start)
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(text, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun Dropdown(
    items: List<Pair<String, String>>, // id to label
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = items.firstOrNull { it.first == selectedId }?.second ?: "—"
    Surface(onClick = { expanded = true }, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Text("▾")
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        items.forEach { (id, title) ->
            DropdownMenuItem(text = { Text(title) }, onClick = {
                expanded = false
                onSelect(id)
            })
        }
    }
}

@Composable
private fun NumberField(value: Int, onValue: (Int) -> Unit, label: String, range: IntRange) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            val t = it.filter { ch -> ch.isDigit() }.take(2)
            text = t
            t.toIntOrNull()?.let { v ->
                val clamped = v.coerceIn(range)
                if (clamped != value) onValue(clamped)
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(120.dp)
    )
}
