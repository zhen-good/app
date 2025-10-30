package com.example.thelastone.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.model.Trip
import com.example.thelastone.data.repo.TripRepository
import com.example.thelastone.di.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface MyTripsUiState {
    data object Loading : MyTripsUiState
    data class Data(
        val createdByMe: List<Trip>,
        val joinedByMe: List<Trip>
    ) : MyTripsUiState
    data object Empty : MyTripsUiState
    data class Error(val message: String) : MyTripsUiState
}


@HiltViewModel
class MyTripsViewModel @Inject constructor(
    private val repo: TripRepository,
    private val session: SessionManager
) : ViewModel() {

    private val retry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<MyTripsUiState> =
        retry.onStart { emit(Unit) }
            .flatMapLatest {
                // trips 已是「跟著帳號切換」的觀察流（我們前面把 TripRepository 這樣實作了）
                repo.observeMyTrips()
                    .combine(session.auth.filterNotNull().map { it.user.id }) { trips, uid ->
                        val created = trips.filter { it.createdBy == uid }
                        val joined  = trips.filter { it.createdBy != uid && it.members.any { m -> m.id == uid } }
                        created to joined
                    }
                    .map { (created, joined) ->
                        if (created.isEmpty() && joined.isEmpty()) MyTripsUiState.Empty
                        else MyTripsUiState.Data(createdByMe = created, joinedByMe = joined)
                    }
                    .onStart { emit(MyTripsUiState.Loading) }
            }
            .catch { emit(MyTripsUiState.Error(it.message ?: "Load failed")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyTripsUiState.Loading)

    fun retry() { retry.tryEmit(Unit) }
}