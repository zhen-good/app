package com.example.thelastone.data.model

import kotlinx.serialization.Serializable

@Serializable
data class StartInfo(
    val placeId: String,
    val weather: WeatherInfo,
    val openNow: Boolean?,
    val openStatusText: String?,
    val openingHours: List<String> = emptyList(),
    val alternatives: List<Alternative> = emptyList(), // 這一頁的前三大選項
    val page: Int = 0                                   // 後端回傳目前頁碼（用於查看更多）
)

@Serializable
data class WeatherInfo(
    val summary: String,          // e.g., "多雲時晴"
    val temperatureC: Int,        // 例如 31
    val rainProbability: Int? = null // 0-100，可選
)

@Serializable
data class Alternative(
    val placeId: String,
    val name: String,
    val address: String? = null,
    val rating: Double? = null,
    val userRatingsTotal: Int? = null,
    val lat: Double,
    val lng: Double,
    val openStatusText: String? = null,
    val photoUrl: String? = null
)
