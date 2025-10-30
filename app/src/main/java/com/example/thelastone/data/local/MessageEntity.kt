package com.example.thelastone.data.local

// data/local/MessageEntity.kt
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.thelastone.data.model.Message
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.data.model.User
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "messages",
    indices = [Index("tripId"), Index("timestamp")]
)
data class MessageEntity(
    @PrimaryKey val id: String,          // 伺服器 id；本地暫存用 local- 開頭，成功後用 server id 覆蓋
    val tripId: String,
    val senderId: String,
    val senderName: String?,
    val text: String,
    val timestamp: Long,
    val isAi: Boolean,
    val status: SendStatus,              // SENDING / SENT / FAILED
    val suggestionsJson: String? = null  // 用於存 PlaceLite[]（kotlinx.serialization / Moshi 都可）
)

enum class SendStatus { SENDING, SENT, FAILED }

// --- Mapper (你已有的 model) ---
fun MessageEntity.toModel(json: Json): Message =
    Message(
        id = id,
        tripId = tripId,
        sender = User(senderId, senderName ?: "User", email = ""),
        text = text,
        timestamp = timestamp,
        isAi = isAi,
        suggestions = suggestionsJson?.let { json.decodeFromString<List<PlaceLite>>(it) }
    )

fun Message.toEntity(json: Json, status: SendStatus): MessageEntity =
    MessageEntity(
        id = id,
        tripId = tripId,
        senderId = sender.id,
        senderName = sender.name,
        text = text,
        timestamp = timestamp,
        isAi = isAi,
        status = status,
        suggestionsJson = suggestions?.let { json.encodeToString(it) }
    )
