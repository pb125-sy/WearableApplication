package com.example.wearableapplication.services


import android.util.Log

import com.example.wearableapplication.BuildConfig
import com.example.wearableapplication.model.StressAnalysis

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.chat.completions.ChatCompletionMessageParam

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable



/**
 * OpenAIManager
 *
 * Handles:
 *
 * - Communication with OpenAI API
 * - Sending stress analysis prompt
 * - Receiving JSON response
 * - Converting JSON into StressAnalysis model
 *
 */
class OpenAIManager {


    companion object {

        private const val TAG =
            "OPENAI"

    }



    private val client: OpenAIClient =

        OpenAIOkHttpClient.builder()

            .apiKey(
                BuildConfig.OPENAI_API_KEY
            )

            .build()



    /**
     * JSON parser configuration.
     *
     * ignoreUnknownKeys:
     *
     * Allows OpenAI to add extra fields
     * without breaking the application.
     */
    private val json = Json {

        ignoreUnknownKeys = true

        isLenient = true

    }





    /**
     * Sends stress analysis request.
     *
     * Returns:
     *
     * StressAnalysis object
     *
     */
    fun analyzeStress(
        prompt: String,
        onSuccess: (StressAnalysis) -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d(TAG, "analyzeStress called. Key length: ${BuildConfig.OPENAI_API_KEY.length}")
        if (BuildConfig.OPENAI_API_KEY.isEmpty()) {
            onError("API Key is missing. Please add it to local.properties")
            return
        }

        Thread {
            try {
                Log.d(TAG, "Sending request to OpenAI with model: gpt-4o-mini")
                
                val params =
                    ChatCompletionCreateParams.builder()
                        .model("gpt-4o-mini")
                        .messages(
                            listOf(
                                ChatCompletionMessageParam.ofUser(
                                    ChatCompletionUserMessageParam.builder()
                                        .content(prompt)
                                        .build()
                                )
                            )
                        )
                        .build()

                Log.d(TAG, "Request params built, calling OpenAI...")
                val response = client.chat().completions().create(params)
                Log.d(TAG, "OpenAI response received")

                val choice = response.choices().firstOrNull()
                if (choice == null) {
                    onError("AI returned no choices")
                    return@Thread
                }

                val jsonResponse = choice.message().content().orElse("")
                if (jsonResponse.isEmpty()) {
                    onError("AI returned an empty response")
                    return@Thread
                }

                Log.d(TAG, "Raw JSON Response: $jsonResponse")

                val cleaned = cleanJson(jsonResponse)
                Log.d(TAG, "Cleaned JSON: $cleaned")

                val analysis = json.decodeFromString<StressAnalysisResponse>(cleaned)





                val result = StressAnalysis(

                    stressScore =
                        analysis.stressScore,


                    stressLevel =
                        analysis.stressLevel,


                    primaryFactors =
                        analysis.primaryFactors,


                    recommendations =
                        analysis.recommendations,


                    breathingExercise =
                        analysis.breathingExercise,


                    activitySuggestion =
                        analysis.activitySuggestion,


                    screenTimeAdvice =
                        analysis.screenTimeAdvice,


                    confidence =
                        analysis.confidence

                )





                Log.d(TAG, "JSON Parsing Successful")

                onSuccess(result)

            } catch (e: Throwable) {
                Log.e(TAG, "OpenAI Error", e)
                val errorMessage = when {
                    e.message?.contains("401") == true -> "Invalid API Key"
                    e.message?.contains("429") == true -> "API Rate Limit Exceeded"
                    e.message?.contains("Unable to resolve host") == true -> "No Internet Connection"
                    else -> e.message ?: "Unknown Error"
                }
                onError(errorMessage)
            }
        }.start()
    }







    /**
     * Sometimes models return:
     *
     * ```json
     * {
     * ...
     * }
     * ```
     *
     * instead of pure JSON.
     *
     * This removes markdown wrappers.
     */
    private fun cleanJson(

        response: String

    ): String {



        return response

            .replace(
                "```json",
                ""
            )

            .replace(
                "```",
                ""
            )

            .trim()

    }

}





/**
 * Internal JSON mapping class.
 *
 * Separate from StressAnalysis because
 * API responses can change.
 *
 */
@Serializable
internal data class StressAnalysisResponse(


    val stressScore: Double,


    val stressLevel: String,


    val primaryFactors: List<String>,


    val recommendations: List<String>,


    val breathingExercise: String,


    val activitySuggestion: String,


    val screenTimeAdvice: String,


    val confidence: Double = 0.0





)