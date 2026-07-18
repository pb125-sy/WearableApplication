package com.example.wearableapplication

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.wearableapplication.model.QuestionnaireEntity

@Database(entities = [TimeWindowRecords::class, QuestionnaireEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // Link the DAOs
    abstract fun timeWindowDao(): TimeWindowDao
    abstract fun questionnaireDao(): QuestionnaireDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // If the instance exists, return it. If not, build it.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wearable_research_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}