package com.example.wearableapplication

import Services.HealthConnectManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.wearableapplication.Services.ScreenTimeManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.RequiresExtension
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import com.example.wearableapplication.databinding.ActivityMainBinding
import androidx.activity.viewModels
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.wearableapplication.ui.transform.HealthCountViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private var txtScreenTime: TextView? = null
    private var txtAppUsage: TextView? = null
    private var txtUnlockCount: TextView? = null
    private var txtSteps: TextView? = null
    private var txtCalories: TextView? = null
    private var txtAnalysis: TextView? = null
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val viewModel: HealthCountViewModel by viewModels()

    private val healthConnectManager by lazy { HealthConnectManager(applicationContext) }
    private var bluetoothManager: BluetoothBpmManager? = null
    private var txtBpm: TextView? = null

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


    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 7)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.e("TEST123", "MAIN ACTIVITY STARTED")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)

        setupHealthConnectPipeline()
        setupNavigation()
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

    private fun setupNavigation() {
        // FIXED: Safe null check instead of !! force-unwrap
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment ?: return

        val navController = navHostFragment.navController

        // Drawer navigation (large screen w1240dp layout)
        binding.appBarMain.contentMain.navView?.let {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_home, R.id.nav_goalTracker, R.id.nav_recommendations, R.id.nav_settings
                ),
                binding.drawerLayout
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }

        // Fallback appBarConfiguration for phone layout (no drawer)
        if (!::appBarConfiguration.isInitialized) {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_home, R.id.nav_goalTracker, R.id.nav_recommendations
                )
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
        }

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
            val screenManager = ScreenTimeManager(this)
            txtScreenTime?.text = screenManager.getTodayScreenTime()
            txtAppUsage = findViewById(R.id.txtAppUsage)
            txtUnlockCount = findViewById(R.id.txtUnlockCount)
            txtAnalysis = findViewById(R.id.txtAnalysis)

            /*
            val appUsageManager = AppUsageManager(this)
            txtAppUsage?.text = appUsageManager.getTodayAppUsage()
            txtUnlockCount?.text = appUsageManager.getUnlockCount().toString()*/

            txtAnalysis?.text = "Analyzing..."

            android.util.Log.d("TEST123", "txtBpm value = $txtBpm")
            android.util.Log.d("TEST123", "ABOUT TO CALL BLUETOOTH")
            checkPermissionsAndConnect()
        }, 500)
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
                runOnUiThread { txtBpm?.text = "$bpm BPM" }
            },
            onStatusChanged = { status ->
                runOnUiThread { txtBpm?.text = status }
            }
        )
        bluetoothManager?.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager?.disconnect()
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val result = super.onCreateOptionsMenu(menu)
        val navView: NavigationView? = findViewById(R.id.nav_view)
        if (navView == null) {
            menuInflater.inflate(R.menu.overflow, menu)
        }
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.nav_settings)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}

