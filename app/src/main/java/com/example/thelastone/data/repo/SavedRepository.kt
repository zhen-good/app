package com.example.thelastone.data.repo

import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.data.model.SavedPlace
import kotlinx.coroutines.flow.Flow

interface SavedRepository {
    fun observeIds(): Flow<Set<String>>
    fun observeAll(): Flow<List<SavedPlace>>
    suspend fun save(place: PlaceLite)
    suspend fun unsave(placeId: String)
    suspend fun toggle(place: PlaceLite)
}