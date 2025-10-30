package com.example.thelastone.data.model

// UI / ViewModel 層要使用的「乾淨資料模型」
data class Message(
    val id: String,
    val tripId: String,
    val sender: User,
    val text: String,
    val timestamp: Long,           // 毫秒
    val isAi: Boolean = false,     // 系統 AI 訊息
    val suggestions: List<PlaceLite>? = null, // ← AI 訊息可帶三個建議地點
    //看訊息是不是問題的訊息，是的話就改成用新版的問題格式
    val isQuestion: Boolean = false,
    val question: SingleChoiceQuestion? = null,
    val buttons: List<ButtonDto>? = null, // 確保您已導入 ButtonDto
    val singleChoiceQuestion: SingleChoiceQuestion? = null,
)

data class AnalysisResult(
    val aiText: String,
    val suggestions: List<PlaceLite>
)