package com.example.wearableapplication

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresExtension
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import Services.HealthConnectManager
import com.example.wearableapplication.services.ScreenTimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
//import com.example.wearableapplication.model.Questionnaire

class DataHarvestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 7)
    override suspend fun doWork(): Result {
        // Move work to a background thread
        return withContext(Dispatchers.IO) {
            try {
                // 1. Calculate the exact time window
                val endTime = Instant.now()
                val startTime = endTime.minus(15, ChronoUnit.MINUTES)

                // 2. Instantiate your Managers
                val healthConnectManager = HealthConnectManager(applicationContext)
                val screenTimeManager = ScreenTimeManager(applicationContext)
                val database = AppDatabase.getDatabase(applicationContext) // Your Room DB

                // 3. Fetch the data clamped to this specific window

                val steps = healthConnectManager.readStepsForWindow(startTime, endTime)
                val calories = healthConnectManager.readCaloriesForWindow(startTime, endTime)
                val screenUsage = screenTimeManager.getUsageForWindow(startTime, endTime)
                val unlocks = screenTimeManager.getUnlocksForWindow(startTime, endTime)
                val latestQuestionnaire = database.questionnaireDao().getLatestQuestionnaire()

                // 4. Create the Room Record

                val record = TimeWindowRecords(
                    windowStartTime = startTime,
                    screenTimeSec = screenUsage,
                    unlockCount = unlocks,
                    stepsTaken = steps,
                    totalCalories = calories,
                    avgBpm = 0, // Placeholder until Bluetooth logic is backgrounded
                    selfReportedStress = latestQuestionnaire?.stressLevel,
                    currentMood = latestQuestionnaire?.mood,
                    sleepRating = latestQuestionnaire?.sleepQuality,
                    tirednessLevel = latestQuestionnaire?.mentalFatigue
                )

                // 5. Save to Room Database
                val dao = database.timeWindowDao()
                dao.insertRecord(record)

                // 6. Export to CSV (Append)
                val csvManager = CsvManager(applicationContext)
                csvManager.appendRecordToCsv(record)

                android.util.Log.d("DataHarvestWorker", "Successfully logged 15-minute window starting at $startTime")

                // Tell Android the background task was successful
                Result.success()

            } catch (e: Exception) {
                android.util.Log.e("DataHarvestWorker", "Error harvesting data", e)
                // Tell Android to try again later if it failed
                Result.retry()
            }
        }
    }
}