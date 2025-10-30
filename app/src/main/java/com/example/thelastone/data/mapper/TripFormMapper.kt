package com.example.thelastone.data.mapper

import com.example.thelastone.data.model.TripForm
import com.example.thelastone.data.remote.RecommendationForm
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 將 App 的 UI 表單 (TripForm) 轉換為 API 看得懂的表單 (RecommendationForm)
 */
fun TripForm.toApiRequestForm(excludeTerms: List<String>): RecommendationForm {

    // 從開始/結束日期計算天數
    val days = try {
        val start = LocalDate.parse(this.startDate)
        val end = LocalDate.parse(this.endDate)
        ChronoUnit.DAYS.between(start, end).toInt() + 1
    } catch (e: Exception) {
        1 // 預設 1 天
    }

    // 將 locations 字串切割成列表
    val locationsList = this.locations.split(Regex("[,、，\\s]+"))
        .filter { it.isNotBlank() }

    // 將 'transportPreferences' 第一個值對應到 'transportation'
    val transport = this.transportPreferences.firstOrNull() ?: "public"

    // 組合
    return RecommendationForm(
        locations = locationsList,
        days = days,
        preferences = this.styles, // 'styles' 欄位直接對應到 'preferences'
        exclude = excludeTerms,    // 使用從預覽畫面傳入的排除條件
        transportation = transport,
        notes = this.extraNote
    )
}
