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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rdinfo.ui.theme.RDInfoTheme
import com.rdinfo.data.JsonStore
import com.rdinfo.data.MedicationRepository
import com.rdinfo.data.UseCaseKey
import com.rdinfo.data.useCaseKeyFromLabel
import com.rdinfo.data.useCaseLabel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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

    // --- Snackbar (Feedback) ---
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
        // nur vordefinierte Einsatzfälle zulassen (Enum-basiert)
        if (useCaseKeyFromLabel(label) == null) {
            // Feedback
            scope.launch { snackbar.showSnackbar("Unbekannter Einsatzfall: $label") }
            return
        }
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
        if (useCaseKeyFromLabel(newLabel) == null) return
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
                ?.filter { it.useCase == ucKey && it.route?.equals(r, true) == true }
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
                Button(onClick = {
                    // Persistenz: JSON schreiben und Repository neu laden
                    val json = buildJsonFromEditor(editorMeds, rulesState)
                    val result = JsonStore.writeWithBackup(ctx, json)
                    if (result.isSuccess) {
                        // Sofort in den laufenden Prozess laden UND Lazy-Loader beibehalten
                        MedicationRepository.loadFromJsonString(json)
                        MedicationRepository.setLazyJsonLoader { JsonStore.readWithAssetsFallback(ctx, ctx.assets) }
                        scope.launch { snackbar.showSnackbar("Gespeichert") }
                    } else {
                        scope.launch { snackbar.showSnackbar("Fehler beim Speichern") }
                    }
                }) { Text("Speichern") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
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
                        val mg = c.mg.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
                        val ml = c.ml.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
                        "$mg mg / $ml ml"
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TopFieldLabel("Anzeige (z. B. 1 mg / 1 ml)")
                    OutlinedTextField(
                        value = display,
                        onValueChange = { },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true
                    )
                }
            }

            // --- Regeln ---
            RulesSection(
                selectedMed = selectedMed,
                selectedUseCase = selectedUseCase,
                selectedRoute = selectedRoute,
                getRulesList = { m, uc, r -> getOrSeedRulesList(m, uc, r) },
                allUseCases = { currentUseCases() },
                routesProvider = { uc -> currentMed()?.useCaseToRoutes?.get(uc)?.sorted() ?: emptyList() },
                onMoveRule = { fromUc, fromRoute, toUc, toRoute, index ->
                    val med = selectedMed ?: return@RulesSection
                    val src = getOrSeedRulesList(med, fromUc, fromRoute)
                    if (index !in src.indices) return@RulesSection
                    val item = src.removeAt(index)
                    val target = getOrSeedRulesList(med, toUc, toRoute)
                    item.useCase = useCaseKeyFromLabel(toUc) ?: item.useCase
                    item.route = toRoute
                    target.add(item)
                }
            )

            // --- Info-Texte (read-only placeholder) ---
            val info = remember(selectedMed) { selectedMed?.let { MedicationRepository.getInfoSections(it) } }
            SectionCard(title = "Indikation") {
                TopFieldLabel("Indikation")
                OutlinedTextField(
                    value = info?.indication ?: "",
                    onValueChange = { },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    readOnly = true
                )
            }
            SectionCard(title = "Kontraindikation") {
                TopFieldLabel("Kontraindikation")
                OutlinedTextField(
                    value = info?.contraindication ?: "",
                    onValueChange = { },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    readOnly = true
                )
            }
            SectionCard(title = "Wirkung") {
                TopFieldLabel("Wirkung")
                OutlinedTextField(
                    value = info?.effect ?: "",
                    onValueChange = { },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    readOnly = true
                )
            }
            SectionCard(title = "Nebenwirkungen") {
                TopFieldLabel("Nebenwirkungen")
                OutlinedTextField(
                    value = info?.sideEffects ?: "",
                    onValueChange = { },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    readOnly = true
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
    if (showNewUseCaseDialog) UseCasePickerDialog(
        existing = currentUseCases(),
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
        text = "Dies entfernt auch alle zugehörigen Einsatzfälle und Routen (im Editor-State).",
        onConfirm = { deleteMedication(); showDeleteMedConfirm = false },
        onDismiss = { showDeleteMedConfirm = false }
    )
    if (showDeleteUseCaseConfirm) ConfirmDialog(
        title = "Einsatzfall löschen?",
        text = "Dies entfernt auch alle zugehörigen Routen (im Editor-State).",
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
                topLabel = "Medikament",
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
                topLabel = "Einsatzfall",
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
                topLabel = "Applikationsart",
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
    topLabel: String,
    options: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var text by remember(selected) { mutableStateOf(selected ?: "") }

    Column(modifier) {
        TopFieldLabel(topLabel)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                readOnly = true,
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
}

// ---- Regeln ----------------------------------------------------------------
@Composable
private fun RulesSection(
    selectedMed: String?,
    selectedUseCase: String,
    selectedRoute: String,
    getRulesList: (String, String, String) -> SnapshotStateList<EditorRule>,
    allUseCases: () -> List<String>,
    routesProvider: (String) -> List<String>,
    onMoveRule: (fromUc: String, fromRoute: String, toUc: String, toRoute: String, index: Int) -> Unit
) {
    val med = selectedMed
    val uc = selectedUseCase
    val r = selectedRoute

    SectionCard(title = "Regeln") {
        if (med == null || uc == "Alle" || r == "Alle") {
            Text("Bitte Medikament, Einsatzfall und Applikationsart wählen, um Regeln zu bearbeiten.")
        } else {
            val rules = remember(med, uc, r) { getRulesList(med, uc, r) }

            // Move-Dialog State
            var moveIndex by remember { mutableStateOf<Int?>(null) }
            var targetUc by remember { mutableStateOf(uc) }
            var targetRoute by remember(targetUc) { mutableStateOf(r) }

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
                        RuleCard(
                            index = idx + 1,
                            rule = rule,
                            onDuplicate = { rules.add(idx + 1, rule.copy()) },
                            onDelete = { rules.removeAt(idx) },
                            onMove = { moveIndex = idx; targetUc = uc; targetRoute = r }
                        )
                    }
                }

                if (moveIndex != null) {
                    AlertDialog(
                        onDismissRequest = { moveIndex = null },
                        title = { Text("Regel verschieben") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Ziel-Einsatzfall
                                ExposedDropdown(
                                    topLabel = "Ziel: Einsatzfall",
                                    options = allUseCases(),
                                    selected = targetUc,
                                    onSelected = { sel ->
                                        targetUc = sel ?: uc
                                        val routes = routesProvider(targetUc)
                                        targetRoute = routes.firstOrNull() ?: r
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                // Ziel-Route
                                ExposedDropdown(
                                    topLabel = "Ziel: Applikationsart",
                                    options = routesProvider(targetUc),
                                    selected = targetRoute,
                                    onSelected = { sel -> targetRoute = sel ?: r },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                val idx = moveIndex ?: return@Button
                                onMoveRule(uc, r, targetUc, targetRoute, idx)
                                moveIndex = null
                            }) { Text("Verschieben") }
                        },
                        dismissButton = { TextButton(onClick = { moveIndex = null }) { Text("Abbrechen") } }
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    index: Int,
    rule: EditorRule,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
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
            Text("Regel $index — ${useCaseLabel(rule.useCase)} / $routeLabel", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            // Geltungsbereich
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledNumberField(label = "Alter min (J)", value = ageMinText, onValueChange = { ageMinText = it; rule.ageMinYears = it.toIntOrNull() }, modifier = Modifier.weight(1f))
                LabeledNumberField(label = "Alter max (J)", value = ageMaxText, onValueChange = { ageMaxText = it; rule.ageMaxYears = it.toIntOrNull() }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledNumberField(label = "Gewicht min (kg)", value = wtMinText, onValueChange = { wtMinText = it; rule.weightMinKg = it.replace(',', '.').toDoubleOrNull() }, modifier = Modifier.weight(1f))
                LabeledNumberField(label = "Gewicht max (kg)", value = wtMaxText, onValueChange = { wtMaxText = it; rule.weightMaxKg = it.replace(',', '.').toDoubleOrNull() }, modifier = Modifier.weight(1f))
            }
            // Dosierung
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledNumberField(label = "doseMgPerKg (mg/kg)", value = dosePerKgText, onValueChange = {
                    dosePerKgText = it; rule.doseMgPerKg = it.replace(',', '.').toDoubleOrNull(); if (it.isNotBlank()) { fixedDoseText = ""; rule.fixedDoseMg = null }
                }, modifier = Modifier.weight(1f))
                LabeledNumberField(label = "fixedDoseMg (mg)", value = fixedDoseText, onValueChange = {
                    fixedDoseText = it; rule.fixedDoseMg = it.replace(',', '.').toDoubleOrNull(); if (it.isNotBlank()) { dosePerKgText = ""; rule.doseMgPerKg = null }
                }, modifier = Modifier.weight(1f))
            }
            // Lösung / Verdünnung
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledNumberField(label = "empf. Konz. (mg/ml)", value = concText, onValueChange = { concText = it; rule.recommendedConcMgPerMl = it.replace(',', '.').toDoubleOrNull() }, modifier = Modifier.weight(1f))
                LabeledNumberField(label = "totalPreparedMl (ml)", value = totalMlText, onValueChange = { totalMlText = it; rule.totalPreparedMl = it.replace(',', '.').toDoubleOrNull() }, modifier = Modifier.weight(1f))
            }
            LabeledTextField(label = "solutionText", value = solutionText, onValueChange = { solutionText = it; rule.solutionText = it }, modifier = Modifier.fillMaxWidth())
            // Maximaldosis
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledNumberField(label = "maxDoseMg (mg)", value = maxDoseTextVal, onValueChange = { maxDoseTextVal = it; rule.maxDoseMg = it.replace(',', '.').toDoubleOrNull() }, modifier = Modifier.weight(1f))
                LabeledTextField(label = "maxDoseText", value = maxDoseFreeText, onValueChange = { maxDoseFreeText = it; rule.maxDoseText = it }, modifier = Modifier.weight(1f))
            }
            LabeledTextField(label = "Hinweis (note)", value = noteText, onValueChange = { noteText = it; rule.note = it }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDuplicate) { Text("Duplizieren") }
                OutlinedButton(onClick = onDelete) { Text("Löschen") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onMove) { Text("Verschieben…") }
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
private fun TopFieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
}

@Composable
private fun LabeledTextField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        TopFieldLabel(label)
        OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LabeledNumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        TopFieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NameInputDialog(title: String, initial: String = "", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LabeledTextField(label = "Name", value = text, onValueChange = { text = it })
        },
        confirmButton = { Button(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun UseCasePickerDialog(existing: List<String>, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val all = remember { UseCaseKey.values().map { useCaseLabel(it) } }
    val options = remember(existing) { all.filterNot { ex -> existing.any { it.equals(ex, true) } } }
    var selected by remember { mutableStateOf(options.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuer Einsatzfall") },
        text = {
            if (options.isEmpty()) {
                Text("Alle Einsatzfälle sind bereits vorhanden.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { opt ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RadioButton(selected = (opt == selected), onClick = { selected = opt })
                            Text(opt)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { selected?.let(onConfirm) }, enabled = !selected.isNullOrBlank()) { Text("OK") }
        },
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

// ---- Persistenz/JSON -------------------------------------------------------
private fun slugifyId(name: String): String = name.lowercase()
    .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    .replace(Regex("[^a-z0-9]+"), "-").trim('-')

private fun buildJsonFromEditor(
    meds: List<EditorMed>,
    rulesState: Map<String, SnapshotStateList<EditorRule>>
): String {
    fun rulesForMedName(name: String): List<com.rdinfo.data.DosingRule> {
        val repoMed = MedicationRepository.getMedicationByName(name)
        val base = repoMed?.dosing?.toMutableList() ?: mutableListOf()
        rulesState.forEach { (key, list) ->
            val parts = key.split("|"); if (parts.size != 3) return@forEach
            val (m, ucLabel, route) = parts; if (m != name) return@forEach
            val uc = useCaseKeyFromLabel(ucLabel) ?: return@forEach
            base.removeAll { it.useCase == uc && (it.route ?: "") == route }
            list.forEach { er ->
                base.add(
                    com.rdinfo.data.DosingRule(
                        useCase = er.useCase,
                        ageMinYears = er.ageMinYears,
                        ageMaxYears = er.ageMaxYears,
                        weightMinKg = er.weightMinKg,
                        weightMaxKg = er.weightMaxKg,
                        doseMgPerKg = er.doseMgPerKg,
                        fixedDoseMg = er.fixedDoseMg,
                        maxDoseMg = er.maxDoseMg,
                        maxDoseText = er.maxDoseText,
                        recommendedConcMgPerMl = er.recommendedConcMgPerMl,
                        route = er.route,
                        note = er.note,
                        solutionText = er.solutionText,
                        totalPreparedMl = er.totalPreparedMl
                    )
                )
            }
        }
        return base
    }

    val root = JSONObject(); val medsArr = JSONArray()
    meds.forEach { m ->
        val repoMed = MedicationRepository.getMedicationByName(m.name)
        val medObj = JSONObject().put("id", slugifyId(m.name)).put("name", m.name)
        repoMed?.defaultConcentration?.let { c ->
            medObj.put("defaultConcentration", JSONObject().put("mg", c.mg).put("ml", c.ml).put("display", "${c.mg} mg / ${c.ml} ml"))
        }
        repoMed?.sections?.let { s ->
            medObj.put("sections", JSONObject().apply {
                s.indication?.let { put("indication", it) }
                s.contraindication?.let { put("contraindication", it) }
                s.effect?.let { put("effect", it) }
                s.sideEffects?.let { put("sideEffects", it) }
            })
        }
        val useCaseKeys = linkedSetOf<UseCaseKey>()
        m.useCaseToRoutes.keys.forEach { lbl -> useCaseKeyFromLabel(lbl)?.let(useCaseKeys::add) }
        rulesForMedName(m.name).forEach { useCaseKeys.add(it.useCase) }
        val ucArr = JSONArray(); useCaseKeys.forEach { ucArr.put(ucToString(it)) }; medObj.put("useCases", ucArr)
        val dosingArr = JSONArray()
        rulesForMedName(m.name).forEach { r ->
            dosingArr.put(JSONObject().apply {
                put("useCase", ucToString(r.useCase))
                r.ageMinYears?.let { put("ageMinYears", it) }
                r.ageMaxYears?.let { put("ageMaxYears", it) }
                r.weightMinKg?.let { put("weightMinKg", it) }
                r.weightMaxKg?.let { put("weightMaxKg", it) }
                r.doseMgPerKg?.let { put("doseMgPerKg", it) }
                r.fixedDoseMg?.let { put("fixedDoseMg", it) }
                r.maxDoseMg?.let { put("maxDoseMg", it) }
                r.maxDoseText?.let { put("maxDoseText", it) }
                r.recommendedConcMgPerMl?.let { put("recommendedConcMgPerMl", it) }
                r.route?.let { put("route", it) }
                r.note?.let { put("note", it) }
                r.solutionText?.let { put("solutionText", it) }
                r.totalPreparedMl?.let { put("totalPreparedMl", it) }
            })
        }
        medObj.put("dosing", dosingArr)
        medsArr.put(medObj)
    }
    root.put("medications", medsArr)
    return root.toString(2)
}

private fun ucToString(key: UseCaseKey): String = key.name
