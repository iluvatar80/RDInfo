// app/src/main/java/com/rdinfo/CompatAliases.kt
package com.rdinfo

import android.content.Context

// -----------------------------------------------------------------------------
//  Minimaler, *autarker* Kompatibilitäts-Layer nur für MainActivity
//  (keine Abhängigkeit zu anderen Dateien) – damit der Build wieder läuft.
// -----------------------------------------------------------------------------

enum class MedicationInfoSections { Indication, Contraindication, Effect, SideEffect }

data class Medication(val id: String, val name: String)

data class UseCase(val id: String, val name: String)

data class InfoTexts(
    val indication: String = "",
    val contraindication: String = "",
    val effect: String = "",
    val sideEffect: String = ""
)

object MedicationRepository {
    fun listMedications(@Suppress("UNUSED_PARAMETER") context: Context): List<Medication> =
        listOf(Medication(id = "adrenalin", name = "Adrenalin"))

    fun getMedicationById(@Suppress("UNUSED_PARAMETER") context: Context, id: String): Medication? =
        listMedications(context).firstOrNull { it.id == id }

    fun useCasesFor(@Suppress("UNUSED_PARAMETER") context: Context, medicationId: String): List<UseCase> =
        listOf(
            UseCase(id = "rea", name = "Reanimation"),
            UseCase(id = "ana", name = "Anaphylaxie")
        )

    fun routesFor(
        @Suppress("UNUSED_PARAMETER") context: Context,
        @Suppress("UNUSED_PARAMETER") medicationId: String,
        useCaseId: String
    ): List<String> = when (useCaseId) {
        "rea" -> listOf("i.v.")
        "ana" -> listOf("i.m.", "i.v.")
        else -> emptyList()
    }

    fun infoTextsFor(@Suppress("UNUSED_PARAMETER") context: Context, @Suppress("UNUSED_PARAMETER") medicationId: String): InfoTexts =
        InfoTexts(
            indication = "Akute Reanimation / Anaphylaxie",
            contraindication = "Keine bei vitaler Indikation",
            effect = "α-/β-adrenerge Wirkung",
            sideEffect = "Tachykardie, Hypertonie, Tremor"
        )
}

// Einheit für alte UI-Stellen
const val mg: String = "mg"

// -------------------------------------------------------------
//  Legacy-Helper mit sehr einfacher Logik (kompilierbar/robust)
// -------------------------------------------------------------

fun recommendedConcMgPerMl(
    @Suppress("UNUSED_PARAMETER") context: Context,
    medicationId: String,
    @Suppress("UNUSED_PARAMETER") useCaseId: String?,
    @Suppress("UNUSED_PARAMETER") routeOrNull: String?,
    @Suppress("UNUSED_PARAMETER") ageYears: Int?,
    @Suppress("UNUSED_PARAMETER") ageMonthsRemainder: Int?,
    manualAmpMg: Double?,
    manualAmpMl: Double?
): Double? {
    // Wenn manuelle Ampulle gesetzt → deren mg/ml
    if (manualAmpMg != null && manualAmpMl != null && manualAmpMl > 0.0) return manualAmpMg / manualAmpMl
    // Fallback: Standard-Ampulle Adrenalin 1 mg / 1 ml
    return if (medicationId == "adrenalin") 1.0 else null
}

fun computeDoseFor(
    @Suppress("UNUSED_PARAMETER") context: Context,
    medicationId: String,
    useCaseId: String,
    @Suppress("UNUSED_PARAMETER") routeOrNull: String?,
    ageYears: Int,
    @Suppress("UNUSED_PARAMETER") ageMonthsRemainder: Int,
    weightKg: Double?,
    @Suppress("UNUSED_PARAMETER") manualAmpMg: Double?,
    @Suppress("UNUSED_PARAMETER") manualAmpMl: Double?
): Double? {
    if (medicationId != "adrenalin") return null
    return when (useCaseId) {
        "rea" -> if (ageYears < 12) (weightKg ?: return null) * 0.01 else 1.0
        "ana" -> (weightKg ?: return null) * 0.01
        else -> null
    }
}

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
    val dose = computeDoseFor(context, medicationId, useCaseId, routeOrNull, ageYears, ageMonthsRemainder, weightKg, manualAmpMg, manualAmpMl)
        ?: return null
    val conc = recommendedConcMgPerMl(context, medicationId, useCaseId, routeOrNull, ageYears, ageMonthsRemainder, manualAmpMg, manualAmpMl)
        ?: return null
    return dose / conc
}

fun defaultConcentration(
    @Suppress("UNUSED_PARAMETER") context: Context,
    medicationId: String
): Double? = if (medicationId == "adrenalin") 1.0 else null
