package com.example.thelastone.data.repo

import com.example.thelastone.data.model.PlaceLite
import javax.inject.Inject

// data/repo/SpotRepository.kt
interface SpotRepository {
    // 附近熱門（可帶座標）；若 lat/lng 為 null，實作可退回全域熱門
    suspend fun getRecommendedSpots(
        userId: String? = null,
        limit: Int = 30,
        lat: Double? = null,
        lng: Double? = null,
        radiusMeters: Int? = null,
        openNow: Boolean? = null
    ): List<PlaceLite>

    // 台灣熱門（無定位就用這個）
    suspend fun getTaiwanPopularSpots(
        userId: String? = null,
        limit: Int = 30
    ): List<PlaceLite>
}



// 之後切到後端時，改寫這個實作就好（或新增 BackendSpotRepository）。
class DefaultSpotRepository @Inject constructor(
    private val placesRepo: PlacesRepository
) : SpotRepository {

    override suspend fun getRecommendedSpots(
        userId: String?,
        limit: Int,
        lat: Double?,
        lng: Double?,
        radiusMeters: Int?,
        openNow: Boolean?
    ): List<PlaceLite> {
        // 有座標 → 以使用者附近搜尋熱門景點；沒有就退回全球熱門
        return placesRepo.searchText(
            query = "top tourist attractions",
            lat = lat, lng = lng,
            radiusMeters = radiusMeters ?: if (lat != null && lng != null) 5000 else null,
            openNow = openNow
        ).take(limit)
    }

    override suspend fun getTaiwanPopularSpots(userId: String?, limit: Int): List<PlaceLite> {
        val taiwanLat = 23.6978
        val taiwanLng = 120.9605

        return placesRepo.searchText(
            query = "top tourist attractions Taiwan",
            lat = taiwanLat,
            lng = taiwanLng,
            radiusMeters = 50_000, // ⬅️ 修正：不得超過 50,000
            openNow = null
        ).take(limit)
    }
}