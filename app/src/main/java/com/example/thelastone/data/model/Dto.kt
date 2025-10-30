package com.example.thelastone.data.model

import kotlinx.serialization.Serializable



//--------------------------------------------------------------

// 匹配 JSON 中 places 陣列內的物件
//這是分析的卡片用的
@Serializable
data class TripNodePlaceDto(
    // 請根據您的後端 JSON (包含 nodes 的結構) 精確匹配欄位名稱
    val place_id: String,
    val name: String,
    val rating: Double? = null,
    val reviews: Int? = null, // 這是 userRatingsTotal 的來源
    val address: String? = null,
    val lat: Double,
    val lng: Double,
    val open_text: String? = null, // 假設後端用這個欄位傳遞營業狀態文案
    // 如果後端推送的 JSON 裡還有其他欄位，您也需要在這裡定義
)

// 匹配 JSON 中 nodes 陣列內的物件
@Serializable
data class TripNodeDto(
    val node_id: String,
    val day: Int,
    // ... 其他節點欄位 (start, end, slot 等)
    val places: List<TripNodePlaceDto>
)

// 匹配 Socket 訊息的最外層結構
// 由於您的 JSON 結構看起來像 {"trip": {"nodes": [...]}}，我們需要匹配它
@Serializable
data class SocketTripInner(
    val nodes: List<TripNodeDto>
)

//---------------------------------------------

// DTO 1: 按鈕的資料結構
@Serializable
data class ButtonDto(
    val label: String, // 按鈕上顯示的文字，例如 "1. Shonojuku Museum" 或 "略過"
    val value: String  // 點擊後要回傳給後端的內容，例如 "1" 或 "略過"
)

// DTO 2: 更新 AiResponsePayload (假設這是您處理 "ai_response" 事件的 DTO)
// 確保您使用這個 DTO 來接收 ai_response 事件的完整內容。
@Serializable
data class AiResponsePayload(
    val message: String, // 🎯 修正：設定為可空並給予預設值
    val recommendation: AiRecommendationData? = null, // 結構化的建議數據
    val buttons: List<ButtonDto>? = null // <--- 讓前端可以渲染按鈕的關鍵
    // 其他欄位，例如 user_id, type 等如果後端有傳送，也應該在這裡定義
)