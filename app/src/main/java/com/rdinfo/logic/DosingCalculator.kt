// Zielpfad: app/src/main/java/com/rdinfo/logic/DosingCalculator.kt
// Vollständige Datei – nutzt Repository‑Regeln inkl. Verdünnung und (optional) Route

package com.rdinfo.logic

import com.rdinfo.data.MedicationRepository

/** Ergebnis der Dosisermittlung für die UI. */
data class DosingResult(
    val mg: Double?,
    val hint: String,
    val recommendedConcMgPerMl: Double?,
    val solutionText: String?,          // z. B. "NaCl 0,9 %"
    val totalPreparedMl: Double?        // z. B. 10.0 → „Lösung: 10 ml …“
)

object DosingCalculator {
    /**
     * Ermittelt die Dosis regelgetrieben. Optional kann eine Route vorgegeben werden
     * (wenn null, wird wie bisher ohne Routenfilter gewählt).
     */
    fun compute(
        medicationName: String,
        useCaseLabel: String,
        ageYears: Int,
        weightKg: Double,
        route: String? = null
    ): DosingResult {
        val rule = if (route.isNullOrBlank()) {
            MedicationRepository.findBestDosingRule(
                medicationName = medicationName,
                useCaseLabelOrKey = useCaseLabel,
                ageYears = ageYears,
                weightKg = weightKg
            )
        } else {
            MedicationRepository.findBestDosingRuleWithRoute(
                medicationName = medicationName,
                useCaseLabelOrKey = useCaseLabel,
                ageYears = ageYears,
                weightKg = weightKg,
                routeDisplayName = route
            )
        }

        // Keine passende Regel gefunden → kurzer Hinweis
        if (rule == null) return DosingResult(
            mg = null,
            hint = "Keine Dosisregel für $useCaseLabel bei $medicationName hinterlegt.",
            recommendedConcMgPerMl = null,
            solutionText = null,
            totalPreparedMl = null
        )

        // Dosisbasis bestimmen
        val baseMg = when {
            rule.fixedDoseMg != null -> rule.fixedDoseMg
            rule.doseMgPerKg != null -> rule.doseMgPerKg * weightKg
            else -> null
        }
        val mg = baseMg?.let { b -> rule.maxDoseMg?.let { max -> kotlin.math.min(b, max) } ?: b }

        // Hinweistext (Route vorangestellt, falls vorhanden)
        val hintPrefix = rule.route?.let { "$it: " } ?: ""
        val hintText = (hintPrefix + (rule.note ?: "")).ifBlank { "—" }

        return DosingResult(
            mg = mg,
            hint = hintText,
            recommendedConcMgPerMl = rule.recommendedConcMgPerMl,
            solutionText = rule.solutionText,
            totalPreparedMl = rule.totalPreparedMl
        )
    }
}

/** Bequemer Wrapper für bestehende Aufrufe ohne Route. */
fun computeDoseFor(
    medication: String,
    useCase: String,
    weightKg: Double,
    ageYears: Int
): DosingResult = DosingCalculator.compute(medication, useCase, ageYears, weightKg, route = null)

/** Wrapper mit benanntem Parameter `routeDisplayName` (Kompatibilität zur MainActivity). */
fun computeDoseFor(
    medication: String,
    useCase: String,
    weightKg: Double,
    ageYears: Int,
    routeDisplayName: String?
): DosingResult = DosingCalculator.compute(medication, useCase, ageYears, weightKg, route = routeDisplayName)


/** ml‑Berechnung aus mg und mg/ml – gibt null zurück, wenn Eingaben fehlen/ungenültig sind. */
fun computeVolumeMl(doseMg: Double?, concentrationMgPerMl: Double?): Double? {
    if (doseMg == null || concentrationMgPerMl == null || concentrationMgPerMl <= 0.0) return null
    return doseMg / concentrationMgPerMl
}
