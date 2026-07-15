package com.example.wearableapplication.services

import com.example.wearableapplication.model.StressFeatures
import java.util.concurrent.TimeUnit


/**
 * PromptBuilder
 *
 * Converts collected physiological and
 * smartphone behavioral data into a
 * structured prompt for OpenAI.
 *
 */
object PromptBuilder {


    /**
     * Builds the complete stress analysis prompt.
     *
     * Input:
     *
     * StressFeatures
     *
     * Output:
     *
     * AI analysis instruction
     *
     */
    fun buildPrompt(

        features: StressFeatures

    ): String {


        val formattedAppUsage =
            formatAppUsage(
                features
            )



        return """
            
You are an AI stress monitoring assistant
for a university research project.

Your task is to estimate the user's
current stress level using physiological
and smartphone behavioral information.

IMPORTANT RULES:

- Do not diagnose medical conditions.
- Do not claim certainty.
- Provide a wellness estimation only.
- Consider multiple signals together.
- Explain which factors influenced your estimation.
- Give practical lifestyle recommendations.
- Recommendations MUST be specifically tailored to the provided data using these logic rules:
    * If Heart Rate > 90 BPM while Steps < 100: User is likely experiencing mental stress. Suggest immediate 2-minute breathing exercise.
    * If Screen Time > 4 hours: Recommend a "Digital Detox" and explain the link between blue light and stress.
    * If Unlock Count > 50: Address "Compulsive Checking" and suggest leaving the phone in another room.
    * If Steps < 4000: Recommend a "Movement Break" to clear the mind.
    * If App Usage shows high social media (Instagram, TikTok, etc.): Suggest limiting social comparison.
    * If User Self-Reported Stress is High but Physiological signals are Normal: Acknowledge the user's feelings and suggest mindfulness.
    * If User Sleep Quality is Low: Explain how poor sleep contributes to today's stress level.
- If Heart Rate is 0, ignore it in the analysis as it means the sensor is still connecting.


==============================
PHYSIOLOGICAL DATA
==============================

Heart Rate:

${features.heartRate} BPM


Interpretation guideline:

Normal resting heart rate varies between
individuals.

Consider elevated heart rate together
with behavioral patterns.


==============================
SMARTPHONE BEHAVIOR DATA
==============================


Today's Screen Time:

${features.screenTime}


Application Usage:

$formattedAppUsage


Unlock Count:

${features.unlockCount}


Interpretation guideline:

Frequent phone checking and excessive
continuous usage may indicate digital
fatigue or increased cognitive load.


==============================
PHYSICAL ACTIVITY DATA
==============================


Steps:

${features.steps}


Calories Burned:

${features.calories}


Interpretation guideline:

Low physical activity combined with
high screen usage may contribute to
mental fatigue.


==============================
USER SELF-REPORTED DATA
==============================

Subjective Stress: ${features.questionnaire?.stressLevel ?: "Not provided"}/5
Mood: ${features.questionnaire?.mood ?: "Not provided"}
Sleep Quality: ${features.questionnaire?.sleepQuality ?: "Not provided"}/5
Mental Fatigue: ${features.questionnaire?.mentalFatigue ?: "Not provided"}/5


==============================
ANALYSIS REQUIREMENT
==============================


Analyze:

1. Possible stress level.

2. Main contributing factors.

3. Relationship between heart rate
and smartphone behavior.

4. Personalized recommendations.


Return ONLY valid JSON.

Do not include markdown.

Use exactly this format:


{
  "stressScore": 0,
  "stressLevel": "Low",
  "primaryFactors": [
    "factor 1",
    "factor 2"
  ],
  "recommendations": [
    "recommendation 1",
    "recommendation 2",
    "recommendation 3"
  ],
  "breathingExercise": "",
  "activitySuggestion": "",
  "screenTimeAdvice": "",
  "confidence": 0
}


Stress score:

0-30:
Low stress

31-70:
Moderate stress

71-100:
High stress


Recommendations should include:

- A short break suggestion
- Breathing exercise when appropriate
- Physical activity suggestion
- Smartphone usage advice


""".trimIndent()

    }





    /**
     * Converts AppUsage objects into
     * readable AI input.
     *
     *
     * Example:
     *
     * Instagram:
     * 1h 30m
     *
     */
    private fun formatAppUsage(

        features: StressFeatures

    ): String {


        if(
            features.appUsage.isEmpty()
        ) {

            return "No application data available"

        }



        return features.appUsage

            .sortedByDescending {

                it.usageTime

            }

            .take(10)

            .joinToString("\n") {


                val duration =
                    formatDuration(
                        it.usageTime
                    )


                "${it.appName}: $duration"

            }


    }





    /**
     * Converts milliseconds into
     * readable duration.
     *
     * Example:
     *
     * 5400000
     *
     * becomes:
     *
     * 1h 30m
     *
     */
    private fun formatDuration(

        milliseconds: Long

    ): String {


        val hours =
            TimeUnit.MILLISECONDS
                .toHours(milliseconds)



        val minutes =
            TimeUnit.MILLISECONDS
                .toMinutes(milliseconds)
                .rem(60)



        return when {


            hours > 0 ->
                "${hours}h ${minutes}m"


            else ->
                "${minutes}m"

        }


    }

}