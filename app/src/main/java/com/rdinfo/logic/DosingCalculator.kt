// app/src/main/java/com/rdinfo/logic/DosingCalculator.kt
package com.rdinfo.logic

import android.content.Context
import com.rdinfo.data.model.AmpouleStrength
import java.text.NumberFormat
import java.util.Locale

/**
 * Regelgetriebener DosingCalculator (berechnet mg/ml/Volumen) –
 * nur noch als Fassade um RuleDosingService. KEINE Top‑Level‑Funktionen mehr hier.
 */
object DosingCalculator {

    // --- rohe Werte (Zahlen) --------------------------------------------------------------
    data class RawResult(
        val ok: Boolean,
        val error: String? = null,
        val doseMg: Double? = null,
        val concentrationMgPerMl: Double? = null,
        val volumeMl: Double? = null,
        val solutionText: String? = null,
        val totalVolumeMl: Double? = null,
        val hint: String? = null
    )

    fun calculateRaw(
        context: Context,
        medicationId: String,
        useCaseId: String,
        routeOrNull: String?,
        ageYears: Int,
        ageMonthsRemainder: Int,
        weightKg: Double?,
        manualAmpMg: Double?,
        manualAmpMl: Double?
    ): RawResult {
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

        if (!res.ok) return RawResult(false, error = res.error)

        return RawResult(
            ok = true,
            doseMg = res.doseMg,
            concentrationMgPerMl = res.concentrationMgPerMl,
            volumeMl = res.volumeMl,
            solutionText = res.solutionText,
            totalVolumeMl = res.totalVolumeMl,
            hint = res.ruleHint
        )
    }

    // --- formatierte Werte (für UI) -------------------------------------------------------
    data class UiResult(
        val ok: Boolean,
        val error: String? = null,
        val doseMg: String? = null,
        val concentration: String? = null,
        val volumeMl: String? = null,
        val solution: String? = null,
        val total: String? = null,
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

    fun calculateUi(
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
        val raw = calculateRaw(
            context,
            medicationId,
            useCaseId,
            routeOrNull,
            ageYears,
            ageMonthsRemainder,
            weightKg,
            manualAmpMg,
            manualAmpMl
        )
        if (!raw.ok) return UiResult(false, error = raw.error)

        val doseStr = raw.doseMg?.let { valueUnit(fmt(it, maxFrac = 2), "mg") }
        val concStr = raw.concentrationMgPerMl?.let { valueUnit(fmt(it, maxFrac = 2), "mg/ml") }
        val volStr = raw.volumeMl?.let { valueUnit(fmt(it, maxFrac = 2), "ml") }
        val totalStr = raw.totalVolumeMl?.let { valueUnit(fmt(it, maxFrac = 1), "ml") }
        val solution = when {
            raw.solutionText != null && totalStr != null -> "${raw.solutionText} (auf $totalStr)"
            raw.solutionText != null -> raw.solutionText
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
            hint = raw.hint
        )
    }
}
