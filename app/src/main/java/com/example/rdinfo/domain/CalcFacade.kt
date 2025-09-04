// File: app/src/main/java/com/example/rdinfo/domain/CalcFacade.kt
package com.example.rdinfo.domain

import com.example.rdinfo.data.local.DoseRuleEntity
import java.util.Locale

/** Kompakte Fassade für UI: nimmt Rule + Eingaben, gibt formatierte Strings zurück. */
object CalcFacade {

    data class Ui(
        val doseText: String,           // z. B. "1,0 mg" oder "0,2 mg"
        val volumeText: String,         // z. B. "10,0 ml"
        val concentrationText: String,  // z. B. "0,1 mg/ml"
        val cappedByMax: Boolean
    )

    /**
     * @param stockConcMgPerMl  Ampullenkonzentration aus der Formulierung (mg/mL)
     * @param manualConcMgPerMl Manueller Override (mg/mL), hat Vorrang, wenn gesetzt
     */
    fun computeUi(
        rule: DoseRuleEntity,
        weightKg: Double?,
        stockConcMgPerMl: Double?,
        manualConcMgPerMl: Double?
    ): Ui {
        val res = DoseMath.compute(
            rule = rule,
            weightKg = weightKg,
            stockConcMgPerMl = stockConcMgPerMl,
            overrideConcMgPerMl = manualConcMgPerMl
        )

        val doseText = res.doseMg?.let { format(it, "mg") } ?: "—"
        val volText = res.volumeMl?.let { format(it, "ml") } ?: "—"
        val concText = res.concentrationUsedMgPerMl?.let { format(it, "mg/ml") } ?: "—"
        return Ui(doseText, volText, concText, res.cappedByMax)
    }

    private fun format(value: Double, unit: String): String {
        // Deutsche Locale: Komma als Dezimaltrenner
        val s = String.format(Locale.GERMANY, "%.1f", value)
        return "$s $unit"
    }
}
