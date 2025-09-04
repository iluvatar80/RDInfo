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
 * Seeder/Importer für assets/meds.json – inkl. Hotfix für Adrenalin-Konzentration.
 */
object MedsSeeder {

    /** Ersetzt den kompletten Inhalt (Entwicklungsmodus). */
    suspend fun seedFromAssetsReplaceAll(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val src: MedsData = MedsJsonImporter.load(context)
        db.withTransaction {
            db.doseRuleDao().deleteAll()
            db.formulationDao().deleteAll()
            db.useCaseDao().deleteAll()
            db.drugDao().deleteAll()
            insertAllFromJson(db, src)
            applyHotfixes(db) // wichtig: Adrenalin 1 mg/ml
        }
    }

    /** Füllt nur initial; bestehende Daten bleiben erhalten. */
    suspend fun seedFromAssetsIfEmpty(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val hasAny: Boolean = db.openHelper.readableDatabase
            .query("SELECT 1 FROM drug LIMIT 1")
            .use { c -> c.moveToFirst() }

        if (!hasAny) {
            val src: MedsData = MedsJsonImporter.load(context)
            db.withTransaction {
                insertAllFromJson(db, src)
            }
        }
        // Hotfix immer ausführen (wirkt auch bei bereits befüllter DB)
        applyHotfixes(db)
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

            // Use-Cases
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

            // Formulierungen (mit sanfter Korrektur bekannt fehlerhafter Werte)
            val formIdByKey = mutableMapOf<String, Long>()
            d.forms.forEach { f ->
                val correctedConc =
                    if (d.name.equals("Adrenalin", true) || d.name.equals("Epinephrin", true) || d.name.equals("Epinephrine", true)) {
                        // Falls versehentlich 0.1 mg/ml importiert wurde, setze standardmäßig 1.0 mg/ml
                        if (f.concentrationMgPerMl != null && kotlin.math.abs(f.concentrationMgPerMl - 0.1) < 1e-9) 1.0 else f.concentrationMgPerMl
                    } else f.concentrationMgPerMl

                val key = "${f.route}|${correctedConc}|${f.label}"
                val formId = stableId("form:${d.name}:$key")
                formIdByKey[key] = formId
                forms += FormulationEntity(
                    id = formId,
                    drugId = drugId,
                    route = f.route,
                    concentrationMgPerMl = correctedConc,
                    label = when {
                        (d.name.equals("Adrenalin", true) || d.name.equals("Epinephrin", true) || d.name.equals("Epinephrine", true)) &&
                                correctedConc != null && kotlin.math.abs(correctedConc - 1.0) < 1e-9 ->
                            if (f.label.isNullOrBlank()) "1 mg/mL (1:1000)" else f.label
                        else -> f.label
                    }
                )
            }

            // Dosisregeln
            d.doseRules.forEach { r ->
                val ucKey = if (r.useCaseCode.isNotBlank()) r.useCaseCode else d.useCases.firstOrNull()?.name.orEmpty()
                val ucId = ucIdByCode[ucKey] ?: 0L

                val formId: Long? = run {
                    val match = d.forms.firstOrNull { it.route.equals(r.route, ignoreCase = true) }
                    match?.let { fm ->
                        val conc = if (d.name.equals("Adrenalin", true) || d.name.equals("Epinephrin", true) || d.name.equals("Epinephrine", true)) {
                            if (fm.concentrationMgPerMl != null && kotlin.math.abs(fm.concentrationMgPerMl - 0.1) < 1e-9) 1.0 else fm.concentrationMgPerMl
                        } else fm.concentrationMgPerMl
                        formIdByKey["${fm.route}|${conc}|${fm.label}"]
                    }
                }

                rules += DoseRuleEntity(
                    id = stableId("rule:${d.name}:${r.useCaseCode}:${r.route}:${r.mode}:${r.flatMg}:${r.mgPerKg}"),
                    drugId = drugId,
                    useCaseId = ucId,
                    formulationId = formId,
                    mode = r.mode,
                    mgPerKg = r.mgPerKg,
                    flatMg = r.flatMg,
                    maxSingleMg = r.maxSingleMg,
                    roundingMl = r.roundingMl ?: 0.1, // Default beibehalten
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

    /** Hotfixes, die auf bestehende Daten angewendet werden. */
    private fun applyHotfixes(db: AppDatabase) {
        val sql = """
            UPDATE formulation
               SET concentrationMgPerMl = 1.0,
                   label = CASE WHEN label IS NULL OR TRIM(label) = '' THEN '1 mg/mL (1:1000)' ELSE label END
             WHERE drugId IN (
                 SELECT id FROM drug WHERE lower(name) IN ('adrenalin','epinephrin','epinephrine')
             )
               AND (concentrationMgPerMl = 0.1 OR ABS(concentrationMgPerMl - 0.1) < 1e-6);
        """.trimIndent()
        db.openHelper.writableDatabase.execSQL(sql)
    }

    /** stabiler, positiver Long aus String */
    private fun stableId(seed: String): Long {
        var h = 1125899906842597L
        for (c in seed) h = 31L * h + c.code
        val v = h and Long.MAX_VALUE
        return if (v == 0L) 1L else v
    }
}
