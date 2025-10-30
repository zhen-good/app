package com.example.thelastone.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.model.User
import com.example.thelastone.data.repo.TripRepository
import com.example.thelastone.data.repo.UserRepository
import com.example.thelastone.di.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val session: SessionManager,
    private val userRepo: UserRepository,
    private val tripRepo: TripRepository
) : ViewModel() {

    data class Ui(
        val me: User? = null,
        val createdCount: Int = 0,
        val participatingCount: Int = 0,
        val loading: Boolean = true
    )

    private val _state = MutableStateFlow(Ui())
    val state: StateFlow<Ui> = _state

    init {
        viewModelScope.launch {
            // 觀察登入者
            session.auth.collect { auth ->
                val me = auth?.user
                _state.update { it.copy(me = me) }
            }
        }

        // 觀察我的 trips 並計算數量
        viewModelScope.launch {
            combine(tripRepo.observeMyTrips(), session.auth.filterNotNull()) { trips, auth ->
                val myId = auth.user.id
                val created = trips.count { it.createdBy == myId }
                val participating = trips.count { it.createdBy != myId && it.members.any { u -> u.id == myId } }
                Triple(auth.user, created, participating)
            }.collect { (me, created, participating) ->
                _state.update {
                    it.copy(
                        me = me,
                        createdCount = created,
                        participatingCount = participating,
                        loading = false
                    )
                }
            }
        }
    }
}
