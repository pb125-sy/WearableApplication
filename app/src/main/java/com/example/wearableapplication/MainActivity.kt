package com.example.wearableapplication

import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
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
import androidx.lifecycle.ViewModel
import androidx.activity.viewModels
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val viewModel: CountViewModel by viewModels()

    val permissions =
        setOf(
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            //HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            //HealthPermission.getWritePermission(StepsRecord::class)
        )

    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 7)
    private val requestPermissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(permissions)) {
            val healthConnectClient = HealthConnectClient.getOrCreate(this)
            viewModel.readCalories(healthConnectClient)
            viewModel.readSteps(healthConnectClient)
        } else {
            Snackbar.make(binding.root, "Permissions denied", Snackbar.LENGTH_SHORT).show()
        }
    }


    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 7)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)




        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) {
            val healthConnectClient = HealthConnectClient.getOrCreate(this)
            lifecycleScope.launch {
                val granted = healthConnectClient.permissionController.getGrantedPermissions()
                if (granted.containsAll(permissions)) {
                    viewModel.readCalories(healthConnectClient)
                    viewModel.readSteps(healthConnectClient)
                } else {
                    requestPermissionsLauncher.launch(permissions)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch{
                    viewModel.cal.collectLatest {calories ->
                        binding.appBarMain.contentMain.txtCalories?.text = "$calories kcal"}
                }

                launch{
                    viewModel.step.collectLatest {steps ->
                        binding.appBarMain.contentMain.txtSteps?.text = "$steps"}
                }
            }
        }



        // FIXED: Safe null check instead of !! force-unwrap
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment

        if (navHostFragment == null) {
            // Fragment not found — check your content_main.xml has the correct ID
            return
        }

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


class CountViewModel : ViewModel() {
    private val _cal = MutableStateFlow<Int>(0)
    val cal: StateFlow<Int> = _cal
    private val _step = MutableStateFlow<Int>(0)
    val step: StateFlow<Int> = _step

    fun updateStep(newValue: Int) {
        _step.value = newValue
    }

    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 7)
    @OptIn(ExperimentalTime::class)
    fun readSteps(healthConnectClient: HealthConnectClient) {
        viewModelScope.launch {
            val startTime = LocalDate.now().atStartOfDay()
            val endTime = LocalDateTime.now()

            val request = AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            try {
                val response = healthConnectClient.aggregate(request)
                val totalSteps = response[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
                updateStep(totalSteps)
            } catch (e: Exception) {
                // Handle exceptions like permission revocations
            }
        }
    }

    fun getStep(): MutableStateFlow<Int>
    {
        return _step
    }

    fun updateCal(newValue: Int) {
        _cal.value = newValue
    }

    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 7)
    @OptIn(ExperimentalTime::class)
    fun readCalories(healthConnectClient: HealthConnectClient) {
        viewModelScope.launch {
            val startTime = LocalDate.now().atStartOfDay()
            val endTime = LocalDateTime.now()

            val request = AggregateRequest(
                metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            try {
                val response = healthConnectClient.aggregate(request)
                val totalCalories = response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.toInt() ?: 0
                updateCal(totalCalories)
            } catch (e: Exception) {
                // Handle exceptions like permission revocations
            }
        }
    }

    fun getCal(): MutableStateFlow<Int>
    {
        return _cal
    }
}
