// app/src/main/java/com/rdinfo/data/MedicationRepository.kt
package com.rdinfo.data

import android.content.Context
import com.rdinfo.data.model.InfoTexts
import com.rdinfo.data.model.Medication

/**
 * Kompatibilitäts-Repository für die UI.
 *
 * Bietet die bisherigen Namen/Einträge an, leitet intern an
 * MedicationAssetRepository weiter (assets/medications.json).
 */
object MedicationRepository {

    fun listMedications(context: Context): List<Medication> =
        MedicationAssetRepository.load(context)

    fun getMedicationById(context: Context, id: String): Medication? =
        MedicationAssetRepository.load(context).firstOrNull { it.id == id }

    fun useCasesFor(context: Context, medicationId: String) =
        getMedicationById(context, medicationId)?.useCases ?: emptyList()

    fun routesFor(context: Context, medicationId: String, useCaseId: String): List<String> =
        getMedicationById(context, medicationId)
            ?.useCases?.firstOrNull { it.id == useCaseId }
            ?.routes?.map { it.route }
            ?: emptyList()

    fun infoTextsFor(context: Context, medicationId: String): InfoTexts? =
        getMedicationById(context, medicationId)?.info
}

/**
 * Enum für die Info-Abschnitte (Tabs/Buttons) in der UI.
 * Hält die Namen stabil, damit bestehender Code weiter kompiliert.
 */
enum class MedicationInfoSections { Indication, Contraindication, Effect, SideEffect }
