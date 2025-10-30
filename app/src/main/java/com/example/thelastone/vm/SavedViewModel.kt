package com.example.thelastone.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.data.model.SavedPlace
import com.example.thelastone.data.repo.SavedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavedUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<SavedPlace> = emptyList(),
    val savedIds: Set<String> = emptySet()
)

@HiltViewModel
class SavedViewModel @Inject constructor(
    private val repo: SavedRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SavedUiState())
    val state: StateFlow<SavedUiState> = _state

    init { observe() }

    private fun observe() {
        viewModelScope.launch {
            combine(
                repo.observeAll(),
                repo.observeIds()
            ) { list, ids -> list to ids }
                .onStart { _state.update { it.copy(loading = true, error = null) } }
                .catch { e -> _state.update { it.copy(loading = false, error = e.message) } }
                .collect { (list, ids) ->
                    _state.value = SavedUiState(
                        loading = false,
                        error = null,
                        items = list,
                        savedIds = ids
                    )
                }
        }
    }

    fun refresh() {
        // 若你的 repo 支援主動拉資料，可呼叫 repo.refresh()
        // 這裡做最保守：重建 observe 流程
        observe()
    }

    fun toggle(place: PlaceLite) = viewModelScope.launch {
        repo.toggle(place)
    }
}
