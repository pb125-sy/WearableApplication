package com.example.wearableapplication.model


/**
 * StressFeatures
 *
 * Contains all physiological and smartphone
 * behavioral features used for AI stress analysis.
 *
 * Data flow:
 *
 * M5StickC Plus2
 *        |
 *        |
 *     heartRate
 *
 * Smartphone
 *        |
 *        |
 * screenTime
 * appUsage
 * unlockCount
 * steps
 * calories
 *
 *        |
 *        |
 * StressFeatures
 *
 *        |
 *        |
 * OpenAI API
 *
 */
data class StressFeatures(


    /**
     * Current heart rate obtained
     * from M5StickC Plus2 pulse sensor.
     *
     * Example:
     *
     * 82 BPM
     *
     */
    val heartRate: Int,



    /**
     * Total screen usage today.
     *
     * Stored as formatted text because
     * Digital Wellbeing displays human-readable
     * values.
     *
     * Example:
     *
     * "3h 45m"
     *
     */
    val screenTime: String,



    /**
     * List of applications and their usage duration.
     *
     * Example:
     *
     * Instagram -> 1h 20m
     * YouTube -> 45m
     *
     */
    val appUsage: List<AppUsage>,



    /**
     * Number of phone unlock events today.
     *
     * Example:
     *
     * 86 unlocks
     *
     */
    val unlockCount: Int,



    /**
     * Total steps today.
     *
     * Obtained later using:
     *
     * Health Connect API
     *
     */
    val steps: Int,



    /**
     * Calories burned today.
     *
     * Obtained later using:
     *
     * Health Connect API
     *
     * Example:
     *
     * "430 kcal"
     *
     */
    val calories: String,



    /**
     * Optional timestamp.
     *
     * Useful when storing multiple
     * stress measurements.
     *
     * Example:
     *
     * 2026-07-13 14:30
     *
     */
    val timestamp: Long = System.currentTimeMillis()

)