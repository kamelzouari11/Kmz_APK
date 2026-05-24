package com.example.simpleradio.ui

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.simpleradio.data.RadioRepository

class MainViewModelFactory(
        private val radioRepository: RadioRepository,
        private val prefs: SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return MainViewModel(radioRepository, prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
