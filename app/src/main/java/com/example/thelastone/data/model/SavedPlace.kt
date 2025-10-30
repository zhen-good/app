package com.example.thelastone.data.model

data class SavedPlace(
    val id: String,                // 唯一 ID（可以是 placeId 或後端生成）
    val userId: String,            // 收藏者 ID
    val place: Place,
    val savedAt: Long              // 時間戳記（毫秒）
)
