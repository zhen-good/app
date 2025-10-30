package com.example.thelastone.data.repo.impl

import com.example.thelastone.BuildConfig
import com.example.thelastone.data.model.PlaceDetails
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.data.remote.ApiPlace
import com.example.thelastone.data.remote.Circle
import com.example.thelastone.data.remote.LatLng
import com.example.thelastone.data.remote.LocationBias
import com.example.thelastone.data.remote.LocationRestriction
import com.example.thelastone.data.remote.PlacesApi
import com.example.thelastone.data.remote.SearchNearbyBody
import com.example.thelastone.data.remote.SearchTextBody
import com.example.thelastone.data.repo.PlacesRepository
import com.example.thelastone.data.repo.RankPreference
import com.example.thelastone.di.GoogleApi // 👈 [新增] 匯入我們的標籤
import com.example.thelastone.utils.buildOpenStatus
import com.example.thelastone.utils.stripCountryTaiwanPrefix
import com.example.thelastone.utils.stripPostalCodeIfAny
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
// data/repo/impl/PlacesRepositoryImpl.kt
class PlacesRepositoryImpl @Inject constructor(
    @GoogleApi private val api: PlacesApi // 👈 [ [ [ 最重要的修改：加上 @GoogleApi 標籤 ] ] ]
) : PlacesRepository {

    override fun buildPhotoUrl(photoName: String, maxWidth: Int): String =
        "https://places.googleapis.com/v1/$photoName/media?maxWidthPx=$maxWidth&key=${BuildConfig.MAPS_API_KEY}"

    override suspend fun searchText(
        query: String,
        lat: Double?, lng: Double?, radiusMeters: Int?, openNow: Boolean?
    ): List<PlaceLite> {
        val bias = if (lat != null && lng != null && radiusMeters != null)
            LocationBias(circle = Circle(center = LatLng(lat, lng), radius = radiusMeters.toDouble()))
        else null

        val resp = api.searchText(
            SearchTextBody(
                textQuery = query,
                locationBias = bias,
                openNow = openNow,
                languageCode = "zh-TW",
                regionCode = "TW"
            )
        )
        return mapApiPlacesToLite(resp.places)
    }

    override suspend fun searchNearby(
        lat: Double,
        lng: Double,
        radiusMeters: Int,
        includedTypes: List<String>,
        rankPreference: RankPreference,
        openNow: Boolean?,
        maxResultCount: Int
    ): List<PlaceLite> {
        val resp = api.searchNearby(
            SearchNearbyBody(
                locationRestriction = LocationRestriction(
                    circle = Circle(center = LatLng(lat, lng), radius = radiusMeters.toDouble())
                ),
                includedTypes = includedTypes,
                maxResultCount = maxResultCount.coerceIn(1, 20),
                openNow = openNow,
                rankPreference = rankPreference.name,   // enum → API 字串
                languageCode = "zh-TW",
                regionCode = "TW"
            )
        )
        return mapApiPlacesToLite(resp.places)
    }

    override suspend fun fetchDetails(placeId: String): PlaceDetails {
        // 這裡不要加 "places/"
        val apiPlace = api.fetchDetails(placeId = placeId)

        val id = apiPlace.id?.substringAfter("places/") ?: placeId
        val photoName = apiPlace.photos?.firstOrNull()?.name
        val photoUrl = photoName?.let { buildPhotoUrl(it, 800) }
        val status = buildOpenStatus(
            current = apiPlace.currentOpeningHours,
            regular = apiPlace.regularOpeningHours,
            utcOffsetMinutes = apiPlace.utcOffsetMinutes ?: 0
        )

        return PlaceDetails(
            placeId = id,
            name = apiPlace.displayName?.text ?: "未命名地點",
            address = apiPlace.formattedAddress
                ?.let(::stripPostalCodeIfAny)
                ?.let(::stripCountryTaiwanPrefix),
            lat = apiPlace.location?.latitude ?: 0.0,
            lng = apiPlace.location?.longitude ?: 0.0,
            rating = apiPlace.rating,
            userRatingsTotal = apiPlace.userRatingCount,
            photoUrl = photoUrl,
            types = emptyList(),
            websiteUri = apiPlace.websiteUri,
            nationalPhoneNumber = apiPlace.nationalPhoneNumber,
            priceLevel = apiPlace.priceLevel,
            openingHours = apiPlace.currentOpeningHours?.weekdayDescriptions.orEmpty(),
            openNow = status?.openNow ?: apiPlace.currentOpeningHours?.openNow,
            openStatusText = status?.text
        )
    }

    private fun mapApiPlacesToLite(list: List<ApiPlace>?): List<PlaceLite> =
        list.orEmpty().mapNotNull { p ->
            val id = p.id?.substringAfter("places/") ?: return@mapNotNull null
            val name = p.displayName?.text ?: "未命名地點"
            val la = p.location?.latitude ?: 0.0
            val lo = p.location?.longitude ?: 0.0

            val photoUrl = p.photos?.firstOrNull()?.name?.let { buildPhotoUrl(it, 400) }

            val addr = p.formattedAddress
                ?.let(::stripPostalCodeIfAny)
                ?.let(::stripCountryTaiwanPrefix)

            val status = buildOpenStatus(
                current = p.currentOpeningHours,
                regular = p.regularOpeningHours,
                utcOffsetMinutes = p.utcOffsetMinutes ?: 0
            )

            PlaceLite(
                placeId = id,
                name = name,
                lat = la,
                lng = lo,
                address = addr,
                rating = p.rating,
                userRatingsTotal = p.userRatingCount,
                photoUrl = photoUrl,
                openingHours = p.currentOpeningHours?.weekdayDescriptions ?: emptyList(),
                openNow = status?.openNow ?: p.currentOpeningHours?.openNow,
                openStatusText = status?.text
            )
        }
}

