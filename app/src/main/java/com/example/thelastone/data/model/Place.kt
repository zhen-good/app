package com.example.thelastone.data.model

import kotlinx.serialization.Serializable

data class Place(
    val placeId: String,
    val name: String,
    val rating: Double?,
    val userRatingsTotal: Int?,
    val address: String?,
    val openingHours: List<String> = emptyList(),
    val openNow: Boolean? = null,          // ← 新增
    val openStatusText: String? = null,    // ← 新增
    val lat: Double,
    val lng: Double,
    val photoUrl: String? = null,
    val miniMapUrl: String? = null
)


// data/model/Place.kt
data class PlaceDetails(
    val placeId: String,
    val name: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val rating: Double?,
    val userRatingsTotal: Int?,
    val photoUrl: String?,
    val types: List<String> = emptyList(),
    val websiteUri: String? = null,
    val nationalPhoneNumber: String? = null,
    val priceLevel: Int? = null,
    val openingHours: List<String> = emptyList(), // weekdayDescriptions
    val openNow: Boolean? = null,
    val openStatusText: String? = null
)


@Serializable
data class PlaceLite(
    val placeId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String? = null,
    val rating: Double? = null,
    val userRatingsTotal: Int? = null,
    val photoUrl: String? = null,
    val openingHours: List<String> = emptyList(), // 👈 新增
    val openNow: Boolean? = null,                  // 👈 可選（若要顯示「營業中」）
    val openStatusText: String? = null            // ✅ 算好的文案：「營業中 · 至 21:00」
)

fun Place.toLite() = PlaceLite(
    placeId = placeId,
    name = name,
    lat = lat,
    lng = lng,
    address = address,
    rating = rating,
    userRatingsTotal = userRatingsTotal,
    photoUrl = photoUrl,
    openingHours = openingHours,
    openNow = null,             // 如果此時沒有，就先 null
    openStatusText = null
)

fun PlaceLite.toFull(): Place = Place(
    placeId = placeId,
    name = name,
    rating = rating,
    userRatingsTotal = userRatingsTotal,
    address = address,
    lat = lat,
    lng = lng,
    photoUrl = photoUrl,
    openingHours = openingHours,
    miniMapUrl = null
)
