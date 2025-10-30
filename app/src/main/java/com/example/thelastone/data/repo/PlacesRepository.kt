package com.example.thelastone.data.repo

import com.example.thelastone.data.model.PlaceDetails
import com.example.thelastone.data.model.PlaceLite

// data/repo/PlacesRepository.kt
interface PlacesRepository {
    suspend fun searchText(
        query: String,
        lat: Double? = null,
        lng: Double? = null,
        radiusMeters: Int? = null,     // ← 用 Int，避免小數
        openNow: Boolean? = null
    ): List<PlaceLite>

    suspend fun searchNearby(
        lat: Double,
        lng: Double,
        radiusMeters: Int,
        includedTypes: List<String> = listOf("tourist_attraction"),
        rankPreference: RankPreference = RankPreference.POPULARITY,   // ← enum
        openNow: Boolean? = null,
        maxResultCount: Int = 20
    ): List<PlaceLite>

    suspend fun fetchDetails(placeId: String): PlaceDetails          // ← 新增
    fun buildPhotoUrl(photoName: String, maxWidth: Int = 800): String // ← 集中建置
}

enum class RankPreference { POPULARITY, DISTANCE }