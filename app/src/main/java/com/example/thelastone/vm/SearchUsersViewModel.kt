package com.example.thelastone.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.model.User
import com.example.thelastone.data.repo.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUsersUiState(
    val query: String = "",
    val results: List<User> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val dialogUser: User? = null,
    val sending: Boolean = false,
    val sentSuccess: Boolean = false
)

@HiltViewModel
class SearchUsersViewModel @Inject constructor(
    private val repo: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUsersUiState())
    val state: StateFlow<SearchUsersUiState> = _state

    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // 簡單防抖
            delay(250)
            doSearch(q)
        }
    }

    private suspend fun doSearch(q: String) {
        if (q.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), loading = false, error = null)
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        try {
            val list = repo.searchUsers(q)
            _state.value = _state.value.copy(loading = false, results = list)
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, error = e.message ?: "Search failed")
        }
    }

    fun openDialog(u: User) {
        _state.value = _state.value.copy(dialogUser = u, sentSuccess = false)
    }
    fun closeDialog() {
        _state.value = _state.value.copy(dialogUser = null, sending = false, sentSuccess = false)
    }

    fun sendRequest() {
        val target = _state.value.dialogUser ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true)
            try {
                repo.sendFriendRequest(target.id)
                _state.value = _state.value.copy(sending = false, sentSuccess = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(sending = false, error = e.message ?: "Send failed")
            }
        }
    }
}