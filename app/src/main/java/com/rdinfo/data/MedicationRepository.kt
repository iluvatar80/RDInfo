// app/src/main/java/com/rdinfo/data/MedicationRepository.kt
package com.rdinfo.data

import android.content.Context
import com.rdinfo.data.model.*

/**
 * Minimal, kompilierendes Repository **ohne** Abhängigkeit auf MedicationAssetRepository.
 *
 * Liefert eine feste Beispiel-Medikation (Adrenalin) mit zwei Use-Cases.
 * Ziel: die letzten "Unresolved reference: MedicationAssetRepository"-Fehler eliminieren.
 *
 * Du kannst dieses Stub später wieder durch den Asset-Loader ersetzen.
 */
object MedicationRepository {

    private val sample: List<Medication> by lazy {
        val adrenalin = Medication(
            id = "adrenalin",
            name = "Adrenalin",
            ampoule = AmpouleStrength(mg = 1.0, ml = 1.0), // Standardampulle 1 mg / 1 ml
            useCases = listOf(
                // Reanimation
                UseCase(
                    id = "rea",
                    name = "Reanimation",
                    defaultRoute = "i.v.",
                    routes = listOf(
                        RouteSpec(
                            route = "i.v.",
                            rules = listOf(
                                // < 12 J.: 0,01 mg/kg
                                DosingRule(
                                    priority = 2,
                                    age = AgeRange(minMonths = null, maxMonthsExclusive = 12 * 12),
                                    calc = DoseCalc(type = "perKg", mgPerKg = 0.01),
                                    dilution = Dilution(solutionText = "NaCl 0,9 %", totalVolumeMl = 10.0),
                                    hint = "Kinder: 0,01 mg/kg i.v.; auf 10 ml mit NaCl 0,9 % verdünnen"
                                ),
                                // ≥ 12 J.: 1 mg
                                DosingRule(
                                    priority = 1,
                                    age = AgeRange(minMonths = 12 * 12, maxMonthsExclusive = null),
                                    calc = DoseCalc(type = "fixed", fixedMg = 1.0),
                                    dilution = Dilution(solutionText = "NaCl 0,9 %", totalVolumeMl = 10.0),
                                    hint = "Erw.: 1 mg i.v.; auf 10 ml mit NaCl 0,9 % verdünnen"
                                )
                            )
                        )
                    )
                ),
                // Anaphylaxie
                UseCase(
                    id = "ana",
                    name = "Anaphylaxie",
                    defaultRoute = "i.m.",
                    routes = listOf(
                        RouteSpec(
                            route = "i.m.",
                            rules = listOf(
                                // 0,01 mg/kg i.m. (ohne Verdünnung)
                                DosingRule(
                                    priority = 1,
                                    calc = DoseCalc(type = "perKg", mgPerKg = 0.01, minMg = 0.05, maxMg = 0.5),
                                    hint = "0,01 mg/kg i.m. (min 0,05 mg / max 0,5 mg)"
                                )
                            )
                        ),
                        RouteSpec(
                            route = "i.v.",
                            rules = listOf(
                                DosingRule(
                                    priority = 1,
                                    calc = DoseCalc(type = "perKg", mgPerKg = 0.001, maxMg = 0.1),
                                    dilution = Dilution(solutionText = "NaCl 0,9 %", totalVolumeMl = 10.0),
                                    hint = "i.v. Titration: 0,001 mg/kg (max 0,1 mg), verdünnt auf 10 ml"
                                )
                            )
                        )
                    )
                )
            ),
            info = InfoTexts(
                indication = "Reanimation, Anaphylaxie",
                contraindication = "Keine bei vitaler Indikation",
                effect = "α-/β-adrenerge Wirkung",
                sideEffect = "Tachykardie, Hypertonie, Tremor"
            ),
            notes = null,
            version = 1
        )
        listOf(adrenalin)
    }

    // ------------------------------- Public API -------------------------------------------

    fun listMedications(@Suppress("UNUSED_PARAMETER") context: Context): List<Medication> = sample

    fun getMedicationById(@Suppress("UNUSED_PARAMETER") context: Context, id: String): Medication? =
        sample.firstOrNull { it.id == id }

    fun useCasesFor(context: Context, medicationId: String): List<UseCase> =
        getMedicationById(context, medicationId)?.useCases ?: emptyList()

    fun routesFor(context: Context, medicationId: String, useCaseId: String): List<String> =
        getMedicationById(context, medicationId)
            ?.useCases?.firstOrNull { it.id == useCaseId }
            ?.routes?.map { it.route }
            ?: emptyList()

    fun infoTextsFor(context: Context, medicationId: String): InfoTexts? =
        getMedicationById(context, medicationId)?.info
}

/** Enum für die Info-Tabs in der UI. */
enum class MedicationInfoSections { Indication, Contraindication, Effect, SideEffect }
