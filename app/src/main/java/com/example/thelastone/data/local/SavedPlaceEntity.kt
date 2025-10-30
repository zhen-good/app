package com.example.thelastone.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_places",
    indices = [Index(value = ["placeId"], unique = true)]
)
/**
 * 先做單使用者版本（以 placeId 作唯一主鍵）。
 * 未來若要支援多帳號，可改成 composite key（userId + placeId）並做 migration。
 */
data class SavedPlaceEntity(
    @PrimaryKey val placeId: String,
    val name: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val rating: Double?,
    val userRatingsTotal: Int?,
    val photoUrl: String?,
    val openingHoursJson: String? = null, // 新增
    val openNow: Boolean? = null,         // 可選
    val openStatusText: String? = null,   // 可選
    val savedAt: Long = System.currentTimeMillis()
)