// app/src/main/java/com/rdinfo/data/MedicationAssetRepository.kt
package com.rdinfo.data

import android.content.Context
import android.util.Log
import com.rdinfo.data.model.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Liest regelgetriebene Medikamentedaten aus assets/medications.json
 * und mappt sie auf die Kotlin-Modelle (ohne externe JSON-Library).
 */
object MedicationAssetRepository {
    private const val TAG = "MedicationAssetRepo"

    /**
     * Lädt und parst die Medikamentenliste aus den App-Assets.
     * @param assetName Standard: "medications.json"
     */
    fun load(context: Context, assetName: String = "medications.json"): List<Medication> {
        val json = readAsset(context, assetName)
        return try {
            parseMedications(json)
        } catch (t: Throwable) {
            Log.e(TAG, "Parsing medications failed", t)
            emptyList()
        }
    }

    private fun readAsset(context: Context, assetName: String): String =
        context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }

    // --- Parsing ---------------------------------------------------------------------------

    private fun parseMedications(json: String): List<Medication> {
        val root = JSONObject(json)
        val meds = root.optJSONArray("medications") ?: JSONArray()
        return meds.mapObjects { medObj ->
            Medication(
                id = medObj.getString("id"),
                name = medObj.getString("name"),
                ampoule = medObj.getJSONObject("ampoule").let { a ->
                    AmpouleStrength(
                        mg = a.getDouble("mg"),
                        ml = a.getDouble("ml"),
                    )
                },
                useCases = medObj.optJSONArray("useCases").mapObjects { ucObj ->
                    UseCase(
                        id = ucObj.getString("id"),
                        name = ucObj.getString("name"),
                        routes = ucObj.optJSONArray("routes").mapObjects { rObj ->
                            RouteSpec(
                                route = rObj.getString("route"),
                                rules = rObj.optJSONArray("rules").mapObjects { ruleObj ->
                                    DosingRule(
                                        id = ruleObj.optStringOrNull("id"),
                                        priority = ruleObj.optInt("priority", 0),
                                        age = ruleObj.optJSONObjectOrNull("age")?.let { ageObj ->
                                            AgeRange(
                                                minMonths = ageObj.optIntOrNull("minMonths"),
                                                maxMonthsExclusive = ageObj.optIntOrNull("maxMonthsExclusive"),
                                            )
                                        },
                                        weight = ruleObj.optJSONObjectOrNull("weight")?.let { wObj ->
                                            WeightRange(
                                                minKg = wObj.optDoubleOrNull("minKg"),
                                                maxKgExclusive = wObj.optDoubleOrNull("maxKgExclusive"),
                                            )
                                        },
                                        calc = ruleObj.getJSONObject("calc").let { cObj ->
                                            DoseCalc(
                                                type = cObj.getString("type"),
                                                mgPerKg = cObj.optDoubleOrNull("mgPerKg"),
                                                fixedMg = cObj.optDoubleOrNull("fixedMg"),
                                                minMg = cObj.optDoubleOrNull("minMg"),
                                                maxMg = cObj.optDoubleOrNull("maxMg"),
                                            )
                                        },
                                        dilution = ruleObj.optJSONObjectOrNull("dilution")?.let { dObj ->
                                            Dilution(
                                                solutionText = dObj.optStringOrNull("solutionText"),
                                                totalVolumeMl = dObj.optDoubleOrNull("totalVolumeMl"),
                                            )
                                        },
                                        conditions = ruleObj.optJSONObjectOrNull("conditions")?.let { condObj ->
                                            Conditions(
                                                requiresManualAmpoule = condObj.optBooleanOrNull("requiresManualAmpoule"),
                                            )
                                        },
                                        hint = ruleObj.optStringOrNull("hint"),
                                        rounding = ruleObj.optJSONObjectOrNull("rounding")?.let { rndObj ->
                                            Rounding(
                                                mgStep = rndObj.optDoubleOrNull("mgStep"),
                                                mlStep = rndObj.optDoubleOrNull("mlStep"),
                                                showTrailingZeros = rndObj.optBooleanOrNull("showTrailingZeros"),
                                            )
                                        },
                                        repeats = ruleObj.optJSONObjectOrNull("repeats")?.let { repObj ->
                                            Repetition(
                                                repeatAllowed = repObj.optBooleanOrNull("repeatAllowed"),
                                                minIntervalMinutes = repObj.optIntOrNull("minIntervalMinutes"),
                                                maxRepeats = repObj.optIntOrNull("maxRepeats"),
                                            )
                                        },
                                        maxCumulativeMgPerEvent = ruleObj.optDoubleOrNull("maxCumulativeMgPerEvent"),
                                    )
                                },
                                notes = rObj.optStringOrNull("notes")
                            )
                        },
                        defaultRoute = ucObj.optStringOrNull("defaultRoute"),
                        info = ucObj.optJSONObjectOrNull("info")?.toInfoTexts(),
                        notes = ucObj.optStringOrNull("notes"),
                    )
                },
                info = medObj.optJSONObjectOrNull("info")?.toInfoTexts(),
                notes = medObj.optStringOrNull("notes"),
                version = medObj.optInt("version", 1)
            )
        }
    }

    // --- Helpers --------------------------------------------------------------------------

    private fun JSONObject.toInfoTexts(): InfoTexts = InfoTexts(
        indication = this.optStringOrNull("indication"),
        contraindication = this.optStringOrNull("contraindication"),
        effect = this.optStringOrNull("effect"),
        sideEffect = this.optStringOrNull("sideEffect"),
    )

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
        val out = ArrayList<T>(length())
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            out += transform(obj)
        }
        return out
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null
    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key) else null
    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
    private fun JSONObject.optBooleanOrNull(key: String): Boolean? = if (has(key) && !isNull(key)) optBoolean(key) else null
    private fun JSONObject.optJSONObjectOrNull(key: String): JSONObject? = if (has(key) && !isNull(key)) optJSONObject(key) else null
    private fun JSONObject.optJSONArray(key: String): JSONArray = if (has(key) && !isNull(key)) getJSONArray(key) else JSONArray()
}
