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
 * 
 * Purpose:
 * - Calculate daily screen time (Interactive duration)
 * - Calculate per-app usage (Exclusive foreground duration)
 * - Combine UsageEvents + UsageStats for accuracy
 */
class ScreenTimeManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "SCREEN_TIME"
        private const val DEBUG_SEPARATOR = "=============================="
        private const val MINUTE = 60 * 1000L
        private const val HOUR = 60 * MINUTE

        /* Packages ignored because they are not user interaction. */
        private val SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.google.android.permissioncontroller",
            "com.google.android.inputmethod.latin",
            "com.google.android.apps.wellbeing",
            "com.google.android.as"
        )
        
        // Event types not always available in older Android but useful
        private const val EVENT_KEYGUARD_SHOWN = 18
        private const val EVENT_KEYGUARD_HIDDEN = 17
    }

    private val ignoredCache = mutableMapOf<String, Boolean>()

    private val launcherPackageName: String? by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }

    private fun isIgnoredPackage(packageName: String): Boolean {
        ignoredCache[packageName]?.let { return it }
        
        val result = when {
            SYSTEM_PACKAGES.contains(packageName) -> true
            packageName == launcherPackageName -> true
            // ALLOW this app itself to be included, but only if it's used as a real app
            packageName.startsWith("com.android.system") -> true
            packageName.startsWith("com.android.providers") -> true
            packageName.startsWith("com.google.android.gms") -> true
            packageName.startsWith("com.google.android.overlay") -> true
            // Whitelist certain system apps
            packageName == "com.android.settings" || 
            packageName == "com.google.android.settings" || 
            packageName == "com.google.android.apps.healthdata" -> false
            else -> try {
                context.packageManager.getLaunchIntentForPackage(packageName) == null
            } catch (e: Exception) {
                true
            }
        }
        
        ignoredCache[packageName] = result
        return result
    }

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openPermissionSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Public API: Total Screen Time (Sum of all app foreground durations)
     * Matches Digital Wellbeing's calculation method.
     */
    fun getTodayScreenTime(): String {
        if (!hasPermission()) return "Permission Needed"
        
        val appUsage = getTodayAppUsage()
        val totalMs = appUsage.sumOf { it.usageTime }
        
        return formatDuration(totalMs)
    }

    fun getTodayUnlockCount(): Int {
        if (!hasPermission()) return 0
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usageStatsManager.queryEvents(getStartOfDay(), getNow())
        val event = UsageEvents.Event()
        var count = 0
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) {
                count++
            }
        }
        return count
    }

    /**
     * Public API: Per-app usage breakdown
     */
    fun getTodayAppUsage(): List<AppUsage> {
        if (!hasPermission()) return emptyList()

        val startTime = getStartOfDay()
        val endTime = getNow()

        val eventData = collectUsageEvents(startTime, endTime)
        val statsData = collectUsageStats(startTime, endTime)
        val pm = context.packageManager

        val allPackages = (eventData.keys + statsData.keys).toSet()
        
        return allPackages.map { packageName ->
            val eventValue = eventData[packageName] ?: 0L
            val statsValue = statsData[packageName] ?: 0L
            
            // Correction logic:
            // Trust UsageStats (System) for historical accuracy.
            // Use UsageEvents (Event) only if it captures a LIVE session not yet in Stats.
            val correctedValue = applyHybridCorrection(eventValue, statsValue)

            var appLabel = try {
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                packageName
            }

            // Clean up label
            if (appLabel == packageName || (appLabel.contains(".") && appLabel.lowercase() == appLabel)) {
                val parts = packageName.split(".")
                if (parts.size >= 2) {
                    val name = if (parts.last().length <= 3 && parts.size >= 3) parts[parts.size - 2] else parts.last()
                    appLabel = name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                }
            }

            AppUsage(packageName, appLabel, correctedValue)
        }
        .filter { it.usageTime >= 5000L } // Ignore anything under 5 seconds
        .sortedByDescending { it.usageTime }
    }

    private fun collectUsageEvents(startTime: Long, endTime: Long): Map<String, Long> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        // Query slightly before to capture the state at midnight
        val events = usageStatsManager.queryEvents(startTime - HOUR, endTime)
        val event = UsageEvents.Event()
        
        val usage = mutableMapOf<String, Long>()
        var activePackage: String? = null
        var sessionStartTime = -1L
        var screenOn = true // Corrected by look-back
        
        var lastEventTime = -1L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val ts = event.timeStamp
            val pkg = event.packageName ?: continue
            val type = event.eventType

            // Skip duplicate events
            if (ts == lastEventTime && pkg == activePackage) continue
            lastEventTime = ts

            when (type) {
                UsageEvents.Event.SCREEN_INTERACTIVE,
                EVENT_KEYGUARD_HIDDEN -> {
                    screenOn = true
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                EVENT_KEYGUARD_SHOWN -> {
                    screenOn = false
                    if (activePackage != null) {
                        val start = maxOf(startTime, sessionStartTime)
                        val end = minOf(endTime, ts)
                        if (end > start && start != -1L) {
                            usage[activePackage!!] = (usage[activePackage!!] ?: 0L) + (end - start)
                        }
                        activePackage = null
                        sessionStartTime = -1L
                    }
                }
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (screenOn) {
                        // Close previous if it changed
                        if (activePackage != null && activePackage != pkg) {
                            val start = maxOf(startTime, sessionStartTime)
                            val end = minOf(endTime, ts)
                            if (end > start && start != -1L) {
                                usage[activePackage!!] = (usage[activePackage!!] ?: 0L) + (end - start)
                            }
                        }
                        
                        if (activePackage != pkg) {
                            if (!isIgnoredPackage(pkg)) {
                                activePackage = pkg
                                sessionStartTime = ts
                            } else {
                                activePackage = null
                                sessionStartTime = -1L
                            }
                        }
                    } else if (ts < startTime) {
                        // State tracking for midnight
                        if (!isIgnoredPackage(pkg)) activePackage = pkg
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (activePackage == pkg) {
                        val start = maxOf(startTime, sessionStartTime)
                        val end = minOf(endTime, ts)
                        if (end > start && start != -1L) {
                            usage[pkg] = (usage[pkg] ?: 0L) + (end - start)
                        }
                        activePackage = null
                        sessionStartTime = -1L
                    }
                }
            }
        }
        
        // Handle current session
        if (activePackage != null && sessionStartTime != -1L) {
            val start = maxOf(startTime, sessionStartTime)
            if (endTime > start) {
                usage[activePackage!!] = (usage[activePackage!!] ?: 0L) + (endTime - start)
            }
        }
        
        return usage
    }

    private fun collectUsageStats(startTime: Long, endTime: Long): Map<String, Long> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        // Query from the start of the day.
        val statsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val result = mutableMapOf<String, Long>()
        
        statsList.forEach { stats ->
            val packageName = stats.packageName
            if (!isIgnoredPackage(packageName)) {
                val time = stats.totalTimeInForeground
                if (time > 0) {
                    // Check if this bucket is "today's" bucket.
                    // A bucket is for today if it ends after today started AND 
                    // either starts after midnight or is the most recent daily bucket.
                    if (stats.lastTimeStamp >= startTime) {
                        val existing = result[packageName] ?: 0L
                        // We take the value, but we need to be careful.
                        // If this bucket started before midnight, it contains yesterday's time.
                        // For the purpose of "Today", we'll use this as a reference but 
                        // favor UsageEvents in the hybrid correction.
                        if (time > existing) {
                            result[packageName] = time
                        }
                    }
                }
            }
        }
        return result
    }

    private fun applyHybridCorrection(eventValue: Long, statsValue: Long): Long {
        // UsageEvents (eventValue) is our reconstruction of time spent SINCE midnight.
        // UsageStats (statsValue) is Android's bucketed total.
        
        if (eventValue <= 0L) return statsValue
        if (statsValue <= 0L) return eventValue

        // If the system total (UsageStats) is much higher than our reconstruction, 
        // it usually means the system bucket started BEFORE midnight and includes yesterday's time.
        // In that case, we trust our UsageEvents reconstruction for "Today".
        if (statsValue > eventValue + (5 * MINUTE)) {
            return eventValue
        }
        
        // If they are close, or if events are higher (live session), trust the events.
        return maxOf(eventValue, statsValue)
    }

    private fun getStartOfDay(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getNow(): Long = System.currentTimeMillis()

    fun formatDuration(milliseconds: Long): String {
        val hours = milliseconds / HOUR
        val minutes = (milliseconds / MINUTE) % 60
        return "${hours}h ${minutes}m"
    }

    fun printDebugReport() {
        if (!hasPermission()) return
        Log.d(TAG, DEBUG_SEPARATOR)
        Log.d(TAG, "SCREEN TIME DEBUG REPORT")
        Log.d(TAG, "Total: ${getTodayScreenTime()}")
        getTodayAppUsage().forEach {
            Log.d(TAG, "App: ${it.appName} (${it.packageName}) - ${formatDuration(it.usageTime)}")
        }
        Log.d(TAG, DEBUG_SEPARATOR)
    }

    fun getUsageForWindow(startTime: java.time.Instant, endTime: java.time.Instant): Long {
        if (!hasPermission()) return 0L
        val usageMap = collectUsageEvents(startTime.toEpochMilli(), endTime.toEpochMilli())
        return usageMap.values.sum() / 1000 // Returns total seconds in that window
    }

    fun getUnlocksForWindow(startTime: java.time.Instant, endTime: java.time.Instant): Int {
        if (!hasPermission()) return 0
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usageStatsManager.queryEvents(startTime.toEpochMilli(), endTime.toEpochMilli())
        val event = android.app.usage.UsageEvents.Event()
        var count = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.KEYGUARD_HIDDEN) count++
        }
        return count
    }
}
