package com.example.wearableapplication.ui.transform

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BpmViewModel : ViewModel() {
    private val _bpm = MutableLiveData<Int?>()
    val bpm: LiveData<Int?> = _bpm

    private val _status = MutableLiveData<String>("Disconnected")
    val status: LiveData<String> = _status

    fun updateBpm(value: Int?) {
        _bpm.postValue(value)
    }

    fun updateStatus(msg: String) {
        _status.postValue(msg)
    }
}