// app/src/main/java/com/rdinfo/data/model/MedicationModels.kt
package com.rdinfo.data.model

/**
 * Zentrale, regeltreibende Datenmodelle für RDInfo.
 * Diese Datei wird von Repository/Loader und vom RuleDosingCalculator verwendet.
 */

// --- Stammdaten ---------------------------------------------------------------------------

data class Medication(
    val id: String,
    val name: String,
    val ampoule: AmpouleStrength,
    val useCases: List<UseCase>,
    val info: InfoTexts? = null,
    val notes: String? = null,
    val version: Int = 1
)

data class AmpouleStrength(
    val mg: Double,
    val ml: Double
)

data class UseCase(
    val id: String,
    val name: String,
    val routes: List<RouteSpec>,
    val defaultRoute: String? = null,
    val info: InfoTexts? = null,
    val notes: String? = null
)

data class RouteSpec(
    val route: String,
    val rules: List<DosingRule>,
    val notes: String? = null
)

// --- Regeln ------------------------------------------------------------------------------

data class DosingRule(
    val id: String? = null,
    val priority: Int = 0,
    val age: AgeRange? = null,
    val weight: WeightRange? = null,
    val calc: DoseCalc,
    val dilution: Dilution? = null,
    val conditions: Conditions? = null,
    val hint: String? = null,
    val rounding: Rounding? = null,
    val repeats: Repetition? = null,
    val maxCumulativeMgPerEvent: Double? = null
)

data class AgeRange(
    val minMonths: Int? = null,
    val maxMonthsExclusive: Int? = null
)

data class WeightRange(
    val minKg: Double? = null,
    val maxKgExclusive: Double? = null
)

/**
 * Dosisberechnung: entweder perKilogramm (mgPerKg) oder fixed (fixedMg).
 * minMg/maxMg können die berechnete Dosis clampen.
 */
data class DoseCalc(
    val type: String,           // "perKg" | "fixed"
    val mgPerKg: Double? = null,
    val fixedMg: Double? = null,
    val minMg: Double? = null,
    val maxMg: Double? = null
)

data class Dilution(
    val solutionText: String? = null,
    val totalVolumeMl: Double? = null
)

data class Conditions(
    val requiresManualAmpoule: Boolean? = null
)

data class Rounding(
    val mgStep: Double? = null,
    val mlStep: Double? = null,
    val showTrailingZeros: Boolean? = null
)

data class Repetition(
    val repeatAllowed: Boolean? = null,
    val minIntervalMinutes: Int? = null,
    val maxRepeats: Int? = null
)

// --- Info-Texte --------------------------------------------------------------------------

data class InfoTexts(
    val indication: String? = null,
    val contraindication: String? = null,
    val effect: String? = null,
    val sideEffect: String? = null
)
