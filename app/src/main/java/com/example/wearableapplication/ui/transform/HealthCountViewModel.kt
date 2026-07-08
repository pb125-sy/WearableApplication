package com.example.wearableapplication.ui.transform

import Services.HealthConnectManager
import android.os.Build
import androidx.annotation.RequiresExtension
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HealthCountViewModel : ViewModel() {

    private val _cal = MutableStateFlow(0)
    val cal: StateFlow<Int> = _cal

    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step

    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 7)
    fun fetchHealthData(healthConnectManager: HealthConnectManager) {
        viewModelScope.launch {
            // Fetch both values in parallel coroutines to optimize speed
            launch {
                _step.value = healthConnectManager.readDailySteps()
            }
            launch {
                _cal.value = healthConnectManager.readDailyCalories()
            }
        }
    }
}