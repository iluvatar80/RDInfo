// Zielpfad: app/src/main/java/com/rdinfo/data/MedicationRepository.kt
// Vollständige Datei – jetzt mit Verdünnungsfeldern und Routing‑Suche

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
 * Dosisregel (reine Daten, keine Logik). Mindestens eine der Größen
 * [doseMgPerKg] oder [fixedDoseMg] muss gesetzt sein.
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
    val recommendedConcMgPerMl: Double? = null,
    val route: String? = null,
    val note: String? = null,
    // --- NEU: Verdünnung/Lösung ---
    val solutionText: String? = null,        // z. B. "NaCl 0,9 %"
    val totalPreparedMl: Double? = null      // z. B. 10.0 → „Lösung: 10 ml …“
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
                // <12 J.: 0,01 mg/kg i.v./i.o.; 1 mg auf 10 ml (0,1 mg/ml), Lösung angeben
                DosingRule(
                    useCase = UseCaseKey.REANIMATION,
                    ageMaxYears = 11,
                    doseMgPerKg = 0.01,
                    recommendedConcMgPerMl = 0.1,
                    route = "IV/IO",
                    note = "<12 J.: 0,01 mg/kg; 1:10 (0,1 mg/ml).",
                    solutionText = "NaCl 0,9 %",
                    totalPreparedMl = 10.0
                ),
                // ≥12 J.: 1 mg i.v./i.o.; bevorzugt 1:10 (0,1 mg/ml), Lösung angeben
                DosingRule(
                    useCase = UseCaseKey.REANIMATION,
                    ageMinYears = 12,
                    fixedDoseMg = 1.0,
                    recommendedConcMgPerMl = 0.1,
                    route = "IV/IO",
                    note = "≥12 J.: 1 mg; 1:10 (0,1 mg/ml).",
                    solutionText = "NaCl 0,9 %",
                    totalPreparedMl = 10.0
                ),
                // Anaphylaxie IM 0,01 mg/kg, max. 0,5 mg – 1 mg/ml
                DosingRule(
                    useCase = UseCaseKey.ANAPHYLAXIE,
                    doseMgPerKg = 0.01,
                    maxDoseMg = 0.5,
                    recommendedConcMgPerMl = 1.0,
                    route = "IM",
                    note = "IM: 0,01 mg/kg; max. 0,5 mg pro Gabe."
                ),
                // Schwellung obere Atemwege – Hinweis
                DosingRule(
                    useCase = UseCaseKey.OBERER_ATEMWEG_SCHWELLUNG,
                    fixedDoseMg = null,
                    route = "inhalativ/nebulisiert",
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

    /**
     * Routen-Auswahl dynamisch aus den hinterlegten Dosisregeln für den Use-Case.
     * Fallback: einfache i.v.-Option, wenn keine Routen gepflegt sind.
     */
    fun getRouteNamesForMedicationUseCase(medicationName: String, useCaseLabel: String): List<String> {
        val med = getMedicationByName(medicationName) ?: return emptyList()
        val key = useCaseKeyFromLabel(useCaseLabel) ?: return emptyList()
        val routes = med.dosing.filter { it.useCase == key }.mapNotNull { it.route }.distinct()
        return if (routes.isNotEmpty()) routes else listOf("i.v.")
    }

    // ---- API (für Logik) ----

    fun getDosingRulesForMedication(name: String): List<DosingRule> =
        getMedicationByName(name)?.dosing ?: emptyList()

    /**
     * Wählt die passendste Regel: Filter nach Use‑Case + Bedingungen, dann die spezifischste (meiste Schranken) nehmen.
     */
    fun findBestDosingRule(
        medicationName: String,
        useCaseLabelOrKey: String,
        ageYears: Int,
        weightKg: Double
    ): DosingRule? {
        val med = getMedicationByName(medicationName) ?: return null
        val keyFromLabel = useCaseKeyFromLabel(useCaseLabelOrKey)
        val key = keyFromLabel ?: runCatching { UseCaseKey.valueOf(useCaseLabelOrKey) }.getOrNull() ?: return null

        val candidates = med.dosing.filter { it.useCase == key }.filter { rule ->
            val ageOk = (rule.ageMinYears == null || ageYears >= rule.ageMinYears) &&
                    (rule.ageMaxYears == null || ageYears <= rule.ageMaxYears)
            val wtOk = (rule.weightMinKg == null || weightKg >= rule.weightMinKg) &&
                    (rule.weightMaxKg == null || weightKg <= rule.weightMaxKg)
            ageOk && wtOk
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

    /**
     * Wie oben – zusätzlich werden (falls möglich) Kandidaten auf die gewünschte Route eingeschränkt.
     */
    fun findBestDosingRuleWithRoute(
        medicationName: String,
        useCaseLabelOrKey: String,
        ageYears: Int,
        weightKg: Double,
        routeDisplayName: String?
    ): DosingRule? {
        val med = getMedicationByName(medicationName) ?: return null
        val keyFromLabel = useCaseKeyFromLabel(useCaseLabelOrKey)
        val key = keyFromLabel ?: runCatching { UseCaseKey.valueOf(useCaseLabelOrKey) }.getOrNull() ?: return null

        var candidates = med.dosing.filter { it.useCase == key }.filter { rule ->
            val ageOk = (rule.ageMinYears == null || ageYears >= rule.ageMinYears) &&
                    (rule.ageMaxYears == null || ageYears <= rule.ageMaxYears)
            val wtOk = (rule.weightMinKg == null || weightKg >= rule.weightMinKg) &&
                    (rule.weightMaxKg == null || weightKg <= rule.weightMaxKg)
            ageOk && wtOk
        }

        if (!routeDisplayName.isNullOrBlank()) {
            val r = routeDisplayName
            val filtered = candidates.filter { it.route?.equals(r, ignoreCase = true) == true }
            if (filtered.isNotEmpty()) candidates = filtered
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
}
