package com.example.thelastone.data.remote

// data/remote/ChatService.kt
import com.example.thelastone.data.model.PlaceLite
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ChatService {
    @GET("trips/{tripId}/messages")
    suspend fun getHistory(@Path("tripId") tripId: String): List<MessageDto>

//    @POST("trips/{tripId}/messages")
//    suspend fun sendMessage(@Path("tripId") tripId: String, @Body body: SendMessageBody): MessageDto

    @POST("trips/{tripId}/analyze")
    suspend fun analyze(@Path("tripId") tripId: String, @Body body: AnalyzeBody): AnalyzeResponse
}

data class MessageDto(
    val id: String,
    val tripId: String,
    val senderId: String,
    val senderName: String?,
    val text: String,
    val timestamp: Long,
    val isAi: Boolean,
    val suggestions: List<PlaceLite>?
)

data class SendMessageBody(val text: String)
data class AnalyzeBody(val history: List<MessageDto>)
data class AnalyzeResponse(val aiText: String, val suggestions: List<PlaceLite>)