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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.wearableapplication.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.example.wearableapplication.Services.ScreenTimeManager

class MainActivity : AppCompatActivity() {

    private var txtScreenTime: TextView? = null
    private var txtAppUsage: TextView? = null
    private var txtUnlockCount: TextView? = null
    private var txtSteps: TextView? = null
    private var txtCalories: TextView? = null
    private var txtAnalysis: TextView? = null

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.e("TEST123", "MAIN ACTIVITY STARTED")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab?.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }

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
            val screenManager = ScreenTimeManager(this)
            txtScreenTime?.text = screenManager.getTodayScreenTime()


            txtAppUsage = findViewById(R.id.txtAppUsage)
            txtUnlockCount = findViewById(R.id.txtUnlockCount)
            txtSteps = findViewById(R.id.txtSteps)
            txtCalories = findViewById(R.id.txtCalories)
            txtAnalysis = findViewById(R.id.txtAnalysis)

            //txtScreenTime?.text = "8 h 25 min"
            txtAppUsage?.text = "2 h 10 min"
            txtUnlockCount?.text = "42"
            txtSteps?.text = "6845"
            txtCalories?.text = "315 kcal"
            txtAnalysis?.text = "Low Stress"

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
        if (navView == null) menuInflater.inflate(R.menu.overflow, menu)
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