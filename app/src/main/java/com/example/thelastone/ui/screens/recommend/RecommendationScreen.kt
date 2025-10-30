package com.example.thelastone.ui.screens.recommend // 👈 確保 package name 正確

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.thelastone.data.remote.RecommendationForm
import com.example.thelastone.data.remote.RecommendationResponse

@Composable
fun RecommendationScreen(
    // 👈 Hilt 會自動提供已設定好的 ViewModel 實例
    viewModel: RecommendationViewModel = hiltViewModel()
) {
    // 收集 ViewModel 的狀態
    // 當 uiState 改變時，這個 Composable 會自動重組 (Recompose)
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                // ❗ 這裡我們使用假的表單資料
                // ❗ 未來您應該從 UI 上的 TextField 讀取這些值
                val testForm = RecommendationForm(
                    locations = listOf("台北", "九份"),
                    days = 2,
                    preferences = listOf("美食", "風景"),
                    exclude = listOf("購物"),
                    transportation = "public", // 或是 "driving"
                    notes = "希望行程輕鬆一點"
                )

                // ❗ 這裡我們使用假的使用者 ID
                // ❗ 未來您應該從您的登入系統 (Firebase Auth) 取得
                val testUserId = "user_123"

                // 呼叫 ViewModel 的函式
                viewModel.fetchRecommendations(testUserId, testForm)
            },
            // 當處於載入狀態時，禁用按鈕
            enabled = uiState !is RecommendationUiState.Loading
        ) {
            Text("產生推薦行程")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 根據不同狀態顯示不同畫面
        when (val state = uiState) {
            is RecommendationUiState.Idle -> {
                Text("請點擊按鈕以產生行程。")
            }
            is RecommendationUiState.Loading -> {
                CircularProgressIndicator() // 顯示讀取中轉圈圈
            }
            is RecommendationUiState.Success -> {
                // 成功時，顯示結果
                RecommendationResult(response = state.data)
            }
            is RecommendationUiState.Error -> {
                // 失敗時，顯示錯誤訊息
                Text(text = "發生錯誤: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * 用於顯示成功結果的 Composable
 */
@Composable
fun RecommendationResult(response: RecommendationResponse) {

    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = response.tripName, style = MaterialTheme.typography.headlineMedium)
        Text(text = "地點: ${response.locationsText}", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // **使用 WebView 顯示 API 回傳的 HTML (最快的方法)**
        // 您的 API 回傳的 'itineraryHtml' 可以直接在這裡顯示
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    // 載入 API 回傳的 HTML 字串
                    loadDataWithBaseURL(null, response.itineraryHtml, "text/html", "UTF-8", null)
                }
            }
        )
    }
}