// File: app/src/main/java/com/rdinfo/editor/MedicationEditorActivity.kt
package com.rdinfo.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import kotlin.math.max
import com.rdinfo.prefs.ThemePrefs
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding


// ------------ Datenmodelle (Editor) -----------------------------------------
private data class EditorMed(
    var name: String,
    var defaultMg: Double?,
    var defaultMl: Double?,
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

private sealed class EditorDialog {
    data class Name(val title: String, val initial: String, val onOk: (String) -> Unit) : EditorDialog()
    data class UseCase(val existing: List<String>, val onOk: (String) -> Unit) : EditorDialog()
    data class Confirm(val title: String, val message: String, val onYes: () -> Unit, val onNo: () -> Unit) : EditorDialog()
    data class Errors(val errors: List<String>) : EditorDialog()
}

class MedicationEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = LocalContext.current
            val dark = remember { mutableStateOf(ThemePrefs.get(ctx)) }

            RDInfoTheme(darkTheme = dark.value) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // Safe-Area, damit nichts in Notch/Navigation rutscht:
                    Box(Modifier.statusBarsPadding().navigationBarsPadding()) {
                        EditorScreen(onClose = { finish() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ------ Dirty-State (Unsaved-Changes-Guard)
    var dirty by remember { mutableStateOf(false) }
    fun markDirty() { dirty = true }

    // Back-Button abfangen
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    BackHandler(enabled = dirty) {
        dialog = EditorDialog.Confirm(
            title = "Ungespeicherte Änderungen",
            message = "Möchtest du die Änderungen verwerfen?",
            onYes = { dirty = false; onClose() },
            onNo = { /* noop */ }
        )
    }

    // Seed: Repository → EditorMed
    val editorMeds = remember {
        val meds = MedicationRepository.getMedicationNames()
        mutableStateListOf<EditorMed>().apply {
            meds.forEach { mName ->
                val med = MedicationRepository.getMedicationByName(mName)
                val ucLabels = MedicationRepository.getUseCaseNamesForMedication(mName)
                val map = mutableMapOf<String, MutableList<String>>()
                ucLabels.forEach { uc ->
                    val routes = MedicationRepository.getRouteNamesForMedicationUseCase(mName, uc)
                    map[uc] = routes.toMutableList()
                }
                add(
                    EditorMed(
                        name = mName,
                        defaultMg = med?.defaultConcentration?.mg,
                        defaultMl = med?.defaultConcentration?.ml,
                        useCaseToRoutes = map
                    )
                )
            }
        }
    }

    var selectedMed by remember { mutableStateOf(editorMeds.firstOrNull()?.name) }
    val allLabel = "Alle"
    var selectedUseCase by remember(selectedMed) { mutableStateOf(allLabel) }
    var selectedRoute by remember(selectedMed, selectedUseCase) { mutableStateOf(allLabel) }

    fun currentMed(): EditorMed? = editorMeds.firstOrNull { it.name == selectedMed }
    fun currentUseCases(): List<String> = currentMed()?.useCaseToRoutes?.keys?.sorted() ?: emptyList()
    fun currentRoutes(): List<String> = currentMed()?.useCaseToRoutes?.get(selectedUseCase)?.sorted() ?: emptyList()

    // Regel-State
    val rulesState = remember { mutableStateMapOf<String, SnapshotStateList<EditorRule>>() }
    fun rulesKey(m: String, uc: String, r: String) = "$m|$uc|$r"
    fun rulesList(m: String, uc: String, r: String): SnapshotStateList<EditorRule> {
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
                title = { Text(if (dirty) "Editor *" else "Editor") },
                actions = {
                    TextButton(onClick = {
                        if (dirty) {
                            dialog = EditorDialog.Confirm(
                                title = "Ungespeicherte Änderungen",
                                message = "Möchtest du die Änderungen verwerfen?",
                                onYes = { dirty = false; onClose() },
                                onNo = { }
                            )
                        } else onClose()
                    }) { Text("Schließen") }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    if (dirty) {
                        dialog = EditorDialog.Confirm(
                            title = "Ungespeicherte Änderungen",
                            message = "Möchtest du die Änderungen verwerfen?",
                            onYes = { dirty = false; onClose() },
                            onNo = { }
                        )
                    } else onClose()
                }) { Text("Abbrechen") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // ---- VALIDIERUNG ----
                    val errors = validateAll(editorMeds, rulesState)
                    if (errors.isNotEmpty()) {
                        dialog = EditorDialog.Errors(errors)
                        return@Button
                    }

                    val json = buildJsonFromEditor(editorMeds, rulesState)
                    val result = JsonStore.writeWithBackup(ctx, json)
                    if (result.isSuccess) {
                        MedicationRepository.loadFromJsonString(json)
                        MedicationRepository.setLazyJsonLoader { JsonStore.readWithAssetsFallback(ctx, ctx.assets) }
                        dirty = false
                        scope.launch { snackbar.showSnackbar("Gespeichert") }
                    } else scope.launch { snackbar.showSnackbar("Fehler beim Speichern") }
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
            ContextBar(
                meds = editorMeds.map { it.name }.sorted(),
                selectedMed = selectedMed,
                onMedChange = { selectedMed = it; selectedUseCase = allLabel; selectedRoute = allLabel },

                // 1) „Neu“ (Medikament): jetzt mit Namensdialog (kein Auto-„Neues Medikament“)
                onMedNew = {
                    dialog = EditorDialog.Name("Neues Medikament", "") { newName ->
                        val name = newName.trim()
                        if (name.isEmpty()) return@Name
                        if (editorMeds.any { it.name.equals(name, true) }) {
                            // Duplikat: still schweigen/ignorieren
                            selectedMed = name
                            return@Name
                        }
                        editorMeds.add(EditorMed(name, null, null, mutableMapOf()))
                        selectedMed = name
                        markDirty()
                    }
                },
                onMedRename = {
                    val med = currentMed() ?: return@ContextBar
                    dialog = EditorDialog.Name("Medikament umbenennen", med.name) { newName ->
                        val nm = newName.trim()
                        if (nm.isNotBlank() && editorMeds.none { it.name.equals(nm, true) }) {
                            med.name = nm; selectedMed = nm; markDirty()
                        }
                    }
                },
                onMedDelete = {
                    val idx = editorMeds.indexOfFirst { it.name == selectedMed }
                    if (idx >= 0) editorMeds.removeAt(idx)
                    selectedMed = editorMeds.firstOrNull()?.name
                    selectedUseCase = allLabel; selectedRoute = allLabel
                    markDirty()
                },

                useCases = listOf(allLabel) + currentUseCases(),
                selectedUseCase = selectedUseCase,
                onUseCaseChange = { selectedUseCase = it ?: allLabel; selectedRoute = allLabel },

                // 2) „Neu“ (Einsatzfall): Freitext-Name; wird nur übernommen, wenn er einem bekannten UseCaseKey entspricht.
                onUseCaseNew = {
                    val med = currentMed() ?: return@ContextBar
                    dialog = EditorDialog.Name("Neuer Einsatzfall", "") { label ->
                        val candidate = label.trim()
                        if (candidate.isEmpty()) return@Name
                        // wenn unbekannt → ignorieren (und leise abbrechen)
                        val ucKey = useCaseKeyFromLabel(candidate) ?: run {
                            scope.launch { snackbar.showSnackbar("Einsatzfall nicht erkannt – bitte Standardbezeichnung verwenden.") }
                            return@Name
                        }
                        // duplikate ignorieren
                        if (med.useCaseToRoutes.keys.any { it.equals(candidate, true) }) return@Name
                        med.useCaseToRoutes[candidate] = mutableListOf()
                        selectedUseCase = candidate
                        selectedRoute = allLabel
                        markDirty()
                    }
                },
                onUseCaseRename = {
                    val med = currentMed() ?: return@ContextBar
                    val uc = selectedUseCase.takeIf { it != allLabel } ?: return@ContextBar
                    dialog = EditorDialog.Name("Einsatzfall umbenennen", uc) { newLabel ->
                        val nl = newLabel.trim()
                        // nur zulassen, wenn bekannte Standardbezeichnung
                        if (nl.isBlank() || useCaseKeyFromLabel(nl) == null) return@Name
                        val routes = med.useCaseToRoutes.remove(uc) ?: mutableListOf()
                        if (med.useCaseToRoutes.keys.none { it.equals(nl, true) }) {
                            med.useCaseToRoutes[nl] = routes; selectedUseCase = nl; markDirty()
                        }
                    }
                },
                onUseCaseDelete = {
                    val med = currentMed() ?: return@ContextBar
                    val uc = selectedUseCase.takeIf { it != allLabel } ?: return@ContextBar
                    med.useCaseToRoutes.remove(uc); selectedUseCase = allLabel; selectedRoute = allLabel
                    markDirty()
                },

                routes = listOf(allLabel) + (if (selectedUseCase == allLabel) emptyList() else currentRoutes()),
                selectedRoute = selectedRoute,
                onRouteChange = { selectedRoute = it ?: allLabel },
                onRouteNew = {
                    val med = currentMed() ?: return@ContextBar
                    val uc = selectedUseCase.takeIf { it != allLabel } ?: return@ContextBar
                    dialog = EditorDialog.Name("Neue Applikationsart", "") { name ->
                        val nm = name.trim()
                        if (nm.isEmpty()) return@Name
                        val list = med.useCaseToRoutes[uc] ?: mutableListOf<String>().also { med.useCaseToRoutes[uc] = it }
                        // 3) Duplikate still ignorieren
                        if (list.any { it.equals(nm, true) }) return@Name
                        list.add(nm); selectedRoute = nm; markDirty()
                    }
                },
                onRouteRename = {
                    val med = currentMed() ?: return@ContextBar
                    val uc = selectedUseCase.takeIf { it != allLabel } ?: return@ContextBar
                    val r = selectedRoute.takeIf { it != allLabel } ?: return@ContextBar
                    dialog = EditorDialog.Name("Applikationsart umbenennen", r) { newName ->
                        val nn = newName.trim()
                        if (nn.isEmpty()) return@Name
                        val list = med.useCaseToRoutes[uc] ?: return@Name
                        if (list.any { it.equals(nn, true) }) return@Name // Duplikat → ignorieren
                        val idx = list.indexOfFirst { it == r }; if (idx >= 0) list[idx] = nn; selectedRoute = nn; markDirty()
                    }
                },
                onRouteDelete = {
                    val med = currentMed() ?: return@ContextBar
                    val uc = selectedUseCase.takeIf { it != allLabel } ?: return@ContextBar
                    val r = selectedRoute.takeIf { it != allLabel } ?: return@ContextBar
                    med.useCaseToRoutes[uc]?.removeAll { it == r }; selectedRoute = allLabel
                    markDirty()
                },

                onShowMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
            )

            Spacer(Modifier.height(12.dp))

            // --- Ampullenkonzentration (editierbar) --------------------------
            SectionCard(title = "Ampullenkonzentration") {
                val med = currentMed()
                var mgText by remember(med?.name) { mutableStateOf(med?.defaultMg?.toString() ?: "") }
                var mlText by remember(med?.name) { mutableStateOf(med?.defaultMl?.toString() ?: "") }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledNumberField(
                        label = "Ampulle mg",
                        value = mgText,
                        onValueChange = {
                            mgText = it; med?.defaultMg = it.replace(',', '.').toDoubleOrNull(); markDirty()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    LabeledNumberField(
                        label = "Ampulle ml",
                        value = mlText,
                        onValueChange = {
                            mlText = it; med?.defaultMl = it.replace(',', '.').toDoubleOrNull(); markDirty()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                val ratio = remember(med?.defaultMg, med?.defaultMl) {
                    val mg = med?.defaultMg; val ml = med?.defaultMl
                    if (mg != null && ml != null && ml > 0.0) mg / ml else null
                }
                if (ratio != null) Text("= ${"%.3f".format(ratio)} mg/ml", style = MaterialTheme.typography.labelSmall)
            }

            // --- Regeln -----------------------------------------------------
            RulesSection(
                selectedMed = selectedMed,
                selectedUseCase = selectedUseCase,
                selectedRoute = selectedRoute,
                getRulesList = { m, uc, r -> rulesList(m, uc, r) },
                allUseCases = { currentUseCases() },
                routesProvider = { uc -> currentMed()?.useCaseToRoutes?.get(uc)?.sorted() ?: emptyList() },
                onMoveRule = { fromUc, fromRoute, toUc, toRoute, index ->
                    val medName = selectedMed ?: return@RulesSection
                    val src = rulesList(medName, fromUc, fromRoute)
                    if (index !in src.indices) return@RulesSection
                    val item = src.removeAt(index)
                    val target = rulesList(medName, toUc, toRoute)
                    useCaseKeyFromLabel(toUc)?.let { item.useCase = it }
                    item.route = toRoute
                    target.add(item)
                    markDirty()
                },
                onAnyChange = { markDirty() }
            )

            Spacer(Modifier.height(48.dp))
        }
    }

    // --- Dialoge rendern ----------------------------------------------------
    when (val d = dialog) {
        is EditorDialog.Name -> NameInputDialog(
            title = d.title,
            initial = d.initial,
            onConfirm = { dialog = null; d.onOk(it) },
            onDismiss = { dialog = null }
        )
        is EditorDialog.UseCase -> UseCasePickerDialog(
            existing = d.existing,
            onConfirm = { dialog = null; d.onOk(it) },
            onDismiss = { dialog = null }
        )
        is EditorDialog.Confirm -> ConfirmDialog(
            title = d.title,
            message = d.message,
            onYes = { dialog = null; d.onYes() },
            onNo = { dialog = null; d.onNo() }
        )
        is EditorDialog.Errors -> ErrorsDialog(d.errors) { dialog = null }
        null -> {}
    }
}

// ------------ Kontext-Leiste (Dropdowns + Buttons) -------------------------
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
    onUseCaseChange: (String?) -> Unit,
    onUseCaseNew: () -> Unit,
    onUseCaseRename: () -> Unit,
    onUseCaseDelete: () -> Unit,

    routes: List<String>,
    selectedRoute: String,
    onRouteChange: (String?) -> Unit,
    onRouteNew: () -> Unit,
    onRouteRename: () -> Unit,
    onRouteDelete: () -> Unit,

    onShowMessage: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        Row(Modifier.fillMaxWidth()) {
            ExposedDropdown(
                topLabel = "Einsatzfall",
                options = useCases,
                selected = selectedUseCase,
                onSelected = onUseCaseChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUseCaseNew) { Text("Neu") }
            OutlinedButton(onClick = onUseCaseRename, enabled = selectedUseCase != "Alle") { Text("Umben.") }
            OutlinedButton(onClick = onUseCaseDelete, enabled = selectedUseCase != "Alle") { Text("Löschen") }
        }

        Row(Modifier.fillMaxWidth()) {
            ExposedDropdown(
                topLabel = "Applikationsart",
                options = routes,
                selected = selectedRoute,
                onSelected = onRouteChange,
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
                onValueChange = { },
                readOnly = true,
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = {
                        expanded = false; text = opt; onSelected(opt)
                    })
                }
            }
        }
    }
}

// ------------ Regeln --------------------------------------------------------
@Composable
private fun RulesSection(
    selectedMed: String?,
    selectedUseCase: String,
    selectedRoute: String,
    getRulesList: (String, String, String) -> SnapshotStateList<EditorRule>,
    allUseCases: () -> List<String>,
    routesProvider: (String) -> List<String>,
    onMoveRule: (fromUc: String, fromRoute: String, toUc: String, toRoute: String, index: Int) -> Unit,
    onAnyChange: () -> Unit
) {
    val med = selectedMed; val uc = selectedUseCase; val r = selectedRoute
    SectionCard(title = "Dosierungsregeln") {
        if (med == null || uc == "Alle" || r == "Alle") {
            Text("Bitte Medikament, Einsatzfall und Applikationsart wählen.")
        } else {
            val rules = remember(med, uc, r) { getRulesList(med, uc, r) }
            OutlinedButton(onClick = {
                val ucKey = useCaseKeyFromLabel(uc) ?: return@OutlinedButton
                rules.add(EditorRule(useCase = ucKey, route = r)); onAnyChange()
            }) { Text("Neu") }
            Spacer(Modifier.height(8.dp))
            if (rules.isEmpty()) Text("Keine Regeln vorhanden.")
            rules.forEachIndexed { idx, rule ->
                RuleCard(
                    index = idx + 1,
                    rule = rule,
                    onDuplicate = { rules.add(idx + 1, rule.copy()); onAnyChange() },
                    onDelete = { rules.removeAt(idx); onAnyChange() },
                    onChanged = onAnyChange
                )
                Spacer(Modifier.height(8.dp))
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
    onChanged: () -> Unit
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
            Text("Regel $index", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledNumberField("Alter min (J)", ageMinText, {
                    ageMinText = it; rule.ageMinYears = it.toIntOrNull(); onChanged()
                }, Modifier.weight(1f))
                LabeledNumberField("Alter max (J)", ageMaxText, {
                    ageMaxText = it; rule.ageMaxYears = it.toIntOrNull(); onChanged()
                }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledNumberField("Gewicht min (kg)", wtMinText, {
                    wtMinText = it; rule.weightMinKg = it.replace(',', '.').toDoubleOrNull(); onChanged()
                }, Modifier.weight(1f))
                LabeledNumberField("Gewicht max (kg)", wtMaxText, {
                    wtMaxText = it; rule.weightMaxKg = it.replace(',', '.').toDoubleOrNull(); onChanged()
                }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledNumberField("doseMgPerKg (mg/kg)", dosePerKgText, {
                    dosePerKgText = it; rule.doseMgPerKg = it.replace(',', '.').toDoubleOrNull()
                    if (it.isNotBlank()) { fixedDoseText = ""; rule.fixedDoseMg = null }
                    onChanged()
                }, Modifier.weight(1f))
                LabeledNumberField("fixedDoseMg (mg)", fixedDoseText, {
                    fixedDoseText = it; rule.fixedDoseMg = it.replace(',', '.').toDoubleOrNull()
                    if (it.isNotBlank()) { dosePerKgText = ""; rule.doseMgPerKg = null }
                    onChanged()
                }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledNumberField("empf. Konz. (mg/ml)", concText, {
                    concText = it; rule.recommendedConcMgPerMl = it.replace(',', '.').toDoubleOrNull(); onChanged()
                }, Modifier.weight(1f))
                LabeledNumberField("totalPreparedMl (ml)", totalMlText, {
                    totalMlText = it; rule.totalPreparedMl = it.replace(',', '.').toDoubleOrNull(); onChanged()
                }, Modifier.weight(1f))
            }
            LabeledTextField("solutionText", solutionText, {
                solutionText = it; rule.solutionText = it; onChanged()
            }, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledNumberField("maxDoseMg (mg)", maxDoseTextVal, {
                    maxDoseTextVal = it; rule.maxDoseMg = it.replace(',', '.').toDoubleOrNull(); onChanged()
                }, Modifier.weight(1f))
                LabeledTextField("maxDoseText", maxDoseFreeText, {
                    maxDoseFreeText = it; rule.maxDoseText = it; onChanged()
                }, Modifier.weight(1f))
            }
            LabeledTextField("Hinweis (note)", noteText, {
                noteText = it; rule.note = it; onChanged()
            }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDuplicate) { Text("Duplizieren") }
                OutlinedButton(onClick = onDelete) { Text("Löschen") }
            }
        }
    }
}

// ------------ UI-Hilfen ----------------------------------------------------
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

// ------------ Dialoge -------------------------------------------------------
@Composable
private fun NameInputDialog(title: String, initial: String = "", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { LabeledTextField(label = "Name", value = text, onValueChange = { text = it }) },
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
            if (options.isEmpty()) Text("Alle Einsatzfälle sind bereits vorhanden.")
            else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { opt ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadioButton(selected = (opt == selected), onClick = { selected = opt })
                        Text(opt)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { selected?.let(onConfirm) }, enabled = !selected.isNullOrBlank()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onYes) { Text("Ja") } },
        dismissButton = { TextButton(onClick = onNo) { Text("Nein") } }
    )
}

@Composable
private fun ErrorsDialog(errors: List<String>, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Bitte korrigieren") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                errors.forEach { e -> Text("• $e") }
            }
        },
        confirmButton = { Button(onClick = onClose) { Text("OK") } }
    )
}

// ------------ Validierung ---------------------------------------------------
private fun validateAll(
    meds: List<EditorMed>,
    rulesState: Map<String, SnapshotStateList<EditorRule>>
): List<String> {
    val errors = mutableListOf<String>()
    val norm: (String) -> String = { it.trim().lowercase() }

    // 1) Medikamente: Name nötig + eindeutig
    val medNames = meds.map { it.name }
    if (medNames.any { it.isBlank() }) errors += "Leere Medikament-Namen sind nicht erlaubt."
    val dupMed = medNames.groupBy { norm(it) }.filter { it.value.size > 1 }.keys
    if (dupMed.isNotEmpty()) errors += "Doppelte Medikament-Namen: ${dupMed.joinToString()}."

    meds.forEach { m ->
        // 2) UseCases je Medikament: eindeutig, kein „Alle“
        val ucs = m.useCaseToRoutes.keys.toList()
        if (ucs.any { it.isBlank() }) errors += "[${m.name}] Einsatzfall-Name darf nicht leer sein."
        if (ucs.any { it.equals("Alle", true) }) errors += "[${m.name}] Einsatzfall-Name „Alle“ ist reserviert."
        val dupUc = ucs.groupBy { norm(it) }.filter { it.value.size > 1 }.keys
        if (dupUc.isNotEmpty()) errors += "[${m.name}] Doppelte Einsatzfälle: ${dupUc.joinToString()}."

        // 3) Routen je UseCase: eindeutig, kein „Alle“
        m.useCaseToRoutes.forEach { (uc, routes) ->
            if (routes.any { it.isBlank() }) errors += "[${m.name} / $uc] Routenname darf nicht leer sein."
            if (routes.any { it.equals("Alle", true) }) errors += "[${m.name} / $uc] Routenname „Alle“ ist reserviert."
            val dupRoutes = routes.groupBy { norm(it) }.filter { it.value.size > 1 }.keys
            if (dupRoutes.isNotEmpty()) errors += "[${m.name} / $uc] Doppelte Routen: ${dupRoutes.joinToString()}."
        }
    }

    // Regeln validieren anhand rulesState
    rulesState.forEach { (key, list) ->
        val parts = key.split("|")
        if (parts.size != 3) return@forEach
        val (medName, ucLabel, route) = parts

        // 4a) Einzelregel-Checks
        list.forEachIndexed { i, r ->
            val idx = i + 1
            val where = "[$medName / $ucLabel / $route] Regel $idx"

            // XOR Dosis
            val hasPerKg = r.doseMgPerKg != null
            val hasFixed = r.fixedDoseMg != null
            if (hasPerKg == hasFixed) errors += "$where: Entweder doseMgPerKg ODER fixedDoseMg angeben."

            // Min/Max Alter
            if (r.ageMinYears != null && r.ageMaxYears != null && r.ageMinYears!! > r.ageMaxYears!!) {
                errors += "$where: Alter min > Alter max."
            }
            // Min/Max Gewicht
            if (r.weightMinKg != null && r.weightMaxKg != null && r.weightMinKg!! > r.weightMaxKg!!) {
                errors += "$where: Gewicht min > Gewicht max."
            }
            // empfohlene Konzentration > 0
            if (r.recommendedConcMgPerMl != null && r.recommendedConcMgPerMl!! <= 0.0) {
                errors += "$where: empfohlene Konzentration muss > 0 sein."
            }
        }

        // 4b) Intervall-Überlappungen
        fun aMin(r: EditorRule) = r.ageMinYears ?: Int.MIN_VALUE
        fun aMax(r: EditorRule) = r.ageMaxYears ?: Int.MAX_VALUE
        fun wMin(r: EditorRule) = r.weightMinKg ?: Double.NEGATIVE_INFINITY
        fun wMax(r: EditorRule) = r.weightMaxKg ?: Double.POSITIVE_INFINITY

        for (i in 0 until list.size) {
            for (j in i + 1 until list.size) {
                val r1 = list[i]; val r2 = list[j]
                val ageOverlap = max(aMin(r1), aMin(r2)) <= kotlin.math.min(aMax(r1), aMax(r2))
                val wtOverlap = kotlin.math.max(wMin(r1), wMin(r2)) <= kotlin.math.min(wMax(r1), wMax(r2))
                if (ageOverlap && wtOverlap) {
                    errors += "[$medName / $ucLabel / $route]: Regeln ${i + 1} und ${j + 1} überschneiden sich (Alter/Gewicht)."
                }
            }
        }
    }

    return errors
}

// ------------ Persistenz: JSON bauen ---------------------------------------
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

        val mg = m.defaultMg ?: repoMed?.defaultConcentration?.mg
        val ml = m.defaultMl ?: repoMed?.defaultConcentration?.ml
        if (mg != null && ml != null) {
            val display = "${trim0(mg)} mg / ${trim0(ml)} ml"
            medObj.put("defaultConcentration", JSONObject().apply {
                put("mg", mg); put("ml", ml); put("mgPerMl", mg / ml); put("display", display)
            })
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
private fun trim0(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
