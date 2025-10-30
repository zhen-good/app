package com.example.thelastone.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 您的 FastAPI 伺服器期望收到的「表單」格式
 */
@Serializable
data class RecommendationForm(
    @SerialName("locations") val locations: List<String>,
    @SerialName("days") val days: Int,
    @SerialName("preferences") val preferences: List<String>,
    @SerialName("exclude") val exclude: List<String>,
    @SerialName("transportation") val transportation: String,
    @SerialName("notes") val notes: String? = null
)

/**
 * App 實際發送的「請求」物件，它會包裝上面的表單
 */
@Serializable
data class RecommendRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("form") val form: RecommendationForm
)

/**
 * 用於解析 API 回應中 `used_places` 列表的資料模型
 */
@Serializable
data class RecommendedPlace(
    @SerialName("name") val name: String? = "Unknown Place",
    @SerialName("place_id") val placeId: String? = null
)

/**
 * App 預期從您的 FastAPI 伺服器收到的「回應」格式
 */
@Serializable
data class RecommendationResponse(
    @SerialName("trip_name") val tripName: String,
    @SerialName("html") val itineraryHtml: String,
    @SerialName("markdown") val markdown: String,
    @SerialName("summary") val summary: String,
    @SerialName("days") val days: Int,
    @SerialName("used_places") val usedPlaces: List<RecommendedPlace>,
    @SerialName("locations_text") val locationsText: String,
    @SerialName("error") val error: Boolean,
    @SerialName("error_message") val errorMessage: String? = null
)
