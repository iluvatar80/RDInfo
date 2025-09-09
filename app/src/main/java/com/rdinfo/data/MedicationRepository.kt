// app/src/main/java/com/rdinfo/data/MedicationRepository.kt
package com.rdinfo.data

// ---- Use-Cases ----

enum class UseCaseKey {
    REANIMATION,
    ANAPHYLAXIE,
    OBERER_ATEMWEG_SCHWELLUNG,
    BRADYKARDIE,
    VENTRIKULAERE_TACHYKARDIE // VT (mit Puls)
}

fun useCaseLabel(key: UseCaseKey): String = when (key) {
    UseCaseKey.REANIMATION -> "Reanimation"
    UseCaseKey.ANAPHYLAXIE -> "Anaphylaxie"
    UseCaseKey.OBERER_ATEMWEG_SCHWELLUNG -> "Schwellung der oberen Atemwege"
    UseCaseKey.BRADYKARDIE -> "Bradykardie"
    UseCaseKey.VENTRIKULAERE_TACHYKARDIE -> "Ventrikuläre Tachykardie (VT)"
}

fun useCaseKeyFromLabel(label: String): UseCaseKey? = when (label) {
    "Reanimation" -> UseCaseKey.REANIMATION
    "Anaphylaxie" -> UseCaseKey.ANAPHYLAXIE
    "Schwellung der oberen Atemwege" -> UseCaseKey.OBERER_ATEMWEG_SCHWELLUNG
    "Bradykardie" -> UseCaseKey.BRADYKARDIE
    "Ventrikuläre Tachykardie (VT)" -> UseCaseKey.VENTRIKULAERE_TACHYKARDIE
    else -> null
}

// ---- Datenmodelle ----

data class Concentration(val mg: Double, val ml: Double, val display: String) {
    val mgPerMl: Double get() = if (ml > 0.0) mg / ml else 0.0
}

data class MedicationInfoSections(
    val indication: String? = null,
    val contraindication: String? = null,
    val effect: String? = null,
    val sideEffects: String? = null,
)

/** Dosisregel (reine Daten) */
data class DosingRule(
    val useCase: UseCaseKey,
    val ageMinYears: Int? = null,
    val ageMaxYears: Int? = null,
    val weightMinKg: Double? = null,
    val weightMaxKg: Double? = null,
    val doseMgPerKg: Double? = null,
    val fixedDoseMg: Double? = null,
    // pro‑Gabe‑Limit (Deckelung für Berechnung)
    val maxDoseMg: Double? = null,
    val maxDoseText: String? = null,
    // GESAMT‑Maximaldosis (Anzeige)
    val totalMaxDoseMg: Double? = null,
    val totalMaxDoseText: String? = null,
    // Konzentration/Applikation
    val recommendedConcMgPerMl: Double? = null,
    val route: String? = null,
    val note: String? = null,
    // Verdünnung/Lösung
    val solutionText: String? = null,
    val totalPreparedMl: Double? = null
)

data class Medication(
    val id: String,
    val name: String,
    val defaultConcentration: Concentration? = null,
    val useCases: List<UseCaseKey> = emptyList(),
    val sections: MedicationInfoSections? = null,
    val dosing: List<DosingRule> = emptyList(),
)

// ---- Repository ----

object MedicationRepository {

    private val meds: List<Medication> = listOf(
        // -------------------- Adrenalin --------------------
        Medication(
            id = "adrenalin",
            name = "Adrenalin",
            // PDF: 1 Ampulle (1 ml) = 1 mg → 1 mg/ml, ohne 1:1000
            defaultConcentration = Concentration(1.0, 1.0, "1 mg/ml"),
            useCases = listOf(
                UseCaseKey.REANIMATION,
                UseCaseKey.ANAPHYLAXIE,
                UseCaseKey.OBERER_ATEMWEG_SCHWELLUNG
            ),
            sections = MedicationInfoSections(
                indication = "Reanimation; Anaphylaxie; Schwellung der oberen Atemwege",
                contraindication = "Bei vitaler Indikation keine absoluten Kontraindikationen.",
                effect = "α/β‑Sympathomimetikum: Vasokonstriktion, Bronchodilatation, positiv chrono-/inotrop.",
                sideEffects = "Tachykardie, Arrhythmien, Tremor, Hypertonie."
            ),
            dosing = listOf(
                // Reanimation – <12 J.: 0,01 mg/kg – empfohlen 0,1 mg/ml – Lösung: 10 ml NaCl 0,9 %
                DosingRule(
                    useCase = UseCaseKey.REANIMATION,
                    ageMaxYears = 11,
                    doseMgPerKg = 0.01,
                    recommendedConcMgPerMl = 0.1,
                    route = "IV/IO",
                    note = "<12 J.: 0,01 mg/kg; bevorzugt 0,1 mg/ml.",
                    solutionText = "NaCl 0,9 %",
                    totalPreparedMl = 10.0
                ),
                // Reanimation – ≥12 J.: 1 mg – empfohlen 0,1 mg/ml – Lösung: 10 ml NaCl 0,9 %
                DosingRule(
                    useCase = UseCaseKey.REANIMATION,
                    ageMinYears = 12,
                    fixedDoseMg = 1.0,
                    recommendedConcMgPerMl = 0.1,
                    route = "IV/IO",
                    note = "≥12 J.: 1 mg; bevorzugt 0,1 mg/ml.",
                    solutionText = "NaCl 0,9 %",
                    totalPreparedMl = 10.0
                ),
                // Anaphylaxie i.m. – altersgestaffelt: <6 J.: 0,15 mg; 6–10 J.: 0,3 mg; ≥11 J.: 0,5 mg
                DosingRule(
                    useCase = UseCaseKey.ANAPHYLAXIE,
                    ageMaxYears = 5,
                    fixedDoseMg = 0.15,
                    recommendedConcMgPerMl = 1.0,
                    route = "IM",
                    note = "<6 J.: 0,15 mg i.m. (pur in den seitlichen Oberschenkel), ggf. Repetition nach 5–10 min"
                ),
                DosingRule(
                    useCase = UseCaseKey.ANAPHYLAXIE,
                    ageMinYears = 6,
                    ageMaxYears = 10,
                    fixedDoseMg = 0.3,
                    recommendedConcMgPerMl = 1.0,
                    route = "IM",
                    note = "6–10 J.: 0,3 mg i.m. (pur in den seitlichen Oberschenkel), ggf. Repetition nach 5–10 min"
                ),
                DosingRule(
                    useCase = UseCaseKey.ANAPHYLAXIE,
                    ageMinYears = 11,
                    fixedDoseMg = 0.5,
                    recommendedConcMgPerMl = 1.0,
                    route = "IM",
                    note = "≥11 J.: 0,5 mg i.m. (pur in den seitlichen Oberschenkel), ggf. Repetition nach 5–10 min"
                ),
                // Schwellung obere Atemwege – 4 mg inhalativ für alle Altersgruppen
                DosingRule(
                    useCase = UseCaseKey.OBERER_ATEMWEG_SCHWELLUNG,
                    fixedDoseMg = 4.0,
                    route = "inhalativ/nebulisiert",
                    note = "4 mg inhalativ (alle Altersgruppen). Flow je Maske (z. B. 8 l/min). Abbruch: Tachykardie >140/min, RR sys >160, ausgeprägte Rhythmusstörung."
                )
            )
        ),

        // -------------------- Amiodaron --------------------
        Medication(
            id = "amiodaron",
            name = "Amiodaron",
            defaultConcentration = Concentration(150.0, 3.0, "150 mg / 3 ml"),
            useCases = listOf(
                UseCaseKey.REANIMATION,
                UseCaseKey.VENTRIKULAERE_TACHYKARDIE
            ),
            sections = MedicationInfoSections(
                indication = "Defibrillationsresistentes Kammerflimmern/pVT (nach 3. Schock); ventrikuläre Tachykardie (VT) mit Puls",
                contraindication = "Bei Reanimation keine absoluten KI; außerhalb Rea: Sinusbradykardie, AV‑Block II/III ohne Schrittmacher, schwere Hypotonie, QT‑Verlängerung.",
                effect = "Klasse‑III‑Antiarrhythmikum: Verlängert Refraktärzeit/Aktionspotenzial; Na⁺-, K⁺-, Ca²⁺‑Kanäle und β‑Blockade.",
                sideEffects = "Akut: Hypotonie (Lösungsmittel), Bradykardie, QT‑Verlängerung; selten Torsade de pointes."
            ),
            dosing = listOf(
                // < 12 J.: 5 mg/kg i.v./i.o. nach 3. Schock
                DosingRule(
                    useCase = UseCaseKey.REANIMATION,
                    ageMaxYears = 11,
                    doseMgPerKg = 5.0,
                    route = "IV/IO",
                    note = "<12 J.: 5 mg/kg i.v./i.o. nach 3. Schock"
                ),
                // ≥ 12 J.: 300 mg langsam i.v. nach 3. Schock; ggf. 150 mg Repetition nach 5. Schock
                DosingRule(
                    useCase = UseCaseKey.REANIMATION,
                    ageMinYears = 12,
                    fixedDoseMg = 300.0,
                    route = "IV/IO",
                    note = "≥12 J.: 300 mg langsam i.v. nach 3. Schock; ggf. 150 mg Repetition nach 5. Schock"
                ),
                // VT mit Puls: 150 mg als langsame Kurzinfusion, Verdünnung 250 ml G5%
                DosingRule(
                    useCase = UseCaseKey.VENTRIKULAERE_TACHYKARDIE,
                    fixedDoseMg = 150.0,
                    route = "i.v. Kurzinfusion",
                    note = "VT mit Puls: 150 mg als langsame Kurzinfusion",
                    solutionText = "G5 %",
                    totalPreparedMl = 250.0
                )
            )
        ),

        // -------------------- Atropin --------------------
        Medication(
            id = "atropin",
            name = "Atropin",
            defaultConcentration = Concentration(1.0, 1.0, "1 mg / 1 ml"),
            useCases = listOf(UseCaseKey.BRADYKARDIE),
            sections = MedicationInfoSections(
                indication = "Symptomatische Bradykardie.",
                contraindication = "Engwinkelglaukom, tachykarde Rhythmusstörungen; relative KI: Myokardischämie. Bei vitaler Indikation Nutzen > Risiko.",
                effect = "Anticholinergikum (Muskarinrezeptor‑Antagonist) → vagolytisch: HF‑Anstieg, AV‑Leitungsverbesserung.",
                sideEffects = "Mundtrockenheit, Sehstörungen, Harnverhalt, Hautrötung, Tachykardie; bei Überdosierung Delir."
            ),
            dosing = emptyList()
        )
    )

    // ---- API für UI ----

    fun getMedicationNames(): List<String> = meds.map { it.name }

    fun getMedicationByName(name: String): Medication? = meds.firstOrNull { it.name == name }

    fun getUseCaseNamesForMedication(medicationName: String): List<String> {
        val med = getMedicationByName(medicationName) ?: return emptyList()
        return med.useCases.map { useCaseLabel(it) }
    }

    fun getRouteNamesForMedicationUseCase(medicationName: String, useCaseLabel: String): List<String> {
        val med = getMedicationByName(medicationName) ?: return emptyList()
        val key = useCaseKeyFromLabel(useCaseLabel) ?: return emptyList()
        val routes = med.dosing.filter { it.useCase == key }.mapNotNull { it.route }.distinct()
        return if (routes.isNotEmpty()) routes else listOf("i.v.")
    }

    fun getInfoSections(medicationName: String): MedicationInfoSections? =
        getMedicationByName(medicationName)?.sections

    // ---- API für Logik ----

    fun findBestDosingRule(
        medicationName: String,
        useCaseLabelOrKey: String,
        ageYears: Int,
        weightKg: Double
    ): DosingRule? {
        val med = getMedicationByName(medicationName) ?: return null
        val key = useCaseKeyFromLabel(useCaseLabelOrKey)
            ?: runCatching { UseCaseKey.valueOf(useCaseLabelOrKey) }.getOrNull()
            ?: return null

        val candidates = med.dosing.filter { it.useCase == key }.filter { r ->
            val ageOk = (r.ageMinYears == null || ageYears >= r.ageMinYears) && (r.ageMaxYears == null || ageYears <= r.ageMaxYears)
            val wtOk = (r.weightMinKg == null || weightKg >= r.weightMinKg) && (r.weightMaxKg == null || weightKg <= r.weightMaxKg)
            ageOk && wtOk
        }
        if (candidates.isEmpty()) return null

        fun score(r: DosingRule): Int {
            var s = 0
            if (r.ageMinYears != null) s++
            if (r.ageMaxYears != null) s++
            if (r.weightMinKg != null) s++
            if (r.weightMaxKg != null) s++
            if (!r.route.isNullOrBlank()) s++      // spezifischer
            return s
        }
        return candidates.maxBy { score(it) }
    }

    fun findBestDosingRuleWithRoute(
        medicationName: String,
        useCaseLabelOrKey: String,
        ageYears: Int,
        weightKg: Double,
        routeDisplayName: String?
    ): DosingRule? {
        val med = getMedicationByName(medicationName) ?: return null
        val key = useCaseKeyFromLabel(useCaseLabelOrKey)
            ?: runCatching { UseCaseKey.valueOf(useCaseLabelOrKey) }.getOrNull()
            ?: return null

        var candidates = med.dosing.filter { it.useCase == key }.filter { r ->
            val ageOk = (r.ageMinYears == null || ageYears >= r.ageMinYears) && (r.ageMaxYears == null || ageYears <= r.ageMaxYears)
            val wtOk = (r.weightMinKg == null || weightKg >= r.weightMinKg) && (r.weightMaxKg == null || weightKg <= r.weightMaxKg)
            ageOk && wtOk
        }

        if (!routeDisplayName.isNullOrBlank()) {
            val filtered = candidates.filter { it.route?.equals(routeDisplayName, ignoreCase = true) == true }
            if (filtered.isNotEmpty()) candidates = filtered
        }

        if (candidates.isEmpty()) return null

        fun score(r: DosingRule): Int {
            var s = 0
            if (r.ageMinYears != null) s++
            if (r.ageMaxYears != null) s++
            if (r.weightMinKg != null) s++
            if (r.weightMaxKg != null) s++
            if (!r.route.isNullOrBlank()) s++
            return s
        }
        return candidates.maxBy { score(it) }
    }
}
