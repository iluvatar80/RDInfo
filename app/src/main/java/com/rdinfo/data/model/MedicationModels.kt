// app/src/main/java/com/rdinfo/data/model/MedicationModels.kt
package com.rdinfo.data.model

/**
 * Regelgetriebenes Datenmodell für Medikamente, Einsatzfälle (Use-Cases),
 * Applikationsarten (Routes) und Dosierungsregeln – ohne Hardcoding.
 *
 * JSON-freundlich gehalten (nur Datenklassen, keine sealed classes),
 * damit ein Parser (kotlinx-serialization/Moshi/etc.) später leicht
 * ergänzt werden kann.
 */

data class Medication(
    val id: String,                       // z.B. "adrenalin"
    val name: String,                     // Anzeigename
    val ampoule: AmpouleStrength,         // Standard-Ampullenkonzentration (mg / ml)
    val useCases: List<UseCase>,          // Einsatzfälle (z. B. Reanimation, Anaphylaxie)
    val info: InfoTexts? = null,          // Indikation/Kontraindikation/Wirkung/Nebenwirkung (global fürs Medikament)
    val notes: String? = null,            // Freitext-Hinweise
    val version: Int = 1                  // Daten-/Schemasversion für Migrationen
)

data class AmpouleStrength(
    val mg: Double,   // Wirkstoff (mg) pro Ampulle
    val ml: Double    // Volumen (ml) pro Ampulle
)

data class UseCase(
    val id: String,                        // z.B. "rea", "ana"
    val name: String,                      // z.B. "Reanimation", "Anaphylaxie"
    val routes: List<RouteSpec>,           // Applikationsarten mit Regeln
    val defaultRoute: String? = null,      // bevorzugte Route (optional)
    val info: InfoTexts? = null,           // optionale Infos speziell für diesen Use-Case
    val notes: String? = null              // optionale Hinweise
)

data class RouteSpec(
    val route: String,                     // z.B. "i.v.", "i.o.", "i.m.", "inhalativ", "s.c."
    val rules: List<DosingRule>,           // beliebig viele Regeln; Auswahl per Alter/Gewicht/Priorität
    val notes: String? = null              // optionale Hinweise zur Route
)

data class DosingRule(
    val id: String? = null,                // optionale Regel-ID (Debug/Tracing)
    val priority: Int = 0,                 // höhere Zahl gewinnt bei Überlappungen
    val age: AgeRange? = null,             // Altersbereich in Monaten (exkl. Obergrenze)
    val weight: WeightRange? = null,       // Gewichtsbereich in kg (exkl. Obergrenze)
    val calc: DoseCalc,                    // Dosierformel (perKg/fixed)
    val dilution: Dilution? = null,        // Verdünnung/Lösung (Text + Zielvolumen)
    val conditions: Conditions? = null,    // z. B. nur bei manueller Ampulle
    val hint: String? = null,              // kurzer UI-Hinweistext
    val rounding: Rounding? = null,        // Rundung/Anzeige für mg/ml
    val repeats: Repetition? = null,       // Wiederholung (alle X Minuten, max n-mal)
    val maxCumulativeMgPerEvent: Double? = null // Maximaldosis (mg) pro Einsatz
)

data class AgeRange(
    val minMonths: Int? = null,            // inklusiv; null = keine Untergrenze
    val maxMonthsExclusive: Int? = null    // exklusiv; null = keine Obergrenze
)

data class WeightRange(
    val minKg: Double? = null,             // inklusiv
    val maxKgExclusive: Double? = null     // exklusiv
)

/**
 * type = "perKg" oder "fixed".
 *  - perKg: mgPerKg (+ optionale Kappungen minMg/maxMg)
 *  - fixed: fixedMg (mg)
 */
data class DoseCalc(
    val type: String,                // "perKg" | "fixed"
    val mgPerKg: Double? = null,     // nur für type=perKg
    val fixedMg: Double? = null,     // nur für type=fixed
    val minMg: Double? = null,       // optionale Untergrenze (mg)
    val maxMg: Double? = null        // optionale Obergrenze (mg)
)

/**
 * Verdünnung/Lösung: Wenn gesetzt, wird vor Applikation auf totalVolumeMl aufgezogen.
 * solutionText ist reiner Anzeige-/Doku-Text (z. B. "NaCl 0,9 %").
 * Ist beides null, findet keine Verdünnung statt.
 */
data class Dilution(
    val solutionText: String? = null,   // Anzeige, z. B. "NaCl 0,9 %"
    val totalVolumeMl: Double? = null   // Ziel-Gesamtvolumen, z. B. 10.0
)

/** Zusätzliche Bedingungen zur Regelaktivierung. */
data class Conditions(
    val requiresManualAmpoule: Boolean? = null // true → Regel gilt nur, wenn der Nutzer eine manuelle Ampulle eingibt
)

/**
 * Rundung/Anzeigeformatierung (optional).
 * mgStep/mlStep: Schrittweite fürs Runden (z. B. 0.01 mg, 0.1 ml).
 */
data class Rounding(
    val mgStep: Double? = null,
    val mlStep: Double? = null,
    val showTrailingZeros: Boolean? = null
)

/** Texte für Info-Buttons (Anzeigeebene). */
data class InfoTexts(
    val indication: String? = null,
    val contraindication: String? = null,
    val effect: String? = null,
    val sideEffect: String? = null
)

/** Wiederholungsangaben (optional). */
data class Repetition(
    val repeatAllowed: Boolean? = null,
    val minIntervalMinutes: Int? = null,
    val maxRepeats: Int? = null
)
