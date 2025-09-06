// app/src/main/java/com/rdinfo/logic/RuleDosingUiAdapter.kt
package com.rdinfo.logic

import android.content.Context
import com.rdinfo.data.model.AmpouleStrength
import java.text.NumberFormat
import java.util.Locale

/** UI-Adapter: vereinfacht den Aufruf aus der Compose-UI und formatiert Werte/Einheiten. */
object RuleDosingUiAdapter {

    data class UiResult(
        val ok: Boolean,
        val error: String? = null,
        val doseMg: String? = null,
        val concentration: String? = null, // z. B. "0,20 mg/ml"
        val volumeMl: String? = null,      // z. B. "1,40 ml"
        val solution: String? = null,      // z. B. "NaCl 0,9 % (auf 10,0 ml)"
        val total: String? = null,         // z. B. "10,0 ml" (Gesamtvolumen)
        val hint: String? = null
    )

    private val localeDE: Locale = Locale.GERMANY
    private const val NBSP = " "

    private fun fmt(value: Double, minFrac: Int = 0, maxFrac: Int = 2): String {
        val nf = NumberFormat.getNumberInstance(localeDE).apply {
            minimumFractionDigits = minFrac
            maximumFractionDigits = maxFrac
        }
        return nf.format(value)
    }

    private fun valueUnit(value: String, unit: String): String = "$value$NBSP$unit"

    /**
     * Komfort-API für die UI. `ageYears` + `ageMonthsRemainder` werden in Monate umgerechnet.
     * `manualAmpMg/ml` sind optional; wenn beide gesetzt → manuelle Ampulle aktiv.
     */
    fun compute(
        context: Context,
        medicationId: String,
        useCaseId: String,
        routeOrNull: String?,
        ageYears: Int,
        ageMonthsRemainder: Int,
        weightKg: Double?,
        manualAmpMg: Double?,
        manualAmpMl: Double?
    ): UiResult {
        val ageMonths = (ageYears * 12) + ageMonthsRemainder
        val manualAmp = if (manualAmpMg != null && manualAmpMl != null) {
            AmpouleStrength(manualAmpMg, manualAmpMl)
        } else null

        val res = RuleDosingService.calculate(
            context = context,
            medicationId = medicationId,
            useCaseId = useCaseId,
            routeOrNull = routeOrNull,
            ageMonths = ageMonths,
            weightKg = weightKg,
            manualAmpoule = manualAmp
        )

        if (!res.ok) return UiResult(false, error = res.error)

        val doseStr = res.doseMg?.let { valueUnit(fmt(it, maxFrac = 2), "mg") }
        val concStr = res.concentrationMgPerMl?.let { valueUnit(fmt(it, maxFrac = 2), "mg/ml") }
        val volStr = res.volumeMl?.let { valueUnit(fmt(it, maxFrac = 2), "ml") }

        val totalStr = res.totalVolumeMl?.let { valueUnit(fmt(it, maxFrac = 1), "ml") }
        val solution = when {
            res.solutionText != null && totalStr != null -> "${res.solutionText} (auf $totalStr)"
            res.solutionText != null -> res.solutionText
            totalStr != null -> totalStr
            else -> null
        }

        return UiResult(
            ok = true,
            doseMg = doseStr,
            concentration = concStr,
            volumeMl = volStr,
            solution = solution,
            total = totalStr,
            hint = res.ruleHint
        )
    }
}
