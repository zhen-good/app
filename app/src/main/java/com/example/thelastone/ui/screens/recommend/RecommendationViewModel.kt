package com.example.thelastone.ui.screens.recommend // 👈 確保 package name 正確

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.remote.ApiService
import com.example.thelastone.data.remote.RecommendationForm
import com.example.thelastone.data.remote.RecommendRequest // 👈 確保您有 import 這個 data class
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel // 👈 告訴 Hilt 這是個 ViewModel
class RecommendationViewModel @Inject constructor(
    private val apiService: ApiService // 👈 Hilt 會自動從 RecommendModule 取得 ApiService 實例
) : ViewModel() {

    // 私有的 MutableStateFlow，只在 ViewModel 內部更改
    private val _uiState = MutableStateFlow<RecommendationUiState>(RecommendationUiState.Idle)
    // 公開的 StateFlow，供 UI 觀察
    val uiState: StateFlow<RecommendationUiState> = _uiState

    /**
     * 供 UI 呼叫的函式，用來觸發 API
     */
    fun fetchRecommendations(userId: String, form: RecommendationForm) {

        // 1. 馬上將狀態設為 "載入中"，通知 UI 顯示 ProgressBar
        _uiState.value = RecommendationUiState.Loading

        // 2. 啟動一個協程
        viewModelScope.launch {
            try {
                // 3. 準備請求物件
                val request = RecommendRequest(userId = userId, form = form)

                // 4. 呼叫 API
                val response = apiService.getRecommendations(request)

                // 5. 根據 API 回應更新狀態
                if (response.error) {
                    _uiState.value = RecommendationUiState.Error(response.errorMessage ?: "API 回報錯誤")
                } else {
                    _uiState.value = RecommendationUiState.Success(response)
                }

            } catch (e: Exception) {
                // 6. 捕捉任何網路或解析錯誤
                e.printStackTrace()
                _uiState.value = RecommendationUiState.Error(e.message ?: "未知的網路錯誤")
            }
        }
    }
}