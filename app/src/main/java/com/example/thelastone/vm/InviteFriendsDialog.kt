package com.example.thelastone.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.model.User
import com.example.thelastone.data.repo.TripRepository
import com.example.thelastone.data.repo.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InviteFriendsViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val tripRepo: TripRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val friends: List<User> = emptyList(),
        val memberIds: Set<String> = emptySet(),
        val selected: Set<String> = emptySet(),
        val query: String = "",
        val submitting: Boolean = false,
        val done: Boolean = false
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching {
                val friends = userRepo.getFriends()
                val trip = tripRepo.getTripDetail(tripId)
                val memberIds = trip.members.map { it.id }.toSet()
                friends to memberIds
            }.onSuccess { (friends, memberIds) ->
                _ui.update { it.copy(loading = false, friends = friends, memberIds = memberIds) }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = e.message ?: "Load failed") }
            }
        }
    }

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }

    fun toggle(userId: String) {
        val state = _ui.value
        if (userId in state.memberIds) return // 已在行程中，不可改
        val next = state.selected.toMutableSet().apply {
            if (!add(userId)) remove(userId)
        }
        _ui.update { it.copy(selected = next) }
    }

    fun selectAllFiltered() {
        val filtered = filteredFriends().map { it.id }.filter { it !in _ui.value.memberIds }.toSet()
        _ui.update { it.copy(selected = filtered) }
    }

    fun clearSelection() = _ui.update { it.copy(selected = emptySet()) }

    fun confirm() {
        val picks = _ui.value.selected.toList()
        if (picks.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(submitting = true) }
            runCatching { tripRepo.addMembers(tripId, picks) }
                .onSuccess { _ui.update { it.copy(submitting = false, done = true) } }
                .onFailure { e -> _ui.update { it.copy(submitting = false, error = e.message ?: "Invite failed") } }
        }
    }

    fun filteredFriends(): List<User> {
        val q = _ui.value.query.trim()
        return if (q.isBlank()) _ui.value.friends
        else _ui.value.friends.filter { it.name.contains(q, true) || it.email.contains(q, true) }
    }
}
