package com.example.wearableapplication.services

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

import com.example.wearableapplication.model.AppUsage

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/*
 * ScreenTimeManager
  * Research-grade smartphone behavior collector

 * Purpose:
 * - Calculate daily screen time
 * - Calculate per-app usage
 * - Combine UsageEvents + UsageStats
 * - Provide debugging information

 * Accuracy strategy:
 *
 * UsageEvents:
 *      Real-time activity transitions
 *
 * UsageStats:
 *      Android calculated foreground duration
 *
 * Hybrid:
 *      Event-based sessions are compared
 *      with Android aggregated statistics.
 */
class ScreenTimeManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "SCREEN_TIME"
        private const val DEBUG_SEPARATOR =
            "=============================="
        private const val MINUTE =
            60 * 1000L
        private const val HOUR =
            60 * MINUTE

        /**
         * Difference threshold.
         * If UsageEvents and UsageStats
         * differ less than this percentage,
         * weighted correction is applied.
         */
        private const val ACCEPTABLE_DIFFERENCE_PERCENT =
            10

        /* Packages ignored because they are not user interaction. */
        private val SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.google.android.permissioncontroller",
            "com.google.android.inputmethod.latin",

            /*
             * Digital Wellbeing's own foreground time is misattributed
             * by Android's UsageStatsManager: totalTimeInForeground for
             * this package keeps incrementing in real time even when it
             * isn't actually on screen (likely because parts of its UI
             * run embedded inside Settings without a clean background
             * transition). Excluding it here avoids wildly inflated
             * screen time / app usage totals.
             */
            "com.google.android.apps.wellbeing"
        )
    }

    /*
     * Represents an active foreground session.
     * Example:
     * Instagram opened:
     * startTime = 14:20:00
     * endTime = 14:50:00
     */
    private data class ActiveSession(
        val packageName: String,
        var startTime: Long
    )

    /*
     * Stores currently running applications.
     * Key:
     * package name
     * Value:
     * session start time
     */
    private val activeSessions =
        mutableMapOf<String, ActiveSession>()

    /**
     * Stores calculated app durations,
     * Key:
     * package name
     * Value:
     * milliseconds
     */
    private val eventUsage =
        mutableMapOf<String, Long>()

    /*
     * The package name of whatever app currently handles the HOME
     * intent (i.e. the launcher / home screen). This is resolved at
     * runtime because launcher package names vary by OEM - e.g.
     * "com.android.launcher" (AOSP), "com.motorola.launcher3"
     * (Motorola), "com.sec.android.app.launcher" (Samsung), Pixel
     * Launcher, etc. A static list of package names can never cover
     * every device, so we just ask Android which package is
     * currently resolved as the home screen and exclude that,
     * matching how Digital Wellbeing itself treats the launcher as
     * "not app usage".
     */
    private val launcherPackageName: String? by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)

        context.packageManager
            .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
    }

    /*
     * Single source of truth for "should this package be excluded
     * from usage stats". Combines the static SYSTEM_PACKAGES list
     * with the dynamically resolved launcher package, so every
     * filtering call site stays in sync.
     */
    private fun isIgnoredPackage(packageName: String): Boolean {
        return SYSTEM_PACKAGES.contains(packageName) ||
                packageName == launcherPackageName
    }

    /* Permission check.
     * Android does not provide
     * runtime permission dialog
     * for Usage Access.
     * User must enable manually.
     */
    fun hasPermission(): Boolean {
        val appOps =
            context.getSystemService(
                Context.APP_OPS_SERVICE
            ) as AppOpsManager

        val mode =
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        return mode ==
                AppOpsManager.MODE_ALLOWED
    }

    /* Opens Android Usage Access settings. */
    fun openPermissionSettings() {
        val intent =
            Intent(
                Settings.ACTION_USAGE_ACCESS_SETTINGS
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )
        context.startActivity(intent)
    }

    /* Public API
     * Returns:
     * Example: 1h 35m
     */
    fun getTodayScreenTime(): String {
        if (!hasPermission()) {
            Log.e(
                TAG,
                "Usage Access permission missing"
            )
            return "Permission Needed"
        }

        val result =
            calculateHybridUsage()

        return formatDuration(
            result
        )
    }

    /**
     * Public API
     * Returns how many times the device was
     * unlocked today.
     *
     * Approach:
     * Android logs a KEYGUARD_HIDDEN event
     * every time the lock screen is dismissed
     * (i.e. every unlock). We reuse the same
     * UsageEvents stream that screen time /
     * app usage already query, and just count
     * how many of those events occurred today.
     *
     * This deliberately does NOT count
     * KEYGUARD_SHOWN (device locking) - only
     * KEYGUARD_HIDDEN (device unlocking).
     */
    fun getTodayUnlockCount(): Int {
        if (!hasPermission()) {
            Log.e(
                TAG,
                "Usage Access permission missing"
            )
            return 0
        }

        return countUnlockEvents(
            getStartOfDay(),
            getNow()
        )
    }

    /*
     * Walks the raw UsageEvents stream for the
     * given window and counts KEYGUARD_HIDDEN
     * events. Kept separate from
     * collectUsageEvents() because unlock
     * counting doesn't need session
     * reconstruction - just a simple tally.
     */
    private fun countUnlockEvents(
        startTime: Long,
        endTime: Long
    ): Int {
        val usageStatsManager =
            context.getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager

        val usageEvents =
            usageStatsManager.queryEvents(
                startTime,
                endTime
            )

        val event =
            UsageEvents.Event()

        var unlockCount = 0

        while (
            usageEvents.hasNextEvent()
        ) {
            usageEvents.getNextEvent(
                event
            )

            if (
                event.eventType ==
                UsageEvents.Event.KEYGUARD_HIDDEN
            ) {
                unlockCount++

                Log.d(
                    TAG,
                    "UNLOCK EVENT at ${formatDate(event.timeStamp)}"
                )
            }
        }

        Log.d(
            TAG,
            "TOTAL UNLOCKS TODAY: $unlockCount"
        )

        return unlockCount
    }

    /* Public API Returns: [ Instagram -> 1h30m, YouTube -> 45m ]     */
    fun getTodayAppUsage(): List<AppUsage> {
        if (!hasPermission()) {
            return emptyList()
        }

        val hybridData =
            calculateHybridAppUsage()

        val pm = context.packageManager

        return hybridData
            .filter {
                !isIgnoredPackage(
                    it.key
                )
            }

            .map {
                var appLabel = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(it.key, 0)).toString()
                } catch (e: Exception) {
                    it.key
                }

                // If the resolved label still looks like a package name, try to extract a better name.
                if (appLabel == it.key || (appLabel.contains(".") && appLabel.lowercase() == appLabel)) {
                    val parts = it.key.split(".")
                    appLabel = if (parts.size >= 2) {
                        val name = if (parts.last().length <= 3 && parts.size >= 3) {
                            // Handle cases like com.example.app (returns app) or com.instagram.android (returns instagram)
                            parts[parts.size - 2]
                        } else {
                            parts.last()
                        }
                        name.replaceFirstChar { char ->
                            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
                        }
                    } else {
                        it.key
                    }
                }

                AppUsage(
                    packageName = it.key,
                    appName = appLabel,
                    usageTime = it.value
                )
            }
            .sortedByDescending {
                it.usageTime
            }
    }

    /* Calculates today's start timestamp.
     * Example:
     * 2026-07-12 00:00:00
     */
    private fun getStartOfDay(): Long {
        val calendar =
            Calendar.getInstance()

        calendar.set(
            Calendar.HOUR_OF_DAY,
            0
        )

        calendar.set(
            Calendar.MINUTE,
            0
        )

        calendar.set(
            Calendar.SECOND,
            0
        )

        calendar.set(
            Calendar.MILLISECOND,
            0
        )
        return calendar.timeInMillis
    }

    /* Returns current timestamp.     */
    private fun getNow(): Long {
        return System.currentTimeMillis()
    }

    /* Converts millisecond: 3600000 into: 1h 0m     */
    private fun formatDuration(
        milliseconds: Long
    ): String {
        val hours =
            milliseconds / HOUR

        val minutes =
            (milliseconds / MINUTE) % 60

        return "${hours}h ${minutes}m"
    }

    /* ========================================================================== **/

    /**
     * Main UsageEvents parser.
     * This is the real-time event engine.
     * It reconstructs:
     * App opened
     *      |
     *      |
     * App closed
     * into usage sessions.
     */
    private fun collectUsageEvents(
        startTime: Long,
        endTime: Long
    ): Map<String, Long> {

        val usageStatsManager =
            context.getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager

        val usageEvents =
            usageStatsManager.queryEvents(
                startTime,
                endTime
            )

        val event =
            UsageEvents.Event()

        resetSessionData()

        var screenInteractive = true

        Log.d(
            TAG,
            DEBUG_SEPARATOR
        )
        Log.d(
            TAG,
            "RAW EVENT COLLECTION START"
        )
        Log.d(
            TAG,
            "START: ${formatDate(startTime)}"
        )
        Log.d(
            TAG,
            "END  : ${formatDate(endTime)}"
        )
        Log.d(
            TAG,
            DEBUG_SEPARATOR
        )

        while (
            usageEvents.hasNextEvent()
        ) {
            usageEvents.getNextEvent(
                event
            )

            val packageName =
                event.packageName ?: continue

            val timestamp =
                event.timeStamp

            val eventType =
                event.eventType

            when(eventType) {
                /* Screen turned ON. The device is usable again. */
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    screenInteractive = true

                    debugEvent(
                        packageName,
                        "SCREEN_INTERACTIVE",
                        timestamp
                    )
                }

                /*
                 * Screen OFF / device locked.
                 * All running sessions
                 * should stop.
                 */
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    screenInteractive = false

                    debugEvent(
                        packageName,
                        "SCREEN_NON_INTERACTIVE",
                        timestamp
                    )

                    closeAllSessions(
                        timestamp
                    )
                }

                /*
                 * Modern Android versions.
                 * Application entered foreground.
                 */
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if(screenInteractive) {
                        startSession(
                            packageName,
                            timestamp,
                            "MOVE_TO_FOREGROUND"
                        )
                    }
                }

                /* Application moved away.*/
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    closeSession(
                        packageName,
                        timestamp,
                        "MOVE_TO_BACKGROUND"
                    )
                }

                /**
                 * Activity visible.
                 * Used as fallback because
                 * some OEMs do not send
                 * MOVE_TO_FOREGROUND.
                 */
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if(screenInteractive) {
                        startSession(
                            packageName,
                            timestamp,
                            "ACTIVITY_RESUMED"
                        )
                    }
                }

                /* Activity paused.*/
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    closeSession(
                        packageName,
                        timestamp,
                        "ACTIVITY_PAUSED"
                    )
                }

                /* Activity completely stopped.*/
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    closeSession(
                        packageName,
                        timestamp,
                        "ACTIVITY_STOPPED"
                    )
                }
            }
        }

        /*
         * Handle applications still running.
         * Example:
         * User opens Chrome
         * Does not close Chrome
         * Opens NeuroWave
         */
        closeRemainingSessions(
            endTime
        )

        Log.d(
            TAG,
            DEBUG_SEPARATOR
        )
        Log.d(
            TAG,
            "RAW EVENT COLLECTION FINISHED"
        )
        Log.d(
            TAG,
            DEBUG_SEPARATOR
        )
        return eventUsage.toMap()
    }

    /**
     * Starts an application session.
     * Duplicate protection:
     * Instagram RESUMED
     * Instagram MOVE_TO_FOREGROUND
     * Both arrive.
     * We keep only one.
     */
    private fun startSession(
        packageName: String,
        timestamp: Long,
        source: String
    ) {
        if(
            isIgnoredPackage(
                packageName
            )
        ) {
            return
        }

        val existing =
            activeSessions[packageName]

        if(existing != null) {
            Log.d(
                TAG,
                """
                    DUPLICATE START IGNORED
    
    Package:
    $packageName
    
    Existing:
    ${formatDate(existing.startTime)}
    
    New:
    ${formatDate(timestamp)}
    
    Source:
    $source
                    """.trimIndent()
            )
            return
        }

        activeSessions[packageName] =
            ActiveSession(
                packageName,
                timestamp
            )

        debugEvent(
            packageName,
            source,
            timestamp
        )
    }

    /* Ends an application session. */
    private fun closeSession(
        packageName: String,
        timestamp: Long,
        source: String
    ) {
        val session =
            activeSessions[packageName]
                ?: return

        val duration =
            timestamp -
                    session.startTime

        if(duration > 0) {
            eventUsage[packageName] =
                (
                        eventUsage[packageName]
                            ?: 0L
                        ) + duration
            Log.d(
                TAG,
                """
                    SESSION CLOSED
    
    Package:
    $packageName
    
    Start:
    ${formatDate(session.startTime)}
    
    End:
    ${formatDate(timestamp)}
    
    Duration:
    ${duration / 1000}s
    
    Source:
    $source
                    """.trimIndent()
            )
        }

        activeSessions.remove(
            packageName
        )
    }

    /* Stops every running application.
     * Used when:
     * Screen OFF
     * Device locked
     */
    private fun closeAllSessions(
        timestamp: Long
    ) {

        val runningApps =
            activeSessions.keys.toList()

        runningApps.forEach {
            closeSession(
                it,
                timestamp,
                "SCREEN_OFF"
            )
        }
    }
    /* Handles apps that never sent. PAUSED/BACKGROUND. */
    private fun closeRemainingSessions(
        timestamp: Long
    ) {
        val runningApps =
            activeSessions.keys.toList()

        runningApps.forEach {
            closeSession(
                it,
                timestamp,
                "END_OF_QUERY"
            )
        }
    }

    /* Clears previous calculation. */
    private fun resetSessionData() {
        activeSessions.clear()
        eventUsage.clear()
    }

    /* Debug helper.*/
    private fun debugEvent(
        packageName: String,
        type: String,
        timestamp: Long
    ) {
        Log.d(
            TAG,
            """
                EVENT
   
    Package:
    $packageName
   
    Type:
    $type
    
    Time:
    ${formatDate(timestamp)}  
                """.trimIndent()
        )
    }

    /*=================================================================*/

    /* Hybrid calculator.
     * Combines:
     * 1. UsageEvents       reconstructed sessions
     * 2. UsageStats        Android calculated usage
     * Returns final corrected
     * total screen time.
     */
    private fun calculateHybridUsage(): Long {
        val startTime =
            getStartOfDay()
        val endTime =
            getNow()
        val eventTotal =
            collectUsageEvents(
                startTime,
                endTime
            )
                .values
                .sum()
        val statsTotal =
            collectUsageStats()
                .values
                .sum()
        val finalTotal =
            applyHybridCorrection(
                eventTotal,
                statsTotal
            )

        printComparisonLog(
            eventTotal,
            statsTotal,
            finalTotal
        )
        return finalTotal
    }
    /* Calculates per-app usage.
     * The final result combines:
     * UsageEvents per app
     * UsageStats per app
     */
    private fun calculateHybridAppUsage():
            Map<String, Long> {

        val startTime =
            getStartOfDay()

        val endTime =
            getNow()

        val eventData =
            collectUsageEvents(
                startTime,
                endTime
            )

        val statsData =
            collectUsageStats()

        val result =
            mutableMapOf<String, Long>()

        val packages =
            mutableSetOf<String>()

        packages.addAll(
            eventData.keys
        )

        packages.addAll(
            statsData.keys
        )

        for(packageName in packages) {
            val eventValue =
                eventData[packageName]
                    ?: 0L

            val statsValue =
                statsData[packageName]
                    ?: 0L

            result[packageName] =
                applyHybridCorrection(
                    eventValue,
                    statsValue
                )
        }
        return result
    }

    /* Reads Android's own usage database.
     * This is similar to what
     * Digital Wellbeing can access.
     */
    private fun collectUsageStats():
            Map<String, Long> {

        val usageStatsManager =
            context.getSystemService(
                Context.USAGE_STATS_SERVICE
            )
                    as UsageStatsManager

        val startTime =
            getStartOfDay()

        val endTime =
            getNow()

        val usageStatsList:
                List<UsageStats> =

            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

        val result =
            mutableMapOf<String, Long>()

        Log.d(
            TAG,
            DEBUG_SEPARATOR
        )

        Log.d(
            TAG,
            "ANDROID USAGE STATS"
        )

        usageStatsList.forEach { stats ->
            val packageName =
                stats.packageName

            if(
                isIgnoredPackage(
                    packageName
                )
            ) {
                return@forEach
            }

            val time =
                stats.totalTimeInForeground

            if(time > 0) {
                result[packageName] =
                    time

                Log.d(
                    TAG,
                    """
        PACKAGE:
        $packageName
        
        Foreground: 
        ${time / 1000}s
                            """.trimIndent()
                )
            }
        }

        Log.d(
            TAG,
            DEBUG_SEPARATOR
        )
        return result
    }

    /**
     * Accuracy correction algorithm.
    Example:
     * UsageEvents:
     * 120 minutes
     * UsageStats:
     * 125 minutes
     * Difference:
     * 4%
     * Result:
     * weighted average
     * If difference is large:
     * trust UsageStats
     */
    private fun applyHybridCorrection(
        eventValue: Long,
        statsValue: Long
    ): Long {
        if(eventValue == 0L) {
            return statsValue
        }

        if(statsValue == 0L) {
            return eventValue
        }

        val differencePercent =
            (
                    abs(eventValue - statsValue).toDouble()
                            /
                            statsValue.toDouble()
                    ) * 100

        return if(
            differencePercent
            <=
            ACCEPTABLE_DIFFERENCE_PERCENT
        ) {

            /**
             * 60% UsageStats
             * 40% UsageEvents
             * Reason:
             * Android already aggregates
             * lifecycle edge cases.
             */
            (
                    statsValue * 0.6
                            +
                            eventValue * 0.4
                    )
                .toLong()
        } else {
            /*
             * Large mismatch.
             * Example:
             * Missing lifecycle events.
             * Android value is safer.
             */
            statsValue
        }
    }
    /* Prints comparison information.*/
    private fun printComparisonLog(
        eventValue: Long,
        statsValue: Long,
        finalValue: Long
    ) {
        val difference =
            abs(
                eventValue -
                        statsValue
            )

        Log.d(
            TAG,
            """
                   
        $DEBUG_SEPARATOR
      
        HYBRID ACCURACY COMPARISON
       
        UsageEvents:
        ${eventValue / MINUTE} minutes
        
        UsageStats:
        ${statsValue / MINUTE} minutes
        
        Difference:
        ${difference / MINUTE} minutes
        
        Final Result:
        ${finalValue / MINUTE} minutes
        
        $DEBUG_SEPARATOR
                    """.trimIndent()
        )
    }

    /* ================================================================== */

    /* Converts timestamp into readable date.
     * Example:
     * 2026-07-12 14:35:20
     */
    private fun formatDate(
        timestamp: Long
    ): String {
        val formatter =
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
            )
        return formatter.format(
            Date(timestamp)
        )
    }

    /* Converts milliseconds into detailed human-readable format.
     * Example: 3725000 ms becomes: 1h 2m 5s
     */
    private fun formatDetailedDuration(
        milliseconds: Long
    ): String {
        val hours =
            milliseconds / HOUR

        val minutes =
            (
                    milliseconds / MINUTE
                    ) % 60

        val seconds =
            (
                    milliseconds / 1000
                    ) % 60

        return "${hours}h ${minutes}m ${seconds}s"
    }

    /* Checks whether package should
     * appear in user statistics.
     * Filters:
     * - Android System UI
     * - Launcher
     * - Keyboard
     * - Settings
     */
    private fun isUserApplication(
        packageName: String
    ): Boolean {
        return !isIgnoredPackage(
            packageName
        )
    }

    /* Generates complete debug report.
     * Useful during research testing.
     */
    fun printDebugReport() {
        if(!hasPermission()) {
            Log.e(
                TAG,
                "Cannot generate report. Permission missing."
            )
            return
        }

        val startTime =
            getStartOfDay()

        val endTime =
            getNow()

        val appUsage =
            calculateHybridAppUsage()

        Log.d(
            TAG,
            """
                    
        $DEBUG_SEPARATOR
        
        SCREEN TIME DEBUG REPORT
        
        $DEBUG_SEPARATOR
        
        
        Start:
        
        ${formatDate(startTime)}
        
        
        End:
        
        ${formatDate(endTime)}
        
        
        $DEBUG_SEPARATOR
        
        APPLICATION USAGE
        
        $DEBUG_SEPARATOR
                    """.trimIndent()
        )
        var total = 0L
        appUsage
            .filter {
                isUserApplication(
                    it.key
                )

            }

            .toList()

            .sortedByDescending {
                it.second
            }

            .forEach {
                total += it.second

                Log.d(
                    TAG,
                    """
                            
        APP:       
        ${it.first}
        
        TIME:
        ${formatDetailedDuration(
                        it.second
                    )}
                            """.trimIndent()
                )
            }

        Log.d(
            TAG,
            """
                    
        $DEBUG_SEPARATOR
        
        TOTAL SCREEN TIME:
        
        ${formatDetailedDuration(total)}
        
        $DEBUG_SEPARATOR
                    """.trimIndent()
        )
    }
}