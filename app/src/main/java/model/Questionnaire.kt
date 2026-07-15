package com.example.wearableapplication.model

data class Questionnaire(
    /**
     * User self-reported stress level (1-5).
     * 1 = Very Low to 5 = Very High
     */
    val stressLevel: Int,

    /**
     * User self-reported mood.
     * Expected values: Happy, Neutral, Stressed
     */
    val mood: String,

    /**
     * User self-reported sleep quality (1-5).
     * 1 = Very Poor to 5 = Excellent
     */
    val sleepQuality: Int,

    /**
     * User self-reported mental fatigue (1-5).
     * 1 = Not Tired to 5 = Extremely Tired
     */
    val mentalFatigue: Int
)