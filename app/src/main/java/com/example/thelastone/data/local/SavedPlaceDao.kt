package com.example.thelastone.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPlaceDao {
    @Query("SELECT placeId FROM saved_places")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT * FROM saved_places ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedPlaceEntity)

    @Query("DELETE FROM saved_places WHERE placeId = :placeId")
    suspend fun delete(placeId: String): Int // ← 回傳 Int
}
