package com.example.wearableapplication


import android.util.Log
import com.example.wearableapplication.model.StressAnalysis
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString


object JsonTest {


    fun testParser() {


        val jsonString = """
        {
          "stressScore": 78,
          "stressLevel": "High",

          "primaryFactors": [
            "High screen time",
            "Elevated heart rate"
          ],

          "recommendations": [
            "Take a 5 minute break",
            "Practice breathing exercise"
          ],

          "breathingExercise":
          "Box breathing for 5 minutes",

          "activitySuggestion":
          "Walk for 10 minutes",

          "screenTimeAdvice":
          "Reduce continuous mobile usage",

          "confidence": 85
        }
        """.trimIndent()



        try {


            val result =

                Json.decodeFromString<StressAnalysis>(
                    jsonString
                )



            Log.d(
                "JSON_TEST",
                "Parsing Successful"
            )


            Log.d(
                "JSON_TEST",
                result.toString()
            )


        }

        catch(e: Exception) {


            Log.e(
                "JSON_TEST",
                "Parsing Failed",
                e
            )

        }


    }


}