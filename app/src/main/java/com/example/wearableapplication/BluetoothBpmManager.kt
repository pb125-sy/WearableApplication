package com.example.wearableapplication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class BluetoothBpmManager(
    private val onBpmReceived: (Int) -> Unit,
    private val onStatusChanged: (String) -> Unit
) {
    // Standard SPP UUID — works for ESP32 Bluetooth Serial
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val DEVICE_NAME = "StressMonitor"

    private var socket: BluetoothSocket? = null
    private var isRunning = false
    private var thread: Thread? = null


    @SuppressLint("MissingPermission")
    fun connect() {
        Log.e("TEST123", "CONNECT CALLED")
        Log.d("BPM", "connect() called")

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            onStatusChanged("● Bluetooth not available")
            return
        }

        Log.d("BPM", "Bluetooth enabled")

        // Find paired device named "StressMonitor"
        val device = adapter.bondedDevices.find { it.name == DEVICE_NAME }
        if (device == null) {
            onStatusChanged("● StressMonitor not paired")
            return
        }
        Log.d("BPM", "Device found: ${device.name}")

        onStatusChanged("● Connecting...")

        thread = Thread {
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                adapter.cancelDiscovery()
                Log.d("BPM", "Attempting socket connection")
                socket!!.connect()
                Log.d("BPM", "Socket connected")
                onStatusChanged("● Connected")
                isRunning = true

                val reader = BufferedReader(InputStreamReader(socket!!.inputStream))
                while (isRunning) {
                    val line = reader.readLine()?.trim() ?: break
                    Log.d("BPM", "Received: $line")
                    Log.d("BPM", "RAW DATA: $line")

                    // Parse BPM — handles both "72" and "BPM:72" formats
                    val bpm = line.filter { it.isDigit() }.toIntOrNull()
                    if (bpm != null) {
                        onHeartRateReceived(bpm)
                        onBpmReceived(bpm)
                    }
                }
            } catch (e: Exception) {
                Log.e("BPM", "Bluetooth error: ${e.message}")
                onStatusChanged("● Disconnected")
            }
        }
        thread?.start()

        Log.d("BPM", "Adapter: $adapter, enabled: ${adapter.isEnabled}")
        Log.d("BPM", "Device found: ${device.name} / ${device.address}")
        Log.d("BPM", "Paired devices: ${adapter.bondedDevices.map { it.name }}")

    }

    fun disconnect() {
        isRunning = false
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e("BPM", "Error closing socket: ${e.message}")
        }
        onStatusChanged("● Disconnected")
    }

    companion object {
        // In-memory variables to hold the running totals for the current 15-minute window
        private var totalHeartRateSum = 0
        private var validReadingCount = 0

        /**
         * Call this function every time your Bluetooth sensor pushes a new value.
         * It applies the Validity Filter (30-220 BPM) and increments the Dynamic Denominator.
         */
        fun onHeartRateReceived(bpm: Int) {
            // 1. The Validity Filter: Only accept biologically plausible readings
            if (bpm in 30..220) {
                totalHeartRateSum += bpm
                validReadingCount += 1 // 2. Dynamic Denominator: Only count good data
            }
        }

        /**
         * Call this from your DataHarvestWorker at the end of the 15 minutes.
         * Returns the averaged integer if the Minimum Threshold (60s) is met, else null.
         */
        fun getAverageAndReset(): Int? {
            // 3. Minimum Threshold: Require at least 60 valid readings (1 minute of data)
            val finalAverage = if (validReadingCount >= 60) {
                totalHeartRateSum / validReadingCount
            } else {
                null // Not enough data to be statistically significant
            }

            // Reset the counters for the next 15-minute window
            totalHeartRateSum = 0
            validReadingCount = 0

            return finalAverage
        }
    }
}
