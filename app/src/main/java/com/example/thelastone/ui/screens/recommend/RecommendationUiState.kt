package com.example.thelastone.ui.screens.recommend // 👈 確保 package name 正確

import com.example.thelastone.data.remote.RecommendationResponse

// 定義 UI 狀態 (更適合 Compose)
sealed interface RecommendationUiState {
    object Idle : RecommendationUiState    // 閒置狀態
    object Loading : RecommendationUiState // 載入中
    data class Success(val data: RecommendationResponse) : RecommendationUiState // 成功
    data class Error(val message: String) : RecommendationUiState // 失敗
}