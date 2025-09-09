// Zielpfad: app/src/main/java/com/rdinfo/data/MedicationRepository.kt
// Vollständige Datei – dynamische Routen, Lösung+Gesamt in Regeln, kompatibel zur MainActivity.

package com.rdinfo.data

// ---- Use-Cases ----

enum class UseCaseKey {
    REANIMATION,
    ANAPHYLAXIE,
    OBERER_ATEMWEG_SCHWELLUNG,
    BRADYKARDIE
}

fun useCaseLabel(key: UseCaseKey): String {
    return when (key) {
        UseCaseKey.REANIMATION -> "Reanimation"
        UseCaseKey.ANAPHYLAXIE -> "Anaphylaxie"
        UseCaseKey.OBERER_ATEMWEG_SCHWELLUNG -> "Schwellung der oberen Atemwege"
        UseCaseKey.BRADYKARDIE -> "Bradykardie"
    }
}

fun useCaseKeyFromLabel(label: String): UseCaseKey? {
    return when (label) {
        "Reanimation" -> UseCaseKey.REANIMATION
        "Anaphylaxie" -> UseCaseKey.ANAPHYLAXIE
        "Schwellung der oberen Atemwege" -> UseCaseKey.OBERER_ATEMWEG_SCHWELLUNG
        "Bradykardie" -> UseCaseKey.BRADYKARDIE
        else -> null
    }
}

// ---- Datenmodelle ----

data class Concentration(
    val mg: Double,
    val ml: Double,
    val display: String
) {
    val mgPerMl: Double get() = if (ml > 0.0) mg / ml else 0.0
}

data class MedicationInfoSections(
    val indication: String,
    val contraindication: String,
    val effect: String,
    val sideEffects: String
)

/**
 * Dosisregel (reine Daten, keine Logik).
 * Mindestens eine der Größen [doseMgPerKg] oder [fixedDoseMg] muss gesetzt sein.
 * "solutionText" und "totalPreparedMl" werden für die Anzeige verwendet.
 */
data class DosingRule(
    val useCase: UseCaseKey,
    val ageMinYears: Int? = null,
    val ageMaxYears: Int? = null,
    val weightMinKg: Double? = null,
    val weightMaxKg: Double? = null,
    val doseMgPerKg: Double? = null,
    val fixedDoseMg: Double? = null,
    val maxDoseMg: Double? = null,
    val recommendedConcMgPerMl: Double? = null, // Arbeitskonzentration (mg/ml) nach evtl. Verdünnung
    val route: String? = null,                  // z. B. "IV/IO", "IM", "inhalativ/nebulisiert"
    val solutionText: String? = null,           // z. B. "NaCl 0,9 %"
    val totalPreparedMl: Double? = null,        // z. B. 10.0
    val note: String? = null
)

data class Medication(
    val id: String,
    val name: String,
    val defaultConcentration: Concentration,
    val concentrations: List<Concentration>,
    val useCases: List<UseCaseKey>,
    val info: MedicationInfoSections,
    val dosing: List<DosingRule> = emptyList()
)

// ---- Repository ----

object MedicationRepository {

    private val meds: List<Medication> = listOf(
        Medication(
            id = "adrenalin",
            name = "Adrenalin",
            defaultConcentration = Concentration(1.0, 1.0, "1 Ampulle (1 ml) = 1 mg"),
            concentrations = listOf(
                Concentration(1.0, 1.0, "1 mg / 1 ml (1:1000)"),
                Concentration(0.1, 1.0, "0,1 mg / 1 ml (1:10000) – verdünnt")
            ),
            useCases = listOf(
                UseCaseKey.REANIMATION,
                UseCaseKey.ANAPHYLAXIE,
                UseCaseKey.OBERER_ATEMWEG_SCHWELLUNG
            ),
            info = MedicationInfoSections(
                indication = "Reanimation; Anaphylaxie; Schwellung der oberen Atemwege",
                contraindication = "Bei vitaler Indikation keine absoluten Kontraindikationen.",
                effect = "α/β‑Sympathomimetikum: Vasokonstriktion, Bronchodilatation, positiv chrono-/inotrop.",
                sideEffects = "Tachykardie, Arrhythmien, Tremor, Hypertonie."
            ),
            dosing = listOf(
                // <12 J.: 0,01 mg/kg i.v.; 1 mg auf 10 ml (0,1 mg/ml)
                DosingRule(
                    useCase = UseCaseKey.REANIMATION,
                    ageMaxYears = 11,
                    doseMgPerKg = 0.01,
                    maxDoseMg = null,
                    recommendedConcMgPerMl = 0.1,         // 1:10 (0,1 mg/ml)
                    route = "IV/IO",
                    solutionText = "NaCl 0,9 %",
                    totalPreparedMl = 10.0,
                    note = "<12 J.: 0,01 mg/kg; 1:10 (0,1 mg/ml)."
                ),
                // ≥12 J. (Erwachsene) – Werte bitte aus PDF ergänzen (derzeit absichtlicher Fehlerhinweis)
                DosingRule(
                    useCase = UseCaseKey.REANIMATION,
                    ageMinYears = 12,
                    fixedDoseMg = null,                    // TODO: aus PDF hinterlegen
                    recommendedConcMgPerMl = 0.1,
                    route = "IV/IO",
                    solutionText = "NaCl 0,9 %",
                    totalPreparedMl = 10.0,
                    note = "Erw.: Reanimationsschema gemäß Leitlinie (Wert im Repo ergänzen)."
                ),
                // Anaphylaxie IM 0,01 mg/kg, max. 0,5 mg – 1 mg/ml (unverdünnt)
                DosingRule(
                    useCase = UseCaseKey.ANAPHYLAXIE,
                    doseMgPerKg = 0.01,
                    maxDoseMg = 0.5,
                    recommendedConcMgPerMl = 1.0,
                    route = "IM",
                    solutionText = null,
                    totalPreparedMl = null,
                    note = "IM: 0,01 mg/kg; max. 0,5 mg pro Gabe."
                ),
                // Schwellung obere Atemwege – Platzhalter
                DosingRule(
                    useCase = UseCaseKey.OBERER_ATEMWEG_SCHWELLUNG,
                    fixedDoseMg = null,
                    route = "inhalativ/nebulisiert",
                    solutionText = null,
                    totalPreparedMl = null,
                    note = "Schema präparateabhängig; getrennt zu hinterlegen."
                )
            )
        ),
        Medication(
            id = "amiodaron",
            name = "Amiodaron",
            defaultConcentration = Concentration(150.0, 3.0, "1 Ampulle (3 ml) = 150 mg"),
            concentrations = listOf(Concentration(150.0, 3.0, "150 mg / 3 ml")),
            useCases = listOf(UseCaseKey.REANIMATION),
            info = MedicationInfoSections(
                indication = "—",
                contraindication = "—",
                effect = "—",
                sideEffects = "—"
            ),
            dosing = emptyList()
        ),
        Medication(
            id = "atropin",
            name = "Atropin",
            defaultConcentration = Concentration(1.0, 1.0, "1 Ampulle (1 ml) = 1 mg"),
            concentrations = listOf(Concentration(1.0, 1.0, "1 mg / 1 ml")),
            useCases = listOf(UseCaseKey.BRADYKARDIE),
            info = MedicationInfoSections(
                indication = "—",
                contraindication = "—",
                effect = "—",
                sideEffects = "—"
            ),
            dosing = emptyList()
        )
    )

    // ---- API (für UI) ----

    fun getMedications(): List<Medication> = meds

    fun getMedicationNames(): List<String> = meds.map { it.name }

    fun getMedicationByName(name: String): Medication? = meds.firstOrNull { it.name == name }

    fun getUseCaseNamesForMedication(name: String): List<String> {
        val med = getMedicationByName(name) ?: return emptyList()
        return med.useCases.map { useCaseLabel(it) }
    }

    fun getDefaultConcentrationText(name: String): String? =
        getMedicationByName(name)?.defaultConcentration?.display

    fun getDefaultConcentrationValue(name: String): Double? =
        getMedicationByName(name)?.defaultConcentration?.mgPerMl

    fun getInfoSections(name: String): MedicationInfoSections? =
        getMedicationByName(name)?.info

    // Dynamische Routen (für UI-Dropdown)
    fun getRouteNamesForMedicationUseCase(medName: String, useCaseLabel: String): List<String> {
        val med = getMedicationByName(medName) ?: return emptyList()
        val key = useCaseKeyFromLabel(useCaseLabel) ?: return emptyList()
        val set = linkedSetOf<String>()
        med.dosing.filter { it.useCase == key }.forEach { r ->
            r.route?.let { expandRouteTokens(it).forEach { tok -> set.add(displayRouteName(tok)) } }
        }
        return set.toList()
    }

    // ---- API (für Logik) ----

    fun getDosingRulesForMedication(name: String): List<DosingRule> =
        getMedicationByName(name)?.dosing ?: emptyList()

    /**
     * Wählt die passendste Regel: nach Use-Case (+ optional Route) filtern,
     * dann die spezifischste nehmen (mehr Schranken = spezifischer).
     */
    fun findBestDosingRule(
        medicationName: String,
        useCaseLabelOrKey: String,
        ageYears: Int,
        weightKg: Double
    ): DosingRule? {
        // Rückwärtskompatibel (ohne Route)
        return findBestDosingRuleWithRoute(medicationName, useCaseLabelOrKey, null, ageYears, weightKg)
    }

    fun findBestDosingRuleWithRoute(
        medicationName: String,
        useCaseLabelOrKey: String,
        routeDisplayName: String?, // z. B. "i.v.", "i.o.", "inhalativ"
        ageYears: Int,
        weightKg: Double
    ): DosingRule? {
        val med = getMedicationByName(medicationName) ?: return null
        val keyFromLabel = useCaseKeyFromLabel(useCaseLabelOrKey)
        val key = keyFromLabel ?: runCatching { UseCaseKey.valueOf(useCaseLabelOrKey) }.getOrNull() ?: return null

        val routeToken = routeDisplayName?.let { normalizeRouteToken(it) }

        val candidates = med.dosing.filter { it.useCase == key }.filter { rule ->
            val ageOk = (rule.ageMinYears == null || ageYears >= rule.ageMinYears) &&
                    (rule.ageMaxYears == null || ageYears <= rule.ageMaxYears)
            val wtOk = (rule.weightMinKg == null || weightKg >= rule.weightMinKg) &&
                    (rule.weightMaxKg == null || weightKg <= rule.weightMaxKg)

            val routeOk = when {
                routeToken == null -> true // keine Route vorgegeben
                rule.route == null -> false
                else -> {
                    val allowed = expandRouteTokens(rule.route).map { normalizeRouteToken(it) }.toSet()
                    allowed.contains(routeToken)
                }
            }
            ageOk && wtOk && routeOk
        }
        if (candidates.isEmpty()) return null

        fun score(r: DosingRule): Int {
            var s = 0
            if (r.ageMinYears != null) s++
            if (r.ageMaxYears != null) s++
            if (r.weightMinKg != null) s++
            if (r.weightMaxKg != null) s++
            return s
        }
        return candidates.maxBy { score(it) }
    }

    // ---- Helpers ----

    // Zerlegt "IV/IO", "inhalativ/nebulisiert", "IM" in Einzeltokens
    private fun expandRouteTokens(route: String): List<String> {
        return route.split('/', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    // Normalisiert zu Vergleichs-Tokens
    private fun normalizeRouteToken(s: String): String {
        val t = s.trim().lowercase()
        return when (t) {
            "iv", "i.v.", "i v" -> "iv"
            "io", "i.o.", "i o" -> "io"
            "im", "i.m.", "i m" -> "im"
            "sc", "s.c.", "s c" -> "sc"
            "inhalativ", "nebulisiert", "neb" -> t
            else -> t
        }
    }

    // Anzeigeform fürs UI
    private fun displayRouteName(token: String): String {
        return when (normalizeRouteToken(token)) {
            "iv" -> "i.v."
            "io" -> "i.o."
            "im" -> "i.m."
            "sc" -> "s.c."
            else -> token // "inhalativ", "nebulisiert", etc.
        }
    }
}
