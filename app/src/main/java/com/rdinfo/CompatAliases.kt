// app/src/main/java/com/rdinfo/CompatAliases.kt
package com.rdinfo

import android.content.Context
import com.rdinfo.data.MedicationRepository as NewMedicationRepository
import com.rdinfo.data.MedicationInfoSections as NewMedicationInfoSections
import com.rdinfo.logic.computeDoseFor as newComputeDoseFor
import com.rdinfo.logic.computeVolumeMl as newComputeVolumeMl
import com.rdinfo.logic.recommendedConcMgPerMl as newRecommendedConcMgPerMl
import com.rdinfo.logic.defaultConcentration as newDefaultConcentration

// Re-export der Enum für bestehenden Code im Paket `com.rdinfo`
typealias MedicationInfoSections = NewMedicationInfoSections

/**
 * Wrapper-Objekt im Paket `com.rdinfo`, damit bestehende Aufrufe wie
 * `MedicationRepository.*` weiter ohne Import funktionieren.
 */
object MedicationRepository {
    fun listMedications(context: Context) = NewMedicationRepository.listMedications(context)
    fun getMedicationById(context: Context, id: String) = NewMedicationRepository.getMedicationById(context, id)
    fun useCasesFor(context: Context, medicationId: String) = NewMedicationRepository.useCasesFor(context, medicationId)
    fun routesFor(context: Context, medicationId: String, useCaseId: String) = NewMedicationRepository.routesFor(context, medicationId, useCaseId)
    fun infoTextsFor(context: Context, medicationId: String) = NewMedicationRepository.infoTextsFor(context, medicationId)
}

/** Einheit-String (historisch als Konstante verwendet). */
const val mg: String = "mg"

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
) = newComputeDoseFor(
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
) = newComputeVolumeMl(
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

fun recommendedConcMgPerMl(
    context: Context,
    medicationId: String,
    useCaseId: String?,
    routeOrNull: String?,
    ageYears: Int?,
    ageMonthsRemainder: Int?,
    manualAmpMg: Double?,
    manualAmpMl: Double?
) = newRecommendedConcMgPerMl(
    context,
    medicationId,
    useCaseId,
    routeOrNull,
    ageYears,
    ageMonthsRemainder,
    manualAmpMg,
    manualAmpMl
)

fun defaultConcentration(
    context: Context,
    medicationId: String
) = newDefaultConcentration(context, medicationId)
