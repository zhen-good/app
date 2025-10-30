package com.example.thelastone.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.model.PlaceDetails
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.data.repo.PlacesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaceDetailUiState(
    val loadingDetails: Boolean = false, // ⬅️ 改名，不再用 loading 表示整個 Dialog 要 skeleton
    val error: String? = null,
    val lite: PlaceLite? = null,
    val details: PlaceDetails? = null
)


@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    private val repo: PlacesRepository
) : ViewModel() {

    private val cache = mutableMapOf<String, PlaceDetails>()
    private val _state = MutableStateFlow(PlaceDetailUiState())
    val state: StateFlow<PlaceDetailUiState> = _state

    fun show(place: PlaceLite) {
        val id = place.placeId

        // 先把 lite 顯示出來、清掉前次錯誤；不要先用 skeleton
        _state.value = PlaceDetailUiState(
            loadingDetails = true,
            error = null,
            lite = place,
            details = cache[id] // 若已有快取，直接帶入
        )

        // 若有快取，其實已足夠顯示；也可選擇跳過請求
        cache[id]?.let { return }

        viewModelScope.launch {
            runCatching { repo.fetchDetails(id) }
                .onSuccess { d ->
                    cache[id] = d
                    _state.value = _state.value.copy(
                        loadingDetails = false,
                        error = null,
                        details = d
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loadingDetails = false,
                        error = e.message
                    )
                }
        }
    }

    fun dismiss() {
        _state.value = PlaceDetailUiState()
    }
}
