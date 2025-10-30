//package com.example.thelastone.data.repo.impl.fake
//
//import com.example.thelastone.data.model.PlaceLite
//import com.example.thelastone.data.remote.AnalyzeBody
//import com.example.thelastone.data.remote.AnalyzeResponse
//import com.example.thelastone.data.remote.ChatService
//import com.example.thelastone.data.remote.MessageDto
//import com.example.thelastone.data.remote.SendMessageBody
//import com.example.thelastone.di.SessionManager
//import kotlinx.coroutines.delay
//import javax.inject.Inject
//import javax.inject.Singleton
//import kotlin.random.Random
//
//@Singleton
//class FakeChatService @Inject constructor(
//    private val session: SessionManager
//) : ChatService {
//
//    private val store = mutableMapOf<String, MutableList<MessageDto>>()
//
//    override suspend fun getHistory(tripId: String): List<MessageDto> {
//        delay(200)
//        return store[tripId]?.toList() ?: emptyList()
//    }
//
//    override suspend fun sendMessage(tripId: String, body: SendMessageBody): MessageDto {
//        delay(150)
//        val me = session.auth.value?.user ?: error("Require login")
//        val dto = MessageDto(
//            id = "srv-${System.nanoTime()}",
//            tripId = tripId,
//            senderId = me.id,
//            senderName = me.name,
//            text = body.text,
//            timestamp = System.currentTimeMillis(),
//            isAi = false,
//            suggestions = null
//        )
//        store.getOrPut(tripId) { mutableListOf() }.add(dto)
//        return dto
//    }
//
//    override suspend fun analyze(tripId: String, body: AnalyzeBody): AnalyzeResponse {
//        delay(650)
//        val baseLat = 25.04 + Random.nextDouble(-0.02, 0.02)
//        val baseLng = 121.56 + Random.nextDouble(-0.02, 0.02)
//        val sug = listOf(
//            PlaceLite("alt-1", "松菸文創園區", baseLat, baseLng, "台北市信義區", 4.5, 23000),
//            PlaceLite("alt-2", "華山1914文化創意產業園區", baseLat+0.01, baseLng+0.01, "台北市中正區", 4.4, 41000),
//            PlaceLite("alt-3", "台北小巨蛋商圈", baseLat-0.01, baseLng-0.01, "台北市松山區", 4.3, 12000),
//        )
//        // 也把 AI 訊息塞進假歷史裡（模擬推播/回寫）
//        val ai = MessageDto(
//            id = "srv-${System.nanoTime()}",
//            tripId = tripId,
//            senderId = "ai",
//            senderName = "Trip AI",
//            text = "這個景點目前沒有營業，是否更換為以下景點？",
//            timestamp = System.currentTimeMillis(),
//            isAi = true,
//            suggestions = sug
//        )
//        store.getOrPut(tripId) { mutableListOf() }.add(ai)
//        return AnalyzeResponse(aiText = ai.text, suggestions = sug)
//    }
//}