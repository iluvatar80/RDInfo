package com.example.rdinfo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        InfoEntity::class,
        DrugEntity::class,
        FormulationEntity::class,
        UseCaseEntity::class,
        DoseRuleEntity::class
    ],
    version = 2, // hochgesetzt, da wir neue Tabellen hinzufügen
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun infoDao(): InfoDao

    // Neue DAO-Schnittstellen für unsere Entitäten
    abstract fun drugDao(): DrugDao
    abstract fun formulationDao(): FormulationDao
    abstract fun useCaseDao(): UseCaseDao
    abstract fun doseRuleDao(): DoseRuleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rdinfo.db"
                )
                    .fallbackToDestructiveMigration() // für Entwicklung ok
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
