package com.example.wearableapplication.Services

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ScreenTimeManager(private val context: Context) {

    companion object {
        private const val TAG = "SCREEN_TIME"
    }

    fun hasPermission(): Boolean {

        val appOps =
            context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )

        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openPermissionSettings() {

        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun getTodayScreenTime(): String {

        if (!hasPermission()) {
            Log.d(TAG, "Usage Access Permission NOT granted")
            return "Permission Needed"
        }

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

        val calendar = Calendar.getInstance()

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val formatter =
            SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        Log.d(TAG, "=======================================")
        Log.d(TAG, "Today's Screen Time Calculation")
        Log.d(TAG, "Start : ${Date(startTime)}")
        Log.d(TAG, "End   : ${Date(endTime)}")
        Log.d(TAG, "=======================================")

        val usageEvents =
            usageStatsManager.queryEvents(startTime, endTime)

        val event = UsageEvents.Event()

        // Stores the latest RESUMED time for each app
        val resumeMap = mutableMapOf<String, Long>()

        // Stores accumulated usage per app
        val appUsage = mutableMapOf<String, Long>()

        Log.d(TAG, "========= RAW EVENTS =========")

        while (usageEvents.hasNextEvent()) {

            usageEvents.getNextEvent(event)

            when (event.eventType) {

                UsageEvents.Event.ACTIVITY_RESUMED -> {

                    resumeMap[event.packageName] = event.timeStamp

                    Log.d(
                        TAG,
                        "RESUME : ${event.packageName}  ${formatter.format(Date(event.timeStamp))}"
                    )
                }

                UsageEvents.Event.ACTIVITY_PAUSED -> {

                    val resumeTime =
                        resumeMap[event.packageName]

                    if (resumeTime != null) {

                        val duration =
                            event.timeStamp - resumeTime

                        appUsage[event.packageName] =
                            (appUsage[event.packageName] ?: 0L) + duration

                        Log.d(
                            TAG,
                            """
PAUSE
Package : ${event.packageName}
Resume  : ${formatter.format(Date(resumeTime))}
Pause   : ${formatter.format(Date(event.timeStamp))}
Session : ${duration / 1000} sec
                            """.trimIndent()
                        )

                        resumeMap.remove(event.packageName)
                    }
                }
            }
        }

        //----------------------------------------------------
        // Handle apps still open (no PAUSE event yet)
        //----------------------------------------------------

        for ((packageName, resumeTime) in resumeMap) {

            val duration =
                endTime - resumeTime

            appUsage[packageName] =
                (appUsage[packageName] ?: 0L) + duration

            Log.d(
                TAG,
                """
ONGOING SESSION
Package : $packageName
Resume  : ${formatter.format(Date(resumeTime))}
Now     : ${formatter.format(Date(endTime))}
Session : ${duration / 1000} sec
                """.trimIndent()
            )
        }

        //----------------------------------------------------
        // Print app usage summary
        //----------------------------------------------------

        Log.d(TAG, "")
        Log.d(TAG, "========= APP SUMMARY =========")

        var total = 0L

        appUsage
            .toList()
            .sortedByDescending { it.second }
            .forEach { (packageName, duration) ->

                total += duration

                val hours = duration / (1000 * 60 * 60)
                val minutes = (duration / (1000 * 60)) % 60
                val seconds = (duration / 1000) % 60

                Log.d(
                    TAG,
                    "$packageName -> ${hours}h ${minutes}m ${seconds}s"
                )
            }

        Log.d(TAG, "===============================")

        //----------------------------------------------------
        // Total
        //----------------------------------------------------

        val totalHours =
            total / (1000 * 60 * 60)

        val totalMinutes =
            (total / (1000 * 60)) % 60

        val totalSeconds =
            (total / 1000) % 60

        Log.d(TAG, "")
        Log.d(TAG, "========= FINAL TOTAL =========")
        Log.d(TAG, "Total Milliseconds : $total")
        Log.d(TAG, "Total Seconds      : ${total / 1000}")
        Log.d(TAG, "Total Minutes      : ${total / 60000}")
        Log.d(TAG, "Screen Time        : ${totalHours}h ${totalMinutes}m ${totalSeconds}s")
        Log.d(TAG, "===============================")

        return "${totalHours}h ${totalMinutes}m"
    }
}