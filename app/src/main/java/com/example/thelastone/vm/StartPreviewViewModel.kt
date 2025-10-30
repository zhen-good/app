package com.example.thelastone.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.mapper.toApiRequestForm
import com.example.thelastone.data.model.TripForm
import com.example.thelastone.data.remote.ApiService
import com.example.thelastone.data.remote.RecommendRequest
import com.example.thelastone.data.remote.RecommendationResponse
import com.example.thelastone.data.repo.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// 定義 AI 行程生成的 UI 狀態
sealed interface GenerationState {
    data object Idle : GenerationState
    data object Loading : GenerationState
    data class Success(val response: RecommendationResponse) : GenerationState
    data class Error(val message: String) : GenerationState
}

data class StartPreviewUiState(
    val form: TripForm? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val generationState: GenerationState = GenerationState.Idle // 用來追蹤 API 呼叫狀態
)

@HiltViewModel
class StartPreviewViewModel @Inject constructor(
    private val tripRepo: TripRepository,
    private val apiService: ApiService // 👈 [注入] 我們的 API Service
) : ViewModel() {

    private val _state = MutableStateFlow(StartPreviewUiState())
    val state = _state.asStateFlow()

    init {
        loadForm()
    }

    private fun loadForm() {
        viewModelScope.launch {
            val form = tripRepo.getTripFormForPreview()
            _state.update {
                if (form != null) {
                    it.copy(form = form, loading = false)
                } else {
                    it.copy(error = "無法載入表單資料", loading = false)
                }
            }
        }
    }

    /**
     * 呼叫 AI API 來生成行程
     * @param userId 目前登入的使用者 ID
     * @param excludeInput 使用者在 UI 上輸入的排除條件字串
     */
    fun generateItinerary(userId: String, excludeInput: String) {
        val currentForm = _state.value.form ?: return

        // 1. 將使用者輸入的排除條件字串轉為列表
        val excludeTerms = excludeInput.split(Regex("[,、，\\s]+"))
            .filter { it.isNotBlank() }

        // 2. 更新 UI 狀態為 Loading
        _state.update { it.copy(generationState = GenerationState.Loading) }

        viewModelScope.launch {
            try {
                // 3. 使用 Mapper 轉換表單，並傳入排除條件
                val apiRequestForm = currentForm.toApiRequestForm(excludeTerms)
                val request = RecommendRequest(userId = userId, form = apiRequestForm)

                // 4. 呼叫 API
                val response = apiService.getRecommendations(request)

                // 5. 根據結果更新 UI 狀態
                if (response.error) {
                    _state.update {
                        it.copy(generationState = GenerationState.Error(response.errorMessage ?: "API 回報錯誤"))
                    }
                } else {
                    _state.update {
                        it.copy(generationState = GenerationState.Success(response))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _state.update {
                    it.copy(generationState = GenerationState.Error(e.message ?: "未知的網路錯誤"))
                }
            }
        }
    }
}

