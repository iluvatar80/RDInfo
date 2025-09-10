// File: app/src/main/java/com/rdinfo/editor/MedicationEditorActivity.kt
package com.rdinfo.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rdinfo.ui.theme.RDInfoTheme
import com.rdinfo.data.MedicationRepository
import com.rdinfo.data.UseCaseKey
import com.rdinfo.data.useCaseKeyFromLabel

// --- Simple in-memory editor model (nested) ---
private data class EditorMed(
    var name: String,
    val useCaseToRoutes: MutableMap<String, MutableList<String>> = mutableMapOf()
)

private data class EditorRule(
    var useCase: UseCaseKey,
    var route: String?,
    var ageMinYears: Int? = null,
    var ageMaxYears: Int? = null,
    var weightMinKg: Double? = null,
    var weightMaxKg: Double? = null,
    var doseMgPerKg: Double? = null,
    var fixedDoseMg: Double? = null,
    var recommendedConcMgPerMl: Double? = null,
    var solutionText: String? = null,
    var totalPreparedMl: Double? = null,
    var maxDoseMg: Double? = null,
    var maxDoseText: String? = null,
    var note: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
class MedicationEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RDInfoTheme { EditorScreen(onClose = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current

    // --- Build initial editor state from Repository ---
    val editorMeds = remember {
        val meds = MedicationRepository.getMedicationNames()
        mutableStateListOf<EditorMed>().apply {
            meds.forEach { m ->
                val ucLabels = MedicationRepository.getUseCaseNamesForMedication(m)
                val map = mutableMapOf<String, MutableList<String>>()
                ucLabels.forEach { uc ->
                    val routes = MedicationRepository.getRouteNamesForMedicationUseCase(m, uc)
                    map[uc] = routes.toMutableList()
                }
                add(EditorMed(name = m, useCaseToRoutes = map))
            }
        }
    }

    // --- Current selections ---
    var selectedMed by remember { mutableStateOf(editorMeds.firstOrNull()?.name) }
    val allLabel = "Alle"
    var selectedUseCase by remember(selectedMed) { mutableStateOf(allLabel) }
    var selectedRoute by remember(selectedMed, selectedUseCase) { mutableStateOf(allLabel) }

    fun currentMed(): EditorMed? = editorMeds.firstOrNull { it.name == selectedMed }
    fun currentUseCases(): List<String> = currentMed()?.useCaseToRoutes?.keys?.sorted() ?: emptyList()
    fun currentRoutes(): List<String> = currentMed()?.useCaseToRoutes?.get(selectedUseCase)?.sorted() ?: emptyList()

    // --- Dialog state ---
    var showNewMedDialog by remember { mutableStateOf(false) }
    var showNewUseCaseDialog by remember { mutableStateOf(false) }
    var showNewRouteDialog by remember { mutableStateOf(false) }

    var showRenameMedDialog by remember { mutableStateOf(false) }
    var showRenameUseCaseDialog by remember { mutableStateOf(false) }
    var showRenameRouteDialog by remember { mutableStateOf(false) }

    var showDeleteMedConfirm by remember { mutableStateOf(false) }
    var showDeleteUseCaseConfirm by remember { mutableStateOf(false) }
    var showDeleteRouteConfirm by remember { mutableStateOf(false) }

    // --- Add handlers ---
    fun addMedication(nameRaw: String) {
        val name = nameRaw.trim()
        if (name.isEmpty()) return
        if (editorMeds.any { it.name.equals(name, ignoreCase = true) }) return
        editorMeds.add(EditorMed(name = name))
        selectedMed = name
        selectedUseCase = allLabel
        selectedRoute = allLabel
    }

    fun addUseCase(labelRaw: String) {
        val med = currentMed() ?: return
        val label = labelRaw.trim()
        if (label.isEmpty()) return
        if (med.useCaseToRoutes.keys.any { it.equals(label, true) }) return
        med.useCaseToRoutes[label] = mutableListOf()
        selectedUseCase = label
        selectedRoute = allLabel
    }

    fun addRoute(nameRaw: String) {
        val med = currentMed() ?: return
        val uc = selectedUseCase.takeIf { it != allLabel } ?: return
        val name = nameRaw.trim()
        if (name.isEmpty()) return
        val list = med.useCaseToRoutes[uc] ?: mutableListOf<String>().also { med.useCaseToRoutes[uc] = it }
        if (list.any { it.equals(name, true) }) return
        list.add(name)
        selectedRoute = name
    }

    // --- Rename handlers ---
    fun renameMedication(newNameRaw: String) {
        val newName = newNameRaw.trim()
        val med = currentMed() ?: return
        if (newName.isEmpty() || editorMeds.any { it.name.equals(newName, true) }) return
        med.name = newName
        selectedMed = newName
    }

    fun renameUseCase(newLabelRaw: String) {
        val med = currentMed() ?: return
        val uc = selectedUseCase.takeIf { it != allLabel } ?: return
        val newLabel = newLabelRaw.trim()
        if (newLabel.isEmpty() || med.useCaseToRoutes.keys.any { it.equals(newLabel, true) }) return
        val routes = med.useCaseToRoutes.remove(uc)
        med.useCaseToRoutes[newLabel] = routes ?: mutableListOf()
        selectedUseCase = newLabel
    }

    fun renameRoute(newNameRaw: String) {
        val med = currentMed() ?: return
        val uc = selectedUseCase.takeIf { it != allLabel } ?: return
        val newName = newNameRaw.trim()
        if (newName.isEmpty()) return
        val routes = med.useCaseToRoutes[uc] ?: return
        if (routes.any { it.equals(newName, true) }) return
        val idx = routes.indexOfFirst { it == selectedRoute }
        if (idx >= 0) {
            routes[idx] = newName
            selectedRoute = newName
        }
    }

    // --- Delete handlers ---
    fun deleteMedication() {
        val idx = editorMeds.indexOfFirst { it.name == selectedMed }
        if (idx >= 0) editorMeds.removeAt(idx)
        selectedMed = editorMeds.firstOrNull()?.name
        selectedUseCase = allLabel
        selectedRoute = allLabel
    }

    fun deleteUseCase() {
        val med = currentMed() ?: return
        val uc = selectedUseCase.takeIf { it != allLabel } ?: return
        med.useCaseToRoutes.remove(uc)
        selectedUseCase = allLabel
        selectedRoute = allLabel
    }

    fun deleteRoute() {
        val med = currentMed() ?: return
        val uc = selectedUseCase.takeIf { it != allLabel } ?: return
        val routes = med.useCaseToRoutes[uc] ?: return
        routes.removeAll { it == selectedRoute }
        selectedRoute = allLabel
    }

    // --- Rules editor state (per med/useCase/route) ---
    val rulesState = remember { mutableStateMapOf<String, SnapshotStateList<EditorRule>>() }
    fun rulesKey(m: String, uc: String, r: String) = "$m|$uc|$r"

    fun getOrSeedRulesList(m: String, uc: String, r: String): SnapshotStateList<EditorRule> {
        val key = rulesKey(m, uc, r)
        return rulesState.getOrPut(key) {
            val medObj = MedicationRepository.getMedicationByName(m)
            val ucKey = useCaseKeyFromLabel(uc)
            val seeded = medObj?.dosing
                ?.filter { it.useCase == ucKey && (r == "Alle" || it.route?.equals(r, true) == true) }
                ?.map {
                    EditorRule(
                        useCase = it.useCase,
                        route = it.route,
                        ageMinYears = it.ageMinYears,
                        ageMaxYears = it.ageMaxYears,
                        weightMinKg = it.weightMinKg,
                        weightMaxKg = it.weightMaxKg,
                        doseMgPerKg = it.doseMgPerKg,
                        fixedDoseMg = it.fixedDoseMg,
                        recommendedConcMgPerMl = it.recommendedConcMgPerMl,
                        solutionText = it.solutionText,
                        totalPreparedMl = it.totalPreparedMl,
                        maxDoseMg = it.maxDoseMg,
                        maxDoseText = it.maxDoseText,
                        note = it.note
                    )
                } ?: emptyList()
            mutableStateListOf<EditorRule>().apply { addAll(seeded) }
        }
    }

    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editor") },
                actions = {
                    TextButton(onClick = onClose) { Text("Schließen") }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { /* TODO: discard changes */ }) { Text("Verwerfen") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { /* TODO: save changes via JsonStore */ }) { Text("Speichern") }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(16.dp)
        ) {
            // --- Kontext-Bar: Auswahl & CRUD-Buttons (Buttons UNTER den Feldern) ---
            ContextBar(
                meds = editorMeds.map { it.name }.sorted(),
                selectedMed = selectedMed,
                onMedChange = { selectedMed = it; selectedUseCase = allLabel; selectedRoute = allLabel },
                onMedNew = { showNewMedDialog = true },
                onMedRename = { if (selectedMed != null) showRenameMedDialog = true },
                onMedDelete = { if (selectedMed != null) showDeleteMedConfirm = true },

                useCases = listOf(allLabel) + currentUseCases(),
                selectedUseCase = selectedUseCase,
                onUseCaseChange = { selectedUseCase = it; selectedRoute = allLabel },
                onUseCaseNew = { if (selectedMed != null) showNewUseCaseDialog = true },
                onUseCaseRename = { if (selectedUseCase != allLabel) showRenameUseCaseDialog = true },
                onUseCaseDelete = { if (selectedUseCase != allLabel) showDeleteUseCaseConfirm = true },

                routes = listOf(allLabel) + (if (selectedUseCase == allLabel) emptyList() else currentRoutes()),
                selectedRoute = selectedRoute,
                onRouteChange = { selectedRoute = it },
                onRouteNew = { if (selectedMed != null && selectedUseCase != allLabel) showNewRouteDialog = true },
                onRouteRename = { if (selectedRoute != allLabel) showRenameRouteDialog = true },
                onRouteDelete = { if (selectedRoute != allLabel) showDeleteRouteConfirm = true }
            )

            Spacer(Modifier.height(12.dp))

            // --- Abschnitte ---
            SectionCard(title = "Ampullenkonzentration") {
                val medObj = selectedMed?.let { MedicationRepository.getMedicationByName(it) }
                val display = remember(medObj) {
                    val c = medObj?.defaultConcentration
                    if (c == null) "—" else {
                        val mg = c.mg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
                        val ml = c.ml?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
                        when {
                            mg != null && ml != null -> "${'$'}mg mg / ${'$'}ml ml"
                            c.mgPerMl != null -> "${'$'}{c.mgPerMl} mg/ml"
                            else -> "—"
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = display,
                        onValueChange = { /* TODO: bind to editor model */ },
                        label = { Text("Anzeige (z. B. 1 mg / 1 ml)") },
                        modifier = Modifier.weight(1f),
                        readOnly = true
                    )
                }
            }

            // --- Regeln ---
            RulesSection(
                selectedMed = selectedMed,
                selectedUseCase = selectedUseCase,
                selectedRoute = selectedRoute,
                getRulesList = { m, uc, r -> getOrSeedRulesList(m, uc, r) }
            )

            // --- Info-Texte (read-only placeholder) ---
            val info = remember(selectedMed) { selectedMed?.let { MedicationRepository.getInfoSections(it) } }
            SectionCard(title = "Indikation") {
                OutlinedTextField(
                    value = info?.indication ?: "",
                    onValueChange = { /* TODO bind */ },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("Indikation") }
                )
            }
            SectionCard(title = "Kontraindikation") {
                OutlinedTextField(
                    value = info?.contraindication ?: "",
                    onValueChange = { /* TODO bind */ },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("Kontraindikation") }
                )
            }
            SectionCard(title = "Wirkung") {
                OutlinedTextField(
                    value = info?.effect ?: "",
                    onValueChange = { /* TODO bind */ },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("Wirkung") }
                )
            }
            SectionCard(title = "Nebenwirkungen") {
                OutlinedTextField(
                    value = info?.sideEffects ?: "",
                    onValueChange = { /* TODO bind */ },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("Nebenwirkungen") }
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }

    // --- Create dialogs ---
    if (showNewMedDialog) NameInputDialog(
        title = "Neues Medikament",
        onConfirm = { addMedication(it); showNewMedDialog = false },
        onDismiss = { showNewMedDialog = false }
    )
    if (showNewUseCaseDialog) NameInputDialog(
        title = "Neuer Einsatzfall",
        onConfirm = { addUseCase(it); showNewUseCaseDialog = false },
        onDismiss = { showNewUseCaseDialog = false }
    )
    if (showNewRouteDialog) NameInputDialog(
        title = "Neue Applikationsart",
        onConfirm = { addRoute(it); showNewRouteDialog = false },
        onDismiss = { showNewRouteDialog = false }
    )

    // --- Rename dialogs ---
    if (showRenameMedDialog) NameInputDialog(
        title = "Medikament umbenennen",
        initial = selectedMed ?: "",
        onConfirm = { renameMedication(it); showRenameMedDialog = false },
        onDismiss = { showRenameMedDialog = false }
    )
    if (showRenameUseCaseDialog) NameInputDialog(
        title = "Einsatzfall umbenennen",
        initial = selectedUseCase,
        onConfirm = { renameUseCase(it); showRenameUseCaseDialog = false },
        onDismiss = { showRenameUseCaseDialog = false }
    )
    if (showRenameRouteDialog) NameInputDialog(
        title = "Applikationsart umbenennen",
        initial = selectedRoute,
        onConfirm = { renameRoute(it); showRenameRouteDialog = false },
        onDismiss = { showRenameRouteDialog = false }
    )

    // --- Delete confirms ---
    if (showDeleteMedConfirm) ConfirmDialog(
        title = "Medikament löschen?",
        text = "Dies entfernt auch alle zugehörigen Einsatzfälle und Routen (im Editor‑State).",
        onConfirm = { deleteMedication(); showDeleteMedConfirm = false },
        onDismiss = { showDeleteMedConfirm = false }
    )
    if (showDeleteUseCaseConfirm) ConfirmDialog(
        title = "Einsatzfall löschen?",
        text = "Dies entfernt auch alle zugehörigen Routen (im Editor‑State).",
        onConfirm = { deleteUseCase(); showDeleteUseCaseConfirm = false },
        onDismiss = { showDeleteUseCaseConfirm = false }
    )
    if (showDeleteRouteConfirm) ConfirmDialog(
        title = "Applikationsart löschen?",
        text = null,
        onConfirm = { deleteRoute(); showDeleteRouteConfirm = false },
        onDismiss = { showDeleteRouteConfirm = false }
    )
}

// ---- Kontext-Bar -----------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextBar(
    meds: List<String>,
    selectedMed: String?,
    onMedChange: (String?) -> Unit,
    onMedNew: () -> Unit,
    onMedRename: () -> Unit,
    onMedDelete: () -> Unit,

    useCases: List<String>,
    selectedUseCase: String,
    onUseCaseChange: (String) -> Unit,
    onUseCaseNew: () -> Unit,
    onUseCaseRename: () -> Unit,
    onUseCaseDelete: () -> Unit,

    routes: List<String>,
    selectedRoute: String,
    onRouteChange: (String) -> Unit,
    onRouteNew: () -> Unit,
    onRouteRename: () -> Unit,
    onRouteDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Medikament (volle Feldbreite)
        Row(Modifier.fillMaxWidth()) {
            ExposedDropdown(
                label = "Medikament",
                options = meds,
                selected = selectedMed,
                onSelected = onMedChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onMedNew) { Text("Neu") }
            OutlinedButton(onClick = onMedRename, enabled = selectedMed != null) { Text("Umben.") }
            OutlinedButton(onClick = onMedDelete, enabled = selectedMed != null) { Text("Löschen") }
        }

        // Einsatzfall (gleich breit)
        Row(Modifier.fillMaxWidth()) {
            ExposedDropdown(
                label = "Einsatzfall",
                options = useCases,
                selected = selectedUseCase,
                onSelected = { onUseCaseChange(it ?: "Alle") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUseCaseNew) { Text("Neu") }
            OutlinedButton(onClick = onUseCaseRename, enabled = selectedUseCase != "Alle") { Text("Umben.") }
            OutlinedButton(onClick = onUseCaseDelete, enabled = selectedUseCase != "Alle") { Text("Löschen") }
        }

        // Applikationsart (unter Einsatzfall, gleich breit)
        Row(Modifier.fillMaxWidth()) {
            ExposedDropdown(
                label = "Applikationsart",
                options = routes,
                selected = selectedRoute,
                onSelected = { onRouteChange(it ?: "Alle") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRouteNew, enabled = selectedUseCase != "Alle") { Text("Neu") }
            OutlinedButton(onClick = onRouteRename, enabled = selectedRoute != "Alle" && selectedUseCase != "Alle") { Text("Umben.") }
            OutlinedButton(onClick = onRouteDelete, enabled = selectedRoute != "Alle" && selectedUseCase != "Alle") { Text("Löschen") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var text by remember(selected) { mutableStateOf(selected ?: "") }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        expanded = false
                        text = opt
                        onSelected(opt)
                    }
                )
            }
        }
    }
}

// ---- Regeln ----------------------------------------------------------------
@Composable
private fun RulesSection(
    selectedMed: String?,
    selectedUseCase: String,
    selectedRoute: String,
    getRulesList: (String, String, String) -> SnapshotStateList<EditorRule>
) {
    val med = selectedMed
    val uc = selectedUseCase
    val r = selectedRoute

    SectionCard(title = "Regeln") {
        if (med == null || uc == "Alle" || r == "Alle") {
            Text("Bitte Medikament, Einsatzfall und Applikationsart wählen, um Regeln zu bearbeiten.")
        } else {
            val rules = remember(med, uc, r) { getRulesList(med, uc, r) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    // Neue leere Regel mit Defaults
                    val ucKey = useCaseKeyFromLabel(uc) ?: return@OutlinedButton
                    rules.add(
                        EditorRule(useCase = ucKey, route = r)
                    )
                }) { Text("Neu") }

                if (rules.isEmpty()) {
                    Text("Keine Regeln vorhanden.")
                } else {
                    rules.forEachIndexed { idx, rule ->
                        RuleCard(index = idx + 1, rule = rule,
                            onDuplicate = { rules.add(idx + 1, rule.copy()) },
                            onDelete = { rules.removeAt(idx) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleCard(index: Int, rule: EditorRule, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var ageMinText by remember(rule) { mutableStateOf(rule.ageMinYears?.toString() ?: "") }
    var ageMaxText by remember(rule) { mutableStateOf(rule.ageMaxYears?.toString() ?: "") }
    var wtMinText by remember(rule) { mutableStateOf(rule.weightMinKg?.toString() ?: "") }
    var wtMaxText by remember(rule) { mutableStateOf(rule.weightMaxKg?.toString() ?: "") }
    var dosePerKgText by remember(rule) { mutableStateOf(rule.doseMgPerKg?.toString() ?: "") }
    var fixedDoseText by remember(rule) { mutableStateOf(rule.fixedDoseMg?.toString() ?: "") }
    var concText by remember(rule) { mutableStateOf(rule.recommendedConcMgPerMl?.toString() ?: "") }
    var totalMlText by remember(rule) { mutableStateOf(rule.totalPreparedMl?.toString() ?: "") }
    var solutionText by remember(rule) { mutableStateOf(rule.solutionText ?: "") }
    var maxDoseTextVal by remember(rule) { mutableStateOf(rule.maxDoseMg?.toString() ?: "") }
    var maxDoseFreeText by remember(rule) { mutableStateOf(rule.maxDoseText ?: "") }
    var noteText by remember(rule) { mutableStateOf(rule.note ?: "") }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            val routeLabel = rule.route ?: "(alle Routen)"
            Text("Regel $index — ${rule.useCase} / $routeLabel", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            // Geltungsbereich
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = ageMinText, onValueChange = { ageMinText = it; rule.ageMinYears = it.toIntOrNull() }, label = { Text("Alter min (J)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = ageMaxText, onValueChange = { ageMaxText = it; rule.ageMaxYears = it.toIntOrNull() }, label = { Text("Alter max (J)") }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = wtMinText, onValueChange = { wtMinText = it; rule.weightMinKg = it.toDoubleOrNull() }, label = { Text("Gewicht min (kg)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = wtMaxText, onValueChange = { wtMaxText = it; rule.weightMaxKg = it.toDoubleOrNull() }, label = { Text("Gewicht max (kg)") }, modifier = Modifier.weight(1f))
            }
            // Dosierung
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = dosePerKgText, onValueChange = { dosePerKgText = it; rule.doseMgPerKg = it.toDoubleOrNull(); if (it.isNotBlank()) { fixedDoseText = ""; rule.fixedDoseMg = null } }, label = { Text("doseMgPerKg (mg/kg)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = fixedDoseText, onValueChange = { fixedDoseText = it; rule.fixedDoseMg = it.toDoubleOrNull(); if (it.isNotBlank()) { dosePerKgText = ""; rule.doseMgPerKg = null } }, label = { Text("fixedDoseMg (mg)") }, modifier = Modifier.weight(1f))
            }
            // Lösung / Verdünnung
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = concText, onValueChange = { concText = it; rule.recommendedConcMgPerMl = it.toDoubleOrNull() }, label = { Text("empf. Konz. (mg/ml)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = totalMlText, onValueChange = { totalMlText = it; rule.totalPreparedMl = it.toDoubleOrNull() }, label = { Text("totalPreparedMl (ml)") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = solutionText, onValueChange = { solutionText = it; rule.solutionText = it }, label = { Text("solutionText") }, modifier = Modifier.fillMaxWidth())
            // Maximaldosis
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = maxDoseTextVal, onValueChange = { maxDoseTextVal = it; rule.maxDoseMg = it.toDoubleOrNull() }, label = { Text("maxDoseMg (mg)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = maxDoseFreeText, onValueChange = { maxDoseFreeText = it; rule.maxDoseText = it }, label = { Text("maxDoseText") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = noteText, onValueChange = { noteText = it; rule.note = it }, label = { Text("Hinweis (note)") }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDuplicate) { Text("Duplizieren") }
                OutlinedButton(onClick = onDelete) { Text("Löschen") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { /* TODO: verschieben */ }, enabled = false) { Text("Verschieben…") }
            }
        }
    }
}

// ---- UI helpers ------------------------------------------------------------
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun NameInputDialog(title: String, initial: String = "", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { Button(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { if (text != null) Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
