// File: app/src/main/java/com/rdinfo/logic/DosingCalculator.kt
package com.rdinfo.logic

import kotlin.math.roundToInt

/**
 * Universeller, eigenständiger Dosierrechner ohne Abhängigkeiten zu anderen Projektteilen.
 *
 * Ziel: Die bisherigen Compilerfehler in dieser Datei beseitigen. Die API ist bewusst klein
 * und stabil – andere Klassen können sie unkompliziert verwenden.
 */
object DosingCalculator {

    /** Gewichtsbasierte Dosierung (mg/kg oder µg/kg). Nur eine der beiden Größen setzen. */
    data class WeightBasedDose(
        val mgPerKg: Double? = null,
        val mcgPerKg: Double? = null,
    )

    /** Konzentration einer Lösung (mg/ml oder µg/ml). Nur eine der beiden Größen setzen. */
    data class Concentration(
        val mgPerMl: Double? = null,
        val mcgPerMl: Double? = null,
    )

    /** Ergebnis der Berechnung. */
    data class Result(
        val targetDoseMg: Double,      // berechnete Zieldosis in mg
        val requiredVolumeMl: Double,  // benötigtes Volumen in ml (unge­rundet)
        val roundedVolumeMl: Double,   // Volumen gerundet auf "volumeRoundingStepMl"
    )

    /**
     * Rechnet eine absolute oder gewichtsbasierte Dosis in ein Volumen um.
     *
     * @param weightKg             Körpergewicht (kg), nur nötig bei gewichtsbasierter Dosis
     * @param absoluteDoseMg       absolute Dosis (mg) – alternativ zu [weightDose]
     * @param weightDose           gewichtsbasierte Dosis (mg/kg oder µg/kg)
     * @param concentration        Konzentration (mg/ml oder µg/ml)
     * @param volumeRoundingStepMl Rundungsschritt (Default 0.1 ml)
     */
    fun calc(
        weightKg: Double?,
        absoluteDoseMg: Double? = null,
        weightDose: WeightBasedDose? = null,
        concentration: Concentration,
        volumeRoundingStepMl: Double = 0.1,
    ): Result {
        val doseMg = resolveDoseMg(weightKg, absoluteDoseMg, weightDose)
        val mgPerMl = resolveMgPerMl(concentration)
        val volume = doseMg / mgPerMl
        val rounded = roundToStep(volume, volumeRoundingStepMl)
        return Result(
            targetDoseMg = doseMg,
            requiredVolumeMl = volume,
            roundedVolumeMl = rounded,
        )
    }

    // ---- Helpers ----

    private fun resolveDoseMg(
        weightKg: Double?,
        absoluteDoseMg: Double?,
        weightDose: WeightBasedDose?,
    ): Double {
        absoluteDoseMg?.let { return it }
        if (weightKg != null && weightDose != null) {
            weightDose.mgPerKg?.let { return it * weightKg }
            weightDose.mcgPerKg?.let { return (it / 1000.0) * weightKg }
        }
        error("No dose provided (need absoluteDoseMg or weightDose + weightKg)")
    }

    private fun resolveMgPerMl(concentration: Concentration): Double = when {
        concentration.mgPerMl != null -> concentration.mgPerMl
        concentration.mcgPerMl != null -> concentration.mcgPerMl / 1000.0
        else -> error("No concentration provided (need mg/ml or µg/ml)")
    }

    private fun roundToStep(value: Double, step: Double): Double {
        if (step <= 0.0) return value
        return (value / step).roundToInt() * step
    }
}
