// File: app/src/main/java/com/rdinfo/MainActivity.kt
package com.rdinfo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.rdinfo.ui.theme.AppColors
import com.rdinfo.ui.theme.RDInfoTheme
import com.rdinfo.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.util.Locale

/* ───────────────────────────  Farb-/Theme-Helfer  ────────────────────────── */

// Neutraler Hellton (Light-Mode Panels – wie vorher)
private val PanelLight = Color(0xFFE6E0E9)
// Neutraler Dunkelton (Dark-Mode Panels – gewünschtes Grau)
private val PanelDark = Color(0xFF2B2B2B)

// App-weit Dark erkennen (nicht System, sondern aktuelles Theme)
@Composable private fun isAppDark(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable private fun panelContainerColor(): Color =
    if (isAppDark()) PanelDark else PanelLight

@Composable private fun panelContentColor(): Color =
    if (isAppDark()) Color.White else MaterialTheme.colorScheme.onSurface

// File: app/src/main/java/com/rdinfo/MainActivity.kt

// ⬇️ Füge diese Composable unterhalb deiner anderen Hilfsfunktionen ein (z. B. nach DropdownChevron()).
//    Sie behebt „Unresolved reference 'InfoTabsRow'“ und vermeidet Typ-Inferenz-Fehler.

@Composable
private fun InfoTabsRow(
    activeTab: InfoTab,
    onChange: (InfoTab) -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val buttonHeight = 36.dp
    val labelStyle = MaterialTheme.typography.bodySmall

    // feste Reihenfolge + kurze Labels (kein Umbruch)
    val items: List<Pair<InfoTab, String>> = listOf(
        InfoTab.INDIKATION to "Indikation",
        InfoTab.KONTRAIND  to "Kontraindikation",
        InfoTab.WIRKUNG    to "Wirkung",
        InfoTab.NEBENWIRKUNG to "Nebenwirkung"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (tab, label) ->
            val selected = tab == activeTab
            val colors = if (selected) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }

            OutlinedButton(
                onClick = { onChange(tab) },
                shape = shape,
                colors = colors,
                border = if (selected) null else ButtonDefaults.outlinedButtonBorder,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(buttonHeight)
                    .weight(1f, fill = true)
            ) {
                Text(
                    text = label,
                    style = labelStyle,
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


/* ───────────────────────────────  App-Setup  ─────────────────────────────── */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MedicationRepository.setLazyJsonLoader {
            JsonStore.readWithAssetsFallback(applicationContext, assets)
        }

        setContent {
            val ctx = LocalContext.current
            var isDark by rememberSaveable { mutableStateOf(ThemePrefs.get(ctx)) }
            var currentScreen by rememberSaveable { mutableStateOf(Screen.MAIN) }

            RDInfoTheme(darkTheme = isDark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (currentScreen) {
                        Screen.MAIN -> MainScreen(onOpenSettings = { currentScreen = Screen.SETTINGS })
                        Screen.SETTINGS -> SettingsScreen(
                            isDark = isDark,
                            onBack = { currentScreen = Screen.MAIN },
                            onToggleDark = {
                                isDark = it
                                ThemePrefs.set(ctx, it)
                            }
                        )
                    }
                }
            }
        }
    }
}

enum class Screen { MAIN, SETTINGS }
enum class InfoTab { INDIKATION, KONTRAIND, WIRKUNG, NEBENWIRKUNG }

/* ───────────────────────────  Reusable UI Bits  ──────────────────────────── */

// Labels sollen NIE umbrechen (z. B. „Ampullenkonzentration“)
@Composable
private fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall
) {
    Text(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun OverflowMenu(onOpenSettings: () -> Unit, onOpenEditor: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { MoreVertIcon() }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Editor") }, onClick = { open = false; onOpenEditor() })
            DropdownMenuItem(text = { Text("Settings") }, onClick = { open = false; onOpenSettings() })
        }
    }
}

// Panel-Card mit konsistenten Farben
@Composable
private fun CalculationCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val cont = panelContainerColor()
    val text = panelContentColor()
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = cont, contentColor = text),
        shape = MaterialTheme.shapes.medium
    ) {
        CompositionLocalProvider(LocalContentColor provides text) { content() }
    }
}

/* ───────────────────────────────  Main Screen  ───────────────────────────── */

@Composable
private fun MainScreen(onOpenSettings: () -> Unit) {
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
            onMedicationsClick = { /* optional */ },
            onMenuClick = onOpenSettings,
            onOpenEditor = { ctx.startActivity(Intent(ctx, MedicationEditorActivity::class.java)) }
        )

        HorizontalDivider(color = AppColors.SoftPink, thickness = 1.dp)
        Spacer(Modifier.height(Spacing.sm))

        // Alter
        var years by rememberSaveable { mutableStateOf(0) }
        LabeledCompactSlider("Jahre", years, 0..100) { v -> years = v }
        Spacer(Modifier.height(Spacing.sm))

        var months by rememberSaveable { mutableStateOf(0) }
        LabeledCompactSlider("Monate", months, 0..11) { v -> months = v }

        Spacer(Modifier.height(Spacing.lg))

        // Gewicht
        val estimated = remember(years, months) { estimateWeightKg(years, months) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("Gewicht (kg)")
            Spacer(Modifier.weight(1f))
            Text(
                "Vorschlag: ${formatKg(estimated)} kg",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Spacing.xs))

        var weightText by rememberSaveable { mutableStateOf("") }
        CompactNumberField(
            value = weightText,
            onValueChange = { t -> if (t.matches(Regex("^[0-9]*[.,]?[0-9]{0,2}$"))) weightText = t },
            modifier = Modifier.fillMaxWidth()
        )

        val manualWeight = parseWeightKg(weightText)
        val effectiveWeight = manualWeight ?: estimated

        Spacer(Modifier.height(Spacing.sm))

        // Medikament
        FieldLabel("Medikament")
        Spacer(Modifier.height(Spacing.xs))
        val medications = remember(refreshTick) { MedicationRepository.getMedicationNames() }
        var selectedMedication by rememberSaveable { mutableStateOf(medications.first()) }
        LaunchedEffect(medications, refreshTick) {
            if (selectedMedication !in medications && medications.isNotEmpty()) {
                selectedMedication = medications.first()
            }
        }
        CompactDropdownField(
            selectedText = selectedMedication,
            options = medications,
            onSelect = { selectedMedication = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.sm))

        // Einsatzfall
        FieldLabel("Einsatzfall")
        Spacer(Modifier.height(Spacing.xs))
        val useCases = remember(selectedMedication, refreshTick) {
            MedicationRepository.getUseCaseNamesForMedication(selectedMedication)
        }
        var selectedUseCase by rememberSaveable(selectedMedication) { mutableStateOf(useCases.firstOrNull() ?: "") }
        LaunchedEffect(useCases, selectedMedication, refreshTick) {
            if (selectedUseCase.isNotEmpty() && selectedUseCase !in useCases) {
                selectedUseCase = useCases.firstOrNull() ?: ""
            }
        }
        CompactDropdownField(
            selectedText = selectedUseCase.ifEmpty { "—" },
            options = useCases,
            onSelect = { selectedUseCase = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.sm))

        // Ampulle + Route
        var concFromSection by rememberSaveable { mutableStateOf<Double?>(null) }
        var manualConcFlag by rememberSaveable { mutableStateOf(false) }
        var ampMg by rememberSaveable { mutableStateOf<Double?>(null) }
        var ampMl by rememberSaveable { mutableStateOf<Double?>(null) }

        val routeOptions = remember(selectedMedication, selectedUseCase, refreshTick) {
            MedicationRepository.getRouteNamesForMedicationUseCase(selectedMedication, selectedUseCase)
        }
        var selectedRoute by rememberSaveable(selectedMedication, selectedUseCase) {
            mutableStateOf(routeOptions.firstOrNull() ?: "i.v.")
        }
        LaunchedEffect(routeOptions, selectedMedication, selectedUseCase, refreshTick) {
            if (selectedRoute !in routeOptions) {
                selectedRoute = routeOptions.firstOrNull() ?: selectedRoute
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AmpouleConcentrationSection(
                medication = selectedMedication,
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
            RouteSection(
                routes = routeOptions,
                selected = selectedRoute,
                onSelect = { selectedRoute = it },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        // Ergebnis
        val dosing = remember(selectedMedication, selectedUseCase, selectedRoute, effectiveWeight, years) {
            runCatching {
                computeDoseFor(
                    medication = selectedMedication,
                    useCase = selectedUseCase,
                    weightKg = effectiveWeight,
                    ageYears = years,
                    routeDisplayName = selectedRoute
                )
            }.getOrElse { e ->
                DosingResult(
                    mg = null,
                    hint = e.message ?: "Unbekannter Fehler.",
                    recommendedConcMgPerMl = null,
                    solutionText = null,
                    totalPreparedMl = null
                )
            }
        }

        val concForVolume = remember(concFromSection, manualConcFlag, dosing.recommendedConcMgPerMl, ampMg, ampMl) {
            val mgLocal = ampMg
            val mlLocal = ampMl
            when {
                manualConcFlag && mgLocal != null && mlLocal != null && mlLocal > 0.0 -> mgLocal / mlLocal
                dosing.recommendedConcMgPerMl != null -> dosing.recommendedConcMgPerMl
                else -> concFromSection
            }
        }
        val volumeMl = remember(dosing.mg, concForVolume) { computeVolumeMl(dosing.mg, concForVolume) }

        val doseText = dosing.mg?.let { "${format2(it)} mg" } ?: "—"
        val volNum = volumeMl?.let { format2(it) }
        val totalNum = dosing.totalPreparedMl?.let { format2(it) }
        val (volPadded, totalPadded) = alignDecimalForTwo(volNum, totalNum)
        val volText = volPadded?.let { "$it ml" } ?: "—"
        val totalText = totalPadded?.let { "$it ml" } ?: "—"

        val solutionText = when {
            dosing.totalPreparedMl != null && dosing.solutionText != null ->
                "${formatNoTrailingZeros(dosing.totalPreparedMl!!)} ml " + dosing.solutionText.replace(" %", " %")
            dosing.solutionText != null -> dosing.solutionText.replace(" %", " %")
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

                val hintCombined = if (!dosing.maxDoseText.isNullOrBlank())
                    listOf(dosing.hint, "Maximaldosis: ${dosing.maxDoseText}").joinToString("\n")
                else dosing.hint

                Text(hintCombined, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // Info-Tabs + Text
        var activeTab by rememberSaveable { mutableStateOf(InfoTab.INDIKATION) }
        InfoTabsRow(activeTab = activeTab, onChange = { tab -> activeTab = tab })
        Spacer(Modifier.height(Spacing.xs))

        val sections: MedicationInfoSections? = remember(selectedMedication) {
            MedicationRepository.getInfoSections(selectedMedication)
        }
        val infoText = when (activeTab) {
            InfoTab.INDIKATION -> sections?.indication
            InfoTab.KONTRAIND -> sections?.contraindication
            InfoTab.WIRKUNG -> sections?.effect
            InfoTab.NEBENWIRKUNG -> sections?.sideEffects
        } ?: "—"

        Card(
            colors = CardDefaults.cardColors(
                containerColor = panelContainerColor(),
                contentColor = panelContentColor()
            ),
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

/* ──────────────────────────────── Panels ─────────────────────────────────── */

@Composable
private fun RouteSection(
    routes: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = panelContainerColor(),
            contentColor = panelContentColor()
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        CompositionLocalProvider(LocalContentColor provides panelContentColor()) {
            Column(Modifier.padding(Spacing.lg)) {
                FieldLabel("Applikationsart")
                Spacer(Modifier.height(Spacing.xs))
                CompactDropdownField(
                    selectedText = selected,
                    options = routes,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/* ─────────────────────  Tabelle stabil & fontScale-fest  ─────────────────── */

// File: app/src/main/java/com/rdinfo/MainActivity.kt

// ⬇️ Ersetze NUR die CalcTable(...) durch diese responsive Version.
@Suppress("UnusedBoxWithConstraintsScope")
@Composable
private fun CalcTable(
    left: List<Pair<String, String>>,
    right: List<Pair<String, String>>,
    // der mittlere Abstand wird automatisch verkleinert auf schmalen Displays
    middleGapDefault: Dp = 16.dp,
    rowGap: Dp = 4.dp
) {
    // FontScale in der Tabelle fixieren → identisches Layout auf allen Geräten
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val W = maxWidth

            // 🔧 Responsive Breiten – rechter Block wird bei wenig Platz schmaler,
            //    damit Zahlen + Einheiten nicht abgeschnitten werden.
            val isNarrow = W < 360.dp
            val labelWidthRight: Dp = if (isNarrow) 68.dp else 80.dp
            val valueWidthRight: Dp = if (isNarrow) 88.dp else 96.dp
            val middleGap: Dp      = if (isNarrow) 8.dp  else middleGapDefault

            val rightBlock = labelWidthRight + valueWidthRight + middleGap

            val labelWidthLeft: Dp = if (isNarrow) 86.dp else 92.dp
            val minLeftValueWidth: Dp = if (isNarrow) 128.dp else 140.dp
            val valueWidthLeft: Dp = (W - rightBlock - labelWidthLeft).coerceAtLeast(minLeftValueWidth)

            val valueStyleTabular: TextStyle =
                MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")

            Row(Modifier.fillMaxWidth()) {

                // ---- Linke Labels
                Column(Modifier.width(labelWidthLeft)) {
                    left.forEachIndexed { i, (label, _) ->
                        Text(
                            "$label:",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = if (i != left.lastIndex) rowGap else 0.dp)
                        )
                    }
                }

                // ---- Linke Werte
                Column(Modifier.width(valueWidthLeft)) {
                    left.forEachIndexed { i, (label, value) ->
                        val isSolution = label == "Lösung"
                        Text(
                            value,
                            style = if (isSolution) MaterialTheme.typography.bodyLarge else valueStyleTabular,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = if (i != left.lastIndex) rowGap else 0.dp)
                        )
                    }
                }

                // ---- Rechte Labels
                Column(
                    Modifier
                        .width(labelWidthRight + middleGap)
                        .padding(start = middleGap)
                ) {
                    right.forEachIndexed { i, (label, _) ->
                        Text(
                            "$label:",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.End,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (i != right.lastIndex) rowGap else 0.dp)
                        )
                    }
                }

                // ---- Rechte Werte (Zahlen + Einheiten, rechtsbündig)
                Column(Modifier.width(valueWidthRight)) {
                    right.forEachIndexed { i, (_, value) ->
                        Text(
                            value,
                            style = valueStyleTabular,
                            textAlign = TextAlign.End,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (i != right.lastIndex) rowGap else 0.dp)
                        )
                    }
                }
            }
        }
    }
}


/* ───────────────────── Eingabe-Controls dunkel & no-wrap ─────────────────── */

@Composable
private fun AmpouleConcentrationSection(
    medication: String,
    refreshKey: Int,
    onStateChanged: (concMgPerMl: Double?, manual: Boolean, mgAmpoule: Double?, mlAmpoule: Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    val med = remember(medication, refreshKey) { MedicationRepository.getMedicationByName(medication) }
    val defaultText = med?.defaultConcentration?.display ?: "—"
    val defaultValue = med?.defaultConcentration?.mgPerMl

    var manual by rememberSaveable(medication) { mutableStateOf(false) }
    var mgText by rememberSaveable(medication) { mutableStateOf("") }
    var mlText by rememberSaveable(medication) { mutableStateOf("") }

    LaunchedEffect(medication, manual, refreshKey) {
        if (!manual) onStateChanged(defaultValue, false, null, null)
        else onStateChanged(null, true, null, null)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = panelContainerColor(),
            contentColor = panelContentColor()
        ),
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
                    Text(defaultText, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                } else {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.width(96.dp)) {
                            FieldLabel("mg")
                            Spacer(Modifier.height(Spacing.xs))
                            CompactNumberField(
                                value = mgText,
                                onValueChange = { t -> if (t.matches(Regex("^[0-9]{0,3}([.,][0-9]{0,2})?$"))) mgText = t }
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column(Modifier.width(96.dp)) {
                            FieldLabel("ml")
                            Spacer(Modifier.height(Spacing.xs))
                            CompactNumberField(
                                value = mlText,
                                onValueChange = { t -> if (t.matches(Regex("^[0-9]{0,3}([.,][0-9]{0,2})?$"))) mlText = t }
                            )
                        }
                    }
                    val mg = mgText.replace(',', '.').toDoubleOrNull()
                    val ml = mlText.replace(',', '.').toDoubleOrNull()
                    val conc = if (mg != null && ml != null && ml > 0.0) mg / ml else null
                    LaunchedEffect(mgText, mlText) { onStateChanged(conc, true, mg, ml) }
                }
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
    val bg = panelContainerColor()
    val fg = panelContentColor()

    Box(
        modifier
            .height(36.dp)
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .then(modifier),
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
    val bg = panelContainerColor()
    val fg = panelContentColor()

    Box(
        modifier
            .height(36.dp)
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .background(bg)
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
            CompositionLocalProvider(LocalContentColor provides fg) {
                Text(
                    selectedText,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                DropdownChevron()
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(fieldWidthDp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodyLarge) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

/* ───────────────────────── Slider (dunkel & kompakt) ─────────────────────── */

@Composable
private fun LabeledCompactSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
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

    val thumbColor = MaterialTheme.colorScheme.primary

    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { size -> widthPx = size.width.toFloat() }
            .pointerInput(range) { detectTapGestures(onTap = { pos -> onValueChange(valueFromX(pos.x)) }) }
            .pointerInput(range) {
                detectDragGestures(
                    onDragStart = { o -> onValueChange(valueFromX(o.x)) },
                    onDrag = { change, _ -> onValueChange(valueFromX(change.position.x)) },
                )
            }
            .semantics { stateDescription = "$value" },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
            val trackCorner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
            val centerY = size.height / 2f
            val top = centerY - trackHeightPx / 2f

            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, top),
                size = Size(size.width, trackHeightPx),
                cornerRadius = trackCorner
            )

            val thumbCenterX = xFromValue(value)
            val thumbSize = trackHeightPx
            val half = thumbSize / 2f
            val clampedCx = thumbCenterX.coerceIn(half, size.width - half)
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(0f, top),
                size = Size(clampedCx.coerceAtLeast(0f), trackHeightPx),
                cornerRadius = trackCorner
            )

            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(clampedCx - half, top),
                size = Size(thumbSize, thumbSize),
                cornerRadius = trackCorner
            )
        }
    }
}

/* ─────────────────────────────  Misc UI  ─────────────────────────────────── */

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
            drawCircle(color = dotColor, radius = dot / 2f, center = Offset(cx, startY + i * spacing))
        }
    }
}

@Composable
private fun DropdownChevron() {
    val chevronColor = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(16.dp)) {
        val w = size.width; val h = size.height
        val yTop = h * 0.4f; val yBottom = h * 0.6f
        val midX = w / 2f
        drawLine(color = chevronColor, start = Offset(midX - w * 0.25f, yTop), end = Offset(midX, yBottom), strokeWidth = 2f)
        drawLine(color = chevronColor, start = Offset(midX + w * 0.25f, yTop), end = Offset(midX, yBottom), strokeWidth = 2f)
    }
}

@Composable
private fun HeaderRow(
    onMedicationsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onOpenEditor: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(
            onClick = onMedicationsClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("Medikamente") }

        OverflowMenu(
            onOpenSettings = onMenuClick,
            onOpenEditor = onOpenEditor
        )
    }
}

/* ───────────────────────── Settings + Utils ──────────────────────────────── */

@Composable
private fun SettingsScreen(
    isDark: Boolean,
    onBack: () -> Unit,
    onToggleDark: (Boolean) -> Unit
) {
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
            } else {
                scope.launch { snackbar.showSnackbar("Import fehlgeschlagen.") }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .safeDrawingPadding()
                .fillMaxSize()
                .padding(Spacing.lg)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Einstellungen", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBack) {
                    // kurze Labels, daher kein Wrap nötig – trotzdem sicherheitshalber:
                    Text("Zurück", maxLines = 1, softWrap = false)
                }
            }
            Spacer(Modifier.height(Spacing.md))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Dark-Mode", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Switch(checked = isDark, onCheckedChange = onToggleDark)
            }

            Spacer(Modifier.height(Spacing.lg))
            Divider()
            Spacer(Modifier.height(Spacing.lg))

            Text("Daten • Backup / Import / Export", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.sm))

            // 🔒 Anti-Umbruch: Buttontexte werden EINZEILIG erzwungen + Ellipsis + zentriert
            @Composable
            fun SingleLineButtonText(label: String) = Text(
                text = label,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val res = JsonStore.createManualBackup(ctx)
                        backups = JsonStore.listBackups(ctx)
                        scope.launch { snackbar.showSnackbar(if (res.isSuccess) "Backup erstellt." else "Backup fehlgeschlagen.") }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) { SingleLineButtonText("Backup erstellen") }

                OutlinedButton(
                    onClick = { showRestoreDialog = true },
                    enabled = backups.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) { SingleLineButtonText("Backup einspielen") }
            }

            Spacer(Modifier.height(Spacing.sm))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("medications_export.json") },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) { SingleLineButtonText("Exportieren…") }

                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "*/*")) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) { SingleLineButtonText("Importieren…") }
            }

            Spacer(Modifier.height(Spacing.lg))

            Text("Vorhandene Backups (neu → alt):", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.sm))
            if (backups.isEmpty()) {
                Text("— keine —", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    backups.forEach { f ->
                        Text("• ${formatBackupDate(f)}", style = MaterialTheme.typography.bodySmall)
                    }
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
                    scope.launch { snackbar.showSnackbar("Backup eingespielt.") }
                } else {
                    scope.launch { snackbar.showSnackbar("Wiederherstellung fehlgeschlagen.") }
                }
            }
        )
    }
}

@Composable
private fun RestoreBackupDialog(
    backups: List<java.io.File>,
    onDismiss: () -> Unit,
    onRestore: (java.io.File) -> Unit
) {
    var selected by remember(backups) { mutableStateOf(backups.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup einspielen") },
        text = {
            if (backups.isEmpty()) {
                Text("Es sind keine Backups vorhanden.")
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    backups.forEach { f ->
                        val isSel = f == selected
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isSel, onClick = { selected = f })
                            Spacer(Modifier.width(8.dp))
                            Text(formatBackupDate(f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = selected != null, onClick = { selected?.let(onRestore) }) { Text("Einspielen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

private fun formatBackupDate(f: java.io.File): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.GERMANY)
    return sdf.format(f.lastModified())
}

@Composable
private fun rememberOnResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

private fun parseWeightKg(text: String): Double? = text.replace(',', '.').toDoubleOrNull()
private fun formatKg(v: Double): String = String.format(Locale.GERMANY, "%.1f", v)
private fun format2(v: Double): String = String.format(Locale.GERMANY, "%.2f", v)

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

private fun alignDecimalForTwo(a: String?, b: String?): Pair<String?, String?> {
    if (a == null && b == null) return null to null
    val aInt = a?.substringBefore(',')?.filter { it.isDigit() } ?: ""
    val bInt = b?.substringBefore(',')?.filter { it.isDigit() } ?: ""
    val maxLen = maxOf(aInt.length, bInt.length)
    fun pad(x: String?): String? {
        if (x == null) return null
        val intPart = x.substringBefore(',')
        val rest = x.substringAfter(',', missingDelimiterValue = "")
        val digitsOnly = intPart.filter { it.isDigit() }
        val padCount = maxLen - digitsOnly.length
        val pad = buildString { repeat(padCount) { append(' ') } } // U+2007
        return if (rest.isEmpty()) pad + intPart else pad + intPart + "," + rest
    }
    return pad(a) to pad(b)
}

private fun formatNoTrailingZeros(v: Double): String {
    val s = format2(v)
    return if (s.endsWith(",00")) s.substringBefore(",") else s
}
