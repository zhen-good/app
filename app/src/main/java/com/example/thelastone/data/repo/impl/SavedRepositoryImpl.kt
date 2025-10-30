package com.example.thelastone.data.repo.impl

import com.example.thelastone.data.local.SavedPlaceDao
import com.example.thelastone.data.local.SavedPlaceEntity
import com.example.thelastone.data.model.Place
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.data.model.SavedPlace
import com.example.thelastone.data.repo.SavedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SavedRepositoryImpl @Inject constructor(
    private val dao: SavedPlaceDao
) : SavedRepository {

    override fun observeIds(): Flow<Set<String>> =
        dao.observeIds().map { it.toSet() }

    // SavedRepositoryImpl.observeAll()
    override fun observeAll(): Flow<List<SavedPlace>> =
        dao.observeAll().map { list ->
            list.map { e ->
                val hours: List<String> =
                    e.openingHoursJson?.let {
                        try { Json.decodeFromString<List<String>>(it) } catch (_: Exception) { emptyList() }
                    } ?: emptyList()

                SavedPlace(
                    id = e.placeId,
                    userId = "local",
                    place = Place(
                        placeId = e.placeId,
                        name = e.name,
                        rating = e.rating,
                        userRatingsTotal = e.userRatingsTotal,
                        address = e.address,
                        lat = e.lat,
                        lng = e.lng,
                        photoUrl = e.photoUrl,
                        openingHours = hours,
                        openNow = e.openNow,                   // ✅ 帶回
                        openStatusText = e.openStatusText,     // ✅ 帶回
                        miniMapUrl = null
                    ),
                    savedAt = e.savedAt
                )
            }
        }


    override suspend fun save(place: PlaceLite) {
        val entity = SavedPlaceEntity(
            placeId = place.placeId,
            name = place.name,
            address = place.address,
            lat = place.lat,
            lng = place.lng,
            rating = place.rating,
            userRatingsTotal = place.userRatingsTotal,
            photoUrl = place.photoUrl,

            // ✅ 新增欄位寫入
            openingHoursJson = place.openingHours
                .takeIf { it.isNotEmpty() }
                ?.let { Json.encodeToString(it) },
            openNow = place.openNow,
            openStatusText = place.openStatusText
        )
        dao.upsert(entity)
    }


    override suspend fun unsave(placeId: String) {
        dao.delete(placeId)
    }

    override suspend fun toggle(place: PlaceLite) {
        val deleted = dao.delete(place.placeId)
        if (deleted == 0) {
            save(place)
        }
    }
}
