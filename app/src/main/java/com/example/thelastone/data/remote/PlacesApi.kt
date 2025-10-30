package com.example.thelastone.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

private const val PLACES_DETAILS_FIELD_MASK =
    "id,displayName,formattedAddress,shortFormattedAddress,location," +
            "rating,userRatingCount,photos.name,businessStatus,types," +
            "websiteUri,nationalPhoneNumber,priceLevel,utcOffsetMinutes," +
            "currentOpeningHours.openNow,currentOpeningHours.periods,currentOpeningHours.weekdayDescriptions," +
            "regularOpeningHours.periods"

private const val PLACES_LIST_FIELD_MASK =
    "places.id,places.displayName,places.formattedAddress,places.location," +
            "places.rating,places.userRatingCount,places.photos.name,places.businessStatus," +
            "places.utcOffsetMinutes,places.currentOpeningHours.openNow,places.currentOpeningHours.periods," +
            "places.currentOpeningHours.weekdayDescriptions,places.regularOpeningHours.periods"

private const val PLACES_NEARBY_FIELD_MASK =
    "places.id,places.displayName,places.formattedAddress,places.location," +
            "places.rating,places.userRatingCount,places.photos.name,places.businessStatus," +
            "places.utcOffsetMinutes,places.currentOpeningHours.openNow,places.currentOpeningHours.periods," +
            "places.currentOpeningHours.weekdayDescriptions,places.regularOpeningHours.periods"


interface PlacesApi {
    @POST("v1/places:searchText")
    @Headers("X-Goog-FieldMask: $PLACES_LIST_FIELD_MASK")
    suspend fun searchText(@Body body: SearchTextBody): SearchTextResponse

    @POST("v1/places:searchNearby")
    @Headers("X-Goog-FieldMask: $PLACES_NEARBY_FIELD_MASK")
    suspend fun searchNearby(@Body body: SearchNearbyBody): SearchNearbyResponse

    @GET("v1/places/{placeId}")
    suspend fun fetchDetails(
        @Path("placeId") placeId: String,
        @Header("X-Goog-FieldMask") fieldMask: String = PLACES_DETAILS_FIELD_MASK
    ): ApiPlace
}

/** ---- DTO ---- */
@Serializable
data class SearchTextBody(
    val textQuery: String,
    val locationBias: LocationBias? = null,
    val openNow: Boolean? = null,
    val languageCode: String? = null,   // ✅ 新增：指定回傳語言
    val regionCode: String? = null
)

@Serializable data class LocationBias(val circle: Circle? = null)
@Serializable data class Circle(val center: LatLng, val radius: Double)
@Serializable data class LatLng(val latitude: Double, val longitude: Double)

@Serializable data class SearchTextResponse(val places: List<ApiPlace>?)
@Serializable
data class ApiPlace(
    val id: String?,
    val displayName: TextVal?,
    val formattedAddress: String?,
    val shortFormattedAddress: String? = null,
    val location: LatLng?,
    val rating: Double? = null,
    val userRatingCount: Int? = null,
    val photos: List<ApiPhoto>? = null,
    val businessStatus: String? = null,
    val utcOffsetMinutes: Int? = null,
    val currentOpeningHours: ApiOpeningHours? = null,
    val regularOpeningHours: ApiOpeningHours? = null,

    // ⬇️ 新增這幾個讓 PlacesRepositoryImpl 能存取
    val websiteUri: String? = null,
    val nationalPhoneNumber: String? = null,
    val priceLevel: Int? = null,
    val types: List<String>? = null
)

@Serializable data class ApiPhoto(val name: String?)
@Serializable data class TextVal(val text: String?)

@Serializable
data class ApiOpeningHours(
    val openNow: Boolean? = null,
    val weekdayDescriptions: List<String>? = null,
    val periods: List<ApiPeriod>? = null        // ← 確保有 periods
)

@Serializable
data class ApiPeriod(
    val open: ApiPoint? = null,
    val close: ApiPoint? = null
)
@Serializable
data class ApiPoint(
    val day: Int? = null,   // ✅ 由 String? 改為 Int?
    val hour: Int? = null,
    val minute: Int? = null
)

@Serializable
data class SearchNearbyBody(
    val locationRestriction: LocationRestriction,         // 必填: 範圍
    val includedTypes: List<String>? = null,              // 最多 5 個 primary type
    val maxResultCount: Int? = 20,                        // 1..20
    val openNow: Boolean? = null,                         // 過濾營業中
    val rankPreference: String? = null,                   // "POPULARITY" | "DISTANCE"
    val languageCode: String? = null,
    val regionCode: String? = null
)

@Serializable data class LocationRestriction(val circle: Circle)
@Serializable
data class SearchNearbyResponse(
    val places: List<ApiPlace>?   // ✅ 這裡要是 ApiPlace（remote DTO）
)