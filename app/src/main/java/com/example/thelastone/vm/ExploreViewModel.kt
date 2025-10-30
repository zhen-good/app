package com.example.thelastone.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.data.model.Trip
import com.example.thelastone.data.repo.SpotRepository
import com.example.thelastone.data.repo.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SpotsSource { TAIWAN, AROUND_ME }

data class ExploreUiState(
    val isLoading: Boolean = true,
    val error: String? = null,

    // Trips
    val popularTrips: List<Trip> = emptyList(),
    val isRefreshing: Boolean = false,

    // Spots
    val spots: List<PlaceLite> = emptyList(),
    val spotsLoading: Boolean = false,
    val spotsError: String? = null,
    val spotsInitialized: Boolean = false,
    val spotsSource: SpotsSource = SpotsSource.TAIWAN // 👈 新增
)



// ExploreViewModel.kt
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val tripRepo: TripRepository,
    private val spotRepo: SpotRepository
) : ViewModel() {

    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun popularTripsFlow(): Flow<List<Trip>> =
        tripRepo.observePublicTrips().map { list -> list.sortedBy { it.startDate } }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val popularResource: Flow<Result<List<Trip>>> =
        refresh.onStart { emit(Unit) }
            .flatMapLatest {
                popularTripsFlow()
                    .map { Result.success(it) }
                    .catch { e -> emit(Result.failure(e)) }
            }

    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    init {
        // Trips
        viewModelScope.launch {
            popularResource.scan(ExploreUiState()) { prev, result ->
                if (result.isSuccess) {
                    prev.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        popularTrips = result.getOrDefault(emptyList())
                    )
                } else {
                    prev.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = result.exceptionOrNull()?.message ?: "發生未知錯誤"
                    )
                }
            }.collect { _state.value = it }
        }
        // Spots 由畫面決定（有權限就附近，沒權限就台灣熱門）
    }

    fun refresh() {
        viewModelScope.launch { refresh.emit(Unit) }
        // Spots 的刷新交給畫面再決定叫哪一個（附近 or 台灣）
    }
    fun retry() = refresh()

    // ====== 你要的新方法 ======

    /** 使用者同意定位後：載入使用者附近 */
    fun loadSpotsAroundMe(
        userId: String? = null,
        limit: Int = 30,
        lat: Double,
        lng: Double,
        radiusMeters: Int = 5000,
        openNow: Boolean? = null
    ) {
        viewModelScope.launch {
            _state.update { it.copy(spotsLoading = true, spotsError = null) }
            runCatching {
                spotRepo.getRecommendedSpots(userId, limit, lat, lng, radiusMeters, openNow)
            }.onSuccess { list ->
                _state.update { it.copy(spots = list, spotsLoading = false, spotsInitialized = true, spotsSource = SpotsSource.AROUND_ME) }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        spotsError = e.message ?: "熱門景點載入失敗",
                        spotsLoading = false,
                        spotsInitialized = true // ✅ 失敗也算已初始化，避免顯示「重試」閃爍
                    )
                }
            }
        }
    }

    fun loadSpotsTaiwan(userId: String? = null, limit: Int = 30) {
        viewModelScope.launch {
            _state.update { it.copy(spotsLoading = true, spotsError = null) }
            runCatching { spotRepo.getTaiwanPopularSpots(userId, limit) }
                .onSuccess { list ->
                    _state.update { it.copy(spots = list, spotsLoading = false, spotsInitialized = true, spotsSource = SpotsSource.TAIWAN) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            spotsError = e.message ?: "熱門景點載入失敗",
                            spotsLoading = false,
                            spotsInitialized = true
                        )
                    }
                }
        }
    }
}