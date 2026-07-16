package com.example.wearableapplication

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CsvManager(private val context: Context) {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    suspend fun exportDatabaseToCsv(): File? {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(context)
            val dao = database.timeWindowDao()
            val records = dao.getAllRecords()

            if (records.isEmpty()) {
                Log.d("CsvManager", "No records to export.")
                return@withContext null
            }

            val fileName = "wearable_data_export_${System.currentTimeMillis()}.csv"
            val file = File(context.getExternalFilesDir(null), fileName)

            try {
                FileWriter(file).use { writer ->
                    writeHeader(writer)
                    records.forEach { record ->
                        writeRecord(writer, record)
                    }
                }
                Log.d("CsvManager", "Database exported to ${file.absolutePath}")
                file
            } catch (e: Exception) {
                Log.e("CsvManager", "Error exporting to CSV", e)
                null
            }
        }
    }

    suspend fun appendRecordToCsv(record: TimeWindowRecords) {
        withContext(Dispatchers.IO) {
            val fileName = "wearable_data_log.csv"
            val file = File(context.getExternalFilesDir(null), fileName)
            val isNewFile = !file.exists()

            try {
                FileWriter(file, true).use { writer ->
                    if (isNewFile) {
                        writeHeader(writer)
                    }
                    writeRecord(writer, record)
                }
                Log.d("CsvManager", "Record appended to ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("CsvManager", "Error appending to CSV", e)
            }
        }
    }

    private fun writeHeader(writer: FileWriter) {
        writer.append("StartTime,ScreenTimeSec,UnlockCount,Steps,Calories,AvgBpm,Stress,Mood,Sleep,Tiredness\n")
    }

    private fun writeRecord(writer: FileWriter, record: TimeWindowRecords) {
        val startTimeStr = formatter.format(record.windowStartTime)
        writer.append("$startTimeStr,")
        writer.append("${record.screenTimeSec},")
        writer.append("${record.unlockCount},")
        writer.append("${record.stepsTaken},")
        writer.append("${record.totalCalories},")
        writer.append("${record.avgBpm},")
        writer.append("${record.selfReportedStress ?: ""},")
        writer.append("${record.currentMood ?: ""},")
        writer.append("${record.sleepRating ?: ""},")
        writer.append("${record.tirednessLevel ?: ""}\n")
    }
}
