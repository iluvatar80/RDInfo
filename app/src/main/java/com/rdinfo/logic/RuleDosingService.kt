// app/src/main/java/com/rdinfo/logic/RuleDosingService.kt
package com.rdinfo.logic

import android.content.Context
import com.rdinfo.data.MedicationRepository
import com.rdinfo.data.model.AmpouleStrength
import com.rdinfo.data.model.Dilution
import com.rdinfo.data.model.DoseCalc
import com.rdinfo.data.model.DosingRule
import com.rdinfo.data.model.Medication
import kotlin.math.max
import kotlin.math.min

/**
 * Lädt Regeln über das Repository und führt die Berechnung aus.
 * Keine UI-Formatierung – gibt nur Zahlen + optionale Texte zurück.
 */
object RuleDosingService {

    data class CalcResult(
        val ok: Boolean,
        val error: String? = null,
        val doseMg: Double? = null,
        val concentrationMgPerMl: Double? = null,
        val volumeMl: Double? = null,
        val solutionText: String? = null,
        val totalVolumeMl: Double? = null,
        val ruleHint: String? = null,
    )

    fun getMedicationById(context: Context, id: String): Medication? =
        MedicationRepository.getMedicationById(context, id)

    fun calculate(
        context: Context,
        medicationId: String,
        useCaseId: String,
        routeOrNull: String?,
        ageMonths: Int,
        weightKg: Double?,
        manualAmpoule: AmpouleStrength?
    ): CalcResult {
        val med = getMedicationById(context, medicationId)
            ?: return CalcResult(false, error = "Medication not found")

        val useCase = med.useCases.firstOrNull { it.id == useCaseId }
            ?: return CalcResult(false, error = "Use-Case not found")

        val routeSpec = (routeOrNull ?: useCase.defaultRoute)
            ?.let { r -> useCase.routes.firstOrNull { it.route == r } }
            ?: useCase.routes.firstOrNull()
            ?: return CalcResult(false, error = "Route not found")

        val rule = selectRule(routeSpec.rules, ageMonths, weightKg, manualAmpoule)
            ?: return CalcResult(false, error = "No matching rule")

        val dose = computeDose(rule.calc, weightKg) ?: return CalcResult(false, error = "Missing weight for perKg rule")

        val (conc, totalVol, solText) = computeConcentration(med.ampoule, rule.dilution, manualAmpoule)
        val volume = if (conc != null && conc > 0.0) dose / conc else null

        return CalcResult(
            ok = true,
            doseMg = dose,
            concentrationMgPerMl = conc,
            volumeMl = volume,
            solutionText = solText,
            totalVolumeMl = totalVol,
            ruleHint = rule.hint
        )
    }

    // --- intern ---------------------------------------------------------------------------

    private fun selectRule(
        rules: List<DosingRule>,
        ageMonths: Int,
        weightKg: Double?,
        manualAmpoule: AmpouleStrength?
    ): DosingRule? {
        return rules
            .filter { r ->
                val ageOk = r.age?.let { a ->
                    val minOk = a.minMonths?.let { ageMonths >= it } ?: true
                    val maxOk = a.maxMonthsExclusive?.let { ageMonths < it } ?: true
                    minOk && maxOk
                } ?: true

                val weightOk = r.weight?.let { w ->
                    val minOk = w.minKg?.let { (weightKg ?: Double.NEGATIVE_INFINITY) >= it } ?: true
                    val maxOk = w.maxKgExclusive?.let { (weightKg ?: Double.POSITIVE_INFINITY) < it } ?: true
                    minOk && maxOk
                } ?: true

                val manualOk = r.conditions?.requiresManualAmpoule?.let { need ->
                    if (need) manualAmpoule != null else true
                } ?: true

                ageOk && weightOk && manualOk
            }
            .sortedByDescending { it.priority }
            .firstOrNull()
    }

    private fun computeDose(calc: DoseCalc, weightKg: Double?): Double? {
        val raw = when (calc.type) {
            "perKg" -> weightKg?.let { kg -> (calc.mgPerKg ?: 0.0) * kg }
            "fixed" -> calc.fixedMg
            else -> null
        } ?: return null
        val minC = calc.minMg ?: raw
        val maxC = calc.maxMg ?: raw
        return raw.coerceIn(min(minC, maxC), max(minC, maxC))
    }

    private fun computeConcentration(
        defaultAmpoule: AmpouleStrength,
        dilution: Dilution?,
        manualAmpoule: AmpouleStrength?
    ): Triple<Double?, Double?, String?> {
        val base = manualAmpoule ?: defaultAmpoule
        val baseConc = if (base.ml > 0.0) base.mg / base.ml else null
        val totalVol = dilution?.totalVolumeMl
        val solution = dilution?.solutionText

        val effConc = if (totalVol != null && totalVol > 0.0) {
            // Verdünnung auf Gesamtvolumen → mg / totalVol
            base.mg / totalVol
        } else baseConc

        return Triple(effConc, totalVol, solution)
    }
}
