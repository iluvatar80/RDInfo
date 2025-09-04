package com.example.rdinfo.data

import android.content.Context
import androidx.room.withTransaction
import com.example.rdinfo.data.local.AppDatabase
import com.example.rdinfo.data.local.DoseRuleEntity
import com.example.rdinfo.data.local.DrugEntity
import com.example.rdinfo.data.local.FormulationEntity
import com.example.rdinfo.data.local.UseCaseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Seeder für assets/meds.json – abgestimmt auf deine aktuellen Entities/DAOs.
 * Erwartet die camelCase-Importmodelle aus MedsJsonImporter.kt (MedsData, DrugJson, …).
 */
object MedsSeeder {

    /** Immer vollständig ersetzen (Entwicklungsmodus). */
    suspend fun seedFromAssetsReplaceAll(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val src: MedsData = MedsJsonImporter.load(context)

        db.withTransaction {
            db.doseRuleDao().deleteAll()
            db.formulationDao().deleteAll()
            db.useCaseDao().deleteAll()
            db.drugDao().deleteAll()

            insertAllFromJson(db, src)
        }
    }

    /** Nur initial füllen, wenn noch leer. */
    suspend fun seedFromAssetsIfEmpty(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val hasAny: Boolean = db.openHelper.readableDatabase
            .query("SELECT 1 FROM drug LIMIT 1")
            .use { c -> c.moveToFirst() }
        if (!hasAny) {
            val src: MedsData = MedsJsonImporter.load(context)
            db.withTransaction { insertAllFromJson(db, src) }
        }
    }

    // --------------------------------------------------------
    private suspend fun insertAllFromJson(db: AppDatabase, src: MedsData) {
        val drugs = mutableListOf<DrugEntity>()
        val useCases = mutableListOf<UseCaseEntity>()
        val forms = mutableListOf<FormulationEntity>()
        val rules = mutableListOf<DoseRuleEntity>()

        src.drugs.forEach { d ->
            val drugId = stableId("drug:${d.name}")

            drugs += DrugEntity(
                id = drugId,
                name = d.name,
                indications = d.indications,
                contraindications = d.contraindications,
                effects = d.effects,
                adverseEffects = d.adverseEffects,
                notes = d.notes
            )

            // Use-Cases (deine UseCaseEntity hat KEIN 'code'-Feld → wir speichern nur name, priority)
            val ucIdByCode = mutableMapOf<String, Long>()
            d.useCases.forEachIndexed { idx, uc ->
                val key = if (uc.code.isNotBlank()) uc.code else uc.name
                val ucId = stableId("uc:${d.name}:$key")
                ucIdByCode[key] = ucId
                useCases += UseCaseEntity(
                    id = ucId,
                    drugId = drugId,
                    name = uc.name,
                    priority = idx
                )
            }

            // Formulierungen → Schlüssel über Route+Konz.+Label
            val formIdByKey = mutableMapOf<String, Long>()
            d.forms.forEach { f ->
                val key = "${f.route}|${f.concentrationMgPerMl}|${f.label}"
                val formId = stableId("form:${d.name}:$key")
                formIdByKey[key] = formId
                forms += FormulationEntity(
                    id = formId,
                    drugId = drugId,
                    route = f.route,
                    concentrationMgPerMl = f.concentrationMgPerMl,
                    label = f.label
                )
            }

            // Dosisregeln
            d.doseRules.forEach { r ->
                val ucKey = if (r.useCaseCode.isNotBlank()) r.useCaseCode else d.useCases.firstOrNull()?.name.orEmpty()
                val ucId = ucIdByCode[ucKey] ?: 0L

                // versuche anhand Route eine passende Formulierung zu finden
                val formId: Long? = run {
                    val match = d.forms.firstOrNull { it.route.equals(r.route, ignoreCase = true) }
                    match?.let { fm -> formIdByKey["${fm.route}|${fm.concentrationMgPerMl}|${fm.label}"] }
                }

                rules += DoseRuleEntity(
                    id = stableId("rule:${d.name}:${r.useCaseCode}:${r.route}:${r.mode}:${r.flatMg}:${r.mgPerKg}"),
                    drugId = drugId,
                    useCaseId = ucId,
                    formulationId = formId, // darf null sein
                    mode = r.mode,
                    mgPerKg = r.mgPerKg,
                    flatMg = r.flatMg,
                    maxSingleMg = r.maxSingleMg,
                    roundingMl = r.roundingMl,
                    displayHint = r.displayHint ?: "",
                    ageMinMonths = r.ageMinMonths,
                    ageMaxMonths = r.ageMaxMonths,
                    weightMinKg = r.weightMinKg,
                    weightMaxKg = r.weightMaxKg
                )
            }
        }

        // Schreiben
        db.drugDao().upsertAll(drugs)
        db.useCaseDao().upsertAll(useCases)
        db.formulationDao().upsertAll(forms)
        db.doseRuleDao().upsertAll(rules)
    }

    /** stabiler, positiver Long aus einem String */
    private fun stableId(seed: String): Long {
        var h = 1125899906842597L
        for (c in seed) h = 31L * h + c.code
        val v = h and Long.MAX_VALUE
        return if (v == 0L) 1L else v
    }
}
