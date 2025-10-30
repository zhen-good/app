package com.example.thelastone.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// data/local/MessageDao.kt
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun observeByTrip(tripId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MessageEntity)

    @Query("UPDATE messages SET id = :newId, status = :newStatus WHERE id = :localId")
    suspend fun promoteLocalToServer(localId: String, newId: String, newStatus: SendStatus = SendStatus.SENT)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: SendStatus)

    @Query("DELETE FROM messages WHERE tripId = :tripId")
    suspend fun deleteByTrip(tripId: String)
}