package com.example.thelastone.vm

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.data.repo.PlacesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaceSearchViewModel @Inject constructor(
    private val repo: PlacesRepository
) : ViewModel() {

    @Immutable
    data class UiState(
        val query: String = "",
        val loading: Boolean = false,
        val results: List<PlaceLite> = emptyList(),
        val error: String? = null
    )

    private val queryFlow = MutableStateFlow("")
    private val manualTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // 若需要地點偏好，可改用 StateFlow<LocationBias?> 注入
    private val locationBias = MutableStateFlow<Triple<Double?, Double?, Int?>?>(null)

    // 對外只暴露 StateFlow
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun updateQuery(q: String) {
        _state.update { it.copy(query = q) }
        queryFlow.value = q
    }

    fun setLocationBias(lat: Double?, lng: Double?, radiusM: Int?) {
        locationBias.value = Triple(lat, lng, radiusM)
    }

    fun searchNow() {
        // 手動觸發（按下搜尋鍵、清掉鍵盤時）
        manualTrigger.tryEmit(Unit)
    }

    init {
        // 自動搜尋：query 變化 350ms 後觸發
        viewModelScope.launch {
            combine(
                queryFlow.debounce(350).distinctUntilChanged(),
                locationBias
            ) { q, bias -> q.trim() to bias }
                .flatMapLatest { (q, bias) ->
                    if (q.isEmpty()) {
                        flow { emit(LoadResult.Success(emptyList<PlaceLite>())) }
                    } else {
                        flow {
                            emit(LoadResult.Loading)
                            val (lat, lng, radius) = bias ?: Triple(null, null, null)
                            runCatching { repo.searchText(q, lat, lng, radius) }
                                .onSuccess { emit(LoadResult.Success(it)) }
                                .onFailure { emit(LoadResult.Failure(it)) }
                        }
                    }
                }
                .collect { r ->
                    when (r) {
                        is LoadResult.Loading -> _state.update { it.copy(loading = true, error = null) }
                        is LoadResult.Success -> _state.update {
                            it.copy(results = r.data, loading = false, error = null)
                        }
                        is LoadResult.Failure -> _state.update {
                            it.copy(
                                loading = false,
                                error = r.throwable.localizedMessage ?: "Uncaught error"
                            )
                        }
                    }
                }
        }

        // 手動搜尋流：與自動搜尋共用同一套邏輯（只需重送當前 query）
        viewModelScope.launch {
            manualTrigger.collect {
                queryFlow.value = _state.value.query // 重新觸發 combine 流
            }
        }
    }
}

private sealed class LoadResult<out T> {
    data object Loading : LoadResult<Nothing>()
    data class Success<T>(val data: T) : LoadResult<T>()
    data class Failure(val throwable: Throwable) : LoadResult<Nothing>()
}