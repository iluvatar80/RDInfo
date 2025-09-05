// Zielpfad: app/src/main/java/com/rdinfo/data/MedicationRepository.kt
// NEUE DATEI anlegen. Enthält Datenmodell + Repository (keine Berechnungslogik).

package com.rdinfo.data

// --- Use-Cases ---

enum class UseCaseKey {
    REANIMATION,
    ANAPHYLAXIE,
    OBERER_ATEMWEG_SCHWELLUNG,
    BRADYKARDIE
}

fun useCaseLabel(key: UseCaseKey): String = when (key) {
    UseCaseKey.REANIMATION -> "Reanimation"
    UseCaseKey.ANAPHYLAXIE -> "Anaphylaxie"
    UseCaseKey.OBERER_ATEMWEG_SCHWELLUNG -> "Schwellung der oberen Atemwege"
    UseCaseKey.BRADYKARDIE -> "Bradykardie"
}

// --- Datenmodelle ---

data class Concentration(
    val mg: Double,
    val ml: Double,
    val display: String // z. B. "1 Ampulle (1 ml) = 1 mg"
) {
    val mgPerMl: Double get() = if (ml > 0) mg / ml else 0.0
}

data class MedicationInfoSections(
    val indication: String,
    val contraindication: String,
    val effect: String,
    val sideEffects: String
)

data class Medication(
    val id: String,
    val name: String,
    val defaultConcentration: Concentration,
    val concentrations: List<Concentration>,
    val useCases: List<UseCaseKey>,
    val info: MedicationInfoSections
)

// --- Repository ---

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
                effect = "α/β‑Sympathomimetikum: Vasokonstriktion, Bronchodilatation, Steigerung Herzzeitvolumen.",
                sideEffects = "Tachykardie, Arrhythmien, Tremor, Hypertonie."
            )
        ),
        Medication(
            id = "amiodaron",
            name = "Amiodaron",
            defaultConcentration = Concentration(150.0, 3.0, "1 Ampulle (3 ml) = 150 mg"),
            concentrations = listOf(
                Concentration(150.0, 3.0, "150 mg / 3 ml")
            ),
            useCases = listOf(UseCaseKey.REANIMATION),
            info = MedicationInfoSections(
                indication = "—",
                contraindication = "—",
                effect = "—",
                sideEffects = "—"
            )
        ),
        Medication(
            id = "atropin",
            name = "Atropin",
            defaultConcentration = Concentration(1.0, 1.0, "1 Ampulle (1 ml) = 1 mg"),
            concentrations = listOf(
                Concentration(1.0, 1.0, "1 mg / 1 ml")
            ),
            useCases = listOf(UseCaseKey.BRADYKARDIE),
            info = MedicationInfoSections(
                indication = "—",
                contraindication = "—",
                effect = "—",
                sideEffects = "—"
            )
        )
    )

    // --- API ---

    fun getMedications(): List<Medication> = meds

    fun getMedicationNames(): List<String> = meds.map { it.name }

    fun getMedicationByName(name: String): Medication? = meds.firstOrNull { it.name == name }

    fun getUseCaseNamesForMedication(name: String): List<String> =
        getMedicationByName(name)?.useCases?.map { useCaseLabel(it) } ?: emptyList()

    fun getDefaultConcentrationText(name: String): String? =
        getMedicationByName(name)?.defaultConcentration?.display

    fun getDefaultConcentrationValue(name: String): Double? =
        getMedicationByName(name)?.defaultConcentration?.mgPerMl

    fun getInfoSections(name: String): MedicationInfoSections? =
        getMedicationByName(name)?.info
}
