package com.example.rdinfo.data

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Liest assets/meds.json und mappt snake_case → camelCase.
 * Keine UI/Composable-Funktionen in dieser Datei!
 */
object MedsJsonImporter {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

    fun load(context: Context): MedsData {
        val txt = context.assets.open("meds.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(MedsData.serializer(), txt)
    }
}

// ------------------ Datenmodelle (nur für den Import) ------------------
@Serializable
data class MedsData(
    val meta: Meta = Meta(),
    val drugs: List<DrugJson> = emptyList()
)

@Serializable
data class Meta(
    val version: Int = 1,
    val source: String = "",
    @SerialName("rounding_ml_default") val roundingMlDefault: Double = 0.1,
    val notes: String = ""
)

@Serializable
data class DrugJson(
    val name: String,
    val indications: String = "",
    val contraindications: String = "",
    val effects: String = "",
    @SerialName("adverse_effects") val adverseEffects: String = "",
    val notes: String = "",
    val forms: List<FormJson> = emptyList(),
    @SerialName("use_cases") val useCases: List<UseCaseJson> = emptyList(),
    @SerialName("dose_rule") val doseRules: List<DoseRuleJson> = emptyList()
)

@Serializable
data class FormJson(
    val route: String,
    @SerialName("concentration_mg_per_ml") val concentrationMgPerMl: Double,
    val label: String = ""
)

@Serializable
data class UseCaseJson(
    val code: String,
    val name: String
)

@Serializable
data class DoseRuleJson(
    @SerialName("use_case_code") val useCaseCode: String,
    val route: String,
    val mode: String,
    @SerialName("mg_per_kg") val mgPerKg: Double? = null,
    @SerialName("flat_mg") val flatMg: Double? = null,
    @SerialName("age_min_months") val ageMinMonths: Int? = null,
    @SerialName("age_max_months") val ageMaxMonths: Int? = null,
    @SerialName("weight_min_kg") val weightMinKg: Double? = null,
    @SerialName("weight_max_kg") val weightMaxKg: Double? = null,
    @SerialName("max_single_mg") val maxSingleMg: Double? = null,
    @SerialName("rounding_ml") val roundingMl: Double? = null,
    @SerialName("display_hint") val displayHint: String? = null
)
