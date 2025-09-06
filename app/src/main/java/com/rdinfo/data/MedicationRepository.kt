// ========================================================================
// File: app/src/main/java/com/rdinfo/data/MedicationRepository.kt
// (Neu erstellt, falls im Projekt referenziert – liest optional aus Assets)
// ========================================================================
package com.rdinfo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Leichtgewichtiger Datensatz, bewusst generisch, um Konflikte zu vermeiden. */
data class MedicationRecord(
    val id: String,
    val name: String,
    val strengths: List<Double> = emptyList(),
    val routes: List<String> = emptyList()
)

interface MedicationRepository {
    fun list(): List<MedicationRecord>
    fun findByName(name: String): MedicationRecord? = list().firstOrNull { it.name.equals(name, true) }
    fun findById(id: String): MedicationRecord? = list().firstOrNull { it.id == id }
}

/**
 * Implementierung, die `assets/medications.json` lädt. Strukturtolerant.
 * Falls Parsing fehlschlägt, gibt sie eine leere Liste zurück (keine Crashes).
 */
class MedicationAssetRepository(private val context: Context) : MedicationRepository {
    override fun list(): List<MedicationRecord> = runCatching {
        val json = context.assets.open("medications.json").bufferedReader().use { it.readText() }
        parse(json)
    }.getOrElse { emptyList() }

    private fun parse(text: String): List<MedicationRecord> {
        val trimmed = text.trim()
        val arr: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                // unterstützt sowohl {"medications":[..]} als auch andere Keys
                val key = root.keys().asSequence().firstOrNull { it.equals("medications", true) } ?: return emptyList()
                root.getJSONArray(key)
            }
            else -> return emptyList()
        }
        return (0 until arr.length()).mapNotNull { idx ->
            val o = arr.optJSONObject(idx) ?: return@mapNotNull null
            val id = o.optString("id", o.optString("code", o.optString("name", "")))
            val name = o.optString("name", id)
            val strengths = when {
                o.has("strengths") -> o.optJSONArray("strengths")?.toDoubleList() ?: emptyList()
                o.has("concentrations") -> o.optJSONArray("concentrations")?.toDoubleList() ?: emptyList()
                else -> emptyList()
            }
            val routes = when {
                o.has("routes") -> o.optJSONArray("routes")?.toStringList() ?: emptyList()
                o.has("applications") -> o.optJSONArray("applications")?.toStringList() ?: emptyList()
                else -> emptyList()
            }
            MedicationRecord(id = id.ifBlank { name }, name = name, strengths = strengths, routes = routes)
        }
    }
}

// kleine JSON‑Hilfen
private fun JSONArray.toDoubleList(): List<Double> = buildList(length()) {
    for (i in 0 until length()) add(this@toDoubleList.optDouble(i))
}

private fun JSONArray.toStringList(): List<String> = buildList(length()) {
    for (i in 0 until length()) add(this@toStringList.optString(i))
}
