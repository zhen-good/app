package com.example.thelastone.data.model

// 確保您專案中已定義這些 enum 和 data class
// enum class AgeBand (在 TripForm.kt 中)
// data class User (在 User.kt 中)
// data class Place (在 Place.kt 中)

enum class TripVisibility { PUBLIC, PRIVATE }

data class Trip(
    val id: String,
    val createdBy: String,
    val name: String,
    val locations: String, // 👈 [ [ [ 在這裡加上新的欄位 ] ] ]
    val totalBudget: Int?,
    val startDate: String,
    val endDate: String,
    val activityStart: String?,
    val activityEnd: String?,
    val avgAge: AgeBand,
    val transportPreferences: List<String>,
    val useGmapsRating: Boolean,
    val styles: List<String>,
    val visibility: TripVisibility = TripVisibility.PRIVATE,
    val members: List<User> = emptyList(),
    val days: List<DaySchedule> = emptyList()
)

data class DaySchedule(
    val date: String,
    val activities: List<Activity> = emptyList()
)

data class Activity(
    val id: String,
    val place: Place,
    val startTime: String? = null,  // "09:00"
    val endTime: String? = null,    // "11:30"
    val note: String? = null
)

// 您 GitHub 中的輔助函式，保持不變
fun Trip.coverPhotoUrl(): String? {
    for (day in days) {
        for (act in day.activities) {
            val url = act.place.photoUrl
            if (!url.isNullOrBlank()) return url
        }
    }
    return null
}

