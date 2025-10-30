package com.example.thelastone.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.repo.TripRepository
import com.example.thelastone.data.repo.TripStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendStatsViewModel @Inject constructor(
    private val tripRepo: TripRepository
) : ViewModel() {

    private val _stats = MutableStateFlow<TripStats?>(null)
    val stats: StateFlow<TripStats?> = _stats

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun load(userId: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _stats.value = tripRepo.getTripStatsFor(userId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _loading.value = false
            }
        }
    }
}
