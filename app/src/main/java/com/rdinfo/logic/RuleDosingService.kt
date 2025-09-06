// app/src/main/java/com/rdinfo/logic/RuleDosingService.kt
package com.rdinfo.logic

import android.content.Context
import com.rdinfo.data.MedicationAssetRepository
import com.rdinfo.data.model.AmpouleStrength
import com.rdinfo.data.model.Medication

/**
 * Fassade für das regelgetriebene Dosieren:
 *  - Lädt & cached Medikamente aus assets/medications.json
 *  - Bietet einfache calculate(...)-Funktion für die UI
 */
object RuleDosingService {

    @Volatile
    private var cached: List<Medication>? = null

    fun getMedications(context: Context): List<Medication> {
        val existing = cached
        if (existing != null) return existing
        val loaded = MedicationAssetRepository.load(context.applicationContext)
        cached = loaded
        return loaded
    }

    fun getMedicationById(context: Context, id: String): Medication? =
        getMedications(context).firstOrNull { it.id == id }

    fun listUseCases(context: Context, medicationId: String): List<Pair<String, String>> =
        getMedicationById(context, medicationId)
            ?.useCases
            ?.map { it.id to it.name }
            ?: emptyList()

    fun listRoutes(context: Context, medicationId: String, useCaseId: String): List<String> =
        getMedicationById(context, medicationId)
            ?.useCases?.firstOrNull { it.id == useCaseId }
            ?.routes?.map { it.route }
            ?: emptyList()

    /**
     * Wählt Route in folgender Reihenfolge aus:
     * 1) exactMatch (falls vorhanden)
     * 2) defaultRoute des Use-Cases
     * 3) erste Route des Use-Cases
     */
    private fun pickRoute(med: Medication, useCaseId: String, preferred: String?): String? {
        val uc = med.useCases.firstOrNull { it.id == useCaseId } ?: return null
        val routes = uc.routes.map { it.route }
        return when {
            preferred != null && routes.contains(preferred) -> preferred
            uc.defaultRoute != null && routes.contains(uc.defaultRoute) -> uc.defaultRoute
            routes.isNotEmpty() -> routes.first()
            else -> null
        }
    }

    /**
     * Haupt-Entry-Point für die UI: berechnet die Dosierung anhand der Regeln.
     */
    fun calculate(
        context: Context,
        medicationId: String,
        useCaseId: String,
        routeOrNull: String?,
        ageMonths: Int,
        weightKg: Double?,
        manualAmpoule: AmpouleStrength? = null
    ): RuleDosingCalculator.Result {
        val med = getMedicationById(context, medicationId)
            ?: return RuleDosingCalculator.Result(false, "Medikament nicht gefunden: $medicationId")

        val route = pickRoute(med, useCaseId, routeOrNull)
            ?: return RuleDosingCalculator.Result(false, "Keine Route für Use-Case: $useCaseId")

        return RuleDosingCalculator.calculate(
            RuleDosingCalculator.Input(
                medication = med,
                useCaseId = useCaseId,
                route = route,
                ageMonths = ageMonths,
                weightKg = weightKg,
                manualAmpoule = manualAmpoule
            )
        )
    }

    /** Test/Debug-Helfer */
    fun clearCache() { cached = null }
}
