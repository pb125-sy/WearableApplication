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
        return withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getDatabase(applicationContext)
                val dao = database.timeWindowDao()
                val healthConnectManager = HealthConnectManager(applicationContext)
                val screenTimeManager = ScreenTimeManager(applicationContext)
                val csvManager = CsvManager(applicationContext)

                if (!healthConnectManager.hasAllPermissions()) {
                    android.util.Log.e("DataHarvestWorker", "Health Connect permissions missing. Some data will be zero.")
                }

                // 1. Determine the start time for the first window in this run
                // Anchored to the exact 15-minute boundary where the last record ended.
                val latestRecord = dao.getLatestRecord()
                var currentStartTime = if (latestRecord != null) {
                    latestRecord.windowStartTime.plus(15, ChronoUnit.MINUTES)
                } else {
                    // First run: align to the start of the previous 15-minute block
                    val currentMinute = Instant.now().atZone(java.time.ZoneId.systemDefault()).minute
                    val minutesToSubtract = (currentMinute % 15) + 15
                    Instant.now().minus(minutesToSubtract.toLong(), ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES)
                }

                val now = Instant.now()
                var recordsCreated = 0
                val newRecords = mutableListOf<TimeWindowRecords>()

                android.util.Log.d("DataHarvestWorker", "Starting harvest. Latest record: ${latestRecord?.windowStartTime}. Catching up from: $currentStartTime")

                // 2. Loop to fill gaps if the worker was delayed (e.g., Doze mode)
                // We keep creating 15-minute blocks until the next potential block would end in the future.
                while (currentStartTime.plus(15, ChronoUnit.MINUTES).isBefore(now)) {
                    val windowEndTime = currentStartTime.plus(15, ChronoUnit.MINUTES)
                    
                    android.util.Log.d("DataHarvestWorker", "Processing window: $currentStartTime to $windowEndTime")

                    // 3. Metadata for questionnaire and BPM logic
                    // We only apply real-time data (BPM) to the VERY LAST window we create in this loop.
                    val isLastWindow = windowEndTime.plus(15, ChronoUnit.MINUTES).isAfter(now)
                    val isPrecedingWindow = !isLastWindow && windowEndTime.plus(30, ChronoUnit.MINUTES).isAfter(now)

                    // 4. Fetch health and usage data for this specific segment
                    val steps = healthConnectManager.readStepsForWindow(currentStartTime, windowEndTime)
                    val calories = healthConnectManager.readCaloriesForWindow(currentStartTime, windowEndTime)
                    val screenUsage = screenTimeManager.getUsageForWindow(currentStartTime, windowEndTime)
                    val categoryUsage = screenTimeManager.getCategoryUsageForWindow(currentStartTime, windowEndTime)
                    val unlocks = screenTimeManager.getUnlocksForWindow(currentStartTime, windowEndTime)

                    // 5. Handle Bluetooth and Questionnaire data
                    // BPM is real-time, so we only apply the averaged buffer to the VERY LAST window.
                    val avgBpm = if (isLastWindow) BluetoothBpmManager.getAverageAndReset() else null

                    // Questionnaire is applied to the Current and Preceding windows per user request.
                    val latestQuestionnaire = database.questionnaireDao().getLatestQuestionnaire()
                    val applyQuestionnaire = isLastWindow || isPrecedingWindow

                    val record = TimeWindowRecords(
                        windowStartTime = currentStartTime,
                        screenTimeSec = screenUsage,
                        unlockCount = unlocks,
                        stepsTaken = steps,
                        totalCalories = calories,
                        avgBpm = avgBpm,
                        selfReportedStress = if (applyQuestionnaire) latestQuestionnaire?.stressLevel else null,
                        currentMood = if (applyQuestionnaire) latestQuestionnaire?.mood else null,
                        sleepRating = if (applyQuestionnaire) latestQuestionnaire?.sleepQuality else null,
                        tirednessLevel = if (applyQuestionnaire) latestQuestionnaire?.mentalFatigue else null,

                        // New Category Fields
                        socialTimeSec = categoryUsage.socialSec,
                        gamingEntertainmentTimeSec = categoryUsage.gamingEntertainmentSec,
                        otherTimeSec = categoryUsage.otherSec
                    )

                    // 6. Save to DB
                    dao.insertRecord(record)
                    newRecords.add(record)

                    recordsCreated++
                    currentStartTime = windowEndTime
                }

                // 7. Export to CSV in bulk
                if (newRecords.isNotEmpty()) {
                    csvManager.appendRecordsToCsv(newRecords)
                }

                // 8. Clear questionnaire so it doesn't leak into future blocks
                if (recordsCreated > 0) {
                    database.questionnaireDao().clearQuestionnaire()
                    android.util.Log.d("DataHarvestWorker", "Finished run. Created $recordsCreated records. Last window end: $currentStartTime")
                } else {
                    android.util.Log.d("DataHarvestWorker", "No new windows to create. currentStartTime + 15m is still after 'now'.")
                }

                Result.success()

            } catch (e: Exception) {
                android.util.Log.e("DataHarvestWorker", "Error harvesting data", e)
                Result.retry()
            }
        }
    }
}