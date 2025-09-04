// File: app/src/main/java/com/example/rdinfo/data/MedsSeeder.kt
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
 * Seeder/Importer für assets/meds.json – DATENGETRIEBEN.
 *
 * – Keine Hardcodes zu Medikamenten in der App-Logik.
 * – Korrekturen (z. B. Adrenalin 1 mg/mL, Reanimation 1:10) werden als **Datenpatch** geschrieben:
 *   Formulierungen/Konzentrationen und `dose_rule.dilutionFactor`.
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
            applyDataPatches(db)
        }
    }

    /** Füllt nur initial; bestehende Daten bleiben erhalten und werden gepatcht. */
    suspend fun seedFromAssetsIfEmpty(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val hasAny: Boolean = db.openHelper.readableDatabase
            .query("SELECT 1 FROM drug LIMIT 1")
            .use { c -> c.moveToFirst() }

        if (!hasAny) {
            val src: MedsData = MedsJsonImporter.load(context)
            db.withTransaction { insertAllFromJson(db, src) }
        }
        applyDataPatches(db)
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

            // Formulierungen
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

            // Dosisregeln (direkt aus JSON übernehmen)
            d.doseRules.forEach { r ->
                val ucKey = if (r.useCaseCode.isNotBlank()) r.useCaseCode else d.useCases.firstOrNull()?.name.orEmpty()
                val ucId = ucIdByCode[ucKey] ?: 0L

                val formId: Long? = run {
                    val match = d.forms.firstOrNull { it.route.equals(r.route, ignoreCase = true) }
                    match?.let { fm -> formIdByKey["${fm.route}|${fm.concentrationMgPerMl}|${fm.label}"] }
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
                    roundingMl = r.roundingMl ?: 0.1,
                    displayHint = r.displayHint ?: "",
                    ageMinMonths = r.ageMinMonths,
                    ageMaxMonths = r.ageMaxMonths,
                    weightMinKg = r.weightMinKg,
                    weightMaxKg = r.weightMaxKg,
                    dilutionFactor = null // wird ggf. per Patch gesetzt
                )
            }
        }

        // Schreiben
        db.drugDao().upsertAll(drugs)
        db.useCaseDao().upsertAll(useCases)
        db.formulationDao().upsertAll(forms)
        db.doseRuleDao().upsertAll(rules)
    }

    /**
     * Daten-Patches nach Import:
     * 1) Adrenalin-Standardampulle sicherstellen: 1 mg/mL (1:1000)
     * 2) Reanimation: **dilutionFactor=10** (1:10) für alle Adrenalin-Regeln dieses Use-Cases
     */
    private fun applyDataPatches(db: AppDatabase) {
        val sqlFixAdrenalineConc = """
            UPDATE formulation
               SET concentrationMgPerMl = 1.0,
                   label = CASE WHEN label IS NULL OR TRIM(label) = '' THEN '1 mg/mL (1:1000)' ELSE label END
             WHERE drugId IN (SELECT id FROM drug WHERE lower(name) IN ('adrenalin','epinephrin','epinephrine'))
               AND (concentrationMgPerMl = 0.1 OR ABS(concentrationMgPerMl - 0.1) < 1e-6);
        """.trimIndent()

        val sqlSetReanimationDilution = """
            UPDATE dose_rule
               SET dilutionFactor = 10.0
             WHERE drugId IN (SELECT id FROM drug WHERE lower(name) IN ('adrenalin','epinephrin','epinephrine'))
               AND useCaseId IN (SELECT id FROM use_case WHERE lower(name) = 'reanimation');
        """.trimIndent()

        db.openHelper.writableDatabase.execSQL(sqlFixAdrenalineConc)
        db.openHelper.writableDatabase.execSQL(sqlSetReanimationDilution)
    }

    /** stabiler, positiver Long aus String */
    private fun stableId(seed: String): Long {
        var h = 1125899906842597L
        for (c in seed) h = 31L * h + c.code
        val v = h and Long.MAX_VALUE
        return if (v == 0L) 1L else v
    }
}
