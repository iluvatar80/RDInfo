// Zielpfad: app/src/main/java/com/rdinfo/logic/DosingCalculator.kt
// NEUE DATEI: Reine Berechnungslogik außerhalb der MainActivity

package com.rdinfo.logic

import kotlin.math.min

/** Ergebnis einer Dosisberechnung. */
data class DosingResult(
    val mg: Double?,         // berechnete Dosis in mg (null, wenn nicht anwendbar)
    val hint: String         // Applikations-/Hinweistext passend zu Medikament/Use-Case
)

/**
 * Zentrale, erweiterbare Berechnung. Keine UI-/Compose-Abhängigkeiten.
 * - medication: Anzeigename (z. B. "Adrenalin")
 * - useCase: Anzeigename (z. B. "Reanimation")
 * - weightKg: effektives Gewicht in kg (manuell > Schätzung)
 */
object DosingCalculator {

    fun computeDoseFor(medication: String, useCase: String, weightKg: Double): DosingResult {
        return when (medication) {
            "Adrenalin" -> adrenalinRules(useCase, weightKg)
            "Amiodaron" -> DosingResult(null, "Dosisregeln für Amiodaron werden separat hinterlegt.")
            "Atropin"   -> DosingResult(null, "Dosisregeln für Atropin werden separat hinterlegt.")
            else         -> DosingResult(null, "Keine Dosisregeln hinterlegt für $medication.")
        }
    }

    private fun adrenalinRules(useCase: String, weightKg: Double): DosingResult = when (useCase) {
        "Reanimation" -> {
            // Kinder: 0,01 mg/kg IV/IO; (Erwachsene typ. fixe Dosis, hier nicht abgebildet)
            val doseMg = 0.01 * weightKg
            DosingResult(
                mg = doseMg,
                hint = "IV/IO: 0,01 mg/kg; bevorzugt Verdünnung 1:10 000 (0,1 mg/ml). Maximaldosen beachten."
            )
        }
        "Anaphylaxie" -> {
            // IM: 0,01 mg/kg, Deckelung 0,5 mg (einfacher Startwert)
            val doseRaw = 0.01 * weightKg
            val doseMg = min(doseRaw, 0.5)
            DosingResult(
                mg = doseMg,
                hint = "IM: 0,01 mg/kg (1 mg/ml), max. 0,5 mg pro Gabe. Seitenwechsel bei Mehrfachgaben."
            )
        }
        "Schwellung der oberen Atemwege" -> {
            // Häufig inhalativ/nebulisiert – mg-Berechnung unterschiedlich; hier zunächst Hinweis
            DosingResult(
                mg = null,
                hint = "Inhalativ/nebulisiert – Schema wird separat hinterlegt (Dosis abhängig vom Präparat)."
            )
        }
        else -> DosingResult(null, "Use-Case nicht hinterlegt: $useCase")
    }
}

/** Optionaler Helfer: Volumen-Berechnung in ml aus mg & Konzentration (mg/ml). */
fun computeVolumeMl(doseMg: Double?, concentrationMgPerMl: Double?): Double? {
    if (doseMg == null || concentrationMgPerMl == null || concentrationMgPerMl <= 0.0) return null
    return doseMg / concentrationMgPerMl
}

// Top‑Level Proxy, falls du einen fun-Import bevorzugst
fun computeDoseFor(medication: String, useCase: String, weightKg: Double): DosingResult =
    DosingCalculator.computeDoseFor(medication, useCase, weightKg)
