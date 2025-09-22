// File: app/src/main/java/com/rdinfo/MainActivity.kt
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.rdinfo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rdinfo.data.JsonStore
import com.rdinfo.data.MedicationInfoSections
import com.rdinfo.data.MedicationRepository
import com.rdinfo.editor.MedicationEditorActivity
import com.rdinfo.logic.DosingResult
import com.rdinfo.logic.computeDoseFor
import com.rdinfo.logic.computeVolumeMl
import com.rdinfo.prefs.ThemePrefs
import com.rdinfo.ui.theme.RDInfoTheme
import com.rdinfo.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* --------------------- Theme helpers --------------------- */
private val PanelLight = Color(0xFFE6E0E9)
private val PanelDark = Color(0xFF2B2B2B)

@Composable private fun isAppDark(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable private fun panelContainerColor(): Color =
    if (isAppDark()) PanelDark else PanelLight

@Composable private fun panelContentColor(): Color =
    if (isAppDark()) Color.White else MaterialTheme.colorScheme.onSurface

/* --------------------- Activity --------------------- */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MedicationRepository.setLazyJsonLoader {
            JsonStore.readWithAssetsFallback(applicationContext, assets)
        }

        setContent {
            val ctx = LocalContext.current
            var isDark by rememberSaveable { mutableStateOf(ThemePrefs.get(ctx)) }
            var current by rememberSaveable { mutableStateOf(Screen.MAIN) }

            RDInfoTheme(darkTheme = isDark) {
                // Back closes keyboard first
                val kb = LocalSoftwareKeyboardController.current
                val imeVisible = WindowInsets.isImeVisible
                BackHandler(enabled = imeVisible) { kb?.hide() }

                Surface(color = MaterialTheme.colorScheme.background) {
                    when (current) {
                        Screen.MAIN -> MainScreen(
                            onOpenSettings = { current = Screen.SETTINGS },
                            onOpenEditor = { ctx.startActivity(Intent(ctx, MedicationEditorActivity::class.java)) }
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            isDark = isDark,
                            onBack = { current = Screen.MAIN },
                            onToggleDark = { ThemePrefs.set(ctx, it); isDark = it }
                        )
                    }
                }
            }
        }
    }
}

enum class Screen { MAIN, SETTINGS }
enum class InfoTab { INDIKATION, KONTRAIND, WIRKUNG, NEBENWIRKUNG }

/* --------------------- Reusable UI bits --------------------- */
@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodySmall) {
    Text(text, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable private fun CalculationCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val cont = panelContainerColor()
    val text = panelContentColor()
    Card(modifier, colors = CardDefaults.cardColors(cont, text), shape = MaterialTheme.shapes.medium) {
        CompositionLocalProvider(LocalContentColor provides text) { content() }
    }
}

@Composable private fun MoreVertIcon(modifier: Modifier = Modifier) {
    val dotColor = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(24.dp).then(modifier)) {
        val r = 3.dp.toPx() / 2f
        val cx = size.width / 2f
        val gap = 6.dp.toPx()
        val startY = size.height / 2f - gap
        repeat(3) { i -> drawCircle(dotColor, r, Offset(cx, startY + i * gap)) }
    }
}

@Composable
private fun InfoTabsRow(activeTab: InfoTab, onChange: (InfoTab) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val items = listOf(
        InfoTab.INDIKATION to "Indikation",
        InfoTab.KONTRAIND to "Kontraindikation",
        InfoTab.WIRKUNG to "Wirkung",
        InfoTab.NEBENWIRKUNG to "Nebenwirkung"
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (tab, label) ->
            val selected = tab == activeTab
            val colors = if (selected)
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            else ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface
            )

            OutlinedButton(
                onClick = { onChange(tab) },
                shape = shape,
                colors = colors,
                border = if (selected) null else ButtonDefaults.outlinedButtonBorder,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp).weight(1f)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/* --------------------- Main Screen --------------------- */
@Composable
private fun MainScreen(onOpenSettings: () -> Unit, onOpenEditor: () -> Unit) {
    val ctx = LocalContext.current
    val scroll = rememberScrollState()
    val refreshTick = rememberOnResumeTick()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(scroll)
            .padding(horizontal = Spacing.lg)
    ) {
        Spacer(Modifier.height(Spacing.sm))
        HeaderRow(
            onMedicationsClick = { /*no-op*/ },
            onOpenSettings = onOpenSettings,
            onOpenEditor = onOpenEditor
        )
        Divider()
        Spacer(Modifier.height(Spacing.sm))

        // Alter
        var years by rememberSaveable { mutableStateOf(0) }
        LabeledCompactSlider("Jahre", years, 0..100) { years = it }
        Spacer(Modifier.height(Spacing.sm))
        var months by rememberSaveable { mutableStateOf(0) }
        LabeledCompactSlider("Monate", months, 0..11) { months = it }

        Spacer(Modifier.height(Spacing.lg))

        // Gewicht
        val estimated = remember(years, months) { estimateWeightKg(years, months) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("Gewicht (kg)")
            Spacer(Modifier.weight(1f))
            Text("Vorschlag: ${formatKg(estimated)} kg", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.xs))
        var weightText by rememberSaveable { mutableStateOf("") }
        CompactNumberField(value = weightText, onValueChange = { t -> if (t.matches(Regex("^[0-9]*[.,]?[0-9]{0,2}$"))) weightText = t }, modifier = Modifier.fillMaxWidth())
        val manualWeight = parseWeightKg(weightText)
        val effectiveWeight = manualWeight ?: estimated

        Spacer(Modifier.height(Spacing.sm))

        // Medication
        FieldLabel("Medikament")
        Spacer(Modifier.height(Spacing.xs))
        val meds = remember(refreshTick) { MedicationRepository.getMedicationNames() }
        var selectedMed by rememberSaveable { mutableStateOf(meds.firstOrNull().orEmpty()) }
        LaunchedEffect(meds, refreshTick) { if (selectedMed !in meds && meds.isNotEmpty()) selectedMed = meds.first() }
        CompactDropdownField(selectedText = selectedMed.ifEmpty { "—" }, options = meds, onSelect = { selectedMed = it }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(Spacing.sm))

        // Use case
        FieldLabel("Einsatzfall")
        Spacer(Modifier.height(Spacing.xs))
        val useCases = remember(selectedMed, refreshTick) { MedicationRepository.getUseCaseNamesForMedication(selectedMed) }
        var selectedUseCase by rememberSaveable(selectedMed) { mutableStateOf(useCases.firstOrNull().orEmpty()) }
        LaunchedEffect(useCases, selectedMed) { if (selectedUseCase !in useCases) selectedUseCase = useCases.firstOrNull().orEmpty() }
        CompactDropdownField(selectedText = selectedUseCase.ifEmpty { "—" }, options = useCases, onSelect = { selectedUseCase = it }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(Spacing.sm))

        // Ampulle + Route
        var concFromSection: Double? by rememberSaveable { mutableStateOf(null) }
        var manualConcFlag by rememberSaveable { mutableStateOf(false) }
        var ampMg by rememberSaveable { mutableStateOf<Double?>(null) }
        var ampMl by rememberSaveable { mutableStateOf<Double?>(null) }

        val routeOptions = remember(selectedMed, selectedUseCase, refreshTick) { MedicationRepository.getRouteNamesForMedicationUseCase(selectedMed, selectedUseCase) }
        var selectedRoute by rememberSaveable(selectedMed, selectedUseCase) { mutableStateOf(routeOptions.firstOrNull() ?: "i.v.") }
        LaunchedEffect(routeOptions) { if (selectedRoute !in routeOptions && routeOptions.isNotEmpty()) selectedRoute = routeOptions.first() }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AmpouleConcentrationSection(
                medication = selectedMed,
                refreshKey = refreshTick,
                onStateChanged = { conc, manual, mgAmp, mlAmp ->
                    concFromSection = conc
                    manualConcFlag = manual
                    ampMg = mgAmp
                    ampMl = mlAmp
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.sm))
            RouteSection(routes = routeOptions, selected = selectedRoute, onSelect = { selectedRoute = it }, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(Spacing.lg))

        // Ergebnis
        val dosing = remember(selectedMed, selectedUseCase, selectedRoute, effectiveWeight, years) {
            runCatching {
                computeDoseFor(selectedMed, selectedUseCase, effectiveWeight, years, selectedRoute)
            }.getOrElse {
                DosingResult(amount = null, unit = "mg", hint = it.message ?: "Unbekannter Fehler.", recommendedConcAmountPerMl = null, solutionText = null, totalPreparedMl = null)
            }
        }

        val resolvedConcMgPerMl = remember(concFromSection, manualConcFlag, dosing.recommendedConcAmountPerMl, dosing.totalPreparedMl, ampMg, ampMl, selectedMed, refreshTick) {
            when {
                // Manuelle Eingabe hat Priorität
                manualConcFlag -> {
                    // Prüfe ob manuelle Trockensubstanz (ampMl = 0)
                    if (ampMl == 0.0 && ampMg != null && dosing.totalPreparedMl != null && dosing.totalPreparedMl!! > 0.0) {
                        ampMg!! / dosing.totalPreparedMl!!  // 1000 mg / 5 ml = 200 mg/ml
                    }
                    else {
                        concFromSection ?: dosing.recommendedConcAmountPerMl
                    }
                }
                // Automatisch aus Medikament
                !manualConcFlag -> {
                    val med = MedicationRepository.getMedicationByName(selectedMed)
                    val dc = med?.defaultConcentration
                    val mlVal = getDoubleOrNull(dc, "ml")
                    val mgVal = getDoubleOrNull(dc, "mg")

                    when {
                        // Trockensubstanz: Verwende Verdünnung falls vorhanden
                        mlVal == 0.0 && mgVal != null && dosing.totalPreparedMl != null && dosing.totalPreparedMl!! > 0.0 -> {
                            mgVal / dosing.totalPreparedMl!!
                        }
                        // Normale Ampulle ABER mit Verdünnung: Verwende Verdünnung
                        mlVal != null && mlVal > 0.0 && mgVal != null && dosing.totalPreparedMl != null && dosing.totalPreparedMl!! > 0.0 -> {
                            mgVal / dosing.totalPreparedMl!!
                        }
                        // Normale Ampulle ohne Verdünnung
                        mlVal != null && mlVal > 0.0 && mgVal != null -> mgVal / mlVal
                        // Fallback
                        else -> getDoubleOrNull(dc, "mgPerMl") ?: dosing.recommendedConcAmountPerMl
                    }
                }
                else -> dosing.recommendedConcAmountPerMl
            }
        }

        val volumeMl = remember(dosing.amount, resolvedConcMgPerMl) { computeVolumeMl(dosing.amount, resolvedConcMgPerMl) }

        val medUnit = dosing.unit
        val doseText = dosing.amount?.let { "${formatAmountInUnit(it, medUnit, 2)} ${unitLabel(medUnit)}" } ?: "—"
        val volNum = volumeMl?.let { formatFixed(it, 2) }
        val totalNum = dosing.totalPreparedMl?.let { formatFixed(it, 2) }
        val (volPadded, totalPadded) = alignDecimalForTwo(volNum, totalNum)
        val volText = volPadded?.let { "$it ml" } ?: "—"
        val totalText = totalPadded?.let { "$it ml" } ?: "—"

        val solutionText = when {
            dosing.totalPreparedMl != null && dosing.solutionText != null ->
                "${formatNoTrailingZeros(dosing.totalPreparedMl!!)} ml ${dosing.solutionText}"
            dosing.solutionText != null -> dosing.solutionText
            else -> "—"
        }

        CalculationCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.lg)) {
                Text("Berechnung", style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                Spacer(Modifier.height(Spacing.sm))

                CalcTable(
                    left = listOf("Dosierung" to doseText, "Lösung" to solutionText),
                    right = listOf("Volumen" to volText, "Gesamt" to totalText)
                )

                Spacer(Modifier.height(Spacing.sm))
                HorizontalDivider()
                Spacer(Modifier.height(Spacing.sm))
                FieldLabel("Hinweis")
                Spacer(Modifier.height(4.dp))
                Text(
                    (if (!dosing.maxDoseText.isNullOrBlank()) listOf(dosing.hint, "Maximaldosis: ${dosing.maxDoseText}") else listOf(dosing.hint))
                        .filter { !it.isNullOrBlank() }.joinToString("\n"),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // Info tabs
        var activeTab by rememberSaveable { mutableStateOf(InfoTab.INDIKATION) }
        InfoTabsRow(activeTab = activeTab, onChange = { activeTab = it })
        Spacer(Modifier.height(Spacing.xs))
        val sections: MedicationInfoSections? = remember(selectedMed) { MedicationRepository.getInfoSections(selectedMed) }
        val infoText = when (activeTab) {
            InfoTab.INDIKATION -> sections?.indication
            InfoTab.KONTRAIND -> sections?.contraindication
            InfoTab.WIRKUNG -> sections?.effect
            InfoTab.NEBENWIRKUNG -> sections?.sideEffects
        } ?: "—"

        Card(
            colors = CardDefaults.cardColors(panelContainerColor(), panelContentColor()),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            CompositionLocalProvider(LocalContentColor provides panelContentColor()) {
                Text(infoText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Spacing.lg))
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/* --------------------- Header --------------------- */
@Composable
private fun HeaderRow(
    onMedicationsClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEditor: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(onClick = onMedicationsClick, shape = CircleShape) { Text("Medikamente") }

        Box {
            IconButton(onClick = { menuOpen = true }) { MoreVertIcon() }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Editor") }, onClick = { menuOpen = false; onOpenEditor() })
                DropdownMenuItem(text = { Text("Einstellungen") }, onClick = { menuOpen = false; onOpenSettings() })
            }
        }
    }
}

/* --------------------- Panels --------------------- */
@Composable
private fun AmpouleConcentrationSection(
    medication: String,
    refreshKey: Int,
    onStateChanged: (concMgPerMl: Double?, manual: Boolean, mgAmpoule: Double?, mlAmpoule: Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    val med = remember(medication, refreshKey) { MedicationRepository.getMedicationByName(medication) }
    val defaultConc = med?.defaultConcentration
    val unit = med?.unit ?: "mg"
    var manual by rememberSaveable(medication) { mutableStateOf(false) }
    var mgText by rememberSaveable(medication) { mutableStateOf("") }
    var mlText by rememberSaveable(medication) { mutableStateOf("") }

    // initial callback
    LaunchedEffect(medication, manual, refreshKey) {
        val defConc =
            getDoubleOrNull(defaultConc, "mgPerMl")
                ?: run {
                    val mg = getDoubleOrNull(defaultConc, "mg")
                    val ml = getDoubleOrNull(defaultConc, "ml")
                    if (mg != null && ml != null && ml > 0.0) mg / ml else null
                }
        if (!manual) onStateChanged(defConc, false, null, null) else onStateChanged(null, true, null, null)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(panelContainerColor(), panelContentColor()),
        shape = MaterialTheme.shapes.medium
    ) {
        CompositionLocalProvider(LocalContentColor provides panelContentColor()) {
            Column(Modifier.padding(Spacing.lg)) {
                FieldLabel("Ampullenkonzentration")
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel("Manuell", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = manual, onCheckedChange = { manual = it })
                }
                Spacer(Modifier.height(Spacing.xs))

                if (!manual) {
                    val mgVal = getDoubleOrNull(defaultConc, "mg")
                    val mlVal = getDoubleOrNull(defaultConc, "ml")
                    val display = getStringOrNull(defaultConc, "display")
                    val line = when {
                        !display.isNullOrBlank() -> display
                        mgVal != null && (mlVal ?: 0.0) == 0.0 ->
                            "${formatAmountInUnit(mgVal, unit, 0)} ${unitLabel(unit)} / Trockensubstanz"
                        mgVal != null && mlVal != null ->
                            "${formatAmountInUnit(mgVal, unit, 0)} ${unitLabel(unit)} / ${formatNoTrailingZeros(mlVal)} ml"
                        else -> "—"
                    }
                    Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)

                    val concMgPerMl =
                        getDoubleOrNull(defaultConc, "mgPerMl")
                            ?: if ((mlVal ?: 0.0) > 0.0 && mgVal != null) mgVal / mlVal!! else null
                    // Nur anzeigen wenn nicht Trockensubstanz (mlVal != 0)
                    if ((mlVal ?: 0.0) != 0.0) {
                        val extra = concMgPerMl?.let { "1 ml = ${formatAmountInUnit(it, unit, 3, perMl = true)} ${unitLabel(unit)}" } ?: "1 ml = —"
                        Text(extra, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                    }
                } else {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            FieldLabel(unitLabel(unit))
                            Spacer(Modifier.height(Spacing.xs))
                            CompactNumberField(value = mgText, onValueChange = { t -> if (t.matches(Regex("^[0-9]{0,4}([.,][0-9]{0,3})?$"))) mgText = t })
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column(Modifier.weight(1f)) {
                            FieldLabel("ml")
                            Spacer(Modifier.height(Spacing.xs))
                            CompactNumberField(value = mlText, onValueChange = { t -> if (t.matches(Regex("^[0-9]{0,4}([.,][0-9]{0,3})?$"))) mlText = t })
                        }
                    }
                    val mg = mgText.replace(',', '.').toDoubleOrNull()
                    val ml = mlText.replace(',', '.').toDoubleOrNull()
                    // Bei Trockensubstanz (ml = 0) keine direkte Konzentration berechnen
                    val conc = if (mg != null && ml != null && ml > 0.0) mg / ml else null
                    LaunchedEffect(mgText, mlText) { onStateChanged(conc, true, mg, ml) }

                    // Nur anzeigen wenn nicht Trockensubstanz (ml != 0)
                    if (ml != 0.0) {
                        val extra = when {
                            mg != null && ml != null && ml > 0.0 -> "1 ml = ${formatAmountInUnit(mg / ml, unit, 3, perMl = true)} ${unitLabel(unit)}"
                            else -> "1 ml = —"
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Text(extra, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteSection(routes: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(panelContainerColor(), panelContentColor()),
        shape = MaterialTheme.shapes.medium
    ) {
        CompositionLocalProvider(LocalContentColor provides panelContentColor()) {
            Column(Modifier.padding(Spacing.lg)) {
                FieldLabel("Applikationsart")
                Spacer(Modifier.height(Spacing.xs))
                CompactDropdownField(selectedText = selected, options = routes, onSelect = onSelect, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/* --------------------- Stable table --------------------- */
@Suppress("UnusedBoxWithConstraintsScope")
@Composable
private fun CalcTable(
    left: List<Pair<String, String>>,
    right: List<Pair<String, String>>,
    middleGapDefault: Dp = 16.dp,
    rowGap: Dp = 4.dp
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val W = maxWidth
            val isNarrow = W < 360.dp
            val labelWidthRight: Dp = if (isNarrow) 68.dp else 80.dp
            val valueWidthRight: Dp = if (isNarrow) 88.dp else 96.dp
            val middleGap: Dp = if (isNarrow) 8.dp else middleGapDefault
            val rightBlock = labelWidthRight + valueWidthRight + middleGap
            val labelWidthLeft: Dp = if (isNarrow) 86.dp else 92.dp
            val minLeftValueWidth: Dp = if (isNarrow) 128.dp else 140.dp
            val valueWidthLeft: Dp = (W - rightBlock - labelWidthLeft).coerceAtLeast(minLeftValueWidth)
            val valueStyleTabular = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")

            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(labelWidthLeft)) {
                    left.forEachIndexed { i, (label, _) ->
                        Text("$label:", style = MaterialTheme.typography.bodyLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = if (i != left.lastIndex) rowGap else 0.dp))
                    }
                }
                Column(Modifier.width(valueWidthLeft)) {
                    left.forEachIndexed { i, (label, value) ->
                        val isSolution = label == "Lösung"
                        Text(value, style = if (isSolution) MaterialTheme.typography.bodyLarge else valueStyleTabular,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = if (i != left.lastIndex) rowGap else 0.dp))
                    }
                }
                Column(Modifier.width(labelWidthRight + middleGap).padding(start = middleGap)) {
                    right.forEachIndexed { i, (label, _) ->
                        Text("$label:", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(bottom = if (i != right.lastIndex) rowGap else 0.dp))
                    }
                }
                Column(Modifier.width(valueWidthRight)) {
                    right.forEachIndexed { i, (_, value) ->
                        Text(value, style = valueStyleTabular, textAlign = TextAlign.End, maxLines = 1, softWrap = false,
                            modifier = Modifier.fillMaxWidth().padding(bottom = if (i != right.lastIndex) rowGap else 0.dp))
                    }
                }
            }
        }
    }
}

/* --------------------- Inputs --------------------- */
@Composable
private fun CompactNumberField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    val borderColor = MaterialTheme.colorScheme.outline
    val textStyle = MaterialTheme.typography.bodyLarge
    val bg = panelContainerColor()
    val fg = panelContentColor()
    Box(
        modifier
            .height(36.dp)
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle.copy(color = fg),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CompactDropdownField(selectedText: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    val borderColor = MaterialTheme.colorScheme.outline
    var expanded by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { mutableStateOf(0) }
    val fieldWidthDp = with(LocalDensity.current) { fieldWidthPx.toDp() }
    val bg = panelContainerColor()
    val fg = panelContentColor()

    Box(
        modifier
            .height(36.dp)
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .background(bg)
            .onSizeChanged { fieldWidthPx = it.width },
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
            CompositionLocalProvider(LocalContentColor provides fg) {
                Text(selectedText, style = MaterialTheme.typography.bodyLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                DropdownChevron()
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(fieldWidthDp).heightIn(max = 300.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option, style = MaterialTheme.typography.bodyLarge) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable private fun DropdownChevron() {
    val c = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(16.dp)) {
        val w = size.width; val h = size.height
        val yTop = h * 0.4f; val yBottom = h * 0.6f; val midX = w / 2f
        drawLine(color = c, start = Offset(midX - w * 0.25f, yTop), end = Offset(midX, yBottom), strokeWidth = 2f)
        drawLine(color = c, start = Offset(midX + w * 0.25f, yTop), end = Offset(midX, yBottom), strokeWidth = 2f)
    }
}

/* --------------------- Slider --------------------- */
@Composable
private fun LabeledCompactSlider(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel("$label:")
        Spacer(Modifier.width(Spacing.xs))
        Text(value.toString(), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
    }
    Spacer(Modifier.height(0.dp))
    CompactSlider(value = value, onValueChange = onValueChange, range = range)
    Spacer(Modifier.height(Spacing.xs))
}

@Composable
private fun CompactSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 14.dp,
    activeColor: Color = panelContainerColor(),
    inactiveColor: Color = panelContainerColor()
) {
    val density = LocalDensity.current
    val trackHeightPx = with(density) { trackHeight.toPx() }
    var widthPx by remember { mutableStateOf(0f) }
    val min = range.first
    val max = range.last
    val span = (max - min).coerceAtLeast(1)
    fun xFromValue(v: Int): Float { val clamped = v.coerceIn(min, max); val frac = (clamped - min).toFloat() / span; return frac * widthPx }
    fun valueFromX(x: Float): Int { if (widthPx <= 0f) return value; val frac = (x / widthPx).coerceIn(0f, 1f); return (min + frac * span).toInt() }
    val thumbColor = MaterialTheme.colorScheme.primary

    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(range) { detectTapGestures(onTap = { pos -> onValueChange(valueFromX(pos.x)) }) }
            .pointerInput(range) { detectDragGestures(onDragStart = { o -> onValueChange(valueFromX(o.x)) }, onDrag = { change, _ -> onValueChange(valueFromX(change.position.x)) }) }
            .semantics { stateDescription = "$value" },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
            val trackCorner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
            val centerY = size.height / 2f
            val top = centerY - trackHeightPx / 2f
            drawRoundRect(color = inactiveColor, topLeft = Offset(0f, top), size = Size(size.width, trackHeightPx), cornerRadius = trackCorner)
            val thumbCenterX = xFromValue(value)
            val thumbSize = trackHeightPx
            val half = thumbSize / 2f
            val clampedCx = thumbCenterX.coerceIn(half, size.width - half)
            drawRoundRect(color = activeColor, topLeft = Offset(0f, top), size = Size(clampedCx.coerceAtLeast(0f), trackHeightPx), cornerRadius = trackCorner)
            drawRoundRect(color = thumbColor, topLeft = Offset(clampedCx - half, top), size = Size(thumbSize, thumbSize), cornerRadius = trackCorner)
        }
    }
}

/* --------------------- Settings --------------------- */
@Composable
private fun SettingsScreen(isDark: Boolean, onBack: () -> Unit, onToggleDark: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var backups by remember { mutableStateOf(JsonStore.listBackups(ctx)) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val res = JsonStore.exportToUri(ctx, uri)
            scope.launch { snackbar.showSnackbar(if (res.isSuccess) "Export erfolgreich." else "Export fehlgeschlagen.") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val res = JsonStore.importFromUri(ctx, uri)
            if (res.isSuccess) {
                val json = JsonStore.readWithAssetsFallback(ctx, ctx.assets)
                MedicationRepository.loadFromJsonString(json)
                MedicationRepository.setLazyJsonLoader { JsonStore.readWithAssetsFallback(ctx, ctx.assets) }
                backups = JsonStore.listBackups(ctx)
                scope.launch { snackbar.showSnackbar("Import erfolgreich.") }
            } else scope.launch { snackbar.showSnackbar("Import fehlgeschlagen.") }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).safeDrawingPadding().fillMaxSize().padding(Spacing.lg)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Einstellungen", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Zurück", maxLines = 1, softWrap = false) }
            }
            Spacer(Modifier.height(Spacing.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Dark-Mode", style = MaterialTheme.typography.bodyLarge); Spacer(Modifier.weight(1f))
                Switch(checked = isDark, onCheckedChange = onToggleDark)
            }
            Spacer(Modifier.height(Spacing.lg))
            Divider()
            Spacer(Modifier.height(Spacing.lg))

            Text("Daten • Backup / Import / Export", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.sm))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val res = JsonStore.createManualBackup(ctx)
                        backups = JsonStore.listBackups(ctx)
                        scope.launch { snackbar.showSnackbar(if (res.isSuccess) "Backup erstellt." else "Backup fehlgeschlagen.") }
                    },
                    modifier = Modifier.weight(1f).height(44.dp)
                ) { Text("Backup erstellen", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }

                OutlinedButton(
                    onClick = { showRestoreDialog = true },
                    enabled = backups.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) { Text("Backup einspielen", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }

            Spacer(Modifier.height(Spacing.sm))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("medications_export.json") },
                    modifier = Modifier.weight(1f).height(44.dp)
                ) { Text("Exportieren…", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }

                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f).height(44.dp)
                ) { Text("Importieren…", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }

            Spacer(Modifier.height(Spacing.lg))
            Text("Vorhandene Backups (neu → alt):", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.sm))
            if (backups.isEmpty()) {
                Text("— keine —", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    backups.forEach { f -> Text("• ${formatBackupDate(f)}", style = MaterialTheme.typography.bodySmall) }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (showRestoreDialog) {
        RestoreBackupDialog(
            backups = backups,
            onDismiss = { showRestoreDialog = false },
            onRestore = { file ->
                val res = JsonStore.restoreBackup(ctx, file)
                if (res.isSuccess) {
                    val json = JsonStore.readWithAssetsFallback(ctx, ctx.assets)
                    MedicationRepository.loadFromJsonString(json)
                    MedicationRepository.setLazyJsonLoader { JsonStore.readWithAssetsFallback(ctx, ctx.assets) }
                    backups = JsonStore.listBackups(ctx)
                    showRestoreDialog = false
                }
            }
        )
    }
}

@Composable
private fun RestoreBackupDialog(backups: List<File>, onDismiss: () -> Unit, onRestore: (File) -> Unit) {
    var selected by remember(backups) { mutableStateOf(backups.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup einspielen") },
        text = {
            if (backups.isEmpty()) Text("Es sind keine Backups vorhanden.") else {
                Column(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    backups.forEach { f ->
                        val isSel = f == selected
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isSel, onClick = { selected = f })
                            Spacer(Modifier.width(8.dp)); Text(formatBackupDate(f))
                        }
                    }
                }
            }
        },
        confirmButton = { Button(enabled = selected != null, onClick = { selected?.let(onRestore) }) { Text("Einspielen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/* --------------------- Utils --------------------- */
@Composable
private fun rememberOnResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

private fun parseWeightKg(text: String): Double? = text.replace(',', '.').toDoubleOrNull()
private fun formatKg(v: Double): String = String.format(Locale.GERMANY, "%.1f", v)
private fun formatFixed(v: Double, decimals: Int): String = String.format(Locale.GERMANY, "%.${decimals}f", v)
private fun formatBackupDate(f: File): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(f.lastModified()))

private fun estimateWeightKg(years: Int, months: Int): Double {
    val totalMonths = years * 12 + months
    if (totalMonths <= 12) return 4.0 + 0.5 * totalMonths

    fun childWeight(y: Int) = when (y) {
        in 1..5 -> 2.0 * y + 8.0
        in 6..12 -> 3.0 * y + 7.0
        else -> 0.0 // Wird nicht verwendet
    }

    return when (years) {
        in 1..12 -> {
            val w0 = childWeight(years)
            val w1 = if (years < 12) childWeight(years + 1) else 50.0 // 13-Jährige ca. 50kg
            w0 + (w1 - w0) * (months.coerceIn(0, 11) / 12.0)
        }
        13 -> 50.0 + months * (55.0 - 50.0) / 12.0  // 13J: 50-55kg
        14 -> 55.0 + months * (60.0 - 55.0) / 12.0  // 14J: 55-60kg
        15 -> 60.0 + months * (65.0 - 60.0) / 12.0  // 15J: 60-65kg
        16 -> 65.0 + months * (70.0 - 65.0) / 12.0  // 16J: 65-70kg
        else -> 70.0 // Ab 17 Jahren konstant 70kg
    }
}

private fun alignDecimalForTwo(a: String?, b: String?): Pair<String?, String?> {
    if (a == null && b == null) return null to null
    val aInt = a?.substringBefore(',')?.filter { it.isDigit() } ?: ""
    val bInt = b?.substringBefore(',')?.filter { it.isDigit() } ?: ""
    val maxLen = maxOf(aInt.length, bInt.length)
    fun pad(x: String?): String? {
        if (x == null) return null
        val intPart = x.substringBefore(',')
        val rest = x.substringAfter(',', "")
        val digitsOnly = intPart.filter { it.isDigit() }
        val padCount = maxLen - digitsOnly.length
        val nbspFigure = '\u2007'
        val pad = buildString { repeat(padCount) { append(nbspFigure) } }
        return if (rest.isEmpty()) pad + intPart else pad + intPart + "," + rest
    }
    return pad(a) to pad(b)
}

private fun formatNoTrailingZeros(v: Double): String {
    val s = String.format(Locale.GERMANY, "%.2f", v)
    return if (s.endsWith(",00")) s.substringBefore(",") else s
}

/** Format amount in selected unit, converting from mg. */
private fun formatAmountInUnit(valueMg: Double, unit: String, decimals: Int, perMl: Boolean = false): String {
    val converted = when (unit) {
        "µg", "ug", "mcg" -> valueMg * 1000.0
        "g" -> valueMg / 1000.0
        else -> valueMg
    }
    // Direkt formatNoTrailingZeros verwenden
    val trimmed = formatNoTrailingZeros(converted)
    val withGermanFormat = trimmed.replace('.', ',')
    val withNbsp = withGermanFormat.replace('.', '\u00A0')
    return withNbsp
}

private fun unitLabel(unit: String): String = when (unit) {
    "µg", "ug", "mcg" -> "µg"
    "g" -> "g"
    "I.E." -> "I.E."
    else -> "mg"
}

/** Safe extractors for repository concentration objects. */
@Suppress("UNCHECKED_CAST")
private fun getDoubleOrNull(obj: Any?, field: String): Double? = when (obj) {
    null -> null
    is Map<*, *> -> (obj[field] as? Number)?.toDouble()
    is com.rdinfo.data.Concentration -> when (field) {
        "mg" -> obj.mg
        "ml" -> obj.ml
        "mgPerMl" -> obj.mgPerMl
        else -> null
    }
    else -> try {
        val m = obj::class.members.firstOrNull { it.name == field } ?: return null
        (m.call(obj) as? Number)?.toDouble()
    } catch (_: Throwable) { null }
}

@Suppress("UNCHECKED_CAST")
private fun getStringOrNull(obj: Any?, field: String): String? = when (obj) {
    null -> null
    is Map<*, *> -> obj[field] as? String
    is com.rdinfo.data.Concentration -> when (field) {
        "display" -> obj.display
        else -> null
    }
    else -> try {
        val m = obj::class.members.firstOrNull { it.name == field } ?: return null
        m.call(obj) as? String
    } catch (_: Throwable) { null }
}