package com.example.wearableapplication.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "latest_questionnaire")
data class QuestionnaireEntity(
    @PrimaryKey
    val id: Int = 0, // Only one record allowed
    val stressLevel: Int,
    val mood: String,
    val sleepQuality: Int,
    val mentalFatigue: Int
)