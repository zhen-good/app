package com.example.thelastone.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.model.*
import com.example.thelastone.data.repo.StartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StartUiState {
    data object Idle: StartUiState
    data object Loading: StartUiState
    data class Preview(val info: StartInfo): StartUiState
    data class Alternatives(val placeId: String, val page: Int, val alts: List<Alternative>): StartUiState
    data class Error(val message: String): StartUiState
}

@HiltViewModel
class StartFlowViewModel @Inject constructor(
    private val repo: StartRepository
): ViewModel() {

    private val _ui = MutableStateFlow<StartUiState>(StartUiState.Idle)
    val ui: StateFlow<StartUiState> = _ui

    private var currentPlaceId: String? = null
    private var currentPage: Int = 0

    fun start(place: Place) {
        currentPlaceId = place.placeId
        _ui.value = StartUiState.Loading
        viewModelScope.launch {
            runCatching { repo.getStartInfo(place) }
                .onSuccess { info ->
                    currentPage = info.page
                    _ui.value = StartUiState.Preview(info)
                }
                .onFailure { _ui.value = StartUiState.Error(it.message ?: "取得資訊失敗") }
        }
    }

    fun showAlternatives() {
        val pid = currentPlaceId ?: return
        val page = currentPage
        _ui.value = StartUiState.Loading
        viewModelScope.launch {
            runCatching { repo.getAlternatives(pid, page) }
                .onSuccess { alts ->
                    _ui.value = StartUiState.Alternatives(pid, page, alts)
                }
                .onFailure { _ui.value = StartUiState.Error(it.message ?: "取得替代選項失敗") }
        }
    }

    fun loadMore() {
        val pid = currentPlaceId ?: return
        currentPage += 1
        val page = currentPage
        _ui.value = StartUiState.Loading
        viewModelScope.launch {
            runCatching { repo.getAlternatives(pid, page) }
                .onSuccess { alts ->
                    _ui.value = StartUiState.Alternatives(pid, page, alts)
                }
                .onFailure { _ui.value = StartUiState.Error(it.message ?: "取得更多選項失敗") }
        }
    }

    fun reset() { _ui.value = StartUiState.Idle }
}
