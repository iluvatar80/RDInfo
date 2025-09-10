// app/src/main/java/com/rdinfo/data/MedicationRepository.kt
package com.rdinfo.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON‑basiertes Repository – keine fachlichen Hardcodes mehr.
 * Die Inhalte werden aus /app/src/main/assets/medications.json geladen,
 * das via [setLazyJsonLoader] bereitgestellt wird.
 */

// ---- Use‑Cases ----

enum class UseCaseKey { REANIMATION, ANAPHYLAXIE, OBERER_ATEMWEG_SCHWELLUNG, BRADYKARDIE, VENTRIKULAERE_TACHYKARDIE }

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

// ---- Datenmodelle (schlank, JSON‑freundlich) ----

data class Concentration(val mg: Double, val ml: Double, val display: String) {
    val mgPerMl: Double get() = if (ml > 0.0) mg / ml else 0.0
}

data class MedicationInfoSections(
    val indication: String? = null,
    val contraindication: String? = null,
    val effect: String? = null,
    val sideEffects: String? = null,
)

data class DosingRule(
    val useCase: UseCaseKey,
    val ageMinYears: Int? = null,
    val ageMaxYears: Int? = null,
    val weightMinKg: Double? = null,
    val weightMaxKg: Double? = null,
    val doseMgPerKg: Double? = null,
    val fixedDoseMg: Double? = null,
    val maxDoseMg: Double? = null,
    val maxDoseText: String? = null,
    val totalMaxDoseMg: Double? = null,
    val totalMaxDoseText: String? = null,
    val recommendedConcMgPerMl: Double? = null,
    val route: String? = null,
    val note: String? = null,
    val solutionText: String? = null,
    val totalPreparedMl: Double? = null
)

data class Medication(
    val id: String,
    val name: String,
    val defaultConcentration: Concentration? = null,
    val useCases: List<UseCaseKey> = emptyList(),
    val sections: MedicationInfoSections? = null,
    val dosing: List<DosingRule> = emptyList()
)

// ---- Repository ----

object MedicationRepository {
    @Volatile private var lazyJsonLoader: (() -> String?)? = null
    @Volatile private var meds: List<Medication> = emptyList()

    /** In onCreate() setzen, z. B. mit assets.open("medications.json"). */
    fun setLazyJsonLoader(loader: () -> String?) { lazyJsonLoader = loader }

    /** Für Tests/DI: direktes Laden aus String. */
    @Synchronized fun loadFromJsonString(json: String) { meds = parseMedications(JSONObject(json)) }

    @Synchronized
    private fun ensureLoaded() {
        if (meds.isNotEmpty()) return
        val json = lazyJsonLoader?.invoke()
        if (!json.isNullOrBlank()) {
            runCatching { meds = parseMedications(JSONObject(json)) }
                .onFailure { meds = emptyList() }
        } else {
            meds = emptyList()
        }
    }

    // ---- Öffentliche API für UI/Logik ----

    fun listMedications(): List<Medication> { ensureLoaded(); return meds }

    /** Für das Medikamenten‑Dropdown. */
    fun getMedicationNames(): List<String> { ensureLoaded(); return meds.map { it.name }.sorted() }

    fun getMedicationByName(name: String): Medication? { ensureLoaded(); return meds.firstOrNull { it.name == name || it.id == name } }

    fun getUseCaseNamesForMedication(medicationName: String): List<String> {
        val med = getMedicationByName(medicationName) ?: return emptyList()
        return med.useCases.map { useCaseLabel(it) }
    }

    /** Routen aus den Regeln, ohne Fallback‑Hardcode. */
    fun getRouteNamesForMedicationUseCase(medicationName: String, useCaseLabel: String): List<String> {
        val med = getMedicationByName(medicationName) ?: return emptyList()
        val key = useCaseKeyFromLabel(useCaseLabel) ?: return emptyList()
        return med.dosing.filter { it.useCase == key }.mapNotNull { it.route }.distinct()
    }

    fun getInfoSections(medicationName: String): MedicationInfoSections? = getMedicationByName(medicationName)?.sections

    fun findBestDosingRule(
        medicationName: String,
        useCaseLabelOrKey: String,
        ageYears: Int,
        weightKg: Double
    ): DosingRule? = findBestDosingRuleWithRoute(medicationName, useCaseLabelOrKey, ageYears, weightKg, null)

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

    // ---- JSON‑Parsing ----

    private fun parseMedications(root: JSONObject): List<Medication> {
        val list = mutableListOf<Medication>()
        val arr = root.optJSONArray("medications") ?: JSONArray()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list += parseMedication(it) }
        return list
    }

    private fun parseMedication(obj: JSONObject): Medication {
        val id = obj.optString("id")
        val name = obj.optString("name", id)
        val defConc = obj.optJSONObject("defaultConcentration")?.let { dc ->
            Concentration(
                mg = dc.optDouble("mg", Double.NaN),
                ml = dc.optDouble("ml", Double.NaN),
                display = dc.optString("display", "")
            )
        }
        val useCases = mutableListOf<UseCaseKey>()
        val ucArr = obj.optJSONArray("useCases") ?: JSONArray()
        for (i in 0 until ucArr.length()) {
            val raw = ucArr.optString(i)
            val key = runCatching { UseCaseKey.valueOf(raw) }.getOrNull() ?: useCaseKeyFromLabel(raw)
            if (key != null) useCases.add(key)
        }
        val sections = obj.optJSONObject("sections")?.let { s ->
            MedicationInfoSections(
                indication = s.optString("indication", null),
                contraindication = s.optString("contraindication", null),
                effect = s.optString("effect", null),
                sideEffects = s.optString("sideEffects", null)
            )
        }
        val dosing = mutableListOf<DosingRule>()
        val dArr = obj.optJSONArray("dosing") ?: JSONArray()
        for (i in 0 until dArr.length()) dArr.optJSONObject(i)?.let { parseDosingRule(it)?.also(dosing::add) }
        return Medication(id = id, name = name, defaultConcentration = defConc, useCases = useCases, sections = sections, dosing = dosing)
    }

    private fun parseDosingRule(obj: JSONObject): DosingRule? {
        val ucRaw = obj.optString("useCase", "")
        val useCase = runCatching { UseCaseKey.valueOf(ucRaw) }.getOrNull() ?: useCaseKeyFromLabel(ucRaw) ?: return null
        return DosingRule(
            useCase = useCase,
            ageMinYears = obj.optNullableInt("ageMinYears"),
            ageMaxYears = obj.optNullableInt("ageMaxYears"),
            weightMinKg = obj.optNullableDouble("weightMinKg"),
            weightMaxKg = obj.optNullableDouble("weightMaxKg"),
            doseMgPerKg = obj.optNullableDouble("doseMgPerKg"),
            fixedDoseMg = obj.optNullableDouble("fixedDoseMg"),
            maxDoseMg = obj.optNullableDouble("maxDoseMg"),
            maxDoseText = obj.optNullableString("maxDoseText"),
            totalMaxDoseMg = obj.optNullableDouble("totalMaxDoseMg"),
            totalMaxDoseText = obj.optNullableString("totalMaxDoseText"),
            recommendedConcMgPerMl = obj.optNullableDouble("recommendedConcMgPerMl"),
            route = obj.optNullableString("route"),
            note = obj.optNullableString("note"),
            solutionText = obj.optNullableString("solutionText"),
            totalPreparedMl = obj.optNullableDouble("totalPreparedMl")
        )
    }
}

// ---- JSONObject‑Helper ----

private fun JSONObject.optNullableString(name: String): String? = if (has(name) && !isNull(name)) optString(name) else null
private fun JSONObject.optNullableInt(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null
private fun JSONObject.optNullableDouble(name: String): Double? = if (has(name) && !isNull(name)) optDouble(name) else null
