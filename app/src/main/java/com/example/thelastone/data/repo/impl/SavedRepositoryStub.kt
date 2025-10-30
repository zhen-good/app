// data/repo/impl/SavedRepositoryStub.kt
package com.example.thelastone.data.repo.impl

import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.data.model.SavedPlace
import com.example.thelastone.data.repo.SavedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedRepositoryStub @Inject constructor() : SavedRepository {

    // 先回傳空的 Flow，之後再換成真正資料來源
    override fun observeIds(): Flow<Set<String>> = flowOf(emptySet())

    override fun observeAll(): Flow<List<SavedPlace>> = flowOf(emptyList())

    // 先不實作任何動作（no-op）
    override suspend fun save(place: PlaceLite) { /* no-op */ }

    override suspend fun unsave(placeId: String) { /* no-op */ }

    override suspend fun toggle(place: PlaceLite) { /* no-op */ }
}
