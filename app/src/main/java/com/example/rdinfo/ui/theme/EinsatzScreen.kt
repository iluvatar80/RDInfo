package com.example.rdinfo.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.rdinfo.data.local.AppDatabase
import com.example.rdinfo.data.local.DoseRuleEntity
import com.example.rdinfo.data.local.DrugEntity
import com.example.rdinfo.data.local.FormulationEntity
import com.example.rdinfo.data.local.UseCaseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun EinsatzScreen() {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }

    // Daten aus Room beobachten
    val drugs by db.drugDao().observeAll().collectAsState(initial = emptyList())

    // Auswahl-States
    var selectedDrug by remember { mutableStateOf<DrugEntity?>(null) }
    var selectedUseCase by remember { mutableStateOf<UseCaseEntity?>(null) }
    var selectedFormulation by remember { mutableStateOf<FormulationEntity?>(null) }

    // abhängige Listen (Dropdowns)
    val useCasesFlow: Flow<List<UseCaseEntity>> =
        selectedDrug?.let { db.useCaseDao().observeByDrug(it.id) } ?: flowOf(emptyList())
    val useCases by useCasesFlow.collectAsState(initial = emptyList())

    val formulationsFlow: Flow<List<FormulationEntity>> =
        selectedDrug?.let { db.formulationDao().observeByDrug(it.id) } ?: flowOf(emptyList())
    val formulations by formulationsFlow.collectAsState(initial = emptyList())

    // ----------------------------- Alter & Gewicht -----------------------------
    var years by remember { mutableStateOf(0) }
    var months by remember { mutableStateOf(0) }

    // Manuelle Konzentration (Ampulle): X ml enthalten Y mg
    var concManual by remember { mutableStateOf(false) }
    var ampMlText by remember { mutableStateOf("") }
    var ampMgText by remember { mutableStateOf("") }

    // Gewichtsvorschlag (APLS‑nah, Monate bis 12 J anteilig)
    val weightSuggestion by remember(years, months) { derivedStateOf { estimateWeight(years, months) } }

    var weightText by remember { mutableStateOf("") }     // Roh-Eingabe
    var weightTouched by remember { mutableStateOf(false) } // schon manuell editiert?

    val weight: Double by remember(weightText, weightSuggestion, weightTouched, years, months) {
        derivedStateOf {
            val hasAge = (years > 0) || (months > 0)
            if (weightTouched) {
                val manual = weightText.replace(',', '.').toDoubleOrNull()
                manual ?: if (hasAge) weightSuggestion else 0.0
            } else {
                if (hasAge) weightSuggestion else 0.0
            }
        }
    }

    // Defaults setzen, sobald Daten da sind
    LaunchedEffect(drugs) { if (drugs.isNotEmpty() && selectedDrug == null) selectedDrug = drugs.first() }
    LaunchedEffect(useCases) {
        if (useCases.isNotEmpty()) {
            val prefer = useCases.firstOrNull { it.name.equals("Reanimation", ignoreCase = true) }
            selectedUseCase = prefer ?: useCases.first()
        }
    }


    // Erlaubte Formulierungen je nach Alter + Einsatzfall bestimmen (ohne Dropdown – auto select)
    LaunchedEffect(selectedDrug?.id, selectedUseCase?.id, years, months, formulations) {
        val drug = selectedDrug ?: return@LaunchedEffect
        val uc = selectedUseCase ?: return@LaunchedEffect
        val age = years * 12 + months
        val rules = withContext(Dispatchers.IO) { db.doseRuleDao().getByDrugAndUseCase(drug.id, uc.id) }
        fun DoseRuleEntity.isAgeOk(): Boolean {
            val minOk = ageMinMonths?.let { age >= it } ?: true
            val maxOk = ageMaxMonths?.let { age <= it } ?: true
            return minOk && maxOk
        }
        val allowedIds = rules.filter { it.isAgeOk() }.mapNotNull { it.formulationId }.toSet()
        val list = if (allowedIds.isNotEmpty()) formulations.filter { it.id in allowedIds } else formulations
        selectedFormulation = list.firstOrNull() // automatisch wählen
    }

    // Regel laden (alters- & route-sensitiv)
    var appliedRule by remember { mutableStateOf<DoseRuleEntity?>(null) }
    LaunchedEffect(selectedDrug?.id, selectedUseCase?.id, selectedFormulation?.id, years, months) {
        val drug = selectedDrug ?: return@LaunchedEffect
        val uc = selectedUseCase ?: return@LaunchedEffect
        val form = selectedFormulation
        val ageMonthsTotal = years * 12 + months
        appliedRule = withContext(Dispatchers.IO) {
            db.doseRuleDao().pickByDrugUseCaseFormulationAndAge(
                drugId = drug.id,
                useCaseId = uc.id,
                formulationId = form?.id,
                ageMonths = ageMonthsTotal
            ) ?: run {
                val rules = db.doseRuleDao().getByDrugAndUseCase(drug.id, uc.id)
                rules.firstOrNull { it.formulationId != null && it.formulationId == form?.id } ?: rules.firstOrNull()
            }
        }
    }

    // Beim Wechsel der Formulierung Standardwerte in die manuellen Felder übernehmen (solange manuell AUS)
    LaunchedEffect(selectedFormulation?.id) {
        if (!concManual) {
            val base = selectedFormulation?.concentrationMgPerMl ?: 0.0
            if (base > 0) {
                ampMlText = "1"
                ampMgText = formatTrim(base)
            } else {
                ampMlText = ""
                ampMgText = ""
            }
        }
    }

    // Berechnung → effektive Konzentration ggf. aus manueller Ampulle ableiten
    val effectiveConcentration by remember(selectedFormulation?.concentrationMgPerMl, concManual, ampMlText, ampMgText) {
        derivedStateOf {
            val base = selectedFormulation?.concentrationMgPerMl ?: 0.0
            if (!concManual) return@derivedStateOf base
            val ml = ampMlText.replace(',', '.').toDoubleOrNull() ?: 0.0
            val mg = ampMgText.replace(',', '.').toDoubleOrNull() ?: 0.0
            if (ml > 0.0 && mg > 0.0) mg / ml else base
        }
    }

    val result = remember(appliedRule, selectedFormulation, weight, effectiveConcentration) {
        calcDose(
            rule = appliedRule,
            formulation = selectedFormulation,
            weightKg = weight,
            defaultRoundingMl = 0.1,
            overrideConcentrationMgPerMl = effectiveConcentration
        )
    }

    // Info-Tabs (nur einer offen)
    var activeInfo by remember { mutableStateOf(InfoSection.IND) }

    val accent = MaterialTheme.colorScheme.error
    val outline = MaterialTheme.colorScheme.outlineVariant
    val cardShape = RoundedCornerShape(8.dp)
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Kopfzeile
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilledTonalButton(
                onClick = { },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
            ) { Text("Medikamente", style = MaterialTheme.typography.labelLarge) }

            OutlinedButton(
                onClick = { },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                border = BorderStroke(1.dp, accent)
            ) { Text("Werte", style = MaterialTheme.typography.labelLarge) }
        }

        Divider()

        // Alter
        Text("Jahre: $years", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = years.toFloat(),
            onValueChange = { years = it.roundToInt().coerceIn(0, 100) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.3f),
                thumbColor = accent
            ),
            modifier = Modifier.fillMaxWidth().height(14.dp)
        )
        Divider(thickness = 1.dp, color = accent.copy(alpha = 0.6f))

        Text("Monate: $months", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = months.toFloat(),
            onValueChange = { months = it.roundToInt().coerceIn(0, 11) },
            valueRange = 0f..11f,
            colors = SliderDefaults.colors(
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.3f),
                thumbColor = accent
            ),
            modifier = Modifier.fillMaxWidth().height(14.dp)
        )
        Divider(thickness = 1.dp, color = accent.copy(alpha = 0.6f))

        // Gewicht
        val showSuggestion = (years > 0) || (months > 0)
        OutlinedTextField(
            value = weightText,
            onValueChange = { raw ->
                val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
                val oneDot = buildString {
                    var sawDot = false
                    for (ch in cleaned) {
                        if (ch == '.') { if (!sawDot) { append(ch); sawDot = true } } else append(ch)
                    }
                }
                val newText = oneDot.take(6)
                weightText = newText
                weightTouched = newText.isNotBlank()
            },
            label = { Text("Gewicht (kg)") },
            supportingText = { if (showSuggestion) Text("Vorschlag: ${format1(weightSuggestion)} kg") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
        )

        // Medikament
        Selector(
            label = "Medikament",
            value = selectedDrug?.name ?: if (drugs.isEmpty()) "Keine Daten" else "Medikament wählen",
            options = drugs.map { it.name },
            enabled = drugs.isNotEmpty(),
            onPick = { name ->
                selectedDrug = drugs.firstOrNull { it.name == name }
                selectedUseCase = null
                if (weightText.isBlank()) weightTouched = false
            }
        )

        // Einsatzfall
        Selector(
            label = "Einsatzfall",
            value = selectedUseCase?.name ?: if (useCases.isEmpty()) "—" else "Use-Case wählen",
            options = useCases.map { it.name },
            enabled = useCases.isNotEmpty(),
            onPick = { n -> selectedUseCase = useCases.firstOrNull { it.name == n } }
        )

        // --- Ampullenkonzentration (statt Applikationsform) ---
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, outline), cardShape),
            shape = cardShape
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ampullenkonzentration", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Manuell", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = concManual, onCheckedChange = { concManual = it })
                    }
                }

                if (!concManual) {
                    val base = selectedFormulation?.concentrationMgPerMl ?: 0.0
                    val line = if (base > 0) "1 Ampulle (1 ml) = ${formatTrim(base)} mg" else "—"
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ampMlText,
                            onValueChange = { raw ->
                                val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
                                val oneDot = buildString {
                                    var saw = false
                                    for (c in cleaned) { if (c == '.') { if (!saw) { append(c); saw = true } } else append(c) }
                                }
                                ampMlText = oneDot.take(6)
                            },
                            label = { Text("ml") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ampMgText,
                            onValueChange = { raw ->
                                val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
                                val oneDot = buildString {
                                    var saw = false
                                    for (c in cleaned) { if (c == '.') { if (!saw) { append(c); saw = true } } else append(c) }
                                }
                                ampMgText = oneDot.take(6)
                            },
                            label = { Text("mg") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val ml = ampMlText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val mg = ampMgText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val conc = if (ml > 0) mg / ml else 0.0
                    val line = if (conc > 0) "= ${formatTrim(conc)} mg/ml" else ""
                    if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Divider()

        // Ergebnis
        ResultCard(result = result, rule = appliedRule, formulation = selectedFormulation, outline = outline, shape = cardShape)

        // Info-Tabs
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallTabButton(text = "Indikation", active = activeInfo == InfoSection.IND, accent = accent, modifier = Modifier.weight(1f)) { activeInfo = InfoSection.IND }
            SmallTabButton(text = "Kontraindikation", active = activeInfo == InfoSection.KONTRA, accent = accent, modifier = Modifier.weight(1f)) { activeInfo = InfoSection.KONTRA }
            SmallTabButton(text = "Wirkung", active = activeInfo == InfoSection.WIRK, accent = accent, modifier = Modifier.weight(1f)) { activeInfo = InfoSection.WIRK }
            SmallTabButton(text = "Nebenwirkung", active = activeInfo == InfoSection.NEBEN, accent = accent, modifier = Modifier.weight(1f)) { activeInfo = InfoSection.NEBEN }
        }
        val infoText = when (activeInfo) {
            InfoSection.IND -> selectedDrug?.indications
            InfoSection.KONTRA -> selectedDrug?.contraindications
            InfoSection.WIRK -> selectedDrug?.effects
            InfoSection.NEBEN -> selectedDrug?.adverseEffects
        } ?: "—"
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, outline), cardShape),
            shape = cardShape
        ) {
            Text(
                text = infoText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

/* ---------- UI-Helfer ---------- */
@Composable
private fun SmallTabButton(
    text: String,
    active: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pad = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
    if (active) {
        FilledTonalButton(
            onClick = onClick,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onError),
            contentPadding = pad,
            modifier = modifier.heightIn(min = 32.dp)
        ) { Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip) }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            border = BorderStroke(1.dp, accent),
            contentPadding = pad,
            modifier = modifier.heightIn(min = 32.dp)
        ) { Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip) }
    }
}

@Composable
private fun Selector(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    onPick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .let { if (enabled) it.clickable { expanded = true } else it }
        )
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onPick(opt); expanded = false })
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: DoseResult,
    rule: DoseRuleEntity?,
    formulation: FormulationEntity?,
    outline: Color,
    shape: RoundedCornerShape
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, outline), shape),
        shape = shape
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Berechnung", style = MaterialTheme.typography.titleMedium)
            LabeledBoldValue(label = "Gesamtdosis", value = "${formatMg(result.mg)} mg")
            LabeledBoldValue(label = "Volumen", value = "${format1(result.ml)} ml")
            val maxTxt = rule?.maxSingleMg?.let { "Maximaldosis: ${format1(it)} mg" } ?: "Maximaldosis: keine"
            if (rule?.maxSingleMg != null && result.wasCapped) {
                Text("Begrenzung aktiv – $maxTxt", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            } else {
                Text(maxTxt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val adminLine = rule?.displayHint?.takeIf { it.isNotBlank() } ?: formulation?.let { "${it.route} • ${it.label}" }
            adminLine?.let { Text("Verabreichung: $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun LabeledBoldValue(label: String, value: String) {
    Text(
        text = buildAnnotatedString {
            append("$label: ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value) }
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

/* ---------- Logik ---------- */
data class DoseResult(val mg: Double, val ml: Double, val wasCapped: Boolean)

private fun calcDose(
    rule: DoseRuleEntity?,
    formulation: FormulationEntity?,
    weightKg: Double,
    defaultRoundingMl: Double,
    overrideConcentrationMgPerMl: Double? = null
): DoseResult {
    if (rule == null || formulation == null || weightKg <= 0.0) return DoseResult(0.0, 0.0, false)

    val mg = when (rule.mode.uppercase()) {
        "MG_PER_KG" -> (rule.mgPerKg ?: 0.0) * weightKg
        "FLAT_MG" -> (rule.flatMg ?: 0.0)
        else -> 0.0
    }

    val cappedMg = rule.maxSingleMg?.let { max -> mg.coerceAtMost(max) } ?: mg
    val wasCapped = cappedMg + 1e-12 < mg

    val conc = overrideConcentrationMgPerMl ?: formulation.concentrationMgPerMl
    val mlRaw = if (conc > 0) cappedMg / conc else 0.0
    val step = rule.roundingMl ?: defaultRoundingMl
    val ml = roundStep(mlRaw, step)

    return DoseResult(cappedMg, ml, wasCapped)
}

private fun roundStep(value: Double, step: Double): Double {
    if (step <= 0.0) return value
    val ratio = (value / step) + 1e-9
    val k = round(ratio)
    val res = k * step
    return if (abs(res) < 1e-12) 0.0 else res
}

private fun estimateWeight(years: Int, months: Int): Double {
    val y = years + months / 12.0
    return when {
        y < 1.0 -> 4 + 0.5 * (months.coerceIn(0, 11))
        y <= 5.0 -> 2.0 * y + 8.0
        y <= 12.0 -> 3.0 * y + 7.0
        else -> 70.0
    }
}

private fun format1(x: Double): String = String.format(Locale.GERMANY, "%.1f", x)
private fun formatMg(x: Double): String {
    val v = if (abs(x) < 1e-12) 0.0 else x
    return if (v < 0.1) String.format(Locale.GERMANY, "%.2f", v) else String.format(Locale.GERMANY, "%.1f", v)
}
private fun formatTrim(x: Double): String {
    val s = String.format(Locale.GERMANY, "%.1f", x)
    return s.replace(Regex(",0$"), "")
}

enum class InfoSection { IND, KONTRA, WIRK, NEBEN }
