// app/src/main/java/com/rdinfo/data/MedicationRepository.kt
package com.rdinfo.data

import org.json.JSONArray
import org.json.JSONObject

// ---- Datenmodelle (String-basierte UseCases) ----

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
    val useCase: String,
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
    val unit: String = "mg",
    val useCases: List<String> = emptyList(),
    val sections: MedicationInfoSections? = null,
    val dosing: List<DosingRule> = emptyList()
)

// ---- Repository ----

object MedicationRepository {
    @Volatile private var lazyJsonLoader: (() -> String?)? = null
    @Volatile private var meds: List<Medication> = emptyList()

    fun setLazyJsonLoader(loader: () -> String?) { lazyJsonLoader = loader }

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

    fun listMedications(): List<Medication> { ensureLoaded(); return meds }

    fun getMedicationNames(): List<String> { ensureLoaded(); return meds.map { it.name }.sorted() }

    fun getMedicationByName(name: String): Medication? { ensureLoaded(); return meds.firstOrNull { it.name == name || it.id == name } }

    fun getUseCaseNamesForMedication(medicationName: String): List<String> {
        val med = getMedicationByName(medicationName) ?: return emptyList()
        return med.useCases
    }

    fun getRouteNamesForMedicationUseCase(medicationName: String, useCaseLabel: String): List<String> {
        val med = getMedicationByName(medicationName) ?: return emptyList()
        return med.dosing.filter { it.useCase == useCaseLabel }.mapNotNull { it.route }.distinct()
    }

    fun getInfoSections(medicationName: String): MedicationInfoSections? = getMedicationByName(medicationName)?.sections

    fun findBestDosingRule(
        medicationName: String,
        useCaseLabel: String,
        ageYears: Int,
        weightKg: Double
    ): DosingRule? = findBestDosingRuleWithRoute(medicationName, useCaseLabel, ageYears, weightKg, null)

    fun findBestDosingRuleWithRoute(
        medicationName: String,
        useCaseLabel: String,
        ageYears: Int,
        weightKg: Double,
        routeDisplayName: String?
    ): DosingRule? {
        val med = getMedicationByName(medicationName) ?: return null

        var candidates = med.dosing.filter { it.useCase == useCaseLabel }.filter { r ->
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

    private fun parseMedications(root: JSONObject): List<Medication> {
        val list = mutableListOf<Medication>()
        val arr = root.optJSONArray("medications") ?: JSONArray()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list += parseMedication(it) }
        return list
    }

    private fun parseMedication(obj: JSONObject): Medication {
        val id = obj.optString("id")
        val name = obj.optString("name", id)
        val unit = obj.optString("unit", "mg")
        val defConc = obj.optJSONObject("defaultConcentration")?.let { dc ->
            Concentration(
                mg = dc.optDouble("mg", Double.NaN),
                ml = dc.optDouble("ml", Double.NaN),
                display = dc.optString("display", "")
            )
        }
        val useCases = mutableListOf<String>()
        val ucArr = obj.optJSONArray("useCases") ?: JSONArray()
        for (i in 0 until ucArr.length()) {
            val useCase = ucArr.optString(i)
            if (useCase.isNotBlank()) useCases.add(useCase)
        }
        val sections = obj.optJSONObject("sections")?.let { s ->
            MedicationInfoSections(
                indication = s.optNullableString("indication"),
                contraindication = s.optNullableString("contraindication"),
                effect = s.optNullableString("effect"),
                sideEffects = s.optNullableString("sideEffects")
            )
        }
        val dosing = mutableListOf<DosingRule>()
        val dArr = obj.optJSONArray("dosing") ?: JSONArray()
        for (i in 0 until dArr.length()) dArr.optJSONObject(i)?.let { parseDosingRule(it)?.also(dosing::add) }
        return Medication(id = id, name = name, defaultConcentration = defConc, unit = unit, useCases = useCases, sections = sections, dosing = dosing)
    }

    private fun parseDosingRule(obj: JSONObject): DosingRule? {
        val useCase = obj.optString("useCase", "")
        if (useCase.isBlank()) return null
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

private fun JSONObject.optNullableString(name: String): String? = if (has(name) && !isNull(name)) optString(name) else null
private fun JSONObject.optNullableInt(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null
private fun JSONObject.optNullableDouble(name: String): Double? = if (has(name) && !isNull(name)) optDouble(name) else null