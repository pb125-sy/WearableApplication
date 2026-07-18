package com.example.wearableapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.wearableapplication.model.QuestionnaireEntity

@Dao
interface QuestionnaireDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQuestionnaire(questionnaire: QuestionnaireEntity)

    @Query("SELECT * FROM latest_questionnaire WHERE id = 0")
    suspend fun getLatestQuestionnaire(): QuestionnaireEntity?
}