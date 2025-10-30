package com.example.thelastone.data.model

import kotlinx.serialization.Serializable

// 用於描述 AI 推薦的單一地點 (替代選項或新增地點) - (非必需，但推薦用於結構化)
@Serializable
data class RecommendedPlaceDto(
    val place_id: String? = null,
    val name: String,
    val lat: Double? = null,
    val lng: Double? = null,
)
//
//// ⭐ 關鍵調整一：新增按鈕 DTO
//@Serializable
//data class ButtonDto(
//    val label: String,
//    val value: String
//)
//

// 描述單個 AI 建議的結構 (對應後端發送的 "recommendation" 字段)
@Serializable
data class AiRecommendationData(
    val type: String,   // 'modify', 'add', 'delete'
    val day: Int? = null,
    val place: String? = null, // 原景點名稱或新增景點名稱
    val reason: String? = null, // 替換原因

    // 💡 匹配後端傳送的 List<String> 景點名稱列表
    val new_places: List<String> = emptyList()

    // 如果後端未來升級為傳送 List<RecommendedPlaceDto>，這裡需要改成：
    // val new_places: List<RecommendedPlaceDto> = emptyList()
)


// ⭐ 關鍵調整二：更新最外層 Payload 以包含 buttons
//@Serializable
//data class AiResponsePayload(
//    val message: String, // AI 的文字提示
//    val recommendation: AiRecommendationData? = null, // 結構化的建議數據
//    val buttons: List<ButtonDto>? = null // <--- 讓前端可以渲染按鈕
//)
