package com.example.wearableapplication.model

data class AppUsage(

    val packageName: String,

    val appName: String,

    /**
     * Total foreground usage duration
     * in milliseconds
     */
    val usageTime: Long

)