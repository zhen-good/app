package com.example.thelastone.data.model

// [ [ [ 在這裡加回 AgeBand 的定義 ] ] ]
enum class AgeBand {
    IGNORE, UNDER_17, A18_25, A26_35, A36_45, A46_55, A56_PLUS
}

data class TripForm(
    val locations: String = "", // 👈 [新增] 旅遊地點
    val name: String,
    val totalBudget: Int?,
    val startDate: String,
    val endDate: String,
    val activityStart: String?,
    val activityEnd: String?,
    val transportPreferences: List<String>,
    val useGmapsRating: Boolean,
    val styles: List<String>,
    val avgAge: AgeBand, // ✅ 現在這個 AgeBand 就可以被正確識別了
    val visibility: TripVisibility = TripVisibility.PRIVATE,
    val extraNote: String? = null,
    val aiDisclaimerChecked: Boolean = false // 您的 GitHub 有這個欄位，我幫您加上
)

