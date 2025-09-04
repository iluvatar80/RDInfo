package com.example.rdinfo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DrugEntity::class,
        UseCaseEntity::class,
        FormulationEntity::class,
        DoseRuleEntity::class,
        InfoEntity::class
    ],
    version = 3, // bumped to trigger migration
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drugDao(): DrugDao
    abstract fun useCaseDao(): UseCaseDao
    abstract fun formulationDao(): FormulationDao
    abstract fun doseRuleDao(): DoseRuleDao
    abstract fun infoDao(): InfoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "rdinfo.db"
            )
                // In Entwicklung: löscht DB automatisch bei Schemaänderung statt zu crashen.
                .fallbackToDestructiveMigration()
                .build()
            INSTANCE = instance
            instance
        }
    }
}
