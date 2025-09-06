// app/src/main/java/com/rdinfo/CompatAliases.kt
package com.rdinfo

import android.content.Context
import com.rdinfo.data.MedicationRepository as DataRepo

// Re-export der Enum ins Paket `com.rdinfo` (für bestehende Verwendungen in MainActivity)
typealias MedicationInfoSections = com.rdinfo.data.MedicationInfoSections

/**
 * Wrapper-Repository im Paket `com.rdinfo` – delegiert an das neue Data-Repo.
 */
object MedicationRepository {
    fun listMedications(context: Context) = DataRepo.listMedications(context)
    fun getMedicationById(context: Context, id: String) = DataRepo.getMedicationById(context, id)
    fun useCasesFor(context: Context, medicationId: String) = DataRepo.useCasesFor(context, medicationId)
    fun routesFor(context: Context, medicationId: String, useCaseId: String) = DataRepo.routesFor(context, medicationId, useCaseId)
    fun infoTextsFor(context: Context, medicationId: String) = DataRepo.infoTextsFor(context, medicationId)
}

/** Einheit-String (Legacy-Kompatibilität). */
const val mg: String = "mg"

/**
 * Legacy-Helfer: berechnet die Dosis (mg) – delegiert auf die regelgetriebene Engine.
 */
fun computeDoseFor(
    context: Context,
    medicationId: String,
    useCaseId: String,
    routeOrNull: String?,
    ageYears: Int,
    ageMonthsRemainder: Int,
    weightKg: Double?,
    manualAmpMg: Double?,
    manualAmpMl: Double?
): Double? {
    val ageMonths = (ageYears * 12) + ageMonthsRemainder
    val manualAmp = if (manualAmpMg != null && manualAmpMl != null)
        com.rdinfo.data.model.AmpouleStrength(manualAmpMg, manualAmpMl) else null

    val res = com.rdinfo.logic.RuleDosingService.calculate(
        context = context,
        medicationId = medicationId,
        useCaseId = useCaseId,
        routeOrNull = routeOrNull,
        ageMonths = ageMonths,
        weightKg = weightKg,
        manualAmpoule = manualAmp
    )
    return res.doseMg
}

/**
 * Legacy-Helfer: berechnet das Volumen (ml) – delegiert auf die regelgetriebene Engine.
 */
fun computeVolumeMl(
    context: Context,
    medicationId: String,
    useCaseId: String,
    routeOrNull: String?,
    ageYears: Int,
    ageMonthsRemainder: Int,
    weightKg: Double?,
    manualAmpMg: Double?,
    manualAmpMl: Double?
): Double? {
    val ageMonths = (ageYears * 12) + ageMonthsRemainder
    val manualAmp = if (manualAmpMg != null && manualAmpMl != null)
        com.rdinfo.data.model.AmpouleStrength(manualAmpMg, manualAmpMl) else null

    val res = com.rdinfo.logic.RuleDosingService.calculate(
        context = context,
        medicationId = medicationId,
        useCaseId = useCaseId,
        routeOrNull = routeOrNull,
        ageMonths = ageMonths,
        weightKg = weightKg,
        manualAmpoule = manualAmp
    )
    return res.volumeMl
}

/**
 * Empfohlene (effektive) Konzentration mg/ml.
 * Mit Use-Case/Route/Alter → Engine; sonst Ampullen-Standard als Fallback.
 */
fun recommendedConcMgPerMl(
    context: Context,
    medicationId: String,
    useCaseId: String?,
    routeOrNull: String?,
    ageYears: Int?,
    ageMonthsRemainder: Int?,
    manualAmpMg: Double?,
    manualAmpMl: Double?
): Double? {
    if (useCaseId != null && ageYears != null && ageMonthsRemainder != null) {
        val ageMonths = (ageYears * 12) + ageMonthsRemainder
        val manualAmp = if (manualAmpMg != null && manualAmpMl != null)
            com.rdinfo.data.model.AmpouleStrength(manualAmpMg, manualAmpMl) else null
        val res = com.rdinfo.logic.RuleDosingService.calculate(
            context = context,
            medicationId = medicationId,
            useCaseId = useCaseId,
            routeOrNull = routeOrNull,
            ageMonths = ageMonths,
            weightKg = null,
            manualAmpoule = manualAmp
        )
        return res.concentrationMgPerMl
    }
    val med = DataRepo.getMedicationById(context, medicationId) ?: return null
    val base = if (manualAmpMg != null && manualAmpMl != null)
        com.rdinfo.data.model.AmpouleStrength(manualAmpMg, manualAmpMl) else med.ampoule
    return if (base.ml == 0.0) null else base.mg / base.ml
}

/**
 * Standard-Konzentration aus Ampulle (mg/ml).
 */
fun defaultConcentration(context: Context, medicationId: String): Double? {
    val med = DataRepo.getMedicationById(context, medicationId) ?: return null
    return if (med.ampoule.ml == 0.0) null else med.ampoule.mg / med.ampoule.ml
}
