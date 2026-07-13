package com.example.wearableapplication.services


import android.util.Log

import com.example.wearableapplication.BuildConfig
import com.example.wearableapplication.model.StressAnalysis

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.responses.ResponseCreateParams

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



        Thread {


            try {


                Log.d(
                    TAG,
                    "Sending request to OpenAI..."
                )


                Log.d(
                    TAG,
                    prompt
                )




                val params =

                    ResponseCreateParams.builder()

                        .model(
                            "gpt-5.5"
                        )

                        .input(
                            prompt
                        )

                        .build()





                val response =

                    client.responses()
                        .create(
                            params
                        )





                /**
                 * Extract text from response.
                 *
                 * OpenAI SDK version used
                 * in this project does not expose
                 * response.outputText().
                 *
                 */
                val jsonResponse =

                    response.output()

                        .mapNotNull {

                                item ->

                            item.message()
                                .orElse(null)

                        }

                        .flatMap {

                                message ->

                            message.content()

                        }

                        .mapNotNull {

                                content ->

                            content.outputText()
                                .orElse(null)

                        }

                        .joinToString("") {

                            it.text()

                        }




                Log.d(
                    TAG,
                    "Raw JSON Response:"
                )


                Log.d(
                    TAG,
                    jsonResponse
                )

                val analysis =

                    json.decodeFromString<StressAnalysisResponse>(

                        cleanJson(
                            jsonResponse
                        )

                    )





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





                Log.d(
                    TAG,
                    "JSON Parsing Successful"
                )



                onSuccess(
                    result
                )



            }

            catch(e: Exception) {


                Log.e(
                    TAG,
                    "OpenAI Error",
                    e
                )


                onError(

                    e.message
                        ?: "Unknown Error"

                )

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