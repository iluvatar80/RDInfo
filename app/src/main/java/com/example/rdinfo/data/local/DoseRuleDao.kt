# RDInfo – Code‑Outbox (Aktiv)

**Zweck:** Hier liefere ich dir **nur** den Code/Diff mit Pfad und kurzen Schritten. Du kopierst 1:1. Dauerhafte Übernahme passiert danach im **Code‑Vault**.

---

## Aktuelle Lieferung #004 — Room: `DoseRuleDao` (konsolidiert)

**Datei (ersetzen):** `app/src/main/java/com/example/rdinfo/data/local/DoseRuleDao.kt`

```kotlin
package com.example.rdinfo.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseRuleDao {

    @Upsert
    suspend fun upsert(item: DoseRuleEntity)

    @Upsert
    suspend fun upsertAll(items: List<DoseRuleEntity>)

    @Query("DELETE FROM dose_rule")
    suspend fun deleteAll()

    @Query("SELECT * FROM dose_rule WHERE drugId = :drugId AND useCaseId = :useCaseId ORDER BY id")
    suspend fun getByDrugAndUseCase(drugId: Long, useCaseId: Long): List<DoseRuleEntity>

    @Query("SELECT * FROM dose_rule WHERE drugId = :drugId AND useCaseId = :useCaseId ORDER BY id")
    fun observeByDrugAndUseCase(drugId: Long, useCaseId: Long): Flow<List<DoseRuleEntity>>

    @Query(
        """
        SELECT * FROM dose_rule
        WHERE drugId = :drugId
          AND useCaseId = :useCaseId
          AND (:formulationId IS NULL OR formulationId = :formulationId)
          AND (ageMinMonths IS NULL OR ageMinMonths <= :ageMonths)
          AND (ageMaxMonths IS NULL OR ageMaxMonths >= :ageMonths)
        ORDER BY
          CASE WHEN formulationId = :formulationId THEN 0 ELSE 1 END,
          CASE WHEN (ageMinMonths IS NULL AND ageMaxMonths IS NULL) THEN 1 ELSE 0 END,
          COALESCE(ageMinMonths, 0) DESC,
          id DESC
        LIMIT 1
        """
    )
    suspend fun pickByDrugUseCaseFormulationAndAge(
        drugId: Long,
        useCaseId: Long,
        formulationId: Long?,
        ageMonths: Int
    ): DoseRuleEntity?
}
```

**Warum diese Fassung?**

* Behält deine Logik, ergänzt aber **Einzel‑Upsert** und robuste **Priorisierung** (exakte Formulation → spezifisches Altersfenster → neuestes).

**Kopierschritte (kurz):**

1. Datei **1:1 ersetzen**
2. **Sync Project**
3. **Build → Rebuild Project**

---

## Geplanter Mini‑Patch (nur falls in `AppDatabase.kt` noch fehlt)

Falls `DoseRuleEntity`/`DoseRuleDao` dort noch nicht eingebunden sind, ergänze:

**Diff (sinngemäß):**

```
// @Database(entities = [..., DoseRuleEntity::class], version = X, exportSchema = true)
- abstract class AppDatabase : RoomDatabase() {
    + abstract class AppDatabase : RoomDatabase() {
        // ...
        +   abstract fun doseRuleDao(): DoseRuleDao
    }
    ```

    Wenn du mir sagst, ob `entities = [...]` die `DoseRuleEntity` bereits enthält, liefere ich dir den **exakten** Patch hier.
