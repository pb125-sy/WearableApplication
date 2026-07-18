package com.example.wearableapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.example.wearableapplication.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.example.wearableapplication.services.ScreenTimeManager
import com.example.wearableapplication.services.OpenAIManager
import com.example.wearableapplication.services.PromptBuilder
import Services.HealthConnectManager
import android.content.Context
import androidx.activity.viewModels
import androidx.annotation.RequiresExtension
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.wearableapplication.ui.transform.HealthCountViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.wearableapplication.model.StressFeatures
import com.example.wearableapplication.model.AppUsage
import com.example.wearableapplication.model.StressAnalysis
import com.example.wearableapplication.model.Questionnaire
import com.example.wearableapplication.model.QuestionnaireEntity

import com.example.wearableapplication.JsonTest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit

private fun startBackgroundDataHarvesting(context: Context) {
    // Optional: Only run if the device has a bit of battery
    val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()

    // Create a request to run every 15 minutes (this is the minimum allowed by Android)
    val harvestRequest = PeriodicWorkRequestBuilder<DataHarvestWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()

    // Enqueue unique work prevents multiple identical timers from running at the same time
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "HarvestDataWork",
        ExistingPeriodicWorkPolicy.KEEP, // If it's already running, don't restart the timer
        harvestRequest
    )
}



class MainActivity : AppCompatActivity() {

    private var txtScreenTime: TextView? = null
    private var txtAppUsage: TextView? = null
    private var txtUnlockCount: TextView? = null
    private var txtSteps: TextView? = null
    private var txtCalories: TextView? = null
    private var txtAnalysis: TextView? = null
    private var btnAnalyze: android.widget.Button? = null

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val viewModel: HealthCountViewModel by viewModels()
    private val healthConnectManager by lazy { HealthConnectManager(applicationContext) }
    private var bluetoothManager: BluetoothBpmManager? = null
    private var txtBpm: TextView? = null

    // Most recent BPM value received from the wearable, used when building the AI prompt.
    private var latestHeartRate: Int = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startBluetooth()
        } else {
            txtBpm?.text = "BT Permission denied"
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 7)
    private val requestPermissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(healthConnectManager.permissions)) {
            val healthConnectClient = HealthConnectClient.getOrCreate(this)
            viewModel.fetchHealthData(healthConnectManager)
        } else {
            Snackbar.make(binding.root, "Permissions denied", Snackbar.LENGTH_SHORT).show()
        }
    }

    private val openAIManager = OpenAIManager()

    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 7)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.e("TEST123", "MAIN ACTIVITY STARTED")
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)

        setupHealthConnectPipeline()
        android.util.Log.d("MAIN_TEST", "Scheduling DataHarvestWorker")
        startBackgroundDataHarvesting(this)

        /*
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment

        if (navHostFragment == null) return
        if (navHostFragment == null) {
            android.util.Log.e("TEST123", "NAV HOST IS NULL")
            return
        }
        */
        /*
                val navController = navHostFragment.navController
                val navView: NavigationView? = findViewById(R.id.nav_view)

                if (navView != null) {
                    appBarConfiguration = AppBarConfiguration(
                        setOf(
                            R.id.nav_home, R.id.nav_goalTracker,
                            R.id.nav_recommendations, R.id.nav_settings
                        ),
                        findViewById(R.id.drawer_layout)
                    )
                    setupActionBarWithNavController(navController, appBarConfiguration)
                    navView.setupWithNavController(navController)
                } else {
                    appBarConfiguration = AppBarConfiguration(
                        setOf(
                            R.id.nav_home, R.id.nav_goalTracker, R.id.nav_recommendations
                        )
                    )
                    setupActionBarWithNavController(navController, appBarConfiguration)
                }
        */
        // Wait for layout to finish then find txtBpm and start Bluetooth
        /*Handler(Looper.getMainLooper()).postDelayed({
            txtBpm = findViewById(R.id.txtBpm)
            android.util.Log.e("TEST123", "txtBpm = $txtBpm")
            android.util.Log.d("BPM_DEBUG", "txtBpm = $txtBpm")
            txtBpm?.text = "TEXTVIEW FOUND"
            android.util.Log.d("BPM_DEBUG", "txtBpm found: ${txtBpm != null}")
            if (txtBpm != null) checkPermissionsAndConnect()
        }, 500)*/

        Handler(Looper.getMainLooper()).postDelayed({
            android.util.Log.d("TEST123", "POST DELAY ENTERED")
            txtBpm = findViewById(R.id.txtBpm)

            txtScreenTime = findViewById(R.id.txtScreenTime)
            txtAppUsage = findViewById(R.id.txtAppUsage)
            txtUnlockCount = findViewById(R.id.txtUnlockCount)
            txtSteps = findViewById(R.id.txtSteps)
            txtCalories = findViewById(R.id.txtCalories)
            txtAnalysis = findViewById(R.id.txtAnalysis)
            btnAnalyze = findViewById(R.id.btnAnalyze)

            // ---- Pull real usage data instead of hardcoding it ----
            val screenManager = ScreenTimeManager(this)

            val appUsageList = screenManager.getTodayAppUsage()
            val totalMs = appUsageList.sumOf { it.usageTime }
            val screenTimeText = screenManager.formatDuration(totalMs)
            val unlockCount = screenManager.getTodayUnlockCount()

            // Build a human readable "App : Xh Ym" block from the real per-app usage list.
            val appUsageText = if (appUsageList.isEmpty()) {
                "No app usage recorded"
            } else {
                appUsageList.joinToString("\n") { usage ->
                    val hours = usage.usageTime / (60 * 60 * 1000L)
                    val minutes = (usage.usageTime / (60 * 1000L)) % 60
                    val label = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                    "${usage.appName} : $label"
                }
            }

            // Pull real values from the ViewModel instead of hardcoding them
            val steps = viewModel.step.value
            val caloriesKcal = viewModel.cal.value

            txtScreenTime?.text = screenTimeText
            txtAppUsage?.text = appUsageText
            txtUnlockCount?.text = unlockCount.toString()
            txtSteps?.text = steps.toString()
            txtCalories?.text = "$caloriesKcal kcal"

            btnAnalyze?.setOnClickListener {
                android.util.Log.d("TEST123", "Analyze button clicked")
                showQuestionnaireDialog()
            }

            android.util.Log.d("TEST123", "txtBpm value = $txtBpm")
            android.util.Log.d("TEST123", "ABOUT TO CALL BLUETOOTH")
            checkPermissionsAndConnect()
        }, 500)
    }

    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 7)
    private fun setupHealthConnectPipeline() {
        if (healthConnectManager.isSdkAvailable()) {
            lifecycleScope.launch {
                if (healthConnectManager.hasAllPermissions()) {
                    viewModel.fetchHealthData(healthConnectManager)
                } else {
                    requestPermissionsLauncher.launch(healthConnectManager.permissions)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.cal.collectLatest { calories ->
                        binding.appBarMain.contentMain.txtCalories?.text = "$calories kcal"
                    }
                }

                launch {
                    viewModel.step.collectLatest { steps ->
                        binding.appBarMain.contentMain.txtSteps?.text = "$steps"
                    }
                }
            }
        }

    }

    private fun checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
            else startBluetooth()
        } else {
            startBluetooth()
        }
    }

    private fun startBluetooth() {
        bluetoothManager?.disconnect()
        bluetoothManager = BluetoothBpmManager(
            onBpmReceived = { bpm ->
                latestHeartRate = bpm
                runOnUiThread {
                    txtBpm?.text = "$bpm BPM"
                }
            },
            onStatusChanged = { status ->
                runOnUiThread { txtBpm?.text = status }
            }
        )
        bluetoothManager?.connect()
    }

    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 7)
    private fun showQuestionnaireDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_questionnaire, null)
        val rgStress = dialogView.findViewById<RadioGroup>(R.id.rgStress)
        val rgMood = dialogView.findViewById<RadioGroup>(R.id.rgMood)
        val rgSleep = dialogView.findViewById<RadioGroup>(R.id.rgSleep)
        val rgFatigue = dialogView.findViewById<RadioGroup>(R.id.rgFatigue)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Submit", null) // Set to null to handle manually
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val stressId = rgStress.checkedRadioButtonId
            val moodId = rgMood.checkedRadioButtonId
            val sleepId = rgSleep.checkedRadioButtonId
            val fatigueId = rgFatigue.checkedRadioButtonId

            if (stressId == -1 || moodId == -1 || sleepId == -1 || fatigueId == -1) {
                Toast.makeText(this, "Please answer all questions", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val stress = dialogView.findViewById<RadioButton>(stressId).text.toString().toInt()
            val mood = dialogView.findViewById<RadioButton>(moodId).text.toString()
            val sleep = dialogView.findViewById<RadioButton>(sleepId).text.toString().toInt()
            val fatigue = dialogView.findViewById<RadioButton>(fatigueId).text.toString().toInt()

            val questionnaire = Questionnaire(stress, mood, sleep, fatigue)

            /* NEW CODE STARTS HERE*/
            // Save questionnaire and apply hybrid labeling
            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(this@MainActivity)

                // 1. Save as latest state
                db.questionnaireDao().saveQuestionnaire(
                    com.example.wearableapplication.model.QuestionnaireEntity(
                        stressLevel = stress,
                        mood = mood,
                        sleepQuality = sleep,
                        mentalFatigue = fatigue
                    )
                )

                // 2. Retroactively label last 2 windows
                val recentRecords = db.timeWindowDao().getRecentRecords(2)
                recentRecords.forEach { record ->
                    val updatedRecord = record.copy(
                        selfReportedStress = stress,
                        currentMood = mood,
                        sleepRating = sleep,
                        tirednessLevel = fatigue
                    )
                    db.timeWindowDao().insertRecord(updatedRecord)
                }

                android.util.Log.d("HybridLabeling", "Applied labels to ${recentRecords.size} recent records")
            }
            /*NEW CODE ENDS HERE*/

            runStressAnalysis(questionnaire)
            dialog.dismiss()
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 7)
    private fun runStressAnalysis(questionnaire: Questionnaire? = null) {
        android.util.Log.d("TEST123", "runStressAnalysis started. HR: $latestHeartRate")
        if (latestHeartRate <= 0) {
            txtAnalysis?.text = "Heart rate sensor is not connected. Please check your wearable connection and try again."
            return
        }

        txtAnalysis?.text = "Analyzing..."

        val screenManager = ScreenTimeManager(this)
        val screenTimeText = screenManager.getTodayScreenTime()
        val appUsageList = screenManager.getTodayAppUsage()
        val unlockCount = screenManager.getTodayUnlockCount()

        val steps = viewModel.step.value
        val caloriesKcal = viewModel.cal.value

        val stressFeatures = StressFeatures(
            heartRate = latestHeartRate,
            screenTime = screenTimeText,
            appUsage = appUsageList,
            unlockCount = unlockCount,
            steps = steps,
            calories = "$caloriesKcal kcal",
            questionnaire = questionnaire
        )

        val prompt = PromptBuilder.buildPrompt(stressFeatures)

        openAIManager.analyzeStress(
            prompt = prompt,
            onSuccess = { analysis ->
                runOnUiThread {
                    txtAnalysis?.text = """
Stress Score:
${analysis.stressScore.toInt()}/100

Stress Level:
${analysis.stressLevel}

Main Factors:
${analysis.primaryFactors.joinToString("\n") { "• $it" }}

Recommendations:
${analysis.recommendations.joinToString("\n") { "• $it" }}

Breathing Exercise:
${analysis.breathingExercise}

Activity:
${analysis.activitySuggestion}

Screen Advice:
${analysis.screenTimeAdvice}

AI Confidence:
${if (analysis.confidence <= 1.0) (analysis.confidence * 100).toInt() else analysis.confidence.toInt()}%
""".trimIndent()
                }
            },
            onError = { error ->
                runOnUiThread { txtAnalysis?.text = error }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager?.disconnect()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val result = super.onCreateOptionsMenu(menu)
        val navView: NavigationView? = findViewById(R.id.nav_view)
        if (navView == null) menuInflater.inflate(R.menu.overflow, menu)
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.nav_settings)
            }
            R.id.action_export_csv -> {
                lifecycleScope.launch {
                    val csvManager = CsvManager(this@MainActivity)
                    val file = csvManager.exportDatabaseToCsv()
                    if (file != null) {
                        Toast.makeText(this@MainActivity, "Exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Export failed or no data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}